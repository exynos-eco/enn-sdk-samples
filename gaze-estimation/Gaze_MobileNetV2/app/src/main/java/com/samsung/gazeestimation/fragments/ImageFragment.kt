package com.samsung.gazeestimation.fragments

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.samsung.gazeestimation.databinding.FragmentImageBinding
import com.samsung.gazeestimation.executor.ModelExecutor
import com.samsung.gazeestimation.view.GazeOverlayItem

class ImageFragment : Fragment(), ModelExecutor.ExecutorListener {

    private lateinit var binding: FragmentImageBinding
    private lateinit var bitmapBuffer: Bitmap
    private lateinit var modelExecutor: ModelExecutor

    private var lastFaceRect: RectF? = null
    private var lastSourceW: Int = 0
    private var lastSourceH: Int = 0
    private var lastOrigin: PointF? = null
    private var lastRollDeg: Float = 0f

    private val faceDetector by lazy {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(opts)
    }

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val bmp = ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(requireContext().contentResolver, it)
                ) { decoder, _, _ ->
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSampleSize(1)
                }

                bitmapBuffer = bmp
                binding.imageView.setImageBitmap(bitmapBuffer)
                binding.gazeOverlay.setResults(emptyList())
                lastFaceRect = null
                lastOrigin = null
                lastRollDeg = 0f

                binding.buttonProcess.isEnabled = true
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentImageBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        modelExecutor = ModelExecutor(context = requireContext(), executorListener = this)

        binding.processData.buttonThresholdPlus.visibility = View.GONE
        binding.processData.buttonThresholdMinus.visibility = View.GONE
        binding.processData.textThreshold.visibility = View.GONE

        binding.processData.detectedItem0.visibility = View.GONE
        binding.processData.detectedItem0Score.visibility = View.GONE
        binding.processData.detectedItem1.visibility = View.GONE
        binding.processData.detectedItem1Score.visibility = View.GONE
        binding.processData.detectedItem2.visibility = View.GONE
        binding.processData.detectedItem2Score.visibility = View.GONE

        binding.buttonLoad.setOnClickListener { getContent.launch("image/*") }
        binding.buttonProcess.isEnabled = false
        binding.buttonProcess.setOnClickListener { processImageGaze(bitmapBuffer) }
        binding.processData.inferenceTime.text = "-"
    }

    private fun processImageGaze(bitmap: Bitmap) {
        lastSourceW = bitmap.width
        lastSourceH = bitmap.height
        binding.gazeOverlay.setImageSourceInfo(lastSourceW, lastSourceH)

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    lastFaceRect = null
                    lastOrigin = null
                    binding.gazeOverlay.setResults(emptyList())
                    Log.w(TAG, "No face detected")
                    return@addOnSuccessListener
                }

                val face = faces.maxBy { f -> f.boundingBox.width() * f.boundingBox.height() }
                val bb = face.boundingBox

                val x0 = bb.left.coerceAtLeast(0)
                val y0 = bb.top.coerceAtLeast(0)
                val x1 = bb.right.coerceAtMost(bitmap.width)
                val y1 = bb.bottom.coerceAtMost(bitmap.height)

                if (x1 <= x0 || y1 <= y0) {
                    lastFaceRect = null
                    lastOrigin = null
                    binding.gazeOverlay.setResults(emptyList())
                    return@addOnSuccessListener
                }

                val rect = RectF(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat())
                lastFaceRect = rect
                lastRollDeg = face.headEulerAngleZ

                val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

                lastOrigin = if (leftEye != null && rightEye != null) {
                    PointF((leftEye.x + rightEye.x) * 0.5f, (leftEye.y + rightEye.y) * 0.5f)
                } else {
                    PointF((rect.left + rect.right) * 0.5f, rect.top + rect.height() * 0.35f)
                }

                val faceCrop = Bitmap.createBitmap(bitmap, x0, y0, x1 - x0, y1 - y0)
                modelExecutor.process(faceCrop)
            }
            .addOnFailureListener { e ->
                lastFaceRect = null
                lastOrigin = null
                binding.gazeOverlay.setResults(emptyList())
                Log.e(TAG, "Face detection failed: ${e.message}")
            }
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

    override fun onError(error: String) {
        Log.e(TAG, "ModelExecutor error: $error")
    }

    override fun onDestroy() {
        super.onDestroy()
        modelExecutor.closeENN()
    }

    companion object {
        private const val TAG = "ImageFragment"
    }
}