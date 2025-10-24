// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.depthestimation.fragments

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.samsung.depthestimation.data.ModelConstants
import com.samsung.depthestimation.databinding.FragmentCameraBinding
import com.samsung.depthestimation.executor.ModelExecutor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class CameraFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentCameraBinding
    private lateinit var modelExecutor: ModelExecutor
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var bitmapBuffer: Bitmap

    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null

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
        modelExecutor.process(processImage(bitmapBuffer))
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

        return Bitmap.createBitmap(scaledBitmap, x, y, INPUT_SIZE_W, INPUT_SIZE_H)
    }

    private fun calculateScaleSize(bitmapWidth: Int, bitmapHeight: Int): Pair<Int, Int> {
        val scaleFactor = maxOf(
            INPUT_SIZE_W.toDouble() / bitmapWidth, INPUT_SIZE_H.toDouble() / bitmapHeight
        )

        return Pair((bitmapWidth * scaleFactor).toInt(), (bitmapHeight * scaleFactor).toInt())
    }

    private fun calculateCenterCropPosition(scaledBitmap: Bitmap): Pair<Int, Int> {
        return Pair(
            (scaledBitmap.width - INPUT_SIZE_W) / 2,
            (scaledBitmap.height - INPUT_SIZE_H) / 2
        )
    }

    private fun setUI() {
        binding.viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER
    }

    override fun onError(error: String) {
        Log.e(TAG, "ModelExecutor error: $error")
    }

    // Handle results
    override fun onResults(
        result: IntArray, inferenceTime: Long
    ) {
        activity?.runOnUiThread {
            binding.processData.inferenceTime.text = "$inferenceTime ms"
            binding.overlay.setResults(
                result, OUTPUT_SIZE_W, OUTPUT_SIZE_H
            )
            binding.overlay.invalidate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        modelExecutor.closeENN()
    }

    companion object {
        private const val TAG = "CameraFragment"
        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
        private const val OUTPUT_SIZE_W = ModelConstants.OUTPUT_SIZE_W
        private const val OUTPUT_SIZE_H = ModelConstants.OUTPUT_SIZE_H
    }
}