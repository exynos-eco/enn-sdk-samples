// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.poseestimation.executor

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.samsung.poseestimation.data.LayerType
import com.samsung.poseestimation.data.ModelConstants
import com.samsung.poseestimation.enn_type.BufferSetInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
        setupENN()
    }

    private fun setupENN() {
        ennInitialize()
        val fileAbsoluteDirectory = File(context.filesDir, MODEL_NAME).absolutePath
        modelId = ennOpenModel(fileAbsoluteDirectory)
        val bufferSetInfo = ennAllocateAllBuffers(modelId)
        bufferSet = bufferSetInfo.buffer_set
        nInBuffer = bufferSetInfo.n_in_buf
        nOutBuffer = bufferSetInfo.n_out_buf
    }

    fun process(image: Bitmap) {
        if (bufferSet == 0L) return
        val input = preProcess(image)
        ennMemcpyHostToDevice(bufferSet, 0, input)
        var inferenceTime = SystemClock.uptimeMillis()
        ennExecute(modelId)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime
        val output = ennMemcpyDeviceToHost(bufferSet, 1)
        val result = postProcess(output)
        executorListener?.onResults(result, inferenceTime)
    }

    fun closeENN() {
        ennReleaseBuffers(bufferSet, nInBuffer + nOutBuffer)
        ennCloseModel(modelId)
        ennDeinitialize()
    }

    private fun preProcess(image: Bitmap): ByteArray {
        val data = convertBitmapToFloatArray(image, INPUT_DATA_LAYER)
        val byteBuffer = ByteBuffer.allocate(data.size * Float.SIZE_BYTES)
        byteBuffer.order(ByteOrder.nativeOrder())
        for (f in data) byteBuffer.putFloat(f)
        return byteBuffer.array()
    }

    private fun convertBitmapToFloatArray(
        image: Bitmap,
        layerType: Enum<LayerType> = LayerType.CHW
    ): FloatArray {
        val resized = Bitmap.createScaledBitmap(image, INPUT_SIZE_W, INPUT_SIZE_H, true)
        val totalPixels = INPUT_SIZE_H * INPUT_SIZE_W
        val pixels = IntArray(totalPixels)
        resized.getPixels(pixels, 0, INPUT_SIZE_W, 0, 0, INPUT_SIZE_W, INPUT_SIZE_H)

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
            val c = pixels[i]
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f

            floatArray[i * stride + offset[0]] = r
            floatArray[i * stride + offset[1]] = g
            floatArray[i * stride + offset[2]] = b
        }
        return floatArray
    }

    private fun postProcess(output: ByteArray): List<Triple<Int?, Int?, Float>> {
        val floatBuffer = ByteBuffer.wrap(output).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val floats = FloatArray(floatBuffer.capacity())
        floatBuffer.get(floats)

        val C = OUTPUT_SIZE_C
        val H = OUTPUT_SIZE_H
        val W = OUTPUT_SIZE_W

        val sx = INPUT_SIZE_W.toFloat() / W
        val sy = INPUT_SIZE_H.toFloat() / H

        val keypoints = MutableList(C) { Triple<Int?, Int?, Float>(null, null, 0f) }

        for (c in 0 until C) {
            var maxVal = Float.NEGATIVE_INFINITY
            var maxIdx = 0
            val base = c * H * W
            for (i in 0 until H * W) {
                val v = floats[base + i]
                if (v > maxVal) {
                    maxVal = v
                    maxIdx = i
                }
            }
            var y = maxIdx / W
            var x = maxIdx % W

            var dx = 0f
            var dy = 0f

            if (x > 0 && x < W - 1) {
                val l = floats[base + y * W + (x - 1)]
                val r = floats[base + y * W + (x + 1)]
                dx = if (r > l) 0.25f else -0.25f
                if (abs(r - l) < 1e-6) dx = 0f
            }
            if (y > 0 && y < H - 1) {
                val u = floats[base + (y - 1) * W + x]
                val d = floats[base + (y + 1) * W + x]
                dy = if (d > u) 0.25f else -0.25f
                if (abs(d - u) < 1e-6) dy = 0f
            }

            val conf = maxVal
            if (conf >= threshold) {
                val fx = ((x.toFloat() + dx) * sx).coerceIn(0f, INPUT_SIZE_W - 1f)
                val fy = ((y.toFloat() + dy) * sy).coerceIn(0f, INPUT_SIZE_H - 1f)
                keypoints[c] = Triple(fx.toInt(), fy.toInt(), conf)
            } else {
                keypoints[c] = Triple(null, null, conf)
            }
        }

        return keypoints
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
        fun onResults(detectionResult: List<Triple<Int?, Int?, Float>>, inferenceTime: Long)
    }

    companion object {
        private const val MODEL_NAME = ModelConstants.MODEL_NAME
        private val INPUT_DATA_LAYER = ModelConstants.INPUT_DATA_LAYER
        private val INPUT_DATA_TYPE = ModelConstants.INPUT_DATA_TYPE
        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
        private const val INPUT_SIZE_C = ModelConstants.INPUT_SIZE_C
        private const val INPUT_CONVERSION_SCALE = ModelConstants.INPUT_CONVERSION_SCALE
        private const val INPUT_CONVERSION_OFFSET = ModelConstants.INPUT_CONVERSION_OFFSET
        private val OUTPUT_DATA_TYPE = ModelConstants.OUTPUT_DATA_TYPE
        private const val OUTPUT_SIZE_C = ModelConstants.OUTPUT_SIZE_C
        private const val OUTPUT_SIZE_H = ModelConstants.OUTPUT_SIZE_H
        private const val OUTPUT_SIZE_W = ModelConstants.OUTPUT_SIZE_W
    }
}
