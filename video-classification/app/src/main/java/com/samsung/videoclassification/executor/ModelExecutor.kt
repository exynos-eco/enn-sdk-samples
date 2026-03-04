// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.videoclassification.executor

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.samsung.videoclassification.data.DataType
import com.samsung.videoclassification.data.LayerType
import com.samsung.videoclassification.data.ModelConstants
import com.samsung.videoclassification.enn_type.BufferSetInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder


@Suppress("IMPLICIT_CAST_TO_ANY")
@OptIn(ExperimentalUnsignedTypes::class)
class ModelExecutor(
    var threshold: Float = 0.5F,
    val context: Context,
    val executorListener: ExecutorListener?
) {
    private external fun ennInitialize()
    private external fun ennDeinitialize()
    private external fun ennOpenModel(filename: String): Long
    private external fun ennCloseModel(modelId: Long)
    private external fun ennAllocateAllBuffers(modelId: Long): BufferSetInfo
    private external fun ennReleaseBuffers(bufferSet: Long, bufferSize: Int)
    private external fun ennExecute(modelId: Long)
    private external fun ennMemcpyHostToDevice(bufferSet: Long, layerNumber: Int, data: ByteArray)
    private external fun ennMemcpyDeviceToHost(bufferSet: Long, layerNumber: Int): ByteArray

    private var modelId: Long = 0
    private var bufferSet: Long = 0
    private var nInBuffer: Int = 0
    private var nOutBuffer: Int = 0

    init {
        System.loadLibrary("enn_jni")
        copyNNCFromAssetsToInternalStorage(MODEL_NAME)
        getLabels()
        setupENN()
    }

    private fun setupENN() {
        // Initialize ENN
        ennInitialize()

        // Open model
        val fileAbsoluteDirectory = File(context.filesDir, MODEL_NAME).absolutePath
        modelId = ennOpenModel(fileAbsoluteDirectory)

        // Allocate all required buffers
        val bufferSetInfo = ennAllocateAllBuffers(modelId)
        bufferSet = bufferSetInfo.buffer_set
        nInBuffer = bufferSetInfo.n_in_buf
        nOutBuffer = bufferSetInfo.n_out_buf
    }

    fun process(frames: List<Bitmap>) {
        // Preprocess 8 frames and merge into a single ByteArray
        val input = preProcess(frames)
        // Copy Input Data
        ennMemcpyHostToDevice(bufferSet, 0, input)

        var inferenceTime = SystemClock.uptimeMillis()
        // Model execute
        ennExecute(modelId)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime
        // Copy Output Data
        val output = ennMemcpyDeviceToHost(bufferSet, nInBuffer)
        executorListener?.onResults(
            postProcess(output), inferenceTime
        )
    }

    fun closeENN() {
        // Release a buffer array
        ennReleaseBuffers(bufferSet, nInBuffer + nOutBuffer)
        // Close a Model and Free all resources
        ennCloseModel(modelId)
        // Destructs ENN process
        ennDeinitialize()
    }

    private fun preProcess(frames: List<Bitmap>): ByteArray {
        val singleFrameSize = INPUT_SIZE_C * INPUT_SIZE_H * INPUT_SIZE_W
        val totalSize = NUM_SEGMENTS * singleFrameSize * Float.SIZE_BYTES
        val byteBuffer = ByteBuffer.allocate(totalSize)
        byteBuffer.order(ByteOrder.nativeOrder())

        for (frame in frames) {
            val floatArray = convertBitmapToFloatArray(frame, INPUT_DATA_LAYER)
            for (value in floatArray) {
                byteBuffer.putFloat(value)
            }
        }

        return byteBuffer.array()
    }

    private fun postProcess(modelOutput: ByteArray): Map<String, Float> {
        val allOutputs = when (OUTPUT_DATA_TYPE) {
            DataType.UINT8 -> {
                modelOutput.toUByteArray().map { it.toFloat() }.toFloatArray()
            }

            DataType.FLOAT32 -> {
                val byteBuffer = ByteBuffer.wrap(modelOutput).order(ByteOrder.nativeOrder())
                val floatBuffer = byteBuffer.asFloatBuffer()
                val floatArray = FloatArray(floatBuffer.remaining())
                floatBuffer.get(floatArray)
                floatArray
            }

            else -> {
                throw IllegalArgumentException("Unsupported output data type: ${OUTPUT_DATA_TYPE}")
            }
        }

        val avgLogits = FloatArray(NUM_CLASSES)
        for (seg in 0 until NUM_SEGMENTS) {
            for (cls in 0 until NUM_CLASSES) {
                avgLogits[cls] += allOutputs[seg * NUM_CLASSES + cls]
            }
        }
        for (cls in 0 until NUM_CLASSES) {
            avgLogits[cls] /= NUM_SEGMENTS.toFloat()
        }

        // Softmax
        val maxLogit = avgLogits.maxOrNull() ?: 0f
        val expScores = avgLogits.map { Math.exp((it - maxLogit).toDouble()) }
        val sumExpScores = expScores.sum()
        val probabilities = expScores.map { (it / sumExpScores).toFloat() }

        if (labelList.size != probabilities.size) {
            Log.w("ModelExecutor",
                "Label count (${labelList.size}) != output classes (${probabilities.size})")
            return emptyMap()
        }

        val output = probabilities.mapIndexed { index, value ->
            labelList[index] to value
        }.filter { it.second >= threshold }
            .sortedByDescending { it.second }
            .toMap()

        return output
    }

    private fun convertBitmapToFloatArray(
        image: Bitmap, layerType: Enum<LayerType> = LayerType.CHW
    ): FloatArray {
        val totalPixels = INPUT_SIZE_H * INPUT_SIZE_W
        val pixels = IntArray(totalPixels)

        image.getPixels(pixels, 0, INPUT_SIZE_W, 0, 0, INPUT_SIZE_W, INPUT_SIZE_H)

        val floatArray = FloatArray(totalPixels * INPUT_SIZE_C)
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        when (layerType) {
            LayerType.CHW -> {
                for (i in 0 until totalPixels) {
                    val color = pixels[i]

                    val r = ((color shr 16) and 0xFF) / 255.0f
                    val g = ((color shr 8) and 0xFF) / 255.0f
                    val b = ((color shr 0) and 0xFF) / 255.0f

                    floatArray[i] = (r - mean[0]) / std[0]
                    floatArray[i + totalPixels] = (g - mean[1]) / std[1]
                    floatArray[i + 2 * totalPixels] = (b - mean[2]) / std[2]
                }
            }

            LayerType.HWC -> {
                for (i in 0 until totalPixels) {
                    val color = pixels[i]
                    val r = ((color shr 16) and 0xFF) / 255.0f
                    val g = ((color shr 8) and 0xFF) / 255.0f
                    val b = ((color shr 0) and 0xFF) / 255.0f

                    floatArray[i * 3] = (r - mean[0]) / std[0]
                    floatArray[i * 3 + 1] = (g - mean[1]) / std[1]
                    floatArray[i * 3 + 2] = (b - mean[2]) / std[2]
                }
            }

            else -> {
                Log.e("ModelExecutor", "Unsupported LayerType: $layerType")
            }
        }

        return floatArray
    }

    private fun copyNNCFromAssetsToInternalStorage(filename: String) {
        try {
            val inputStream = context.assets.open(filename)
            val outputFile = File(context.filesDir, filename)
            val outputStream = FileOutputStream(outputFile)
            val buffer = ByteArray(2048)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            inputStream.close()
            outputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun getLabels() {
        try {
            context.assets.open(LABEL_FILE)
                .bufferedReader().use { reader -> labelList = reader.readLines()
                }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    interface ExecutorListener {
        fun onError(error: String)
        fun onResults(
            result: Map<String, Float>, inferenceTime: Long
        )
    }

    companion object {
        var labelList: List<String> = mutableListOf()

        private const val MODEL_NAME = ModelConstants.MODEL_NAME

        private val INPUT_DATA_LAYER = ModelConstants.INPUT_DATA_LAYER

        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
        private const val INPUT_SIZE_C = ModelConstants.INPUT_SIZE_C

        private const val NUM_SEGMENTS = ModelConstants.NUM_SEGMENTS
        private const val NUM_CLASSES = ModelConstants.NUM_CLASSES

        private val OUTPUT_DATA_TYPE = ModelConstants.OUTPUT_DATA_TYPE

        private const val LABEL_FILE = ModelConstants.LABEL_FILE
    }
}
