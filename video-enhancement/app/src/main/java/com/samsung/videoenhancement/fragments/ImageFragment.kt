// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.videoenhancement.fragments

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.samsung.videoenhancement.data.ModelConstants
import com.samsung.videoenhancement.databinding.FragmentImageBinding
import com.samsung.videoenhancement.executor.ModelExecutor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ImageFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentImageBinding
    private lateinit var modelExecutor: ModelExecutor
    private lateinit var interpolationExecutor: ExecutorService

    private var image1: Bitmap? = null
    private var image2: Bitmap? = null
    private var nextImageIndex = 0
    private var resultAnimation: AnimationDrawable? = null

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@registerForActivityResult

            try {
                val resized = resizeForModel(decodeBitmap(uri))

                if (nextImageIndex == 0) {
                    image1 = resized
                    binding.imageView1.setImageBitmap(resized)
                    binding.textImage1.visibility = View.GONE
                    nextImageIndex = 1

                    Toast.makeText(
                        requireContext(),
                        "Image 1 loaded. Press LOAD again to select Image 2.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    image2 = resized
                    binding.imageView2.setImageBitmap(resized)
                    binding.textImage2.visibility = View.GONE
                    nextImageIndex = 0

                    Toast.makeText(
                        requireContext(),
                        "Image 2 loaded. Press PROCESS.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                clearResult()
            } catch (e: Exception) {
                Log.e(TAG, "Image loading failed", e)
                showErrorDialog("Failed to load image.\n${e.message.orEmpty()}")
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

        interpolationExecutor = Executors.newSingleThreadExecutor()
        modelExecutor = ModelExecutor(
            context = requireContext(),
            executorListener = this
        )

        binding.root.setBackgroundColor(Color.WHITE)

        binding.buttonLoad.setOnClickListener {
            getContent.launch("image/*")
        }

        binding.buttonProcess.setOnClickListener {
            val first = image1
            val second = image2

            if (first == null || second == null) {
                showMissingImageDialog(first == null, second == null)
                return@setOnClickListener
            }

            binding.buttonLoad.isEnabled = false
            binding.buttonProcess.isEnabled = false
            stopResultAnimation()
            binding.processData.inferenceTime.text = "Processing..."

            /*
             * DEPTH = 3 creates:
             * 0%, 12.5%, 25%, 37.5%, 50%, 62.5%, 75%, 87.5%, 100%
             *
             * Seven RIFE inferences are executed in the worker thread.
             */
            interpolationExecutor.execute {
                try {
                    val result = modelExecutor.generateIntermediateFrames(
                        frame0 = first,
                        frame1 = second,
                        depth = INTERPOLATION_DEPTH
                    )

                    activity?.runOnUiThread {
                        if (!isAdded || view == null) {
                            return@runOnUiThread
                        }

                        binding.processData.inferenceTime.text =
                            "${result.totalInferenceTimeMs} ms"
                        showResultAnimation(result.frames)
                        binding.buttonLoad.isEnabled = true
                        binding.buttonProcess.isEnabled = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Multi-frame interpolation failed", e)
                    activity?.runOnUiThread {
                        binding.buttonLoad.isEnabled = true
                        binding.buttonProcess.isEnabled = true
                        showErrorDialog(
                            "Frame interpolation failed.\n${e.message.orEmpty()}"
                        )
                    }
                }
            }
        }

        // RIFE does not use a confidence threshold.
        binding.processData.buttonThresholdPlus.visibility = View.GONE
        binding.processData.buttonThresholdMinus.visibility = View.GONE
        binding.processData.textThreshold.visibility = View.GONE
    }

    private fun showMissingImageDialog(missingImage1: Boolean, missingImage2: Boolean) {
        val message = when {
            missingImage1 && missingImage2 ->
                "Please load Image 1 and Image 2 before processing."
            missingImage1 -> "Please load Image 1 before processing."
            else -> "Please load Image 2 before processing."
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Images Not Loaded")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun decodeBitmap(uri: Uri): Bitmap {
        return ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(requireContext().contentResolver, uri)
        ) { decoder, _, _ ->
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private fun resizeForModel(bitmap: Bitmap): Bitmap {
        val targetRatio = INPUT_SIZE_W.toFloat() / INPUT_SIZE_H.toFloat()
        val sourceRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

        val cropWidth: Int
        val cropHeight: Int
        val cropX: Int
        val cropY: Int

        if (sourceRatio > targetRatio) {
            cropHeight = bitmap.height
            cropWidth = (cropHeight * targetRatio).toInt().coerceAtLeast(1)
            cropX = (bitmap.width - cropWidth) / 2
            cropY = 0
        } else {
            cropWidth = bitmap.width
            cropHeight = (cropWidth / targetRatio).toInt().coerceAtLeast(1)
            cropX = 0
            cropY = (bitmap.height - cropHeight) / 2
        }

        val cropped = Bitmap.createBitmap(
            bitmap,
            cropX,
            cropY,
            cropWidth,
            cropHeight
        )

        return Bitmap.createScaledBitmap(
            cropped,
            INPUT_SIZE_W,
            INPUT_SIZE_H,
            true
        )
    }

    /**
     * Plays the generated frames forward and backward.
     *
     * For nine generated frames:
     * 0 → 1 → ... → 8 → 7 → ... → 1 → repeat
     *
     * The first and last frames are not duplicated at the turning points,
     * preventing a visible pause.
     */
    private fun showResultAnimation(frames: List<Bitmap>) {
        if (frames.size < 2) return

        val animation = AnimationDrawable().apply {
            isOneShot = false

            frames.forEach { frame ->
                addFrame(
                    BitmapDrawable(resources, frame),
                    FRAME_DURATION_MS
                )
            }

            for (index in frames.size - 2 downTo 1) {
                addFrame(
                    BitmapDrawable(resources, frames[index]),
                    FRAME_DURATION_MS
                )
            }
        }

        resultAnimation = animation
        binding.resultImageView.setImageDrawable(animation)
        binding.textResult.visibility = View.GONE

        // Start after the drawable has been attached and laid out.
        binding.resultImageView.post {
            animation.start()
        }
    }

    private fun clearResult() {
        stopResultAnimation()
        binding.resultImageView.setImageDrawable(null)
        binding.textResult.visibility = View.VISIBLE
        binding.processData.inferenceTime.text = "- ms"
    }

    private fun stopResultAnimation() {
        resultAnimation?.stop()
        binding.resultImageView.setImageDrawable(null)
        resultAnimation = null
    }

    private fun showErrorDialog(message: String) {
        if (!isAdded) return

        AlertDialog.Builder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    /*
     * Kept for CameraFragment and other callers that use the original
     * single-interpolation API.
     */
    override fun onError(error: String) {
        Log.e(TAG, error)
        activity?.runOnUiThread {
            binding.buttonLoad.isEnabled = true
            binding.buttonProcess.isEnabled = true
            showErrorDialog(error)
        }
    }

    override fun onResults(interpolatedFrame: Bitmap, inferenceTime: Long) {
        activity?.runOnUiThread {
            val first = image1 ?: return@runOnUiThread
            val second = image2 ?: return@runOnUiThread

            binding.processData.inferenceTime.text = "$inferenceTime ms"
            showResultAnimation(listOf(first, interpolatedFrame, second))
            binding.buttonLoad.isEnabled = true
            binding.buttonProcess.isEnabled = true
        }
    }

    override fun onDestroyView() {
        stopResultAnimation()

        if (::interpolationExecutor.isInitialized) {
            interpolationExecutor.shutdownNow()
        }

        modelExecutor.closeENN()
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "RifeImageFragment"

        private const val FRAME_DURATION_MS = 100
        private const val INTERPOLATION_DEPTH = 3

        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
    }
}
