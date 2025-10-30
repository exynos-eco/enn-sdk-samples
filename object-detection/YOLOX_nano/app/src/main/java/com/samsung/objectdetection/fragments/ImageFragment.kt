// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.objectdetection.fragments

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.samsung.objectdetection.data.ModelConstants
import com.samsung.objectdetection.databinding.FragmentImageBinding
import com.samsung.objectdetection.data.DetectionResult
import com.samsung.objectdetection.executor.ModelExecutor

class ImageFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentImageBinding
    private lateinit var bitmapBuffer: Bitmap
    private lateinit var modelExecutor: ModelExecutor

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val resizedImage = processImage(ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(
                        requireContext().contentResolver, it
                    )
                ) { decoder, _, _ ->
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSampleSize(1)
                })

                binding.imageView.setImageBitmap(resizedImage)
                binding.buttonProcess.isEnabled = true
                bitmapBuffer = resizedImage
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentImageBinding.inflate(layoutInflater)

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
            getContent.launch("image/*")
        }

        binding.buttonProcess.isEnabled = false
        binding.buttonProcess.setOnClickListener {
            process(bitmapBuffer)
        }

        binding.processData.buttonThresholdPlus.setOnClickListener {
            adjustThreshold(0.1F)
        }

        binding.processData.buttonThresholdMinus.setOnClickListener {
            adjustThreshold(-0.1F)
        }
    }

    private fun process(bitmapBuffer: Bitmap) {
        modelExecutor.process(bitmapBuffer)
    }

    private fun processImage(bitmap: Bitmap): Bitmap {
        binding.overlay.clear()

        val (scaledWidth, scaledHeight) = calculateScaleSize(bitmap.width, bitmap.height)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        val rawX = (scaledBitmap.width - INPUT_SIZE_W) / 2
        val rawY = (scaledBitmap.height - INPUT_SIZE_H) / 2
        val safeX = rawX.coerceIn(0, maxOf(0, scaledBitmap.width - INPUT_SIZE_W))
        val safeY = rawY.coerceIn(0, maxOf(0, scaledBitmap.height - INPUT_SIZE_H))
        val cropW = minOf(INPUT_SIZE_W, scaledBitmap.width - safeX)
        val cropH = minOf(INPUT_SIZE_H, scaledBitmap.height - safeY)

        val cropped = Bitmap.createBitmap(scaledBitmap, safeX, safeY, cropW, cropH)

        if (cropW != INPUT_SIZE_W || cropH != INPUT_SIZE_H) {
            val padded = Bitmap.createBitmap(INPUT_SIZE_W, INPUT_SIZE_H, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(padded)
            val left = ((INPUT_SIZE_W - cropW) / 2f)
            val top = ((INPUT_SIZE_H - cropH) / 2f)
            canvas.drawBitmap(cropped, left, top, null)
            return padded
        }
        return cropped
    }

    private fun calculateScaleSize(bitmapWidth: Int, bitmapHeight: Int): Pair<Int, Int> {
        val scaleFactor = maxOf(
            INPUT_SIZE_W.toFloat() / bitmapWidth.toFloat(),
            INPUT_SIZE_H.toFloat() / bitmapHeight.toFloat()
        )
        val scaledW = kotlin.math.ceil(bitmapWidth * scaleFactor.toDouble()).toInt()
        val scaledH = kotlin.math.ceil(bitmapHeight * scaleFactor.toDouble()).toInt()
        return Pair(scaledW, scaledH)
    }

    private fun adjustThreshold(delta: Float) {
        val newThreshold = modelExecutor.threshold + delta
        if (newThreshold in 0.05 .. 0.95) {
            modelExecutor.threshold = newThreshold
            binding.processData.textThreshold.text = String.format("%.1f", newThreshold)
        }
    }

    override fun onError(error: String) {
        Log.e(TAG, "ModelExecutor error: $error")
    }

    override fun onResults(
        detectionResult: List<DetectionResult>, inferenceTime: Long
    ) {
        activity?.runOnUiThread {
            binding.processData.inferenceTime.text = "$inferenceTime ms"
            binding.overlay.setResults(detectionResult)
            binding.overlay.invalidate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        modelExecutor.closeENN()
    }

    companion object {
        private const val TAG = "ImageFragment"
        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
    }
}