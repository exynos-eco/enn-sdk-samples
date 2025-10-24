// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.imageclassification.fragments

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.samsung.imageclassification.data.ModelConstants
import com.samsung.imageclassification.databinding.FragmentImageBinding
import com.samsung.imageclassification.executor.ModelExecutor


class ImageFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentImageBinding
    private lateinit var bitmapBuffer: Bitmap
    private lateinit var modelExecutor: ModelExecutor
    private lateinit var detectedItems: List<Pair<TextView, TextView>>

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

        detectedItems = listOf(
            binding.processData.detectedItem0 to binding.processData.detectedItem0Score,
            binding.processData.detectedItem1 to binding.processData.detectedItem1Score,
            binding.processData.detectedItem2 to binding.processData.detectedItem2Score
        )
    }

    private fun process(bitmapBuffer: Bitmap) {
        modelExecutor.process(bitmapBuffer)
    }

    private fun processImage(bitmap: Bitmap): Bitmap {
        val (scaledWidth, scaledHeight) = calculateScaleSize(bitmap.width, bitmap.height)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        val (x, y) = calculateCenterCropPosition(scaledBitmap)
        val cropWidth = minOf(INPUT_SIZE_W, scaledBitmap.width - x)
        val cropHeight = minOf(INPUT_SIZE_H, scaledBitmap.height - y)

        return Bitmap.createBitmap(scaledBitmap, x, y, cropWidth, cropHeight)
    }

    private fun calculateScaleSize(bitmapWidth: Int, bitmapHeight: Int): Pair<Int, Int> {
        val scaleFactor = maxOf(
            INPUT_SIZE_W.toFloat() / bitmapWidth, INPUT_SIZE_H.toFloat() / bitmapHeight
        )

        return Pair((bitmapWidth * scaleFactor).toInt(), (bitmapHeight * scaleFactor).toInt())
    }

    private fun calculateCenterCropPosition(scaledBitmap: Bitmap): Pair<Int, Int> {
        val x = maxOf((scaledBitmap.width - INPUT_SIZE_W) / 2, 0)
        val y = maxOf((scaledBitmap.height - INPUT_SIZE_H) / 2, 0)
        return Pair(x, y)
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
        result: Map<String, Float>, inferenceTime: Long
    ) {
        activity?.runOnUiThread {
            binding.processData.inferenceTime.text = "$inferenceTime ms"
            updateUI(result)
        }
    }

    private fun updateUI(result: Map<String, Float>) {
        detectedItems.forEachIndexed { index, pair ->
            if (index < result.size) {
                val key = result.keys.elementAt(index)
                pair.first.text = key
                pair.second.text = String.format("%.5f", result[key])
            } else {
                pair.first.text = ""
                pair.second.text = ""
            }
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