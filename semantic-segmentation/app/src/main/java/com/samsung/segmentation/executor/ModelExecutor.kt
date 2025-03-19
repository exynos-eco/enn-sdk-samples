// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.segmentation.executor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import com.samsung.segmentation.data.DataType
import com.samsung.segmentation.data.LayerType
import com.samsung.segmentation.data.ModelConstants
import com.samsung.segmentation.enn_type.BufferSetInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder


@Suppress("IMPLICIT_CAST_TO_ANY")
@OptIn(ExperimentalUnsignedTypes::class)
class ModelExecutor(
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
        // Process Image to Input Byte Array
        val input = preProcess(image)
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

    private fun preProcess(image: Bitmap): ByteArray {
        val byteArray = when (INPUT_DATA_TYPE) {
            DataType.UINT8 -> {
                convertBitmapToUByteArray(image, INPUT_DATA_LAYER).asByteArray()
            }

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

    private fun postProcess(modelOutput: ByteArray): IntArray {
        val output = when (OUTPUT_DATA_TYPE) {
            DataType.UINT8 -> {
                modelOutput.asUByteArray().map { it ->
                    ((it.toInt()
                            - OUTPUT_CONVERSION_OFFSET)
                            / OUTPUT_CONVERSION_SCALE).toInt()
                }.toIntArray()
            }

            DataType.FLOAT32 -> {
                val byteBuffer = ByteBuffer.wrap(modelOutput).order(ByteOrder.nativeOrder())
                val floatBuffer = byteBuffer.asFloatBuffer()
                val data = FloatArray(floatBuffer.remaining())
                floatBuffer.get(data)

                val pixelCount = OUTPUT_SIZE_H * OUTPUT_SIZE_W
                val result = IntArray(pixelCount)

                for (i in 0 until pixelCount) {
                    var maxValue = 0F
                    var maxIndex = 0

                    for (c in 0 until OUTPUT_SIZE_C) {
                        val index = c * pixelCount + i
                        if (data[index] > maxValue) {
                            maxValue = data[index]
                            maxIndex = c
                        }
                    }
                    result[i] = maxIndex
                }
                result
            }

            else -> {
                throw IllegalArgumentException("Unsupported output data type: ${OUTPUT_DATA_TYPE}")
            }
        }

            return output.map { colorList[it] }.toIntArray()
    }

    private fun convertBitmapToUByteArray(
        image: Bitmap, layerType: Enum<LayerType> = LayerType.HWC
    ): UByteArray {
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

        val uByteArray = UByteArray(totalPixels * INPUT_SIZE_C)
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
            uByteArray[i * stride + offset[0]] = ((((color shr 16) and 0xFF)
                    - INPUT_CONVERSION_OFFSET)
                    / INPUT_CONVERSION_SCALE).toInt().toUByte()
            uByteArray[i * stride + offset[1]] = ((((color shr 8) and 0xFF)
                    - INPUT_CONVERSION_OFFSET)
                    / INPUT_CONVERSION_SCALE).toInt().toUByte()
            uByteArray[i * stride + offset[2]] = ((((color shr 0) and 0xFF)
                    - INPUT_CONVERSION_OFFSET)
                    / INPUT_CONVERSION_SCALE).toInt().toUByte()
        }

        return uByteArray
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
            floatArray[i * stride + offset[0]] = ((((color shr 16) and 0xFF)
                    - INPUT_CONVERSION_OFFSET)
                    / INPUT_CONVERSION_SCALE)
            floatArray[i * stride + offset[1]] = ((((color shr 8) and 0xFF)
                    - INPUT_CONVERSION_OFFSET)
                    / INPUT_CONVERSION_SCALE)
            floatArray[i * stride + offset[2]] = ((((color shr 0) and 0xFF)
                    - INPUT_CONVERSION_OFFSET)
                    / INPUT_CONVERSION_SCALE)
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

    interface ExecutorListener {
        fun onError(error: String)
        fun onResults(
            result: IntArray, inferenceTime: Long
        )
    }

    companion object {
        private val colorList = mutableListOf<Int>().apply {
            for (i in 0 until ModelConstants.OUTPUT_SIZE_C) {
                add(Color.HSVToColor(150, floatArrayOf(360F / 19 * i, 0.8F, 1F)))
            }
        }

        private const val MODEL_NAME = ModelConstants.MODEL_NAME

        private val INPUT_DATA_LAYER = ModelConstants.INPUT_DATA_LAYER
        private val INPUT_DATA_TYPE = ModelConstants.INPUT_DATA_TYPE

        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
        private const val INPUT_SIZE_C = ModelConstants.INPUT_SIZE_C

        private const val INPUT_CONVERSION_SCALE = ModelConstants.INPUT_CONVERSION_SCALE
        private const val INPUT_CONVERSION_OFFSET = ModelConstants.INPUT_CONVERSION_OFFSET

        private val OUTPUT_DATA_TYPE = ModelConstants.OUTPUT_DATA_TYPE

        private const val OUTPUT_SIZE_W = ModelConstants.OUTPUT_SIZE_W
        private const val OUTPUT_SIZE_H = ModelConstants.OUTPUT_SIZE_H
        private const val OUTPUT_SIZE_C = ModelConstants.OUTPUT_SIZE_C

        private const val OUTPUT_CONVERSION_SCALE = ModelConstants.OUTPUT_CONVERSION_SCALE
        private const val OUTPUT_CONVERSION_OFFSET = ModelConstants.OUTPUT_CONVERSION_OFFSET
    }
}
