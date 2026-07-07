// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.driveassistance.executor

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.samsung.driveassistance.data.LayerType
import com.samsung.driveassistance.data.ModelConstants
import com.samsung.driveassistance.enn_type.BufferSetInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ModelExecutor(
    var threshold: Float = 0.5F,
    private val context: Context,
    private val executorListener: ExecutorListener?
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

    private var modelId: Long = 0L
    private var bufferSet: Long = 0L
    private var nInBuffer: Int = 0
    private var nOutBuffer: Int = 0

    init {
        System.loadLibrary("enn_jni")
        copyModelFromAssets()
        setupENN()
    }

    private fun setupENN() {
        ennInitialize()

        val modelPath = File(context.filesDir, MODEL_NAME).absolutePath
        modelId = ennOpenModel(modelPath)

        val bufferSetInfo = ennAllocateAllBuffers(modelId)
        bufferSet = bufferSetInfo.buffer_set
        nInBuffer = bufferSetInfo.n_in_buf
        nOutBuffer = bufferSetInfo.n_out_buf
    }

    fun process(bitmap: Bitmap) {
        try {
            ennMemcpyHostToDevice(bufferSet, 0, preProcess(bitmap))

            var inferenceTime = SystemClock.uptimeMillis()
            ennExecute(modelId)
            inferenceTime = SystemClock.uptimeMillis() - inferenceTime

            val drivableOutput = toFloatArray(ennMemcpyDeviceToHost(bufferSet, nInBuffer + DRIVABLE_OUTPUT_INDEX))
            val laneOutput = toFloatArray(ennMemcpyDeviceToHost(bufferSet, nInBuffer + LANE_OUTPUT_INDEX))

            executorListener?.onResults(
                YoloPResult(
                    drivableMask = makeMask(drivableOutput),
                    laneMask = makeMask(laneOutput)
                ),
                inferenceTime
            )
        } catch (e: Exception) {
            executorListener?.onError(e.message ?: "YoloP inference failed")
        }
    }

    fun closeENN() {
        if (bufferSet != 0L) {
            ennReleaseBuffers(bufferSet, nInBuffer + nOutBuffer)
            bufferSet = 0L
        }
        if (modelId != 0L) {
            ennCloseModel(modelId)
            modelId = 0L
        }
        ennDeinitialize()
    }

    private fun preProcess(bitmap: Bitmap): ByteArray {
        val resizedBitmap = if (bitmap.width == INPUT_SIZE_W && bitmap.height == INPUT_SIZE_H) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, INPUT_SIZE_W, INPUT_SIZE_H, true)
        }

        val pixels = IntArray(INPUT_SIZE_W * INPUT_SIZE_H)
        resizedBitmap.getPixels(pixels, 0, INPUT_SIZE_W, 0, 0, INPUT_SIZE_W, INPUT_SIZE_H)

        val input = FloatArray(INPUT_ELEMENT_COUNT)
        val imageSize = INPUT_SIZE_W * INPUT_SIZE_H

        when (INPUT_DATA_LAYER) {
            LayerType.CHW -> {
                for (i in pixels.indices) {
                    val pixel = pixels[i]
                    input[i] = ((pixel shr 16) and 0xFF) / 255.0F
                    input[imageSize + i] = ((pixel shr 8) and 0xFF) / 255.0F
                    input[imageSize * 2 + i] = (pixel and 0xFF) / 255.0F
                }
            }

            LayerType.HWC -> {
                for (i in pixels.indices) {
                    val pixel = pixels[i]
                    val base = i * INPUT_SIZE_C
                    input[base] = ((pixel shr 16) and 0xFF) / 255.0F
                    input[base + 1] = ((pixel shr 8) and 0xFF) / 255.0F
                    input[base + 2] = (pixel and 0xFF) / 255.0F
                }
            }

            LayerType.RAW -> TODO()
        }

        if (resizedBitmap !== bitmap) {
            resizedBitmap.recycle()
        }

        return toByteArray(input)
    }

    private fun makeMask(output: FloatArray): BooleanArray {
        val imageSize = INPUT_SIZE_W * INPUT_SIZE_H
        val mask = BooleanArray(imageSize)

        if (output.size < imageSize * 2) {
            return mask
        }

        for (i in 0 until imageSize) {
            val background = output[i]
            val target = output[imageSize + i]
            mask[i] = target > background && target >= threshold
        }
        return mask
    }

    private fun toByteArray(data: FloatArray): ByteArray {
        return ByteBuffer.allocate(data.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .apply { asFloatBuffer().put(data) }
            .array()
    }

    private fun toFloatArray(data: ByteArray): FloatArray {
        val floatBuffer = ByteBuffer.wrap(data)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        return FloatArray(floatBuffer.remaining()).also {
            floatBuffer.get(it)
        }
    }

    private fun copyModelFromAssets() {
        try {
            val outputFile = File(context.filesDir, MODEL_NAME)
            if (outputFile.exists() && outputFile.length() > 0L) return

            context.assets.open(MODEL_NAME).use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    inputStream.copyTo(outputStream, 1024 * 1024)
                }
            }
        } catch (e: IOException) {
            executorListener?.onError("Failed to copy NNC from assets: $MODEL_NAME")
        }
    }

    data class YoloPResult(
        val drivableMask: BooleanArray,
        val laneMask: BooleanArray
    )

    interface ExecutorListener {
        fun onError(error: String)
        fun onResults(result: YoloPResult, inferenceTime: Long)
    }

    companion object {
        private const val MODEL_NAME = ModelConstants.MODEL_NAME

        private val INPUT_DATA_LAYER = ModelConstants.INPUT_DATA_LAYER
        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
        private const val INPUT_SIZE_C = ModelConstants.INPUT_SIZE_C
        private const val INPUT_ELEMENT_COUNT = ModelConstants.INPUT_ELEMENT_COUNT

        private const val DRIVABLE_OUTPUT_INDEX = 3
        private const val LANE_OUTPUT_INDEX = 4
    }
}
