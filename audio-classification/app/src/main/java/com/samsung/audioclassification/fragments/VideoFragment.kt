package com.samsung.audioclassification.fragments

import android.animation.ValueAnimator
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.samsung.audioclassification.data.ModelConstants
import com.samsung.audioclassification.databinding.FragmentVideoBinding
import com.samsung.audioclassification.executor.ModelExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import java.nio.ShortBuffer

@UnstableApi
class VideoFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentVideoBinding
    private lateinit var modelExecutor: ModelExecutor

    private lateinit var detectedItems: List<DetectedItemViewGroup>

    private lateinit var exoPlayer: ExoPlayer
    private var mediaUri: Uri? = null
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var fullPcmData: FloatArray? = null
    private var inferenceJob: kotlinx.coroutines.Job? = null

    data class DetectedItemViewGroup(
        val label: TextView,
        val score: TextView,
        val gauge: ProgressBar,
        val container: View,
        var lastScore: Float = 0f
    )

    private var inferenceIntervalMs: Long = 300L

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                mediaUri = it
                setVideoView()

                // Reset previous data
                stopInferenceLoop()
                fullPcmData = null
                
                // Automatically run inference once after media is loaded
                runInference()
            }
        }

    private fun setVideoView() {
        val uri = mediaUri ?: return
        releasePlayer()

        exoPlayer = ExoPlayer.Builder(requireContext()).build().also { player ->
            binding.videoView.player = player
            binding.videoView.controllerAutoShow = false
            binding.videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

            player.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                    adjustVideoViewSize()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        startInferenceLoop()
                    } else {
                        stopInferenceLoop()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        stopInferenceLoop()
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                            doInferenceAtCurrentPosition()
                        }
                    }
                }
            })
        }

        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun adjustVideoViewSize() {
        if (videoWidth == 0 || videoHeight == 0) return

        val containerW = binding.root.width
        val containerH = binding.root.height

        val squareSize = minOf(containerW, containerH)
        val marginX = (containerW - squareSize) / 2
        val marginY = (containerH - squareSize) / 2

        val lp = (binding.videoView.layoutParams as ViewGroup.MarginLayoutParams)
        lp.width = squareSize
        lp.height = squareSize
        lp.setMargins(marginX, marginY, marginX, marginY)
        binding.videoView.layoutParams = lp

        binding.videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        binding.videoView.setBackgroundColor(Color.WHITE)
    }

    /**
     * Extract PCM audio data from media file
     * Returns: mono float array normalized to [-1.0, 1.0], resampled to 16kHz
     */
    private fun extractAudioPCM(): FloatArray? {
        val uri = mediaUri ?: return null
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(requireContext(), uri, null)

            // Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                Log.e(TAG, "No audio track found")
                return null
            }

            extractor.selectTrack(audioTrackIndex)

            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return null

            // Decode audio using MediaCodec
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val pcmSamples = mutableListOf<Short>()
            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false
            // Extract entire audio (memory permitting for sample app)
            // approx 10MB per minute of 16kHz mono audio

            while (!isEOS) {
                // Feed input
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize,
                            extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                // Get output
                var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                while (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                        val shortBuffer: ShortBuffer = outputBuffer.asShortBuffer()
                        val shorts = ShortArray(shortBuffer.remaining())
                        shortBuffer.get(shorts)
                        pcmSamples.addAll(shorts.toList())
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            codec.stop()
            codec.release()

            if (pcmSamples.isEmpty()) {
                Log.e(TAG, "No PCM samples decoded")
                return null
            }

            // Convert to mono if stereo
            val monoSamples = if (channelCount > 1) {
                ShortArray(pcmSamples.size / channelCount) { i ->
                    var sum = 0L
                    for (ch in 0 until channelCount) {
                        sum += pcmSamples[i * channelCount + ch]
                    }
                    (sum / channelCount).toShort()
                }
            } else {
                pcmSamples.toShortArray()
            }

            // Resample to 16kHz if needed
            val resampledSamples = if (sampleRate != SAMPLE_RATE) {
                resample(monoSamples, sampleRate, SAMPLE_RATE)
            } else {
                monoSamples
            }

            // Convert to float [-1.0, 1.0]
            FloatArray(resampledSamples.size) { i ->
                resampledSamples[i].toFloat() / 32768f
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting audio", e)
            null
        } finally {
            extractor.release()
        }
    }

    /**
     * Simple linear resampling
     */
    private fun resample(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        val ratio = fromRate.toDouble() / toRate
        val outputLength = (input.size / ratio).toInt()
        return ShortArray(outputLength) { i ->
            val srcIndex = (i * ratio).toInt().coerceIn(0, input.size - 1)
            input[srcIndex]
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentVideoBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        modelExecutor = ModelExecutor(
            context = requireContext(), executorListener = this
        )

        setUI()
    }

    private fun setUI() {
        binding.buttonLoad.setOnClickListener {
            // Use visualMediaPicker or similar for better UX, or just GetContent with */*
            // To allow both audio and video, we use */* and can't easily filter strictly with GetContent(String)
            // But we can use an Intent directly or just use "*/*" and let extractAudioPCM handle it.
            getContent.launch("*/*")
        }

        binding.buttonProcess.isEnabled = false
        binding.buttonProcess.setOnClickListener {
            runInference()
        }

        binding.processData.buttonThresholdPlus.setOnClickListener {
            adjustThreshold(0.1F)
        }

        binding.processData.buttonThresholdMinus.setOnClickListener {
            adjustThreshold(-0.1F)
        }

        binding.processData.textThreshold.text = String.format("%.1f", modelExecutor.threshold)


        setDetectedItems()
    }

    private fun runInference() {
        binding.buttonProcess.isEnabled = false
        binding.processData.inferenceTime.text = "Decoding Audio..."

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val pcmData = extractAudioPCM()
            if (pcmData != null && pcmData.isNotEmpty()) {
                fullPcmData = pcmData
                withContext(Dispatchers.Main) {
                    binding.buttonProcess.isEnabled = true
                    binding.processData.inferenceTime.text = "Ready"
                    // If already playing, start loop
                    if (exoPlayer.isPlaying) {
                        startInferenceLoop()
                    } else {
                        // Run once at start anyway
                        doInferenceAtCurrentPosition()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to extract audio data.",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.buttonProcess.isEnabled = true
                    binding.processData.inferenceTime.text = "-"
                }
            }
        }
    }

    private fun startInferenceLoop() {
        if (inferenceJob?.isActive == true) return
        if (fullPcmData == null) return

        inferenceJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            while (true) {
                doInferenceAtCurrentPosition()
                kotlinx.coroutines.delay(inferenceIntervalMs)
            }
        }
    }

    private fun stopInferenceLoop() {
        inferenceJob?.cancel()
        inferenceJob = null
    }

    private suspend fun doInferenceAtCurrentPosition() {
        val pcm = fullPcmData ?: return
        val currentMs = withContext(Dispatchers.Main) { exoPlayer.currentPosition }
        
        // Map ms to samples (16 samples per ms at 16kHz)
        val startIndex = (currentMs * (SAMPLE_RATE / 1000)).toInt()
        val windowSize = STFT_WINDOW_SIZE + (INPUT_SIZE_H - 1) * STFT_HOP_SIZE
        
        val inputPcm = FloatArray(windowSize)
        for (i in 0 until windowSize) {
            val idx = startIndex + i
            inputPcm[i] = if (idx in pcm.indices) pcm[idx] else 0f
        }

        withContext(Dispatchers.Main) {
            modelExecutor.process(inputPcm)
        }
    }

    private fun setDetectedItems() {
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
    }

    private fun adjustThreshold(delta: Float) {
        val newThreshold = modelExecutor.threshold + delta
        if (newThreshold in 0.00..0.95) {
            modelExecutor.threshold = newThreshold
            binding.processData.textThreshold.text = String.format("%.1f", newThreshold)
        }
    }

    private fun releasePlayer() {
        if (::exoPlayer.isInitialized) {
            exoPlayer.release()
        }
    }

    override fun onError(error: String) {
        Log.e(TAG, "ModelExecutor error: $error")
    }

    override fun onResults(
        result: Map<String, Float>, inferenceTime: Long
    ) {
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
                item.gauge.visibility = View.VISIBLE
            } else {
                item.label.text = ""
                item.score.text = ""
                item.gauge.progress = 0
                item.container.visibility = View.INVISIBLE
            }
        }
    }

    private fun animateScoreAndProgress(
        item: DetectedItemViewGroup,
        targetScore: Float
    ) {
        val duration = 300L
        val startScore = item.lastScore

        val scoreAnimator = ValueAnimator.ofFloat(startScore, targetScore).apply {
            this.duration = duration
            addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Float
                item.score.text = String.format("%.1f%%", animatedValue * 100)
            }
        }

        val progressAnimator =
            ValueAnimator.ofInt((startScore * 1000).toInt(), (targetScore * 1000).toInt()).apply {
                this.duration = duration
                addUpdateListener { animation ->
                    val animatedProgress = animation.animatedValue as Int
                    item.gauge.progress = animatedProgress
                }
            }

        item.lastScore = targetScore
        scoreAnimator.start()
        progressAnimator.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        inferenceJob?.cancel()
        releasePlayer()
        modelExecutor.closeENN()
    }

    companion object {
        private const val TAG = "VideoFragment"
        private const val SAMPLE_RATE = ModelConstants.SAMPLE_RATE
        private const val STFT_WINDOW_SIZE = ModelConstants.STFT_WINDOW_SIZE
        private const val STFT_HOP_SIZE = ModelConstants.STFT_HOP_SIZE
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H   // Time frames
    }
}