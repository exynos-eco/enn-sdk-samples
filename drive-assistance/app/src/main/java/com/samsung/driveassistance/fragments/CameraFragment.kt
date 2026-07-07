// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.driveassistance.fragments

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.samsung.driveassistance.data.ModelConstants
import com.samsung.driveassistance.databinding.FragmentCameraBinding
import com.samsung.driveassistance.executor.ModelExecutor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CameraFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentCameraBinding
    private lateinit var modelExecutor: ModelExecutor
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var bitmapBuffer: Bitmap

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var lastInputBitmap: Bitmap? = null
    private val busy = AtomicBoolean(false)

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

        binding.viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER
        binding.processData.inferenceTime.text = "-"
        binding.processData.textThreshold.text = String.format("%.1f", modelExecutor.threshold)

        binding.processData.buttonThresholdMinus.setOnClickListener {
            updateThreshold(-0.1F)
        }

        binding.processData.buttonThresholdPlus.setOnClickListener {
            updateThreshold(0.1F)
        }

        startCamera()
    }

    private fun startCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            preview = Preview.Builder()
                .setTargetRotation(binding.viewFinder.display.rotation)
                .build()

            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetRotation(binding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analyzer ->
                    analyzer.setAnalyzer(cameraExecutor) { image -> processFrame(image) }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
                preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            } catch (e: Exception) {
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun processFrame(image: ImageProxy) {
        if (!busy.compareAndSet(false, true)) {
            image.close()
            return
        }

        try {
            if (!::bitmapBuffer.isInitialized ||
                bitmapBuffer.width != image.width ||
                bitmapBuffer.height != image.height
            ) {
                bitmapBuffer = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            }

            image.use {
                bitmapBuffer.copyPixelsFromBuffer(it.planes[0].buffer)
            }

            val rotatedBitmap = rotateBitmap(bitmapBuffer, image.imageInfo.rotationDegrees)
            lastInputBitmap = Bitmap.createScaledBitmap(
                rotatedBitmap,
                ModelConstants.INPUT_SIZE_W,
                ModelConstants.INPUT_SIZE_H,
                true
            ).copy(Bitmap.Config.ARGB_8888, false)

            modelExecutor.process(lastInputBitmap!!)
        } catch (e: Exception) {
            busy.set(false)
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun updateThreshold(delta: Float) {
        modelExecutor.threshold = (modelExecutor.threshold + delta).coerceIn(0.0F, 0.95F)
        binding.processData.textThreshold.text = String.format("%.1f", modelExecutor.threshold)
    }

    override fun onResults(result: ModelExecutor.YoloPResult, inferenceTime: Long) {
        busy.set(false)
        activity?.runOnUiThread {
            binding.processData.inferenceTime.text = "$inferenceTime ms"

            lastInputBitmap?.let {
                binding.viewFinder.foreground = BitmapDrawable(resources, drawOverlay(it, result))
            }
        }
    }

    override fun onError(error: String) {
        Log.e(TAG, error)
        busy.set(false)
        activity?.runOnUiThread {
            binding.processData.inferenceTime.text = "Error"
        }
    }

    private fun drawOverlay(source: Bitmap, result: ModelExecutor.YoloPResult): Bitmap {
        val width = ModelConstants.INPUT_SIZE_W
        val height = ModelConstants.INPUT_SIZE_H
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val overlay = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        for (i in pixels.indices) {
            pixels[i] = when {
                result.laneMask[i] -> Color.argb(190, 255, 0, 0)
                result.drivableMask[i] -> Color.argb(120, 0, 255, 0)
                else -> Color.TRANSPARENT
            }
        }

        overlay.setPixels(pixels, 0, width, 0, 0, width, height)
        Canvas(output).drawBitmap(overlay, 0F, 0F, null)
        overlay.recycle()
        return output
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::modelExecutor.isInitialized) {
            modelExecutor.closeENN()
        }
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }

    companion object {
        private const val TAG = "YoloPCameraFragment"
    }
}
