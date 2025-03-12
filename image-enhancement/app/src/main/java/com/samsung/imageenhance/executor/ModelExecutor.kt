// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.imageenhance.executor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import com.samsung.imageenhance.data.DataType
import com.samsung.imageenhance.data.LayerType
import com.samsung.imageenhance.data.ModelConstants
import com.samsung.imageenhance.enn_type.BufferSetInfo
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
            postProcess(output, OUTPUT_DATA_LAYER), inferenceTime
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

    private fun postProcess(
        output: ByteArray, layerType: Enum<LayerType> = LayerType.HWC
    ): IntArray {
        val totalPixels = OUTPUT_SIZE_W * OUTPUT_SIZE_H
        val data = convertOutputByteToFloatArray(output)

        val (offset, stride) = if (layerType == LayerType.CHW) {
            Pair(intArrayOf(0, totalPixels, 2 * totalPixels), 1)
        } else {
            Pair(intArrayOf(0, 1, 2), 3)
        }

        return IntArray(OUTPUT_SIZE_W * OUTPUT_SIZE_H) { i ->
            convertToArgb(
                r = data[i * stride + offset[0]],
                g = data[i * stride + offset[1]],
                b = data[i * stride + offset[2]],
                a = 255
            )
        }
    }

    private fun convertToArgb(r: Float, g: Float, b: Float, a: Int): Int {
        val red = (r * OUTPUT_CONVERSION_SCALE + OUTPUT_CONVERSION_OFFSET).toInt()
        val green = (g * OUTPUT_CONVERSION_SCALE + OUTPUT_CONVERSION_OFFSET).toInt()
        val blue = (b * OUTPUT_CONVERSION_SCALE + OUTPUT_CONVERSION_OFFSET).toInt()
        return Color.argb(a, red, green, blue)
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

    private fun convertOutputByteToFloatArray(
        modelOutput: ByteArray
    ): FloatArray {
        return when (OUTPUT_DATA_TYPE) {
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
            pixels: IntArray, inferenceTime: Long
        )
    }

    companion object {
        val dequantizedValues = List(256) { it.toFloat() * 0.00390625F }

        private const val MODEL_NAME = ModelConstants.MODEL_NAME

        private val INPUT_DATA_LAYER = ModelConstants.INPUT_DATA_LAYER
        private val INPUT_DATA_TYPE = ModelConstants.INPUT_DATA_TYPE

        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
        private const val INPUT_SIZE_C = ModelConstants.INPUT_SIZE_C

        private const val INPUT_CONVERSION_SCALE = ModelConstants.INPUT_CONVERSION_SCALE
        private const val INPUT_CONVERSION_OFFSET = ModelConstants.INPUT_CONVERSION_OFFSET

        private val OUTPUT_DATA_TYPE = ModelConstants.OUTPUT_DATA_TYPE
        private val OUTPUT_DATA_LAYER = ModelConstants.OUTPUT_DATA_LAYER

        private const val OUTPUT_SIZE_W = ModelConstants.OUTPUT_SIZE_W
        private const val OUTPUT_SIZE_H = ModelConstants.OUTPUT_SIZE_H
        private const val OUTPUT_SIZE_C = ModelConstants.OUTPUT_SIZE_C

        private const val OUTPUT_CONVERSION_SCALE = ModelConstants.OUTPUT_CONVERSION_SCALE
        private const val OUTPUT_CONVERSION_OFFSET = ModelConstants.OUTPUT_CONVERSION_OFFSET
    }
}
