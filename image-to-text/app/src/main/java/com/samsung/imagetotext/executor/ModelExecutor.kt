package com.samsung.imagetotext.executor

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.samsung.imagetotext.data.DetectionResult
import com.samsung.imagetotext.data.ModelConstants
import com.samsung.imagetotext.enn_type.BufferSetInfo
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class ModelExecutor(
    var threshold: Float = ModelConstants.DEFAULT_TEXT_THRESHOLD,
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

    private data class EnnModel(
        val name: String,
        var modelId: Long = 0L,
        var bufferSet: Long = 0L,
        var inputCount: Int = 0,
        var outputCount: Int = 0
    )

    private val detector = EnnModel(ModelConstants.DETECTOR_MODEL_NAME)
    private val recognizer = EnnModel(ModelConstants.RECOGNIZER_MODEL_NAME)
    private val running = AtomicBoolean(false)
    private var initialized = false
    private var closed = false
    private var characterList: List<String> = emptyList()

    init {
        try {
            System.loadLibrary("enn_jni")
            copyAsset(ModelConstants.DETECTOR_MODEL_NAME)
            copyAsset(ModelConstants.RECOGNIZER_MODEL_NAME)
            characterList = loadCharacters()
            setupENN()
        } catch (t: Throwable) {
            executorListener?.onError(t.message ?: "EasyOCR initialization failed")
            showErrorDialog("EasyOCR initialization failed", t.message ?: "Unknown error")
        }
    }

    private fun setupENN() {
        ennInitialize()
        initialized = true
        openModel(detector)
        openModel(recognizer)

        if (detector.bufferSet == 0L || recognizer.bufferSet == 0L) {
            throw IllegalStateException("The NNC file is not compatible with this device.")
        }
    }

    private fun openModel(model: EnnModel) {
        val path = File(context.filesDir, model.name).absolutePath
        model.modelId = ennOpenModel(path)
        if (model.modelId == 0L) error("Failed to open ${model.name}")

        val info = ennAllocateAllBuffers(model.modelId)
        model.bufferSet = info.buffer_set
        model.inputCount = info.n_in_buf
        model.outputCount = info.n_out_buf
    }

    fun process(image: Bitmap) {
        if (closed || detector.bufferSet == 0L || recognizer.bufferSet == 0L) return
        if (!running.compareAndSet(false, true)) return

        try {
            val source = ensureArgb8888(image)
            val detectorInput = makeDetectorInput(source)

            val start = SystemClock.elapsedRealtime()
            val detectorOutput = execute(
                detector,
                detectorInputToBytes(detectorInput.bitmap)
            )
            val boxes = decodeDetector(detectorOutput).mapNotNull {
                detectorInput.toSourceCoordinates(it)
            }

            val results = ArrayList<DetectionResult>(boxes.size)
            for ((index, box) in boxes.take(ModelConstants.MAX_TEXT_BOXES).withIndex()) {
                val crop = cropNormalized(source, box) ?: continue
                val recognizerInput = recognizerInputToBytes(crop)
                val recognizerOutput = execute(recognizer, recognizerInput)
                val recognition = decodeRecognizer(recognizerOutput)


                if (
                    recognition.text.isNotBlank() &&
                    recognition.confidence >= ModelConstants.DEFAULT_RECOGNITION_THRESHOLD
                ) {
                    results += DetectionResult(
                        Pair(recognition.text, recognition.confidence),
                        box
                    )
                }
            }

            val elapsed = SystemClock.elapsedRealtime() - start
            executorListener?.onResults(sortReadingOrder(results), elapsed)
        } catch (t: Throwable) {
            Log.e(TAG, "EasyOCR inference failed", t)
            executorListener?.onError(t.message ?: "EasyOCR inference failed")
        } finally {
            running.set(false)
        }
    }

    private fun execute(model: EnnModel, input: ByteArray): ByteArray {
        ennMemcpyHostToDevice(model.bufferSet, 0, input)
        ennExecute(model.modelId)

        return ennMemcpyDeviceToHost(model.bufferSet, model.inputCount)
    }

    private fun detectorInputToBytes(bitmap: Bitmap): ByteArray {
        val width = ModelConstants.DETECTOR_INPUT_WIDTH
        val height = ModelConstants.DETECTOR_INPUT_HEIGHT
        val plane = width * height
        val pixels = IntArray(plane)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val input = FloatArray(plane * 3)

        for (i in pixels.indices) {
            val color = pixels[i]
            val rgb = floatArrayOf(
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f
            )
            input[i] = (rgb[0] - ModelConstants.DETECTOR_MEAN[0]) / ModelConstants.DETECTOR_STD[0]
            input[plane + i] = (rgb[1] - ModelConstants.DETECTOR_MEAN[1]) / ModelConstants.DETECTOR_STD[1]
            input[2 * plane + i] = (rgb[2] - ModelConstants.DETECTOR_MEAN[2]) / ModelConstants.DETECTOR_STD[2]
        }
        return floatsToBytes(input)
    }

    private fun recognizerInputToBytes(crop: Bitmap): ByteArray {
        val targetW = ModelConstants.RECOGNIZER_INPUT_WIDTH   // 100
        val targetH = ModelConstants.RECOGNIZER_INPUT_HEIGHT  // 32

        val resized = Bitmap.createScaledBitmap(crop, targetW, targetH, true)

        val pixels = IntArray(targetW * targetH)

        resized.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

        val input = FloatArray(targetW * targetH)

        for (i in pixels.indices) {
            val color = pixels[i]

            val gray = (
                    0.299f * Color.red(color) +
                            0.587f * Color.green(color) +
                            0.114f * Color.blue(color)
                    ) / 255.0f

            input[i] = (gray - 0.5f) / 0.5f
        }

        return floatsToBytes(input)
    }

    private fun decodeDetector(outputBytes: ByteArray): List<RectF> {
        val output = bytesToFloats(outputBytes)

        val width = ModelConstants.DETECTOR_OUTPUT_WIDTH
        val height = ModelConstants.DETECTOR_OUTPUT_HEIGHT
        val channels = ModelConstants.DETECTOR_OUTPUT_CHANNELS
        val expected = width * height * channels

        require(output.size >= expected) {
            "Detector output size mismatch: ${output.size}, expected $expected"
        }

        var minValue = Float.POSITIVE_INFINITY
        var maxValue = Float.NEGATIVE_INFINITY
        var channel0Min = Float.POSITIVE_INFINITY
        var channel0Max = Float.NEGATIVE_INFINITY
        var channel1Min = Float.POSITIVE_INFINITY
        var channel1Max = Float.NEGATIVE_INFINITY

        for (p in 0 until width * height) {
            val channel0 = output[p * 2]
            val channel1 = output[p * 2 + 1]

            minValue = min(minValue, min(channel0, channel1))
            maxValue = max(maxValue, max(channel0, channel1))

            channel0Min = min(channel0Min, channel0)
            channel0Max = max(channel0Max, channel0)
            channel1Min = min(channel1Min, channel1)
            channel1Max = max(channel1Max, channel1)
        }

        val active = BooleanArray(width * height)
        val score = FloatArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = y * width + x
                val text = output[p * 2]
                val link = output[p * 2 + 1]

                score[p] = text

                active[p] =
                    text >= ModelConstants.DEFAULT_LOW_TEXT_THRESHOLD ||
                            link >= ModelConstants.DEFAULT_LINK_THRESHOLD
            }
        }
        val boxes = connectedComponents(active = active, score = score, width = width, height = height)

        return boxes
    }

    private fun connectedComponents(active: BooleanArray, score: FloatArray, width: Int, height: Int): List<RectF> {
        val visited = BooleanArray(active.size)
        val queue = ArrayDeque<Int>()
        val boxes = ArrayList<Pair<RectF, Float>>()
        val dx = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
        val dy = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)

        for (start in active.indices) {
            if (!active[start] || visited[start]) continue
            visited[start] = true
            queue.add(start)
            var minX = width
            var minY = height
            var maxX = 0
            var maxY = 0
            var area = 0
            var peak = 0f

            while (queue.isNotEmpty()) {
                val index = queue.removeFirst()
                val x = index % width
                val y = index / width
                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)
                peak = max(peak, score[index])
                area++

                for (k in dx.indices) {
                    val nx = x + dx[k]
                    val ny = y + dy[k]
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val next = ny * width + nx
                    if (active[next] && !visited[next]) {
                        visited[next] = true
                        queue.add(next)
                    }
                }
            }

            if (area < ModelConstants.MIN_COMPONENT_AREA || peak < threshold) continue

            val boxWidth = maxX - minX + 1
            val boxHeight = maxY - minY + 1
            val padX = max(3, (boxWidth * 0.04f).toInt())
            val padY = max(2, (boxHeight * 0.08f).toInt())

            val left = (minX - padX).coerceAtLeast(0) / width.toFloat()
            val top = (minY - padY).coerceAtLeast(0) / height.toFloat()
            val right = (maxX + 1 + padX).coerceAtMost(width) / width.toFloat()
            val bottom = (maxY + 1 + padY).coerceAtMost(height) / height.toFloat()

            if (right - left > 0.005f && bottom - top > 0.005f) {
                boxes += RectF(left, top, right, bottom) to peak
            }
        }

        return boxes.sortedByDescending { it.second }.map { it.first }
    }

    private data class Recognition(val text: String, val confidence: Float)

    private fun decodeRecognizer(outputBytes: ByteArray): Recognition {
        val logits = bytesToFloats(outputBytes)
        val timeSteps = ModelConstants.RECOGNIZER_TIME_STEPS
        val classes = ModelConstants.RECOGNIZER_CLASS_COUNT
        require(logits.size >= timeSteps * classes) {
            "Recognizer output size mismatch: ${logits.size}, expected ${timeSteps * classes}"
        }

        val text = StringBuilder()
        var previous = -1
        var confidenceSum = 0f
        var confidenceCount = 0

        for (t in 0 until timeSteps) {
            val base = t * classes
            var bestIndex = 0
            var bestLogit = logits[base]
            var maxLogit = bestLogit
            for (c in 1 until classes) {
                val value = logits[base + c]
                if (value > bestLogit) {
                    bestLogit = value
                    bestIndex = c
                }
                if (value > maxLogit) maxLogit = value
            }

            var denominator = 0.0
            for (c in 0 until classes) {
                denominator += exp((logits[base + c] - maxLogit).toDouble())
            }
            val probability = (exp((bestLogit - maxLogit).toDouble()) / denominator).toFloat()

            if (bestIndex != 0 && bestIndex != previous) {
                val characterIndex = bestIndex - 1
                val token = characterList.getOrNull(characterIndex) ?: "<$bestIndex>"
                text.append(token)
                confidenceSum += probability
                confidenceCount++
            }
            previous = bestIndex
        }

        return Recognition(
            text = text.toString(),
            confidence = if (confidenceCount == 0) 0f else confidenceSum / confidenceCount
        )
    }

    private fun cropNormalized(bitmap: Bitmap, box: RectF): Bitmap? {
        val left = (box.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (box.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = (box.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (box.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        if (right <= left || bottom <= top) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun sortReadingOrder(results: List<DetectionResult>): List<DetectionResult> {
        return results.sortedWith(compareBy<DetectionResult> {
            val box = it.requireBoundingBox()
            (box.top * 20f).toInt()
        }.thenBy { it.requireBoundingBox().left })
    }

    private data class DetectorInput(val bitmap: Bitmap, val sourceWidth: Int, val sourceHeight: Int, val scale: Float, val offsetX: Float, val offsetY: Float) {
        fun toSourceCoordinates(detectorBox: RectF): RectF? {
            val inputWidth = ModelConstants.DETECTOR_INPUT_WIDTH.toFloat()
            val inputHeight = ModelConstants.DETECTOR_INPUT_HEIGHT.toFloat()
            val inputLeft = detectorBox.left * inputWidth
            val inputTop = detectorBox.top * inputHeight
            val inputRight = detectorBox.right * inputWidth
            val inputBottom = detectorBox.bottom * inputHeight
            val sourceLeft = (inputLeft - offsetX) / scale
            val sourceTop = (inputTop - offsetY) / scale
            val sourceRight = (inputRight - offsetX) / scale
            val sourceBottom = (inputBottom - offsetY) / scale

            val mapped = RectF(
                (sourceLeft / sourceWidth.toFloat()).coerceIn(0f, 1f),
                (sourceTop / sourceHeight.toFloat()).coerceIn(0f, 1f),
                (sourceRight / sourceWidth.toFloat()).coerceIn(0f, 1f),
                (sourceBottom / sourceHeight.toFloat()).coerceIn(0f, 1f)
            )

            return if (mapped.width() > 0.005f && mapped.height() > 0.005f) {
                mapped
            } else {
                null
            }
        }
    }

    private fun makeDetectorInput(bitmap: Bitmap): DetectorInput {
        val targetW = ModelConstants.DETECTOR_INPUT_WIDTH
        val targetH = ModelConstants.DETECTOR_INPUT_HEIGHT
        val scale = min(targetW.toFloat() / bitmap.width, targetH.toFloat() / bitmap.height)
        val width = max(1, (bitmap.width * scale).toInt())
        val height = max(1, (bitmap.height * scale).toInt())
        val offsetX = (targetW - width) / 2f
        val offsetY = (targetH - height) / 2f
        val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(resized, offsetX, offsetY, Paint(Paint.FILTER_BITMAP_FLAG))
        return DetectorInput(output, bitmap.width, bitmap.height, scale, offsetX, offsetY)
    }

    private fun ensureArgb8888(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        return bitmap.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun floatsToBytes(values: FloatArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        byteBuffer.asFloatBuffer().put(values)
        return byteBuffer.array()
    }

    private fun bytesToFloats(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buffer.remaining()).also(buffer::get)
    }

    private fun loadCharacters(): List<String> {
        return try {
            val text = context.assets.open(ModelConstants.CHARACTER_FILE_NAME)
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
                .replace("\r", "")
            val characters = if ('\n' in text) {
                text.lines().filter { it.isNotEmpty() }
            } else {
                val list = ArrayList<String>()
                var index = 0
                while (index < text.length) {
                    val codePoint = text.codePointAt(index)
                    list += String(Character.toChars(codePoint))
                    index += Character.charCount(codePoint)
                }
                list
            }
            characters
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun copyAsset(filename: String) {
        val output = File(context.filesDir, filename)
        context.assets.open(filename).use { input ->
            FileOutputStream(output).use { stream -> input.copyTo(stream) }
        }
    }

    @Synchronized
    fun closeENN() {
        if (closed) return
        closed = true
        closeModel(recognizer)
        closeModel(detector)
        if (initialized) {
            ennDeinitialize()
            initialized = false
        }
    }

    private fun closeModel(model: EnnModel) {
        try {
            if (model.bufferSet != 0L) {
                ennReleaseBuffers(model.bufferSet, model.inputCount + model.outputCount)
                model.bufferSet = 0L
            }
            if (model.modelId != 0L) {
                ennCloseModel(model.modelId)
                model.modelId = 0L
            }
        } catch (t: Throwable) {
        }
    }

    private fun showErrorDialog(title: String, message: String) {
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    interface ExecutorListener {
        fun onError(error: String)
        fun onResults(detectionResult: List<DetectionResult>, inferenceTime: Long)
    }

    companion object {
        private const val TAG = "EasyOCRExecutor"
    }
}
