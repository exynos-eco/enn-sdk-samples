package com.samsung.imagetotext.fragments

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
import com.samsung.imagetotext.data.DetectionResult
import com.samsung.imagetotext.databinding.FragmentImageBinding
import com.samsung.imagetotext.executor.ModelExecutor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ImageFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentImageBinding
    private lateinit var modelExecutor: ModelExecutor
    private lateinit var inferenceExecutor: ExecutorService
    private lateinit var bitmapBuffer: Bitmap

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val bitmap = ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(requireContext().contentResolver, uri)
        ) { decoder, _, _ ->
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }.copy(Bitmap.Config.ARGB_8888, false)

        binding.overlay.clear()
        binding.imageView.setImageBitmap(bitmap)
        binding.overlay.setImageSize(
            bitmap.width,
            bitmap.height
        )
        bitmapBuffer = bitmap
        binding.buttonProcess.isEnabled = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        binding = FragmentImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        inferenceExecutor = Executors.newSingleThreadExecutor()
        modelExecutor = ModelExecutor(context = requireContext(), executorListener = this)
        setUI()
    }

    private fun setUI() {
        binding.buttonLoad.setOnClickListener { getContent.launch("image/*") }
        binding.buttonProcess.isEnabled = false
        binding.buttonProcess.setOnClickListener {
            if (!::bitmapBuffer.isInitialized) return@setOnClickListener
            binding.buttonProcess.isEnabled = false
            inferenceExecutor.execute { modelExecutor.process(bitmapBuffer) }
        }
        binding.processData.textThreshold.text = String.format("%.1f", modelExecutor.threshold)
        binding.processData.buttonThresholdPlus.setOnClickListener { adjustThreshold(0.05f) }
        binding.processData.buttonThresholdMinus.setOnClickListener { adjustThreshold(-0.05f) }
    }

    private fun adjustThreshold(delta: Float) {
        modelExecutor.threshold = (modelExecutor.threshold + delta).coerceIn(0.05f, 0.95f)
        binding.processData.textThreshold.text = String.format("%.2f", modelExecutor.threshold)
    }

    override fun onError(error: String) {
        Log.e(TAG, error)
        activity?.runOnUiThread { binding.buttonProcess.isEnabled = true }
    }

    override fun onResults(detectionResult: List<DetectionResult>, inferenceTime: Long) {
        activity?.runOnUiThread {
            binding.processData.inferenceTime.text = "$inferenceTime ms"
            binding.overlay.setResults(detectionResult)
            binding.buttonProcess.isEnabled = true
        }
    }

    override fun onDestroyView() {
        modelExecutor.closeENN()
        inferenceExecutor.shutdownNow()
        super.onDestroyView()
    }

    companion object { private const val TAG = "ImageFragment" }
}
