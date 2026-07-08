// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.imageediting.fragments

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
import com.samsung.imageediting.data.ModelConstants
import com.samsung.imageediting.databinding.FragmentCameraBinding
import com.samsung.imageediting.executor.ModelExecutor
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
    private var isProcessing = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCameraBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        modelExecutor = ModelExecutor(
            context = requireContext(),
            executorListener = this
        )

        setUI()
        setCamera()
    }

    private fun setCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                setPreview()
                setImageAnalyzer()

                try {
                    cameraProvider.unbindAll()
                    camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                    preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                } catch (exc: Exception) {
                }
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    private fun setPreview() {
        preview = Preview.Builder()
            .setTargetRotation(binding.viewFinder.display.rotation)
            .build()
    }

    private fun setImageAnalyzer() {
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { image ->
                    if (!::bitmapBuffer.isInitialized) {
                        bitmapBuffer = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                    }
                    process(image)
                }
            }
    }

    private fun process(image: ImageProxy) {
        image.use {
            if (isProcessing) return
            isProcessing = true
            bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer)
            modelExecutor.process(processImage(bitmapBuffer))
        }
    }

    private fun processImage(bitmap: Bitmap): Bitmap {
        val rotationMatrix = Matrix().apply { postRotate(90F) }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            rotationMatrix,
            true
        )

        val (scaledWidth, scaledHeight) = calculateScaleSize(
            rotatedBitmap.width,
            rotatedBitmap.height
        )
        val scaledBitmap = Bitmap.createScaledBitmap(rotatedBitmap, scaledWidth, scaledHeight, true)
        val (x, y) = calculateCenterCropPosition(scaledBitmap)

        return Bitmap.createBitmap(scaledBitmap, x, y, INPUT_SIZE_W, INPUT_SIZE_H)
    }

    private fun calculateScaleSize(bitmapWidth: Int, bitmapHeight: Int): Pair<Int, Int> {
        val scaleFactor = maxOf(
            INPUT_SIZE_W.toFloat() / bitmapWidth,
            INPUT_SIZE_H.toFloat() / bitmapHeight
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
        binding.processData.textThreshold.text = "-"
        binding.processData.buttonThresholdPlus.visibility = View.GONE
        binding.processData.buttonThresholdMinus.visibility = View.GONE
        binding.processData.detectedItem0.text = "LaMa-Dilated"
        binding.processData.detectedItem0Score.text = "Image Inpainting"
        binding.processData.detectedItem1.text = ""
        binding.processData.detectedItem1Score.text = ""
        binding.processData.detectedItem2.text = ""
        binding.processData.detectedItem2Score.text = ""
    }

    override fun onError(error: String) {
        isProcessing = false
        Log.e(TAG, "ModelExecutor error: $error")
    }

    override fun onResults(resultBitmap: Bitmap, inferenceTime: Long) {
        activity?.runOnUiThread {
            binding.processData.inferenceTime.text = "$inferenceTime ms"
            isProcessing = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        modelExecutor.closeENN()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }

    companion object {
        private const val TAG = "CameraFragment"
        private const val INPUT_SIZE_W = ModelConstants.INPUT1_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT1_H
    }
}
