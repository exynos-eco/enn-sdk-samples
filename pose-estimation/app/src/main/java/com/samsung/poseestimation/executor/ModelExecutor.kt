// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.poseestimation.executor

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.samsung.poseestimation.data.BodyPart
import com.samsung.poseestimation.data.DataType
import com.samsung.poseestimation.data.Human
import com.samsung.poseestimation.data.LayerType
import com.samsung.poseestimation.data.ModelConstants
import com.samsung.poseestimation.enn_type.BufferSetInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max


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
        val heatmapModelOutput = ennMemcpyDeviceToHost(bufferSet, 3)
        val offsetModelOutput = ennMemcpyDeviceToHost(bufferSet, 4)

        executorListener?.onResults(
            postProcess(heatmapModelOutput, offsetModelOutput), inferenceTime
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

    private fun postProcess(heatmapModelOutput: ByteArray, offsetModelOutput: ByteArray): Human {
        val heatmap = convertModelOutputToArray(
            heatmapModelOutput, arrayOf(
                HEATMAP_SIZE_W, HEATMAP_SIZE_H, HEATMAP_SIZE_C
            ), HEATMAP_DATA_TYPE
        )
        val offset = convertModelOutputToArray(
            offsetModelOutput, arrayOf(
                OFFSET_SIZE_W, OFFSET_SIZE_H, OFFSET_SIZE_C
            ), OFFSET_DATA_TYPE
        )

        return Human.fromOutput(postProcessModelOutputs(heatmap, offset))
    }

    private fun postProcessModelOutputs(
        heatmaps: Array<Array<FloatArray>>, offsets: Array<Array<FloatArray>>
    ): Array<FloatArray> {
        val numKeypoints = heatmaps[0][0].size
        val keypointPositions = Array(numKeypoints) { findMaxInHeatmap(heatmaps, it) }
        val (yCoords, xCoords, confidenceScores) = computeCoordinatesAndConfidence(
            keypointPositions, offsets, heatmaps
        )

        return enumValues<BodyPart>().mapIndexed { idx, _ ->
            floatArrayOf(xCoords[idx].toFloat(), yCoords[idx].toFloat(), confidenceScores[idx])
        }.toTypedArray()
    }

    private fun findMaxInHeatmap(
        heatmaps: Array<Array<FloatArray>>,
        keypoint: Int
    ): Pair<Int, Int> =
        heatmaps.withIndex().flatMap { (row, heatmapRow) ->
            heatmapRow.withIndex().map { (col, heatmapCol) ->
                Triple(heatmapCol[keypoint], row, col)
            }
        }.maxByOrNull { it.first }?.let { Pair(it.second, it.third) }
            ?: Pair(0, 0)

    private fun computeCoordinatesAndConfidence(
        keypointPositions: Array<Pair<Int, Int>>,
        offsets: Array<Array<FloatArray>>,
        heatmaps: Array<Array<FloatArray>>
    ): Triple<IntArray, IntArray, FloatArray> {
        val height = heatmaps.size
        val width = heatmaps[0].size
        val numKeypoints = heatmaps[0][0].size
        val yCoords = IntArray(numKeypoints)
        val xCoords = IntArray(numKeypoints)
        val confidenceScores = FloatArray(numKeypoints)

        val cropDimensions = computeCropDimensions(INPUT_SIZE_W, INPUT_SIZE_H)

        keypointPositions.forEachIndexed { idx,  (y, x) ->
            yCoords[idx] = computeCoordinate(
                axisPosition = y,
                axisLength = height,
                imageSize = INPUT_SIZE_H,
                offset = offsets[y][x][idx],
                cropDimension = cropDimensions.first
            )
            xCoords[idx] = computeCoordinate(
                axisPosition = x,
                axisLength = width,
                imageSize = INPUT_SIZE_W,
                offset = offsets[y][x][idx + numKeypoints],
                cropDimension = cropDimensions.second
            )
            confidenceScores[idx] = sigmoid(heatmaps[y][x][idx])
        }

        return Triple(yCoords, xCoords, confidenceScores)
    }

    private fun computeCropDimensions(width: Int, height: Int): Pair<Float, Float> {
        val cropWidth = if (width > height) 0F else (height - width).toFloat()
        val cropHeight = if (width > height) (width - height).toFloat() else 0F
        return cropHeight to cropWidth
    }

    private fun computeCoordinate(
        axisPosition: Int,
        axisLength: Int,
        imageSize: Int,
        offset: Float,
        cropDimension: Float
    ): Int {
        val scale = max(INPUT_SIZE_W, INPUT_SIZE_H).toFloat()
        val coordinate = axisPosition / (axisLength - 1).toFloat() * imageSize + offset
        return ((coordinate * scale / imageSize) - cropDimension / 2).toInt()
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

    private fun convertModelOutputToArray(
        modelOutput: ByteArray, shapeArray: Array<Int>, dataType: DataType
    ): Array<Array<FloatArray>> {
        val output = convertOutputByteToFloatArray(modelOutput, dataType)

        return Array(shapeArray[0]) { i ->
            Array(shapeArray[1]) { j ->
                FloatArray(shapeArray[2]) { k ->
                    output[(i * shapeArray[1] * shapeArray[2]) + (j * shapeArray[2]) + k]
                }
            }
        }
    }

    private fun convertOutputByteToFloatArray(
        modelOutput: ByteArray,
        dataType: DataType
    ): FloatArray {
        return when (dataType) {
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
                throw IllegalArgumentException("Unsupported output data type: ${dataType}")
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

    private fun sigmoid(x: Float): Float {
        return (1.0f / (1.0f + exp(-x)))
    }

    interface ExecutorListener {
        fun onError(error: String)
        fun onResults(
            detectionResult: Human, inferenceTime: Long
        )
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

        private val HEATMAP_DATA_TYPE = ModelConstants.HEATMAP_DATA_TYPE

        private const val HEATMAP_SIZE_C = ModelConstants.HEATMAP_SIZE_C
        private const val HEATMAP_SIZE_H = ModelConstants.HEATMAP_SIZE_H
        private const val HEATMAP_SIZE_W = ModelConstants.HEATMAP_SIZE_W

        private val OFFSET_DATA_TYPE = ModelConstants.OFFSET_DATA_TYPE

        private const val OFFSET_SIZE_C = ModelConstants.OFFSET_SIZE_C
        private const val OFFSET_SIZE_H = ModelConstants.OFFSET_SIZE_H
        private const val OFFSET_SIZE_W = ModelConstants.OFFSET_SIZE_W
    }
}
