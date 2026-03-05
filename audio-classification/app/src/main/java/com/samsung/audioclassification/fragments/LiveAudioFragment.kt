package com.samsung.audioclassification.fragments

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.samsung.audioclassification.data.ModelConstants
import com.samsung.audioclassification.databinding.FragmentLiveAudioBinding
import com.samsung.audioclassification.executor.ModelExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveAudioFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentLiveAudioBinding
    private lateinit var modelExecutor: ModelExecutor
    private lateinit var detectedItems: List<DetectedItemViewGroup>

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var inferenceJob: Job? = null
    private var isRecording = false

    // Multi-frame buffer for YAMNet (15600 samples)
    private val windowSize = ModelConstants.STFT_WINDOW_SIZE + (ModelConstants.INPUT_SIZE_H - 1) * ModelConstants.STFT_HOP_SIZE
    private val audioBuffer = FloatArray(windowSize)
    private var bufferPointer = 0

    private val inferenceIntervalMs = 300L

    data class DetectedItemViewGroup(
        val label: TextView,
        val score: TextView,
        val gauge: ProgressBar,
        val container: View,
        var lastScore: Float = 0f
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentLiveAudioBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        modelExecutor = ModelExecutor(context = requireContext(), executorListener = this)
        setUI()
        startLiveInference()
    }

    private fun setUI() {
        detectedItems = listOf(
            DetectedItemViewGroup(
                binding.detectedItem0,
                binding.detectedItem0Score,
                binding.detectedItem0Gauge,
                binding.detectedBlock0
            ),
            DetectedItemViewGroup(
                binding.detectedItem1,
                binding.detectedItem1Score,
                binding.detectedItem1Gauge,
                binding.detectedBlock1
            ),
            DetectedItemViewGroup(
                binding.detectedItem2,
                binding.detectedItem2Score,
                binding.detectedItem2Gauge,
                binding.detectedBlock2
            )
        )
        binding.processData.textThreshold.text = String.format("%.1f", modelExecutor.threshold)

        binding.processData.buttonThresholdPlus.setOnClickListener { adjustThreshold(0.1f) }
        binding.processData.buttonThresholdMinus.setOnClickListener { adjustThreshold(-0.1f) }
    }

    @SuppressLint("MissingPermission")
    private fun startLiveInference() {
        val sampleRate = ModelConstants.SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBufferSize.coerceAtLeast(windowSize * 2)
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            return
        }

        audioRecord?.startRecording()
        isRecording = true
        binding.statusText.text = "Listening..."

        // Job 1: Continuously read audio into buffer
        recordingJob = lifecycleScope.launch(Dispatchers.IO) {
            val readBuffer = ShortArray(1024)
            while (isRecording) {
                val readCount = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                if (readCount > 0) {
                    val batchFloats = FloatArray(readCount) { i -> readBuffer[i].toFloat() / 32768f }
                    
                    synchronized(audioBuffer) {
                        for (i in 0 until readCount) {
                            // Shift left and add new sample (Sliding window)
                            System.arraycopy(audioBuffer, 1, audioBuffer, 0, windowSize - 1)
                            audioBuffer[windowSize - 1] = batchFloats[i]
                        }
                    }

                    // Update Waveform on UI
                    withContext(Dispatchers.Main) {
                        binding.waveformView.updateSamples(batchFloats)
                    }
                }
            }
        }

        // Job 2: Periodically trigger inference
        inferenceJob = lifecycleScope.launch(Dispatchers.Default) {
            while (isRecording) {
                val inputCopy = synchronized(audioBuffer) { audioBuffer.copyOf() }
                withContext(Dispatchers.Main) {
                    modelExecutor.process(inputCopy)
                }
                delay(inferenceIntervalMs)
            }
        }
    }

    private fun stopLiveInference() {
        isRecording = false
        recordingJob?.cancel()
        inferenceJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun adjustThreshold(delta: Float) {
        val newThreshold = modelExecutor.threshold + delta
        if (newThreshold in 0.00..0.95) {
            modelExecutor.threshold = newThreshold
            binding.processData.textThreshold.text = String.format("%.1f", newThreshold)
        }
    }

    override fun onResults(result: Map<String, Float>, inferenceTime: Long) {
        activity?.runOnUiThread {
            binding.processData.inferenceTime.text = "$inferenceTime ms"
            updateUI(result)
        }
    }

    private fun updateUI(result: Map<String, Float>) {
        detectedItems.forEachIndexed { index, item ->
            if (index < result.size) {
                val key = result.keys.elementAt(index)
                val value = result[key] ?: 0f
                item.label.text = key
                animateScoreAndProgress(item, value)
                item.container.visibility = View.VISIBLE
            } else {
                item.label.text = ""
                item.score.text = ""
                item.gauge.progress = 0
                item.container.visibility = View.INVISIBLE
            }
        }
    }

    private fun animateScoreAndProgress(item: DetectedItemViewGroup, targetScore: Float) {
        val duration = 300L
        val startScore = item.lastScore

        ValueAnimator.ofFloat(startScore, targetScore).apply {
            this.duration = duration
            addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Float
                item.score.text = String.format("%.1f%%", animatedValue * 100)
                item.gauge.progress = (animatedValue * 1000).toInt()
            }
            start()
        }
        item.lastScore = targetScore
    }

    override fun onError(error: String) {
        Log.e(TAG, "ModelExecutor error: $error")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLiveInference()
        modelExecutor.closeENN()
    }

    companion object {
        private const val TAG = "LiveAudioFragment"
    }
}
