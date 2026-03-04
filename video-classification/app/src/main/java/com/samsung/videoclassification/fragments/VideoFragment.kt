package com.samsung.videoclassification.fragments

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaMetadataRetriever
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
import com.samsung.videoclassification.data.ModelConstants
import com.samsung.videoclassification.databinding.FragmentVideoBinding
import com.samsung.videoclassification.executor.ModelExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class VideoFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentVideoBinding
    private lateinit var modelExecutor: ModelExecutor

    private lateinit var detectedItems: List<DetectedItemViewGroup>

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var videoUri: Uri
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    data class DetectedItemViewGroup(
        val label: TextView,
        val score: TextView,
        val gauge: ProgressBar,
        val container: View
    )

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                videoUri = it
                setVideoView()

                for (detectedItem in detectedItems) {
                    detectedItem.container.visibility = View.INVISIBLE
                }

                // Automatically run inference once after video is loaded
                runInference()
            }
        }

    private fun setVideoView() {
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
            })
        }

        val mediaItem = MediaItem.fromUri(videoUri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun adjustVideoViewSize() {
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

    private fun extractFrames(): List<Bitmap>? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(requireContext(), videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return null

            val durationUs = durationMs * 1000L
            val frames = mutableListOf<Bitmap>()

            for (i in 0 until NUM_SEGMENTS) {
                // Calculate frame position at equal intervals (center of each segment)
                val timeUs = (durationUs * (2 * i + 1)) / (2 * NUM_SEGMENTS)
                val frame = retriever.getFrameAtTime(
                    timeUs, MediaMetadataRetriever.OPTION_CLOSEST
                )

                if (frame != null) {
                    val processed = processImage(frame)
                    frames.add(processed)
                    if (frame != processed) frame.recycle()
                } else {
                    Log.w(TAG, "Failed to extract frame at segment $i (timeUs=$timeUs)")
                    return null
                }
            }
            frames
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting frames", e)
            null
        } finally {
            retriever.release()
        }
    }

    private fun processImage(bitmap: Bitmap): Bitmap {
        val (scaledWidth, scaledHeight) = calculateScaleSize(bitmap.width, bitmap.height)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        return padBitmapToSquare(scaledBitmap, INPUT_SIZE_W, INPUT_SIZE_H)
    }

    private fun padBitmapToSquare(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val paddedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(paddedBitmap)
        canvas.drawColor(Color.rgb(114, 114, 114))

        val left = (targetWidth - bitmap.width) / 2
        val top = (targetHeight - bitmap.height) / 2

        canvas.drawBitmap(bitmap, left.toFloat(), top.toFloat(), null)
        return paddedBitmap
    }

    private fun calculateScaleSize(bitmapWidth: Int, bitmapHeight: Int): Pair<Int, Int> {
        val scaleFactor = minOf(
            INPUT_SIZE_W.toFloat() / bitmapWidth,
            INPUT_SIZE_H.toFloat() / bitmapHeight
        )
        return Pair((bitmapWidth * scaleFactor).toInt(), (bitmapHeight * scaleFactor).toInt())
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
            getContent.launch("video/*")
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

        setDetectedItems()
    }

    private fun runInference() {
        binding.buttonProcess.isEnabled = false
        binding.processData.inferenceTime.text = "Processing..."

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val frames = extractFrames()
            if (frames != null && frames.size == NUM_SEGMENTS) {
                withContext(Dispatchers.Main) {
                    modelExecutor.process(frames)
                    frames.forEach { it.recycle() }
                    binding.buttonProcess.isEnabled = true
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to extract $NUM_SEGMENTS frames from video.",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.buttonProcess.isEnabled = true
                    binding.processData.inferenceTime.text = "-"
                }
            }
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
                animateScoreAndProgress(item.score, item.gauge, value)
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
        scoreView: TextView,
        progressBar: ProgressBar,
        targetScore: Float
    ) {
        val duration = 1000L

        val scoreAnimator = ValueAnimator.ofFloat(0f, targetScore).apply {
            this.duration = duration
            addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Float
                scoreView.text = String.format("%.1f%%", animatedValue * 100)
            }
        }

        val progressAnimator =
            ValueAnimator.ofInt(0, (targetScore * 1000).toInt()).apply {
                this.duration = duration
                addUpdateListener { animation ->
                    val animatedProgress = animation.animatedValue as Int
                    progressBar.progress = animatedProgress
                }
            }

        scoreAnimator.start()
        progressAnimator.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        modelExecutor.closeENN()
    }

    companion object {
        private const val TAG = "VideoFragment"
        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
        private const val NUM_SEGMENTS = ModelConstants.NUM_SEGMENTS
    }
}