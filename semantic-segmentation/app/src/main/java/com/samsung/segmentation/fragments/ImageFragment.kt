// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.segmentation.fragments

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
import com.samsung.segmentation.data.ModelConstants
import com.samsung.segmentation.databinding.FragmentImageBinding
import com.samsung.segmentation.executor.ModelExecutor


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
    }

    private fun process(bitmapBuffer: Bitmap) {
        modelExecutor.process(bitmapBuffer)
    }

    private fun processImage(bitmap: Bitmap): Bitmap {
        val (scaledWidth, scaledHeight) = calculateScaleSize(
            bitmap.width, bitmap.height
        )
        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap, scaledWidth, scaledHeight, true
        )
        val (x, y) = calculateCenterCropPosition(scaledBitmap)

        return Bitmap.createBitmap(scaledBitmap, x, y, INPUT_SIZE_W, INPUT_SIZE_H)
    }

    private fun calculateScaleSize(bitmapWidth: Int, bitmapHeight: Int): Pair<Int, Int> {
        val scaleFactor = maxOf(
            INPUT_SIZE_W.toFloat() / bitmapWidth, INPUT_SIZE_H.toFloat() / bitmapHeight
        )

        return Pair((bitmapWidth * scaleFactor).toInt(), (bitmapHeight * scaleFactor).toInt())
    }

    private fun calculateCenterCropPosition(scaledBitmap: Bitmap): Pair<Int, Int> {
        return Pair(
            (scaledBitmap.width - INPUT_SIZE_W) / 2,
            (scaledBitmap.height - INPUT_SIZE_H) / 2
        )
    }

    override fun onError(error: String) {
        Log.e(TAG, "ModelExecutor error: $error")
    }

    override fun onResults(
        result: IntArray, inferenceTime: Long
    ) {
        activity?.runOnUiThread {
            binding.processData.inferenceTime.text = "$inferenceTime ms"
            binding.overlay.setResults(result, OUTPUT_SIZE_W, OUTPUT_SIZE_H)
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
        private const val OUTPUT_SIZE_W = ModelConstants.OUTPUT_SIZE_W
        private const val OUTPUT_SIZE_H = ModelConstants.OUTPUT_SIZE_H
    }
}