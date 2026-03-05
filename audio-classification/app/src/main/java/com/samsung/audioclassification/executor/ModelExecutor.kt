// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.audioclassification.executor

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.samsung.audioclassification.data.DataType
import com.samsung.audioclassification.data.LayerType
import com.samsung.audioclassification.data.ModelConstants
import com.samsung.audioclassification.enn_type.BufferSetInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max


@Suppress("IMPLICIT_CAST_TO_ANY")
@OptIn(ExperimentalUnsignedTypes::class)
class ModelExecutor(
    var threshold: Float = 0.1F,
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

    /**
     * YAMNet inference for sound classification
     * Input: PCM waveform (mono, 16kHz, [-1.0, 1.0])
     * Model input shape: float32[1, 1, 96, 64]
     * Model output shape: float32[1, 1, 1, 521]
     */
    fun process(waveform: FloatArray) {
        // Compute log mel spectrogram from waveform
        val input = preProcess(waveform)
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

    /**
     * Compute log mel spectrogram from PCM waveform
     * Result shape: [1, 1, 96, 64] (float32)
     *
     * Steps:
     * 1. Apply STFT with Hann window (400 samples / 25ms, hop 160 samples / 10ms)
     * 2. Compute magnitude spectrum
     * 3. Apply 64-bin mel filterbank (125-7500 Hz)
     * 4. Apply stabilized log: log(mel + 0.001)
     */
    private fun preProcess(waveform: FloatArray): ByteArray {
        val melSpectrogram = computeLogMelSpectrogram(waveform)
        // val totalPixels = INPUT_SIZE_H * INPUT_SIZE_W

        val byteArray = when (INPUT_DATA_TYPE) {
            DataType.FLOAT32 -> {
                val data = convertMelToFloatArray(melSpectrogram, INPUT_DATA_LAYER)
                val byteBuffer = ByteBuffer.allocate(data.size * Float.SIZE_BYTES)
                byteBuffer.order(ByteOrder.nativeOrder())
                byteBuffer.asFloatBuffer().put(data)
                byteBuffer.array()
            }

            DataType.UINT8 -> {
                convertMelToUByteArray(melSpectrogram, INPUT_DATA_LAYER).asByteArray()
            }

            else -> {
                throw IllegalArgumentException("Unsupported input data type: $INPUT_DATA_TYPE")
            }
        }

        return byteArray
    }

    private fun convertMelToFloatArray(
        melSpectrogram: Array<FloatArray>, layerType: Enum<LayerType> = LayerType.CHW
    ): FloatArray {
        val totalPixels = INPUT_SIZE_H * INPUT_SIZE_W
        val floatArray = FloatArray(totalPixels * INPUT_SIZE_C)

        when (layerType) {
            LayerType.CHW -> {
                // [C, H, W] = [1, 96, 64]
                for (t in 0 until INPUT_SIZE_H) {
                    for (m in 0 until INPUT_SIZE_W) {
                        floatArray[t * INPUT_SIZE_W + m] = melSpectrogram[t][m]
                    }
                }
            }

            LayerType.HWC -> {
                // [H, W, C] = [96, 64, 1]
                for (t in 0 until INPUT_SIZE_H) {
                    for (m in 0 until INPUT_SIZE_W) {
                        floatArray[(t * INPUT_SIZE_W + m) * INPUT_SIZE_C] = melSpectrogram[t][m]
                    }
                }
            }

            else -> {
                Log.e("ModelExecutor", "Unsupported LayerType: $layerType")
            }
        }

        return floatArray
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun convertMelToUByteArray(
        melSpectrogram: Array<FloatArray>, layerType: Enum<LayerType> = LayerType.CHW
    ): UByteArray {
        val totalPixels = INPUT_SIZE_H * INPUT_SIZE_W
        val uByteArray = UByteArray(totalPixels * INPUT_SIZE_C)

        when (layerType) {
            LayerType.CHW -> {
                for (t in 0 until INPUT_SIZE_H) {
                    for (m in 0 until INPUT_SIZE_W) {
                        uByteArray[t * INPUT_SIZE_W + m] =
                            ((melSpectrogram[t][m] - INPUT_CONVERSION_OFFSET) / INPUT_CONVERSION_SCALE)
                                .toInt().coerceIn(0, 255).toUByte()
                    }
                }
            }

            LayerType.HWC -> {
                for (t in 0 until INPUT_SIZE_H) {
                    for (m in 0 until INPUT_SIZE_W) {
                        uByteArray[(t * INPUT_SIZE_W + m) * INPUT_SIZE_C] =
                            ((melSpectrogram[t][m] - INPUT_CONVERSION_OFFSET) / INPUT_CONVERSION_SCALE)
                                .toInt().coerceIn(0, 255).toUByte()
                    }
                }
            }

            else -> {
                Log.e("ModelExecutor", "Unsupported LayerType: $layerType")
            }
        }

        return uByteArray
    }

    /**
     * Compute log mel spectrogram
     * Returns: Array[INPUT_SIZE_H][INPUT_SIZE_W] (= [96][64])
     */
    private fun computeLogMelSpectrogram(waveform: FloatArray): Array<FloatArray> {
        val hannWindow = FloatArray(STFT_WINDOW_SIZE) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / STFT_WINDOW_SIZE))).toFloat()
        }

        val fftSize = STFT_WINDOW_SIZE
        val melFilterbank = createMelFilterbank(fftSize, SAMPLE_RATE, INPUT_SIZE_W, 125.0f, 7500.0f)

        val melSpectrogram = Array(INPUT_SIZE_H) { FloatArray(INPUT_SIZE_W) }

        for (t in 0 until INPUT_SIZE_H) {
            val start = t * STFT_HOP_SIZE

            // Extract windowed frame
            val frame = FloatArray(fftSize)
            for (i in 0 until fftSize) {
                frame[i] = if (start + i < waveform.size) {
                    waveform[start + i] * hannWindow[i]
                } else {
                    0f
                }
            }

            // Compute magnitude spectrum using DFT
            val magnitudeSpectrum = computeMagnitudeSpectrum(frame, fftSize)

            // Apply mel filterbank and log
            for (m in 0 until INPUT_SIZE_W) {
                var melEnergy = 0f
                for (k in magnitudeSpectrum.indices) {
                    melEnergy += melFilterbank[m][k] * magnitudeSpectrum[k]
                }
                melSpectrogram[t][m] = ln(melEnergy + 0.001f)
            }
        }

        return melSpectrogram
    }

    /**
     * Compute magnitude spectrum using real DFT
     * Returns positive frequency bins only (fftSize/2 + 1)
     */
    private fun computeMagnitudeSpectrum(frame: FloatArray, fftSize: Int): FloatArray {
        val numBins = fftSize / 2 + 1
        val magnitude = FloatArray(numBins)

        for (k in 0 until numBins) {
            var real = 0.0
            var imag = 0.0
            for (n in frame.indices) {
                val angle = 2.0 * PI * k * n / fftSize
                real += frame[n] * cos(angle)
                imag -= frame[n] * kotlin.math.sin(angle)
            }
            magnitude[k] = kotlin.math.sqrt(real * real + imag * imag).toFloat()
        }

        return magnitude
    }

    /**
     * Create mel filterbank matrix
     * Returns: Array[numMelBins][numSpecBins] where numSpecBins = fftSize/2 + 1
     */
    private fun createMelFilterbank(
        fftSize: Int, sampleRate: Int, numMelBins: Int,
        fMin: Float, fMax: Float
    ): Array<FloatArray> {
        val numSpecBins = fftSize / 2 + 1

        fun hzToMel(hz: Float): Float = 2595f * kotlin.math.log10(1f + hz / 700f)
        fun melToHz(mel: Float): Float = 700f * (Math.pow(10.0, (mel / 2595f).toDouble()).toFloat() - 1f)

        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)
        val melPoints = FloatArray(numMelBins + 2) { i ->
            melToHz(melMin + (melMax - melMin) * i / (numMelBins + 1))
        }

        // Convert Hz to FFT bin indices
        val binPoints = FloatArray(numMelBins + 2) { i ->
            melPoints[i] * fftSize / sampleRate
        }

        val filterbank = Array(numMelBins) { FloatArray(numSpecBins) }

        for (m in 0 until numMelBins) {
            val left = binPoints[m]
            val center = binPoints[m + 1]
            val right = binPoints[m + 2]

            val enorm = 2.0f / (right - left)
            for (k in 0 until numSpecBins) {
                filterbank[m][k] = when {
                    k.toFloat() < left -> 0f
                    k.toFloat() <= center -> (k - left) / (center - left) * enorm
                    k.toFloat() <= right -> (right - k) / (right - center) * enorm
                    else -> 0f
                }
            }
        }

        return filterbank
    }

    /**
     * YAMNet output post-processing
     * Model output shape: float32[1, 1, 1, 521]
     * Map scores directly to 521 sound class labels
     */
    private fun postProcess(modelOutput: ByteArray): Map<String, Float> {
        val scores = when (OUTPUT_DATA_TYPE) {
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

        if (scores.size < NUM_CLASSES || labelList.size != NUM_CLASSES) {
            Log.e("ModelExecutor", "Output size mismatch: scores=${scores.size}, labels=${labelList.size}, expected=$NUM_CLASSES")
            return emptyMap()
        }

        return scores.take(NUM_CLASSES).mapIndexed { index, value ->
            labelList[index] to value
        }.filter { it.second >= threshold }
            .sortedByDescending { it.second }
            .toMap()
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

    private fun getLabels() {
        try {
            context.assets.open(LABEL_FILE)
                .bufferedReader().use { reader -> labelList = reader.readLines()
                }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    interface ExecutorListener {
        fun onError(error: String)
        fun onResults(
            result: Map<String, Float>, inferenceTime: Long
        )
    }

    companion object {
        var labelList: List<String> = mutableListOf()

        private const val MODEL_NAME = ModelConstants.MODEL_NAME

        private val INPUT_DATA_LAYER = ModelConstants.INPUT_DATA_LAYER
        private val INPUT_DATA_TYPE = ModelConstants.INPUT_DATA_TYPE

        private const val INPUT_SIZE_W = ModelConstants.INPUT_SIZE_W   // Mel bins
        private const val INPUT_SIZE_H = ModelConstants.INPUT_SIZE_H   // Time frames
        private const val INPUT_SIZE_C = ModelConstants.INPUT_SIZE_C   // Mono audio

        private const val INPUT_CONVERSION_OFFSET = ModelConstants.INPUT_CONVERSION_OFFSET
        private const val INPUT_CONVERSION_SCALE = ModelConstants.INPUT_CONVERSION_SCALE

        private val OUTPUT_DATA_TYPE = ModelConstants.OUTPUT_DATA_TYPE

        // Audio-specific constants
        private const val SAMPLE_RATE = ModelConstants.SAMPLE_RATE
        private const val STFT_WINDOW_SIZE = ModelConstants.STFT_WINDOW_SIZE
        private const val STFT_HOP_SIZE = ModelConstants.STFT_HOP_SIZE
        private const val NUM_CLASSES = ModelConstants.NUM_CLASSES

        private const val LABEL_FILE = ModelConstants.LABEL_FILE
    }
}
