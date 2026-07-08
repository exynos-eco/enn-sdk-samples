// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.imageediting.fragments

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
import com.samsung.imageediting.data.ModelConstants
import com.samsung.imageediting.databinding.FragmentImageBinding
import com.samsung.imageediting.executor.ModelExecutor

class ImageFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentImageBinding
    private lateinit var bitmapBuffer: Bitmap
    private lateinit var modelExecutor: ModelExecutor
    private var hasImage: Boolean = false

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val decoded = ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(requireContext().contentResolver, it)
                ) { decoder, _, _ ->
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSampleSize(1)
                }

                val resizedImage = processImage(decoded)

                bitmapBuffer = resizedImage
                hasImage = true
                binding.imageView.setImageBitmap(resizedImage)
                binding.maskView.clear()
                binding.maskView.visibility = View.VISIBLE
                binding.maskView.isEnabled = true

                binding.buttonLoad.isEnabled = true
                binding.buttonClear.isEnabled = true
                binding.buttonProcess.isEnabled = true
                binding.processData.inferenceTime.text = "-"
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        modelExecutor = ModelExecutor(
            context = requireContext(),
            executorListener = this
        )
        setUI()
    }

    private fun setUI() {
        binding.buttonLoad.text = "LOAD"
        binding.buttonLoad.isEnabled = true
        binding.buttonLoad.setOnClickListener {
            getContent.launch("image/*")
        }

        binding.buttonClear.text = "CLEAR"
        binding.buttonClear.isEnabled = false
        binding.buttonClear.setOnClickListener {
            if (!hasImage) return@setOnClickListener

            binding.maskView.clear()
            binding.maskView.visibility = View.VISIBLE
            binding.maskView.isEnabled = true
            binding.imageView.setImageBitmap(bitmapBuffer)
            binding.processData.inferenceTime.text = "-"
            binding.buttonProcess.isEnabled = true
        }

        binding.buttonProcess.text = "PROCESS"
        binding.buttonProcess.isEnabled = false
        binding.buttonProcess.setOnClickListener {
            if (hasImage) {
                process(bitmapBuffer)
            }
        }

        binding.maskView.clear()
        binding.maskView.visibility = View.VISIBLE
        binding.maskView.isEnabled = false

        binding.processData.textThreshold.text = "-"
        binding.processData.buttonThresholdPlus.visibility = View.GONE
        binding.processData.buttonThresholdMinus.visibility = View.GONE
        binding.processData.detectedItem0.text = "LaMa-Dilated"
        binding.processData.detectedItem0Score.text = "Draw mask and process"
        binding.processData.detectedItem1.text = ""
        binding.processData.detectedItem1Score.text = ""
        binding.processData.detectedItem2.text = ""
        binding.processData.detectedItem2Score.text = ""
        binding.processData.inferenceTime.text = "-"
    }

    private fun process(sourceBitmap: Bitmap) {
        binding.buttonProcess.isEnabled = false
        binding.buttonLoad.isEnabled = false
        binding.buttonClear.isEnabled = false
        binding.maskView.isEnabled = false

        val maskBitmap = binding.maskView.getMaskBitmap(
            targetWidth = INPUT_SIZE_W,
            targetHeight = INPUT_SIZE_H
        )

        binding.maskView.visibility = View.GONE
        modelExecutor.process(sourceBitmap, maskBitmap)
    }

    private fun processImage(bitmap: Bitmap): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, INPUT_SIZE_W, INPUT_SIZE_H, true)
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            binding.buttonLoad.isEnabled = true
            binding.buttonClear.isEnabled = hasImage
            binding.buttonProcess.isEnabled = hasImage

            binding.maskView.visibility = View.VISIBLE
            binding.maskView.isEnabled = hasImage
        }
    }

    override fun onResults(resultBitmap: Bitmap, inferenceTime: Long) {
        activity?.runOnUiThread {
            binding.imageView.setImageBitmap(resultBitmap)

            binding.maskView.clear()
            binding.maskView.visibility = View.GONE
            binding.maskView.isEnabled = false

            binding.processData.inferenceTime.text = "$inferenceTime ms"
            binding.buttonLoad.isEnabled = true
            binding.buttonClear.isEnabled = hasImage
            binding.buttonProcess.isEnabled = hasImage
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        modelExecutor.closeENN()
    }

    companion object {
        private const val TAG = "ImageFragment"
        private const val INPUT_SIZE_W = ModelConstants.INPUT1_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT1_H
    }
}
