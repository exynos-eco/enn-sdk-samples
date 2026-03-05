// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.audioclassification.fragments

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.samsung.audioclassification.data.ModelConstants
import com.samsung.audioclassification.databinding.FragmentCameraBinding
import com.samsung.audioclassification.executor.ModelExecutor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class CameraFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentCameraBinding
    private lateinit var modelExecutor: ModelExecutor
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var bitmapBuffer: Bitmap
    private lateinit var detectedItems: List<DetectedItemViewGroup>

    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private val lastResults = mutableMapOf<Int, Pair<String, Float>>()

    data class DetectedItemViewGroup(
        val label: TextView,
        val score: TextView,
        val gauge: ProgressBar,
        val container: View
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentCameraBinding.inflate(layoutInflater)

        return binding.root
    }

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        modelExecutor = ModelExecutor(
            context = requireContext(), executorListener = this
        )

        setCamera()
        setUI()
    }

    private fun setCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // Get the ProcessCameraProvider. This is used to bind the lifecycle of cameras to the lifecycle owner
                val cameraProvider = cameraProviderFuture.get()
                // Select the back camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Set up the preview use case and the image analyzer use case
                setPreview()
                setImageAnalyzer()

                try {
                    // Unbind all use cases before rebinding
                    cameraProvider.unbindAll()
                    // Bind the cameraSelector, preview and imageAnalyzer use cases to the cameraProvider
                    // The camera's lifecycle will be tied to the lifecycle of the fragment
                    camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalyzer
                    )

                    // Connect the preview use case to the viewfinder surface
                    preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                } catch (exc: java.lang.Exception) {
                    Log.e(TAG, "Camera binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Set up the preview for the camera.
    private fun setPreview() {
        preview = Preview.Builder().setTargetRotation(binding.viewFinder.display.rotation).build()
    }

    // Set up the preview
    private fun setImageAnalyzer() {
        // Build an ImageAnalysis instance with the desired configuration
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetRotation(binding.viewFinder.display.rotation) // Set the target rotation to the current rotation of the viewfinder
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Set the backpressure strategy to keep only the latest image
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888) // Set the output image format to RGBA_8888
            .build().also {
                it.setAnalyzer(cameraExecutor) { image -> // Set the analyzer to run on the previously created executor
                    if (!::bitmapBuffer.isInitialized) { // If the bitmapBuffer is not initialized
                        // Create a new bitmap with the same dimensions as the image
                        bitmapBuffer = Bitmap.createBitmap(
                            image.width, image.height, Bitmap.Config.ARGB_8888
                        )
                    }
                    // Process the image
                    process(image)
                }
            }
    }

    // Process the image
    private fun process(image: ImageProxy) {
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }
        // YAMNet model is Audio-only - Camera not used
        // modelExecutor.process(processImage(bitmapBuffer))
    }

    private fun processImage(bitmap: Bitmap): Bitmap {
        val rotationMatrix = Matrix().apply { postRotate(90F) }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, rotationMatrix, true
        )
        val (scaledWidth, scaledHeight) = calculateScaleSize(
            rotatedBitmap.width, rotatedBitmap.height
        )
        val scaledBitmap = Bitmap.createScaledBitmap(
            rotatedBitmap, scaledWidth, scaledHeight, true
        )
        val (x, y) = calculateCenterCropPosition(scaledBitmap)

        /*
        return Bitmap.createBitmap(scaledBitmap, x, y, INPUT_SIZE_W, INPUT_SIZE_H)
        */
        return scaledBitmap // 임시 반환
    }

    private fun calculateScaleSize(bitmapWidth: Int, bitmapHeight: Int): Pair<Int, Int> {
       /* val scaleFactor = maxOf(
            INPUT_SIZE_W.toFloat() / bitmapWidth, INPUT_SIZE_H.toFloat() / bitmapHeight
        )*/

        /*
        return Pair((bitmapWidth * scaleFactor).toInt(), (bitmapHeight * scaleFactor).toInt())
        */
        return Pair(bitmapWidth, bitmapHeight)
    }

    private fun calculateCenterCropPosition(scaledBitmap: Bitmap): Pair<Int, Int> {
        /*
        return Pair(
            (scaledBitmap.width - INPUT_SIZE_W) / 2,
            (scaledBitmap.height - INPUT_SIZE_H) / 2
        )
        */
        return Pair(0, 0)
    }

    private fun setUI() {
        binding.processData.buttonThresholdPlus.setOnClickListener {
            adjustThreshold(0.1F)
        }

        binding.processData.buttonThresholdMinus.setOnClickListener {
            adjustThreshold(-0.1F)
        }

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

        binding.viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER
    }

    private fun adjustThreshold(delta: Float) {
        val newThreshold = modelExecutor.threshold + delta

        if (newThreshold in 0.00 .. 0.95) {
            modelExecutor.threshold = newThreshold
            binding.processData.textThreshold.text = String.format("%.1f", newThreshold)
        }
    }

    // Handle errors
    override fun onError(error: String) {
        Log.e(TAG, "ModelExecutor error: $error")
    }

    // Handle results
    override fun onResults(
        result: Map<String, Float>, inferenceTime: Long
    ) {
        activity?.runOnUiThread {
            binding.processData.inferenceTime.text = "$inferenceTime ms"
            updateUI(result)
        }
    }

    private fun updateUI(result: Map<String, Float>) {
        val threshold = 0.05f

        detectedItems.forEachIndexed { index, item ->
            if (index < result.size) {
                val key = result.keys.elementAt(index)
                val value = result[key] ?: 0f
                val last = lastResults[index]
                val shouldUpdate =
                    last == null || last.first != key || kotlin.math.abs(last.second - value) > threshold
                val keyUpdate = last == null || last.first != key
                if (shouldUpdate) {
                    item.label.text = key
                    animateScoreAndProgress(item.score, item.gauge, value, keyUpdate)
                    item.container.visibility = View.VISIBLE
                    item.gauge.visibility = View.VISIBLE

                    lastResults[index] = Pair(key, value)
                }

            } else {
                item.label.text = ""
                item.score.text = ""
                item.gauge.progress = 0
                item.container.visibility = View.INVISIBLE
                lastResults.remove(index)
            }
        }
    }
    private fun animateScoreAndProgress(
        scoreView: TextView,
        progressBar: ProgressBar,
        targetScore: Float,
        keyUpdate: Boolean
    ) {
        val duration = 10L

        val scoreAnimator = ValueAnimator.ofFloat(
            if (keyUpdate) 0f else progressBar.progress.toFloat() * 0.001f,
            targetScore
        ).apply {
            this.duration = duration
            addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Float
                scoreView.text = String.format("%.1f%%", animatedValue * 100)
            }
        }

        val progressAnimator = ValueAnimator.ofInt(
            if (keyUpdate) 0 else progressBar.progress,
            (targetScore * 1000).toInt()
        ).apply {
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
        modelExecutor.closeENN()
    }

    companion object {
        private const val TAG = "CameraFragment"
        // private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        // private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
    }
}