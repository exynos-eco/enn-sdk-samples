// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.videoenhancement.executor

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.samsung.videoenhancement.data.DataType
import com.samsung.videoenhancement.data.LayerType
import com.samsung.videoenhancement.data.ModelConstants
import com.samsung.videoenhancement.enn_type.BufferSetInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ModelExecutor(
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
    private external fun ennMemcpyHostToDevice(
        bufferSet: Long,
        layerNumber: Int,
        data: ByteArray
    )
    private external fun ennMemcpyDeviceToHost(
        bufferSet: Long,
        layerNumber: Int
    ): ByteArray

    data class MultiFrameResult(
        val frames: List<Bitmap>,
        val totalInferenceTimeMs: Long
    )

    private data class SingleInferenceResult(
        val bitmap: Bitmap,
        val inferenceTimeMs: Long
    )

    private var modelId: Long = 0L
    private var bufferSet: Long = 0L
    private var nInBuffer: Int = 0
    private var nOutBuffer: Int = 0
    private var isClosed = false

    init {
        System.loadLibrary("enn_jni")
        if (copyNncFromAssetsToInternalStorage(MODEL_NAME)) {
            setupEnn()
        }
    }

    private fun setupEnn() {
        try {
            ennInitialize()

            val modelPath = File(context.filesDir, MODEL_NAME).absolutePath
            modelId = ennOpenModel(modelPath)

            val bufferSetInfo = ennAllocateAllBuffers(modelId)
            bufferSet = bufferSetInfo.buffer_set
            nInBuffer = bufferSetInfo.n_in_buf
            nOutBuffer = bufferSetInfo.n_out_buf

            if (bufferSet == 0L || nInBuffer < INPUT_COUNT || nOutBuffer < 1) {
                Log.e(
                    TAG,
                    "Invalid ENN buffers: bufferSet=$bufferSet, " +
                            "inputs=$nInBuffer, outputs=$nOutBuffer"
                )
                showModelDownloadPopup()
            }
        } catch (e: Exception) {
            notifyError("ENN initialization failed: ${e.message}")
        }
    }

    /**
     * Original single-frame interpolation API.
     * This remains available for CameraFragment and existing callers.
     */
    @Synchronized
    fun process(frame0: Bitmap, frame1: Bitmap) {
        if (!isReady()) {
            showModelDownloadPopup()
            return
        }

        try {
            val result = executeSingleInterpolation(frame0, frame1)
            executorListener?.onResults(
                result.bitmap,
                result.inferenceTimeMs
            )
        } catch (e: Exception) {
            Log.e(TAG, "RIFE execution failed", e)
            notifyError("RIFE execution failed: ${e.message}")
        }
    }

    /**
     * Recursively generates intermediate frames.
     *
     * depth=1:
     *   A, M, B
     *
     * depth=2:
     *   A, M25, M50, M75, B
     *
     * depth=3:
     *   A, M12.5, M25, M37.5, M50, M62.5, M75, M87.5, B
     *
     * The number of RIFE executions is (2^depth - 1).
     */
    @Synchronized
    fun generateIntermediateFrames(
        frame0: Bitmap,
        frame1: Bitmap,
        depth: Int = 3
    ): MultiFrameResult {
        require(depth in 1..MAX_INTERPOLATION_DEPTH) {
            "Interpolation depth must be between 1 and $MAX_INTERPOLATION_DEPTH."
        }
        check(isReady()) {
            "ENN model is not initialized."
        }

        var totalInferenceTime = 0L

        fun interpolateRecursive(
            left: Bitmap,
            right: Bitmap,
            currentDepth: Int
        ): List<Bitmap> {
            if (currentDepth == 0) {
                return listOf(left, right)
            }

            val middleResult = executeSingleInterpolation(left, right)
            totalInferenceTime += middleResult.inferenceTimeMs

            val leftFrames = interpolateRecursive(
                left,
                middleResult.bitmap,
                currentDepth - 1
            )
            val rightFrames = interpolateRecursive(
                middleResult.bitmap,
                right,
                currentDepth - 1
            )

            // The middle frame appears at the end of leftFrames and the
            // beginning of rightFrames. Drop one copy.
            return leftFrames + rightFrames.drop(1)
        }

        val frames = interpolateRecursive(frame0, frame1, depth)
        return MultiFrameResult(
            frames = frames,
            totalInferenceTimeMs = totalInferenceTime
        )
    }

    /**
     * Runs exactly one NNC inference and returns the generated midpoint.
     *
     * The caller must serialize access to this method because one shared ENN
     * buffer set is used. Public entry points are synchronized.
     */
    private fun executeSingleInterpolation(
        frame0: Bitmap,
        frame1: Bitmap
    ): SingleInferenceResult {
        val input0 = preProcess(frame0)
        val input1 = preProcess(frame1)

        ennMemcpyHostToDevice(
            bufferSet,
            INPUT_0_LAYER_INDEX,
            input0
        )
        ennMemcpyHostToDevice(
            bufferSet,
            INPUT_1_LAYER_INDEX,
            input1
        )

        val startTime = SystemClock.uptimeMillis()
        ennExecute(modelId)
        val inferenceTime = SystemClock.uptimeMillis() - startTime

        val outputBytes = ennMemcpyDeviceToHost(
            bufferSet,
            OUTPUT_LAYER_INDEX
        )
        val expectedByteCount =
            OUTPUT_ELEMENT_COUNT * Float.SIZE_BYTES

        require(outputBytes.size == expectedByteCount) {
            "Unexpected output size: ${outputBytes.size} bytes " +
                    "(expected $expectedByteCount bytes)"
        }

        return SingleInferenceResult(
            bitmap = postProcess(outputBytes),
            inferenceTimeMs = inferenceTime
        )
    }

    private fun isReady(): Boolean {
        return !isClosed &&
                modelId != 0L &&
                bufferSet != 0L &&
                nInBuffer >= INPUT_COUNT &&
                nOutBuffer >= 1
    }

    private fun preProcess(source: Bitmap): ByteArray {
        require(INPUT_DATA_TYPE == DataType.FLOAT32) {
            "Only FLOAT32 input is supported."
        }

        val resized = if (
            source.width == INPUT_SIZE_W &&
            source.height == INPUT_SIZE_H
        ) {
            source
        } else {
            Bitmap.createScaledBitmap(
                source,
                INPUT_SIZE_W,
                INPUT_SIZE_H,
                true
            )
        }

        val floatData = convertBitmapToFloatArray(
            resized,
            INPUT_DATA_LAYER
        )
        val byteBuffer = ByteBuffer
            .allocate(floatData.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)

        byteBuffer.asFloatBuffer().put(floatData)
        return byteBuffer.array()
    }

    private fun postProcess(outputBytes: ByteArray): Bitmap {
        val floatBuffer = ByteBuffer
            .wrap(outputBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()

        val output = FloatArray(floatBuffer.remaining())
        floatBuffer.get(output)

        val planeSize = OUTPUT_SIZE_W * OUTPUT_SIZE_H
        val pixels = IntArray(planeSize)

        for (i in 0 until planeSize) {
            val r = floatToUint8(output[i])
            val g = floatToUint8(output[planeSize + i])
            val b = floatToUint8(output[2 * planeSize + i])

            pixels[i] =
                (0xFF shl 24) or
                        (r shl 16) or
                        (g shl 8) or
                        b
        }

        return Bitmap.createBitmap(
            pixels,
            OUTPUT_SIZE_W,
            OUTPUT_SIZE_H,
            Bitmap.Config.ARGB_8888
        )
    }

    private fun convertBitmapToFloatArray(
        image: Bitmap,
        layerType: LayerType
    ): FloatArray {
        val pixelCount = INPUT_SIZE_W * INPUT_SIZE_H
        val pixels = IntArray(pixelCount)

        image.getPixels(
            pixels,
            0,
            INPUT_SIZE_W,
            0,
            0,
            INPUT_SIZE_W,
            INPUT_SIZE_H
        )

        val output = FloatArray(pixelCount * INPUT_SIZE_C)

        for (i in 0 until pixelCount) {
            val color = pixels[i]
            val r =
                ((color shr 16) and 0xFF) * INPUT_SCALE + INPUT_OFFSET
            val g =
                ((color shr 8) and 0xFF) * INPUT_SCALE + INPUT_OFFSET
            val b =
                (color and 0xFF) * INPUT_SCALE + INPUT_OFFSET

            if (layerType == LayerType.CHW) {
                output[i] = r
                output[pixelCount + i] = g
                output[2 * pixelCount + i] = b
            } else {
                val base = i * 3
                output[base] = r
                output[base + 1] = g
                output[base + 2] = b
            }
        }

        return output
    }

    private fun floatToUint8(value: Float): Int {
        return (value.coerceIn(0.0F, 1.0F) * 255.0F)
            .toInt()
            .coerceIn(0, 255)
    }

    fun closeENN() {
        if (isClosed) return
        isClosed = true

        try {
            if (bufferSet != 0L) {
                ennReleaseBuffers(
                    bufferSet,
                    nInBuffer + nOutBuffer
                )
            }

            if (modelId != 0L) {
                ennCloseModel(modelId)
            }

            ennDeinitialize()
        } catch (e: Exception) {
            Log.e(TAG, "ENN close failed", e)
        } finally {
            bufferSet = 0L
            modelId = 0L
        }
    }

    private fun copyNncFromAssetsToInternalStorage(
        filename: String
    ): Boolean {
        return try {
            context.assets.open(filename).use { inputStream ->
                FileOutputStream(
                    File(context.filesDir, filename)
                ).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: IOException) {
            Log.e(TAG, "Model copy failed", e)
            showModelNotFoundPopup()
            false
        }
    }

    private fun notifyError(message: String) {
        Handler(Looper.getMainLooper()).post {
            executorListener?.onError(message)
        }
    }

    private fun showModelNotFoundPopup() {
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(context)
                .setTitle("Model File Not Found")
                .setMessage(
                    "Place '$MODEL_NAME' in " +
                            "app/src/main/assets/ and rebuild the application."
                )
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    (context as? android.app.Activity)?.finish()
                }
                .show()
        }
    }

    private fun showModelDownloadPopup() {
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(context)
                .setTitle("NNC File Error")
                .setMessage(
                    "The RIFE NNC file is not compatible with this device, " +
                            "or its buffer configuration does not match " +
                            "2 inputs and 1 output."
                )
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    (context as? android.app.Activity)?.finish()
                }
                .show()
        }
    }

    interface ExecutorListener {
        fun onError(error: String)

        fun onResults(
            interpolatedFrame: Bitmap,
            inferenceTime: Long
        )
    }

    companion object {
        private const val TAG = "RifeModelExecutor"

        private const val MAX_INTERPOLATION_DEPTH = 4

        private const val MODEL_NAME = ModelConstants.MODEL_NAME
        private val INPUT_DATA_TYPE = ModelConstants.INPUT_DATA_TYPE
        private val INPUT_DATA_LAYER = ModelConstants.INPUT_DATA_LAYER

        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H
        private const val INPUT_SIZE_C = ModelConstants.INPUT_SIZE_C
        private const val INPUT_COUNT = ModelConstants.INPUT_COUNT

        private const val OUTPUT_SIZE_W = ModelConstants.OUTPUT_SIZE_W
        private const val OUTPUT_SIZE_H = ModelConstants.OUTPUT_SIZE_H
        private const val OUTPUT_SIZE_C = ModelConstants.OUTPUT_SIZE_C
        private const val OUTPUT_ELEMENT_COUNT = OUTPUT_SIZE_W * OUTPUT_SIZE_H * OUTPUT_SIZE_C

        private const val INPUT_SCALE = ModelConstants.INPUT_CONVERSION_SCALE
        private const val INPUT_OFFSET = ModelConstants.INPUT_CONVERSION_OFFSET

        private const val INPUT_0_LAYER_INDEX = 0
        private const val INPUT_1_LAYER_INDEX = 1
        private const val OUTPUT_LAYER_INDEX = 2
    }
}
