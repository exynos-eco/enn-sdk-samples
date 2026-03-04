// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.videoclassification.fragments

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.samsung.videoclassification.R
import com.samsung.videoclassification.data.ModelConstants
import com.samsung.videoclassification.databinding.FragmentImageBinding
import com.samsung.videoclassification.executor.ModelExecutor


class ImageFragment : Fragment(), ModelExecutor.ExecutorListener {
    private lateinit var binding: FragmentImageBinding
    private lateinit var bitmapBuffer: Bitmap
    private lateinit var modelExecutor: ModelExecutor

    private lateinit var detectedItems: List<DetectedItemViewGroup>


    data class DetectedItemViewGroup(
        val label: TextView,
        val score: TextView,
        val gauge: ProgressBar,
        val container: View
    )

    private lateinit var origin_img: Bitmap

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                origin_img = ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(requireContext().contentResolver, uri)
                ) { decoder, _, _ ->
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSampleSize(1)
                }

                val resizedImage = processImage(origin_img)

                binding.imageView.setImageBitmap(origin_img)
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

            for (detectedItem in detectedItems) {
                detectedItem.container.visibility = View.INVISIBLE
            }
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
            DetectedItemViewGroup(
                binding.detectedItem0,
                binding.detectedItem0Score,
                binding.detectedItem0Gauge,
                binding.detectedBlock0
            ),
            DetectedItemViewGroup(
                binding.detectedItem1,
                binding.detectedItem1Score,
                binding.detectedItem1Gauge,
                binding.detectedBlock1
            ),
            DetectedItemViewGroup(
                binding.detectedItem2,
                binding.detectedItem2Score,
                binding.detectedItem2Gauge,
                binding.detectedBlock2
            ),
            DetectedItemViewGroup(
                binding.detectedItem3,
                binding.detectedItem3Score,
                binding.detectedItem3Gauge,
                binding.detectedBlock3
            ),
            DetectedItemViewGroup(
                binding.detectedItem4,
                binding.detectedItem4Score,
                binding.detectedItem4Gauge,
                binding.detectedBlock4
            )
        )
    }

    private fun process(bitmapBuffer: Bitmap) {
    }

    private fun processImage(bitmap: Bitmap): Bitmap {
        val (scaledWidth, scaledHeight) = calculateScaleSize(bitmap.width, bitmap.height)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        return padBitmapToSquare(scaledBitmap, INPUT_SIZE_W, INPUT_SIZE_H)
    }


    private fun padBitmapToSquare(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val paddedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(paddedBitmap)
        canvas.drawColor(requireContext().getColor( R.color.soft_gray)) // Set background fill color

        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        val left = (targetWidth - originalWidth) / 2
        val top = (targetHeight - originalHeight) / 2

        canvas.drawBitmap(bitmap, left.toFloat(), top.toFloat(), null)

        return paddedBitmap
    }


    private fun calculateScaleSize(bitmapWidth: Int, bitmapHeight: Int): Pair<Int, Int> {
        val scaleFactor = minOf(
            INPUT_SIZE_W.toFloat() / bitmapWidth, INPUT_SIZE_H.toFloat() / bitmapHeight
        )

        return Pair((bitmapWidth * scaleFactor).toInt(), (bitmapHeight * scaleFactor).toInt())
    }


    private fun adjustThreshold(delta: Float) {
        val newThreshold = modelExecutor.threshold + delta
        if (newThreshold in 0.00..0.95) {
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
        detectedItems.forEachIndexed { index, item ->
            if (index < result.size) {
                val key = result.keys.elementAt(index)
                val value = result[key] ?: 0f

                item.label.text = key
                animateScoreAndProgress(item.score, item.gauge, value)
                item.container.visibility = View.VISIBLE
                item.gauge.visibility = View.VISIBLE
            } else {
                item.label.text = ""
                item.score.text = ""
                item.gauge.progress = 0
                item.container.visibility = View.INVISIBLE
            }
        }
    }


    private fun animateScoreAndProgress(
        scoreView: TextView,
        progressBar: ProgressBar,
        targetScore: Float
    ) {
        val duration = 1000L

        val scoreAnimator = ValueAnimator.ofFloat(0f/*progressBar.progress*/, targetScore).apply {
            this.duration = duration
            addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Float
                scoreView.text =   String.format("%.1f%%", animatedValue * 100)

            }
        }

        val progressAnimator =
            ValueAnimator.ofInt(0/*progressBar.progress*/, (targetScore * 1000).toInt()).apply {
                this.duration = duration
                addUpdateListener { animation ->
                    val animatedProgress = animation.animatedValue as Int
                    progressBar.progress = animatedProgress
                }
            }

        scoreAnimator.start()
        progressAnimator.start()
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