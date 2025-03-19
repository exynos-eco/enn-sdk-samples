// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.objectdetection.executor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.samsung.objectdetection.data.DataType
import com.samsung.objectdetection.data.DetectionResult
import com.samsung.objectdetection.data.LayerType
import com.samsung.objectdetection.data.ModelConstants
import com.samsung.objectdetection.enn_type.BufferSetInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Suppress("IMPLICIT_CAST_TO_ANY")
@OptIn(ExperimentalUnsignedTypes::class)
class ModelExecutor(
    var threshold: Float = 0.3F,
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

    fun process(image: Bitmap) {
        val input = preProcess(image)
        ennMemcpyHostToDevice(bufferSet, 0, input)

        var inferenceTime = SystemClock.uptimeMillis()
        // Model execute
        ennExecute(modelId)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime

        val logitsOutput = ennMemcpyDeviceToHost(bufferSet, 1)  // logits
        val boxesOutput = ennMemcpyDeviceToHost(bufferSet, 2)  // boxes

        executorListener?.onResults(
            postProcess(logitsOutput, boxesOutput), inferenceTime
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

    private fun preProcess(image: Bitmap): ByteArray {
        val byteArray = when (INPUT_DATA_TYPE) {
            DataType.FLOAT32 -> {
                val data = convertBitmapToFloatArray(image, INPUT_DATA_LAYER)
                val byteBuffer = ByteBuffer.allocate(data.size * Float.SIZE_BYTES)
                byteBuffer.order(ByteOrder.nativeOrder())
                byteBuffer.asFloatBuffer().put(data)
                byteBuffer.array()
            }
            else -> {
                throw IllegalArgumentException("Unsupported input data type: ${INPUT_DATA_TYPE}")
            }
        }
        return byteArray
    }

    private fun postProcess(logitsOutput: ByteArray, boxesOutput: ByteArray): List<DetectionResult> {
        val logits = ByteBuffer.wrap(logitsOutput).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val floatLogits = FloatArray(logits.remaining())

        logits.get(floatLogits)

        val boxes = ByteBuffer.wrap(boxesOutput).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val floatBoxes = FloatArray(boxes.remaining())
        boxes.get(floatBoxes)

        val result = mutableListOf<DetectionResult>()

        for (i in 0 until OUTPUT_NUM_BOXES) {
            val index = i * OUTPUT_NUM_CLASSES
            val classProbabilities = softmax(floatLogits, index, OUTPUT_NUM_CLASSES)
            val maxClassIndex = classProbabilities.indices.maxByOrNull { classProbabilities[it] } ?: 0
            if (maxClassIndex == 91) {
                Log.d("Debug", "Skipping background detection (Index: 91)")
                continue
            }

            val maxClassLabel = labelList[maxClassIndex -1]
            val maxClassScore = classProbabilities[maxClassIndex]

            if (maxClassScore >= threshold) {
                val boxIndex = i * OUTPUT_BOX_COORDS

                val xCenter = floatBoxes[boxIndex]
                val yCenter = floatBoxes[boxIndex + 1]
                val width = floatBoxes[boxIndex + 2]
                val height = floatBoxes[boxIndex + 3]

                val left = xCenter - width / 2
                val top = yCenter - height / 2
                val right = xCenter + width / 2
                val bottom = yCenter + height / 2

                if (width <= 0.02 || height <= 0.02 || left < 0 || top < 0 || right > 1 || bottom > 1) {
                    Log.w("Debug", "Warning: Small/Invalid bounding box detected! Skipping...")
                    continue
                }

                result.add(
                    DetectionResult(
                        Pair(maxClassLabel, maxClassScore),
                        RectF(left, top, right, bottom)
                    )
                )
            }
        }

        return result
    }

    fun softmax(logits: FloatArray, offset: Int, length: Int): FloatArray {
        val expValues = FloatArray(length)
        var sum = 0f
        for (i in 0 until length) {
            expValues[i] = kotlin.math.exp(logits[offset + i])
            sum += expValues[i]
        }
        for (i in 0 until length) {
            expValues[i] /= sum
        }
        return expValues
    }

    private fun convertBitmapToFloatArray(
        image: Bitmap, layerType: Enum<LayerType> = LayerType.HWC
    ): FloatArray {
        val totalPixels = INPUT_SIZE_H * INPUT_SIZE_W
        val pixels = IntArray(totalPixels)

        image.getPixels(
            pixels,
            0,
            INPUT_SIZE_W,
            0,
            0,
            INPUT_SIZE_W,
            INPUT_SIZE_H
        )

        val floatArray = FloatArray(totalPixels * INPUT_SIZE_C)
        val offset: IntArray
        val stride: Int

        if (layerType == LayerType.CHW) {
            offset = intArrayOf(0, totalPixels, 2 * totalPixels)
            stride = 1
        } else {
            offset = intArrayOf(0, 1, 2)
            stride = 3
        }

        for (i in 0 until totalPixels) {
            val color = pixels[i]
            val r = ((color shr 16) and 0xFF) / 255.0f
            val g = ((color shr 8) and 0xFF) / 255.0f
            val b = ((color shr 0) and 0xFF) / 255.0f

            floatArray[i * stride + offset[0]] = (r - 0.485f) / 0.229f
            floatArray[i * stride + offset[1]] = (g - 0.456f) / 0.224f
            floatArray[i * stride + offset[2]] = (b - 0.406f) / 0.225f
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
                .bufferedReader().use { reader -> labelList = reader.readLines() }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    interface ExecutorListener {
        fun onError(error: String)
        fun onResults(
            detectionResult: List<DetectionResult>, inferenceTime: Long
        )
    }

    companion object {
        var labelList: List<String> = mutableListOf()

        private const val MODEL_NAME = ModelConstants.MODEL_NAME

        private val INPUT_DATA_LAYER = ModelConstants.INPUT_DATA_LAYER
        private val INPUT_DATA_TYPE = ModelConstants.INPUT_DATA_TYPE

        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
        private const val INPUT_SIZE_C = ModelConstants.INPUT_SIZE_C

        private const val OUTPUT_NUM_CLASSES = ModelConstants.OUTPUT_NUM_CLASSES
        private const val OUTPUT_BOX_COORDS = ModelConstants.OUTPUT_BOX_COORDS
        private const val OUTPUT_NUM_BOXES = ModelConstants.OUTPUT_NUM_BOXES

        private const val LABEL_FILE = ModelConstants.LABEL_FILE
    }
}
