// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.imageediting.executor

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.samsung.imageediting.data.ModelConstants
import com.samsung.imageediting.enn_type.BufferSetInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

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

    private var modelId: Long = 0L
    private var bufferSet: Long = 0L
    private var nInBuffer: Int = 0
    private var nOutBuffer: Int = 0
    private var ennReady: Boolean = false

    init {
        System.loadLibrary("enn_jni")
        copyNNCFromAssetsToInternalStorage(MODEL_NAME)
        setupENN()
    }

    private fun setupENN() {
        try {
            ennInitialize()

            val fileAbsolutePath = File(context.filesDir, MODEL_NAME).absolutePath
            modelId = ennOpenModel(fileAbsolutePath)

            if (modelId == 0L) {
                showModelDownloadPopup()
                return
            }

            val bufferSetInfo = ennAllocateAllBuffers(modelId)
            bufferSet = bufferSetInfo.buffer_set
            nInBuffer = bufferSetInfo.n_in_buf
            nOutBuffer = bufferSetInfo.n_out_buf

            if (bufferSet == 0L || nInBuffer < 2 || nOutBuffer < 1) {
                showModelDownloadPopup()
                return
            }
            ennReady = true
        } catch (e: Exception) {
            showModelDownloadPopup()
        }
    }

    fun process(image: Bitmap) {
        process(image, null)
    }

    fun process(image: Bitmap, maskBitmap: Bitmap?) {
        val inputMask = makeMaskInput(maskBitmap)
        val originalInputImage = makeImageInput(image, inputMask, applyMaskToImage = false)
        val inputImage = makeImageInput(image, inputMask, applyMaskToImage = true)

        if (!ennReady || bufferSet == 0L) {
            showModelDownloadPopup()
            return
        }

        try {
            ennMemcpyHostToDevice(bufferSet, 0, inputImage)
            ennMemcpyHostToDevice(bufferSet, 1, inputMask)

            var inferenceTime = SystemClock.uptimeMillis()
            ennExecute(modelId)
            inferenceTime = SystemClock.uptimeMillis() - inferenceTime

            val outputLayer = nInBuffer
            val output = ennMemcpyDeviceToHost(bufferSet, outputLayer)
            val outputBitmap = postProcess(output, originalInputImage, inputMask)

            Handler(Looper.getMainLooper()).post {
                executorListener?.onResults(outputBitmap, inferenceTime)
            }
        } catch (e: Exception) {
            executorListener?.onError(e.message ?: "ModelExecutor process failed")
            showRuntimeErrorPopup(e.message ?: "ModelExecutor process failed")
        }
    }

    fun closeENN() {
        try {
            if (bufferSet != 0L && nInBuffer + nOutBuffer > 0) {
                ennReleaseBuffers(bufferSet, nInBuffer + nOutBuffer)
            }
        } catch (e: Exception) {
        } finally {
            bufferSet = 0L
            nInBuffer = 0
            nOutBuffer = 0
            ennReady = false
        }

        try {
            if (modelId != 0L) {
                ennCloseModel(modelId)
            }
        } catch (e: Exception) {
        } finally {
            modelId = 0L
        }

        try {
            ennDeinitialize()
        } catch (e: Exception) {
        }
    }

    private fun makeImageInput(
        image: Bitmap,
        inputMask: ByteArray,
        applyMaskToImage: Boolean
    ): ByteArray {
        val resized = Bitmap.createScaledBitmap(image, INPUT1_W, INPUT1_H, true)
        val pixels = IntArray(INPUT1_W * INPUT1_H)
        resized.getPixels(pixels, 0, INPUT1_W, 0, 0, INPUT1_W, INPUT1_H)

        val planeSize = INPUT1_W * INPUT1_H
        val input = FloatArray(INPUT1_ELEMENT_COUNT)

        for (i in 0 until planeSize) {
            val pixel = pixels[i]

            input[i] = ((pixel shr 16) and 0xFF) / 255.0f
            input[i + planeSize] = ((pixel shr 8) and 0xFF) / 255.0f
            input[i + planeSize * 2] = (pixel and 0xFF) / 255.0f
        }
        return floatArrayToByteArray(input, INPUT1_BYTE_SIZE)
    }

    private fun makeMaskInput(maskBitmap: Bitmap?): ByteArray {
        val planeSize = INPUT2_W * INPUT2_H
        val input = FloatArray(INPUT2_ELEMENT_COUNT)

        if (maskBitmap != null) {
            val resizedMask = Bitmap.createScaledBitmap(maskBitmap, INPUT2_W, INPUT2_H, true)
            val maskPixels = IntArray(planeSize)
            resizedMask.getPixels(maskPixels, 0, INPUT2_W, 0, 0, INPUT2_W, INPUT2_H)

            for (i in 0 until planeSize) {
                input[i] = rgbToGray01(maskPixels[i])
            }
        } else {
            for (y in 0 until INPUT2_H) {
                for (x in 0 until INPUT2_W) {
                    val i = y * INPUT2_W + x
                    input[i] = if (x in 166..344 && y in 166..344) 0.0f else 1.0f
                }
            }
        }

        return floatArrayToByteArray(input, INPUT2_BYTE_SIZE)
    }

    private fun postProcess(
        modelOutput: ByteArray,
        originalInputImage: ByteArray,
        inputMask: ByteArray
    ): Bitmap {
        val planeSize = OUTPUT_W * OUTPUT_H

        val output = byteArrayToFloatArray(modelOutput, OUTPUT_ELEMENT_COUNT)
        val original = byteArrayToFloatArray(originalInputImage, INPUT1_ELEMENT_COUNT)
        val mask = byteArrayToFloatArray(inputMask, INPUT2_ELEMENT_COUNT)

        val pixels = IntArray(planeSize)
        for (i in 0 until planeSize) {
            val m = mask[i].coerceIn(0.0f, 1.0f)
            val invM = 1.0f - m

            val r = original[i] * invM + output[i] * m
            val g = original[i + planeSize] * invM + output[i + planeSize] * m
            val b = original[i + planeSize * 2] * invM + output[i + planeSize * 2] * m

            pixels[i] = Color.rgb(
                floatToUint8(r),
                floatToUint8(g),
                floatToUint8(b)
            )
        }
        return Bitmap.createBitmap(pixels, OUTPUT_W, OUTPUT_H, Bitmap.Config.ARGB_8888)
    }

    private fun byteArrayToFloatArray(data: ByteArray, expectedCount: Int): FloatArray {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder())
        val out = FloatArray(expectedCount)

        var i = 0
        while (i < expectedCount && buffer.remaining() >= 4) {
            out[i] = buffer.float
            i++
        }
        return out
    }

    private fun floatArrayToByteArray(values: FloatArray, byteSize: Int): ByteArray {
        val buffer = ByteBuffer.allocate(byteSize).order(ByteOrder.nativeOrder())
        for (value in values) {
            buffer.putFloat(value)
        }
        return buffer.array()
    }

    private fun rgbToGray01(pixel: Int): Float {
        val r = ((pixel shr 16) and 0xFF) / 255.0f
        val g = ((pixel shr 8) and 0xFF) / 255.0f
        val b = (pixel and 0xFF) / 255.0f

        return (0.299f * r + 0.587f * g + 0.114f * b).coerceIn(0.0f, 1.0f)
    }

    private fun floatToUint8(value: Float): Int {
        return (value.coerceIn(0.0f, 1.0f) * 255.0f).roundToInt().coerceIn(0, 255)
    }

    private fun copyNNCFromAssetsToInternalStorage(filename: String) {
        try {
            context.assets.open(filename).use { inputStream ->
                FileOutputStream(File(context.filesDir, filename), false).use { outputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                }
            }
        } catch (e: IOException) {
            showModelDownloadPopup()
        }
    }

    private fun showModelDownloadPopup() {
        Handler(Looper.getMainLooper()).post {
            if (context is android.app.Activity && !context.isFinishing) {
                AlertDialog.Builder(context)
                    .setTitle("NNC File Error")
                    .setMessage(
                        "The NNC file currently in use is not compatible with your device.\n" +
                                "Please check your device's chipset and place LaMa_Dilated.nnc in the assets folder."
                    )
                    .setCancelable(false)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun showRuntimeErrorPopup(message: String) {
        Handler(Looper.getMainLooper()).post {
            if (context is android.app.Activity && !context.isFinishing) {
                AlertDialog.Builder(context)
                    .setTitle("NNC / ENN Error")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    interface ExecutorListener {
        fun onError(error: String)
        fun onResults(resultBitmap: Bitmap, inferenceTime: Long)
    }

    companion object {
        private const val TAG = "ModelExecutor"
        private const val MODEL_NAME = ModelConstants.MODEL_NAME

        private const val INPUT1_W = ModelConstants.INPUT1_W
        private const val INPUT1_H = ModelConstants.INPUT1_H
        private const val INPUT1_BYTE_SIZE = ModelConstants.INPUT1_BYTE_SIZE
        private const val INPUT1_ELEMENT_COUNT = ModelConstants.INPUT1_ELEMENT_COUNT

        private const val INPUT2_W = ModelConstants.INPUT2_W
        private const val INPUT2_H = ModelConstants.INPUT2_H
        private const val INPUT2_BYTE_SIZE = ModelConstants.INPUT2_BYTE_SIZE
        private const val INPUT2_ELEMENT_COUNT = ModelConstants.INPUT2_ELEMENT_COUNT

        private const val OUTPUT_W = ModelConstants.OUTPUT_W
        private const val OUTPUT_H = ModelConstants.OUTPUT_H
        private const val OUTPUT_ELEMENT_COUNT = ModelConstants.OUTPUT_ELEMENT_COUNT
    }
}
