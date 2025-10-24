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
    private var lastR = 1.0f
    private var lastDw = 0f
    private var lastDh = 0f
    private var origW = 0
    private var origH = 0

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
        ennReleaseBuffers(bufferSet, nInBuffer + nOutBuffer)
        ennCloseModel(modelId)
        ennDeinitialize()
    }

    private fun preProcess(image: Bitmap): ByteArray {
        origW = image.width
        origH = image.height

        val (lb, r, dw, dh) = if (ModelConstants.KEEP_RATIO) {
            letterbox(image, INPUT_SIZE_W, INPUT_SIZE_H, 114)
        } else {
            val resized = Bitmap.createScaledBitmap(image, INPUT_SIZE_W, INPUT_SIZE_H, true)
            Quad(resized, origH.toFloat() / INPUT_SIZE_H, 0f, 0f) // dummy r,dw,dh
        }
        lastR = r
        lastDw = dw
        lastDh = dh

        val data = convertBitmapToFloatArrayNormalized(lb, INPUT_DATA_LAYER) // (x-mean)/std
        val byteBuffer = ByteBuffer.allocate(data.size * Float.SIZE_BYTES)
        byteBuffer.order(ByteOrder.nativeOrder())
        byteBuffer.asFloatBuffer().put(data)
        return byteBuffer.array()
    }

    private fun postProcess(totalOutput: ByteArray): List<DetectionResult> {
        val fb = ByteBuffer.wrap(totalOutput).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val out = FloatArray(fb.remaining()); fb.get(out)

        val numClasses = OUTPUT_NUM_CLASSES
        val regMax = ModelConstants.REG_MAX
        val elem = numClasses + 4 * (regMax + 1)
        val strides = ModelConstants.STRIDES
        val inputW = INPUT_SIZE_W
        val inputH = INPUT_SIZE_H

        val levelH = strides.map { inputH / it }
        val levelW = strides.map { inputW / it }
        val levelN = levelH.zip(levelW) { h, w -> h * w }
        val starts = IntArray(strides.size)
        for (i in 1 until starts.size) starts[i] = starts[i - 1] + levelN[i - 1]

        val proj = FloatArray(regMax + 1) { it.toFloat() }

        val detB = ArrayList<RectF>()
        val detS = ArrayList<Float>()
        val detL = ArrayList<Int>()

        for (lv in strides.indices) {
            val s = strides[lv]
            val Ws = levelW[lv]
            val n = levelN[lv]
            val st = starts[lv]

            for (i in 0 until n) {
                val base = (st + i) * elem
                var maxScore = -1f; var maxId = -1
                for (c in 0 until numClasses) {
                    val sc = sigmoid(out[base + c])
                    if (sc > maxScore) { maxScore = sc; maxId = c }
                }
                if (maxScore < threshold)
                    continue

                val dist = FloatArray(4)
                var pOff = base + numClasses
                for (k in 0 until 4) {
                    val tmp = FloatArray(regMax + 1)
                    var maxv = Float.NEGATIVE_INFINITY
                    for (b in 0..regMax) { val v = out[pOff + b]; tmp[b] = v; if (v > maxv) maxv = v }
                    var sum = 0f
                    for (b in 0..regMax) { tmp[b] = exp(tmp[b] - maxv); sum += tmp[b] }
                    var ex = 0f
                    for (b in 0..regMax) { val pb = tmp[b] / sum; ex += pb * proj[b] }
                    dist[k] = ex * s
                    pOff += (regMax + 1)
                }

                val gy = i / Ws
                val gx = i - gy * Ws
                val cx = (gx + 0.5f) * s
                val cy = (gy + 0.5f) * s

                val x1 = cx - dist[0]
                val y1 = cy - dist[1]
                val x2 = cx + dist[2]
                val y2 = cy + dist[3]

                var xx1 = (x1 - lastDw).coerceIn(0f, inputW - 1f)
                var yy1 = (y1 - lastDh).coerceIn(0f, inputH - 1f)
                var xx2 = (x2 - lastDw).coerceIn(0f, inputW - 1f)
                var yy2 = (y2 - lastDh).coerceIn(0f, inputH - 1f)

                xx1 /= lastR; yy1 /= lastR; xx2 /= lastR; yy2 /= lastR
                val rw = origW.toFloat(); val rh = origH.toFloat()

                val rect = RectF(
                    (xx1 / rw).coerceIn(0f, 1f),
                    (yy1 / rh).coerceIn(0f, 1f),
                    (xx2 / rw).coerceIn(0f, 1f),
                    (yy2 / rh).coerceIn(0f, 1f)
                )
                detB.add(rect)
                detS.add(maxScore)
                detL.add(maxId)
            }
        }

        val merged = mutableListOf<DetectionResult>()
        val byCls = detL.withIndex().groupBy { it.value } // class -> indices
        for ((cls, idxs) in byCls) {
            val idxList = idxs.map { it.index }
            val items = idxList.map { DetectionResult(Pair(labelList[cls], detS[it]), detB[it]) }
            merged += nms(items, iouThreshold = 0.5f)
        }
        return merged
    }

    private fun sigmoid(x: Float) = 1f / (1f + exp(-x))

    data class Quad(val bmp: Bitmap, val r: Float, val dw: Float, val dh: Float)
    private fun letterbox(src: Bitmap, dstW: Int, dstH: Int, padColor: Int): Quad {
        val w0 = src.width.toFloat()
        val h0 = src.height.toFloat()
        val r = min(dstW / w0, dstH / h0)
        val newW = (w0 * r).roundToInt()
        val newH = (h0 * r).roundToInt()

        val resized = Bitmap.createScaledBitmap(src, newW, newH, true)
        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(android.graphics.Color.rgb(padColor, padColor, padColor))
        val dw = ((dstW - newW) / 2f)
        val dh = ((dstH - newH) / 2f)
        canvas.drawBitmap(resized, dw, dh, null)
        return Quad(out, r.toFloat(), dw, dh)
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

    private fun convertBitmapToFloatArrayNormalized(
        image: Bitmap, layerType: Enum<LayerType> = LayerType.CHW
    ): FloatArray {
        val totalPixels = INPUT_SIZE_H * INPUT_SIZE_W
        val pixels = IntArray(totalPixels)
        image.getPixels(pixels, 0, INPUT_SIZE_W, 0, 0, INPUT_SIZE_W, INPUT_SIZE_H)

        val out = FloatArray(totalPixels * INPUT_SIZE_C)
        val (m0,m1,m2) = ModelConstants.NORM_MEAN
        val (s0,s1,s2) = ModelConstants.NORM_STD

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
            val r = ((c shr 16) and 0xFF) * ModelConstants.INPUT_CONVERSION_SCALE
            val g = ((c shr 8) and 0xFF) * ModelConstants.INPUT_CONVERSION_SCALE
            val b = ((c) and 0xFF) * ModelConstants.INPUT_CONVERSION_SCALE

            out[i * stride + offset[0]] = ((r - m0) / s0)
            out[i * stride + offset[1]] = ((g - m1) / s1)
            out[i * stride + offset[2]] = ((b - m2) / s2)
        }
        return out
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
        private const val LABEL_FILE = ModelConstants.LABEL_FILE
    }
}