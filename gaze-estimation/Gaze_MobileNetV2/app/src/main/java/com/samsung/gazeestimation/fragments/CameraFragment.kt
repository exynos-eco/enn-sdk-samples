// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.gazeestimation.fragments

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.samsung.gazeestimation.databinding.FragmentCameraBinding
import com.samsung.gazeestimation.executor.ModelExecutor
import com.samsung.gazeestimation.view.GazeOverlayItem
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

    private var lastFaceRect: RectF? = null
    private var lastOrigin: PointF? = null
    private var lastRollDeg: Float = 0f
    private var lastSourceW: Int = 0
    private var lastSourceH: Int = 0

    private val faceDetector by lazy {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(opts)
    }
    private val busy = AtomicBoolean(false)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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

    private fun setUI() {
        binding.viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER

        binding.processData.buttonThresholdPlus.visibility = View.GONE
        binding.processData.buttonThresholdMinus.visibility = View.GONE
        binding.processData.textThreshold.visibility = View.GONE

        binding.processData.detectedItem0.visibility = View.GONE
        binding.processData.detectedItem0Score.visibility = View.GONE
        binding.processData.detectedItem1.visibility = View.GONE
        binding.processData.detectedItem1Score.visibility = View.GONE
        binding.processData.detectedItem2.visibility = View.GONE
        binding.processData.detectedItem2Score.visibility = View.GONE

        binding.processData.inferenceTime.text = "-"
    }

    private fun setCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            setPreview()
            setImageAnalyzer()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            } catch (exc: Exception) {
                Log.e(TAG, "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
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
                        bitmapBuffer = Bitmap.createBitmap(
                            image.width, image.height, Bitmap.Config.ARGB_8888
                        )
                    }
                    process(image)
                }
            }
    }

    private fun process(image: ImageProxy) {
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }

        val rotationDeg = image.imageInfo.rotationDegrees
        val displayBitmap = rotateBitmap(bitmapBuffer, rotationDeg)

        lastSourceW = displayBitmap.width
        lastSourceH = displayBitmap.height
        binding.gazeOverlay.setImageSourceInfo(lastSourceW, lastSourceH)

        val inputImage = InputImage.fromBitmap(displayBitmap, 0)
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    lastFaceRect = null
                    binding.gazeOverlay.setResults(emptyList())
                    return@addOnSuccessListener
                }

                val face = faces.maxBy { it.boundingBox.width() * it.boundingBox.height() }
                val bb = face.boundingBox

                val x0 = bb.left.coerceAtLeast(0)
                val y0 = bb.top.coerceAtLeast(0)
                val x1 = bb.right.coerceAtMost(displayBitmap.width)
                val y1 = bb.bottom.coerceAtMost(displayBitmap.height)
                if (x1 <= x0 || y1 <= y0) return@addOnSuccessListener

                lastFaceRect = RectF(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat())
                lastRollDeg = face.headEulerAngleZ

                val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                lastOrigin = if (leftEye != null && rightEye != null) {
                    PointF((leftEye.x + rightEye.x) * 0.5f, (leftEye.y + rightEye.y) * 0.5f)
                } else {
                    val r = lastFaceRect!!
                    PointF((r.left + r.right) * 0.5f, r.top + r.height() * 0.35f)
                }

                val faceCrop = Bitmap.createBitmap(displayBitmap, x0, y0, x1 - x0, y1 - y0)
                modelExecutor.process(faceCrop)
            }
            .addOnFailureListener {
                lastFaceRect = null
                binding.gazeOverlay.setResults(emptyList())
            }
    }

    private fun rotateBitmap(src: Bitmap, rotationDeg: Int): Bitmap {
        if (rotationDeg == 0) return src
        val m = Matrix().apply { postRotate(rotationDeg.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    override fun onError(error: String) {
        Log.e(TAG, "ModelExecutor error: $error")
    }

    override fun onResults(result: ModelExecutor.GazeResult, inferenceTime: Long) {
        activity?.runOnUiThread {
            binding.processData.inferenceTime.text = "$inferenceTime ms"

            val rect = lastFaceRect
            val origin = lastOrigin
            val rollDeg = lastRollDeg

            if (rect != null && origin != null) {
                binding.gazeOverlay.setImageSourceInfo(lastSourceW, lastSourceH)
                binding.gazeOverlay.setResults(
                    listOf(
                        GazeOverlayItem(
                            bbox = rect,
                            origin = origin,
                            pitchDeg = result.pitchDeg,
                            yawDeg = result.yawDeg,
                            rollDeg = rollDeg
                        )
                    )
                )
            } else {
                binding.gazeOverlay.setResults(emptyList())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            faceDetector.close()
        } catch (_: Throwable) {}
        modelExecutor.closeENN()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "GazeCameraFragment"
    }
}