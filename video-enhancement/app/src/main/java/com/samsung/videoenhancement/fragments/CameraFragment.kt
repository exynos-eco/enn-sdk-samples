// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.videoenhancement.fragments

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.samsung.videoenhancement.data.ModelConstants
import com.samsung.videoenhancement.databinding.FragmentCameraBinding
import com.samsung.videoenhancement.executor.ModelExecutor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CameraFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentCameraBinding
    private lateinit var modelExecutor: ModelExecutor
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var bitmapBuffer: Bitmap

    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var previousFrame: Bitmap? = null
    private val isProcessing = AtomicBoolean(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        modelExecutor = ModelExecutor(
            context = requireContext(),
            executorListener = this
        )

        binding.processData.buttonThresholdPlus.visibility = View.GONE
        binding.processData.buttonThresholdMinus.visibility = View.GONE
        binding.processData.textThreshold.visibility = View.GONE
        binding.viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER

        setCamera()
    }

    private fun setCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            preview = Preview.Builder()
                .setTargetRotation(binding.viewFinder.display.rotation)
                .build()

            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetRotation(binding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { image ->
                        analyzeFrame(image)
                    }
                }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun analyzeFrame(image: ImageProxy) {
        try {
            if (!::bitmapBuffer.isInitialized ||
                bitmapBuffer.width != image.width ||
                bitmapBuffer.height != image.height
            ) {
                bitmapBuffer = Bitmap.createBitmap(
                    image.width,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
            }

            bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer)
            val currentFrame = processImage(bitmapBuffer)
            val firstFrame = previousFrame

            if (firstFrame == null) {
                previousFrame = currentFrame.copy(Bitmap.Config.ARGB_8888, false)
                return
            }

            if (isProcessing.compareAndSet(false, true)) {
                // Retain current frame as the first input of the next interpolation pair.
                previousFrame = currentFrame.copy(Bitmap.Config.ARGB_8888, false)
                modelExecutor.process(firstFrame, currentFrame)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Camera frame processing failed", e)
            isProcessing.set(false)
        } finally {
            image.close()
        }
    }

    private fun processImage(bitmap: Bitmap): Bitmap {
        val rotation = Matrix().apply { postRotate(90.0F) }
        val rotated = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            rotation,
            true
        )

        val targetRatio = INPUT_SIZE_W.toFloat() / INPUT_SIZE_H.toFloat()
        val sourceRatio = rotated.width.toFloat() / rotated.height.toFloat()

        val cropWidth: Int
        val cropHeight: Int
        val cropX: Int
        val cropY: Int

        if (sourceRatio > targetRatio) {
            cropHeight = rotated.height
            cropWidth = (cropHeight * targetRatio).toInt()
            cropX = (rotated.width - cropWidth) / 2
            cropY = 0
        } else {
            cropWidth = rotated.width
            cropHeight = (cropWidth / targetRatio).toInt()
            cropX = 0
            cropY = (rotated.height - cropHeight) / 2
        }

        val cropped = Bitmap.createBitmap(
            rotated,
            cropX,
            cropY,
            cropWidth.coerceAtLeast(1),
            cropHeight.coerceAtLeast(1)
        )

        return Bitmap.createScaledBitmap(
            cropped,
            INPUT_SIZE_W,
            INPUT_SIZE_H,
            true
        )
    }

    override fun onError(error: String) {
        Log.e(TAG, error)
        isProcessing.set(false)
    }

    override fun onResults(interpolatedFrame: Bitmap, inferenceTime: Long) {
        activity?.runOnUiThread {
            binding.processData.inferenceTime.text = "$inferenceTime ms"
        }
        isProcessing.set(false)
    }

    override fun onDestroyView() {
        imageAnalyzer?.clearAnalyzer()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        modelExecutor.closeENN()
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "RifeCameraFragment"
        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
    }
}
