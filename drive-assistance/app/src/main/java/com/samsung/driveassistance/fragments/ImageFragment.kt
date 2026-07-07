// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.driveassistance.fragments

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import com.samsung.driveassistance.data.ModelConstants
import com.samsung.driveassistance.databinding.FragmentImageBinding
import com.samsung.driveassistance.executor.ModelExecutor

class ImageFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentImageBinding
    private lateinit var modelExecutor: ModelExecutor
    private lateinit var inputBitmap: Bitmap

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { loadImage(it) }
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

        binding.buttonLoad.text = "LOAD"
        binding.buttonProcess.text = "PROCESS"
        binding.buttonProcess.isEnabled = false
        binding.processData.inferenceTime.text = "-"
        binding.processData.textThreshold.text = String.format("%.1f", modelExecutor.threshold)

        binding.buttonLoad.setOnClickListener {
            getContent.launch("image/*")
        }

        binding.buttonProcess.setOnClickListener {
            if (::inputBitmap.isInitialized) {
                modelExecutor.process(inputBitmap)
            }
        }

        binding.processData.buttonThresholdMinus.setOnClickListener {
            updateThreshold(-0.1F)
        }

        binding.processData.buttonThresholdPlus.setOnClickListener {
            updateThreshold(0.1F)
        }
    }

    private fun loadImage(uri: Uri) {
        val bitmap = ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(requireContext().contentResolver, uri)
        ) { decoder, _, _ ->
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

        inputBitmap = Bitmap.createScaledBitmap(
            bitmap,
            ModelConstants.INPUT_SIZE_W,
            ModelConstants.INPUT_SIZE_H,
            true
        ).copy(Bitmap.Config.ARGB_8888, false)

        binding.imageView.setImageBitmap(inputBitmap)
        binding.buttonProcess.isEnabled = true
        binding.processData.inferenceTime.text = "-"
    }

    private fun updateThreshold(delta: Float) {
        modelExecutor.threshold = (modelExecutor.threshold + delta).coerceIn(0.0F, 0.95F)
        binding.processData.textThreshold.text = String.format("%.1f", modelExecutor.threshold)
    }

    override fun onResults(result: ModelExecutor.YoloPResult, inferenceTime: Long) {
        activity?.runOnUiThread {
            binding.processData.inferenceTime.text = "$inferenceTime ms"
            binding.imageView.setImageBitmap(drawOverlay(inputBitmap, result))
        }
    }

    override fun onError(error: String) {
        Log.e(TAG, error)
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
    }

    companion object {
        private const val TAG = "YoloPImageFragment"
    }
}
