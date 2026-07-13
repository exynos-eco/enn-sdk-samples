package com.samsung.imagetotext.fragments

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
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
import com.samsung.imagetotext.data.DetectionResult
import com.samsung.imagetotext.databinding.FragmentCameraBinding
import com.samsung.imagetotext.executor.ModelExecutor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentCameraBinding
    private lateinit var modelExecutor: ModelExecutor
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var bitmapBuffer: Bitmap
    private var lastSubmittedAt = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        modelExecutor = ModelExecutor(context = requireContext(), executorListener = this)
        binding.viewFinder.scaleType = PreviewView.ScaleType.FILL_CENTER
        binding.processData.textThreshold.text = String.format("%.2f", modelExecutor.threshold)
        binding.processData.buttonThresholdPlus.setOnClickListener { adjustThreshold(0.05f) }
        binding.processData.buttonThresholdMinus.setOnClickListener { adjustThreshold(-0.05f) }
        startCamera()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(cameraExecutor, ::analyze)
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (t: Throwable) {
                Log.e(TAG, "Camera binding failed", t)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastSubmittedAt < CAMERA_INTERVAL_MS) return
            if (!::bitmapBuffer.isInitialized || bitmapBuffer.width != image.width || bitmapBuffer.height != image.height) {
                bitmapBuffer = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            }
            bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer)
            val rotation = image.imageInfo.rotationDegrees.toFloat()
            val matrix = Matrix().apply { postRotate(rotation) }
            val rotated = Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true)
            lastSubmittedAt = now
            modelExecutor.process(rotated)
        } finally {
            image.close()
        }
    }

    private fun adjustThreshold(delta: Float) {
        modelExecutor.threshold = (modelExecutor.threshold + delta).coerceIn(0.05f, 0.95f)
        binding.processData.textThreshold.text = String.format("%.2f", modelExecutor.threshold)
    }

    override fun onError(error: String) { Log.e(TAG, error) }

    override fun onResults(detectionResult: List<DetectionResult>, inferenceTime: Long) {
        activity?.runOnUiThread {
            binding.processData.inferenceTime.text = "$inferenceTime ms"
            binding.overlay.setResults(detectionResult)
            binding.overlay.invalidate()
        }
    }

    override fun onDestroyView() {
        modelExecutor.closeENN()
        cameraExecutor.shutdownNow()
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "CameraFragment"
        private const val CAMERA_INTERVAL_MS = 800L
    }
}
