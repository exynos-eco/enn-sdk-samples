// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.objectdetection.executor

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
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

import kotlin.math.*

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

    fun process(image: Bitmap) {
        val input = preProcess(image)
        // Show a popup when an NNC file for a different chipset is used
        if (bufferSet == 0L) {
            showModelDownloadPopup()
            return
        }
        ennMemcpyHostToDevice(bufferSet, 0, input)

        var inferenceTime = SystemClock.uptimeMillis()
        // Model execute
        ennExecute(modelId)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime

        val totalOutput = ennMemcpyDeviceToHost(bufferSet, 1)  // logits (single tensor)

        val floatCount = totalOutput.size / 4
        val elem = 5 + labelList.size
        if (floatCount % elem != 0) {
            Log.e("sos", "Output float count=$floatCount not divisible by elem(5+K)=$elem. " +
                    "Check classes/export format.")
        }

        executorListener?.onResults(
            postProcess(totalOutput), inferenceTime
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

    private fun postProcess(totalOutput: ByteArray): List<DetectionResult> {
        val floatBuffer = ByteBuffer.wrap(totalOutput)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
        val floatOutputs = FloatArray(floatBuffer.remaining())
        floatBuffer.get(floatOutputs)

        val results = mutableListOf<DetectionResult>()

        val numClasses = labelList.size
        val elementSize = 5 + numClasses

        val strides = intArrayOf(8, 16, 32)
        val inputW = INPUT_SIZE_W
        val inputH = INPUT_SIZE_H

        var offset = 0
        for (stride in strides) {
            val gridW = inputW / stride
            val gridH = inputH / stride

            for (gy in 0 until gridH) {
                for (gx in 0 until gridW) {
                    val idx = offset + (gy * gridW + gx) * elementSize

                    val tx = floatOutputs[idx + 0]
                    val ty = floatOutputs[idx + 1]
                    val tw = floatOutputs[idx + 2]
                    val th = floatOutputs[idx + 3]
                    val obj = floatOutputs[idx + 4]

                    var maxClassScore = -1f
                    var classId = -1
                    for (c in 0 until numClasses) {
                        val score = floatOutputs[idx + 5 + c]
                        if (score > maxClassScore) {
                            maxClassScore = score
                            classId = c
                        }
                    }

                    val conf = obj * maxClassScore
                    if (conf >= threshold) {
                        val cx = (tx + gx) * stride / inputW.toFloat()
                        val cy = (ty + gy) * stride / inputH.toFloat()
                        val w = (exp(tw) * stride) / inputW.toFloat()
                        val h = (exp(th) * stride) / inputH.toFloat()

                        val x1 = cx - w / 2f
                        val y1 = cy - h / 2f
                        val x2 = cx + w / 2f
                        val y2 = cy + h / 2f

                        val rect = RectF(
                            x1.coerceIn(0f, 1f),
                            y1.coerceIn(0f, 1f),
                            x2.coerceIn(0f, 1f),
                            y2.coerceIn(0f, 1f)
                        )

                        results.add(
                            DetectionResult(
                                Pair(labelList[classId], conf),
                                rect
                            )
                        )
                    }
                }
            }
            offset += gridW * gridH * elementSize
        }

        return nms(results, iouThreshold = 0.6f)
    }



    private fun nms(
        detections: List<DetectionResult>,
        iouThreshold: Float = 0.5f
    ): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()
        val sorted = detections.sortedByDescending { it.score.second }
        val picked = BooleanArray(sorted.size) { false }

        for (i in sorted.indices) {
            if (picked[i]) continue
            val current = sorted[i]
            results.add(current)

            for (j in i + 1 until sorted.size) {
                if (!picked[j]) {
                    val iouVal = iou(current.requireBoundingBox(), sorted[j].requireBoundingBox())
                    if (iouVal > iouThreshold &&
                        current.score.first == sorted[j].score.first
                    ) {
                        picked[j] = true
                    }
                }
            }
        }
        return results
    }


    private fun iou(a: RectF, b: RectF): Float {
        val x1 = maxOf(a.left, b.left)
        val y1 = maxOf(a.top, b.top)
        val x2 = minOf(a.right, b.right)
        val y2 = minOf(a.bottom, b.bottom)

        val intersection = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)

        return if (areaA + areaB - intersection > 0f) {
            intersection / (areaA + areaB - intersection)
        } else {
            0f
        }
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
            val r = ((color shr 16) and 0xFF) / 1.0F // 255.0f
            val g = ((color shr 8) and 0xFF) / 1.0F
            val b = ((color shr 0) and 0xFF) / 1.0F

            floatArray[i * stride + offset[0]] = r
            floatArray[i * stride + offset[1]] = g
            floatArray[i * stride + offset[2]] = b
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
            showModelNotFoundPopup()
        }
    }

    private fun showModelNotFoundPopup() {
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(context)
                .setTitle("Model File Not Found")
                .setMessage("Please download the 'DETR_ResNet_dc5.nnc' file from AI Studio Farm and place it in the assets folder. Refer to the README file for the correct file path.")
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    if (context is android.app.Activity) {
                        context.finish()
                    } else {
                        Log.e("ModelExecutor", "Context is not an Activity, cannot finish()")
                    }
                }
                .show()
        }
    }

    private fun showModelDownloadPopup() {
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(context)
                .setTitle("NNC File Error")
                .setMessage("The NNC file currently in use is not compatible with your device.\n" +
                        "Please check your device's chipset and download the appropriate NNC file from AI Studio Farm.\n" +
                        "Place the file in the assets folder. Refer to the README file for the exact file path.")
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    if (context is android.app.Activity) {
                        context.finish()
                    } else {
                        Log.e("ModelExecutor", "Context is not an Activity, cannot finish()")
                    }
                }
                .show()
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
