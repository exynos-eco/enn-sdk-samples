// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.gazeestimation.executor

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.samsung.gazeestimation.data.LayerType
import com.samsung.gazeestimation.data.ModelConstants
import com.samsung.gazeestimation.enn_type.BufferSetInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

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
        ennInitialize()

        val fileAbsolutePath = File(context.filesDir, MODEL_NAME).absolutePath
        modelId = ennOpenModel(fileAbsolutePath)

        val bufferSetInfo = ennAllocateAllBuffers(modelId)
        bufferSet = bufferSetInfo.buffer_set
        nInBuffer = bufferSetInfo.n_in_buf
        nOutBuffer = bufferSetInfo.n_out_buf

        Log.i(TAG, "ENN buffers: nIn=$nInBuffer, nOut=$nOutBuffer, bufferSet=$bufferSet")
    }

    fun process(image: Bitmap) {
        val inputBytes = preProcess(image)

        if (bufferSet == 0L) {
            showModelDownloadPopup()

            return
        }
        ennMemcpyHostToDevice(bufferSet, 0, inputBytes)

        var inferenceTime = SystemClock.uptimeMillis()
        ennExecute(modelId)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime

        try {
            val pitchBytes = ennMemcpyDeviceToHost(bufferSet, nInBuffer + 0)
            val yawBytes = ennMemcpyDeviceToHost(bufferSet, nInBuffer + 1)
            val gaze = postProcess(pitchBytes, yawBytes)

            executorListener?.onResults(gaze, inferenceTime)
        } catch (e: Exception) {
            executorListener?.onError("Postprocess failed: ${e.message}")
        }
    }

    fun closeENN() {
        ennReleaseBuffers(bufferSet, nInBuffer + nOutBuffer)
        ennCloseModel(modelId)
        ennDeinitialize()
    }

    private fun preProcess(image: Bitmap): ByteArray {
        val resizedBitmap = Bitmap.createScaledBitmap(image, INPUT_SIZE_W, INPUT_SIZE_H, true)
        val floatArray = convertBitmapToFloatArray(resizedBitmap, INPUT_DATA_LAYER)

        val byteBuffer = ByteBuffer.allocate(floatArray.size * Float.SIZE_BYTES)

        byteBuffer.order(ByteOrder.nativeOrder())
        byteBuffer.asFloatBuffer().put(floatArray)

        return byteBuffer.array()
    }

    data class GazeResult(
        val pitchArgMaxIndex: Int,
        val yawArgMaxIndex: Int,
        val pitchExpectedIndex: Float,
        val yawExpectedIndex: Float,
        val pitchDeg: Float,
        val yawDeg: Float
    )

    private fun postProcess(pitchOut: ByteArray, yawOut: ByteArray): GazeResult {
        val pitchLogits = bytesToFloatArray(pitchOut)
        val yawLogits = bytesToFloatArray(yawOut)

        if (pitchLogits.size != NUM_BINS || yawLogits.size != NUM_BINS) {
            throw IllegalStateException("Unexpected output size. pitch=${pitchLogits.size}, yaw=${yawLogits.size}, expected=$NUM_BINS")
        }

        val pitchProb = softmax(pitchLogits)
        val yawProb = softmax(yawLogits)

        val pitchArgMax = argmax(pitchProb)
        val yawArgMax = argmax(yawProb)

        val pitchExpected = expectedIndex(pitchProb)
        val yawExpected = expectedIndex(yawProb)

        val pitchDeg = pitchExpected * PITCH_SCALE + PITCH_OFFSET
        val yawDeg = yawExpected * YAW_SCALE + YAW_OFFSET

        return GazeResult(
            pitchArgMaxIndex = pitchArgMax,
            yawArgMaxIndex = yawArgMax,
            pitchExpectedIndex = pitchExpected,
            yawExpectedIndex = yawExpected,
            pitchDeg = pitchDeg,
            yawDeg = yawDeg
        )
    }

    private fun bytesToFloatArray(modelOutput: ByteArray): FloatArray {
        val bb = ByteBuffer.wrap(modelOutput).order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        val data = FloatArray(fb.remaining())
        fb.get(data)

        return data
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size)
        var sum = 0.0
        for (i in logits.indices) {
            val v = exp((logits[i] - max).toDouble())
            exps[i] = v.toFloat()
            sum += v
        }
        for (i in exps.indices) exps[i] = (exps[i] / sum.toFloat())

        return exps
    }

    private fun argmax(prob: FloatArray): Int {
        var bestIdx = 0
        var best = prob[0]
        for (i in 1 until prob.size) {
            if (prob[i] > best) {
                best = prob[i]
                bestIdx = i
            }
        }

        return bestIdx
    }

    private fun expectedIndex(prob: FloatArray): Float {
        var s = 0f
        for (i in prob.indices) s += i.toFloat() * prob[i]

        return s
    }

    private fun convertBitmapToFloatArray(
        image: Bitmap,
        layerType: Enum<LayerType> = LayerType.CHW
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

            else -> Log.e(TAG, "Unsupported LayerType: $layerType")
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
            showModelDownloadPopup()
        }
    }

    private fun showModelDownloadPopup() {
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(context)
                .setTitle("NNC File Error")
                .setMessage(
                    "The NNC file currently in use is not compatible with your device.\n" +
                            "Please check your device's chipset and download the appropriate NNC file.\n" +
                            "Place the file in the assets folder. Refer to the README for the exact path."
                )
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    if (context is android.app.Activity) context.finish()
                }
                .show()
        }
    }

    interface ExecutorListener {
        fun onError(error: String)
        fun onResults(result: GazeResult, inferenceTime: Long)
    }

    companion object {
        private const val TAG = "GazeModelExecutor"

        private const val MODEL_NAME = ModelConstants.MODEL_NAME

        private val INPUT_DATA_LAYER = ModelConstants.INPUT_DATA_LAYER
        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
        private const val INPUT_SIZE_C = ModelConstants.INPUT_SIZE_C

        private const val NUM_BINS = ModelConstants.NUM_BINS

        private const val PITCH_SCALE = ModelConstants.PITCH_INDEX_TO_DEG_SCALE
        private const val PITCH_OFFSET = ModelConstants.PITCH_INDEX_TO_DEG_OFFSET

        private const val YAW_SCALE = ModelConstants.YAW_INDEX_TO_DEG_SCALE
        private const val YAW_OFFSET = ModelConstants.YAW_INDEX_TO_DEG_OFFSET
    }
}