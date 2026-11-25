package com.samsung.textgeneration.executor

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.samsung.textgeneration.data.ModelConstants
import com.samsung.textgeneration.enn_type.BufferSetInfo
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class ModelExecutor(
    private val context: Context,
    private val listener: ExecutorListener?
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
        val filename: String,
        var modelId: Long = 0,
        var bufferSet: Long = 0,
        var nIn: Int = 0,
        var nOut: Int = 0
    )

    private data class PrefillResult(
        val hiddenFinal: FloatArray,
        val pastK: Array<FloatArray>,
        val pastV: Array<FloatArray>
    )

    private data class DecodeResult(
        val hiddenFinal: FloatArray,
        val pastK: Array<FloatArray>,
        val pastV: Array<FloatArray>
    )

    private val prefillP1 = EnnModel(ModelConstants.PREFILL_P1_MODEL)
    private val prefillP2 = EnnModel(ModelConstants.PREFILL_P2_MODEL)
    private val decode = EnnModel(ModelConstants.DECODE_MODEL)

    private lateinit var embeddingBuffer: FloatBuffer
    private external fun nativeInitLmHead(path: String, vocab: Int, hidden: Int)
    private external fun nativeArgmaxLmHead(hidden: FloatArray, hiddenOffset: Int): Int
    private external fun nativeReleaseLmHead()

    private lateinit var cosPrefill: FloatArray
    private lateinit var sinPrefill: FloatArray
    private lateinit var cosDecode: FloatArray
    private lateinit var sinDecode: FloatArray

    private lateinit var tokenizer: QwenTokenizer

    init {
        System.loadLibrary("enn_jni")

        checkRequiredFiles()

        embeddingBuffer = mmapFloatFile(commonPath(ModelConstants.EMBEDDING_BIN))
        require(embeddingBuffer.capacity() == ModelConstants.VOCAB * ModelConstants.HIDDEN)
        nativeInitLmHead(commonPath(ModelConstants.LM_HEAD_BIN), ModelConstants.VOCAB, ModelConstants.HIDDEN)

        cosPrefill = loadFloatBinFromFile(commonPath(ModelConstants.COS_PREFILL_BIN))
        sinPrefill = loadFloatBinFromFile(commonPath(ModelConstants.SIN_PREFILL_BIN))
        cosDecode = loadFloatBinFromFile(commonPath(ModelConstants.COS_DECODE_BIN))
        sinDecode = loadFloatBinFromFile(commonPath(ModelConstants.SIN_DECODE_BIN))

        Log.i(TAG, "embedding floats=${embeddingBuffer.capacity()}")
        Log.i(TAG, "cosPrefill floats=${cosPrefill.size}")
        Log.i(TAG, "sinPrefill floats=${sinPrefill.size}")
        Log.i(TAG, "cosDecode floats=${cosDecode.size}")
        Log.i(TAG, "sinDecode floats=${sinDecode.size}")

        tokenizer = QwenTokenizer(
            tokenizerJsonPath = commonPath(ModelConstants.TOKENIZER_JSON),
            tokenizerConfigPath = commonPath(ModelConstants.TOKENIZER_CONFIG_JSON),
            vocabJsonPath = commonPath(ModelConstants.VOCAB_JSON)
        )

        setupENN()
    }

    private fun setupENN() {
        ennInitialize()
        openModel(prefillP1)
        openModel(prefillP2)
        openModel(decode)
    }

    private fun openModel(m: EnnModel) {
        val path = modelPath(m.filename)

        m.modelId = ennOpenModel(path)

        val info = ennAllocateAllBuffers(m.modelId)
        m.bufferSet = info.buffer_set
        m.nIn = info.n_in_buf
        m.nOut = info.n_out_buf
    }

    @Synchronized
    fun close() {
        releaseModel(prefillP1)
        releaseModel(prefillP2)
        releaseModel(decode)
        ennDeinitialize()
        nativeReleaseLmHead()
    }

    private fun releaseModel(m: EnnModel) {
        if (m.bufferSet != 0L) {
            ennReleaseBuffers(m.bufferSet, m.nIn + m.nOut)
        }
        if (m.modelId != 0L) {
            ennCloseModel(m.modelId)
        }
    }

    @Synchronized
    fun generate(prompt: String, shouldStop: () -> Boolean = { false }) {
        try {
            tokenizer.reset()

            val start = SystemClock.uptimeMillis()
            val generatedText = StringBuilder()
            fun finishStopped() {
                listener?.onStopped(generatedText.toString(), SystemClock.uptimeMillis() - start)
            }
            if (shouldStop()) { finishStopped(); return }

            val promptIds = tokenizer.encodeChatPrompt(
                userText = prompt,
                enableThinking = false
            )

            val promptLen = promptIds.size

            require(promptLen <= ModelConstants.PREFILL_SEQ) {
                "promptLen=$promptLen exceeds PREFILL_SEQ=${ModelConstants.PREFILL_SEQ}"
            }

            if (shouldStop()) { finishStopped(); return }
            val prefillResult = runPrefill(promptIds, promptLen)
            if (shouldStop()) { finishStopped(); return }

            var pastK = prefillResult.pastK
            var pastV = prefillResult.pastV
            var validCacheLen = promptLen

            var nextToken = argmaxLmHead(
                hidden = prefillResult.hiddenFinal,
                hiddenOffset = (promptLen - 1) * ModelConstants.HIDDEN
            )

            val generatedIds = mutableListOf<Int>()

            var inThinkBlock = false

            for (step in 0 until ModelConstants.MAX_NEW_TOKENS) {
                if (shouldStop()) { finishStopped(); return }
                generatedIds.add(nextToken)

                if (isEosToken(nextToken)) {
                    break
                }

                if (nextToken == THINK_START_TOKEN_ID) {
                    inThinkBlock = true
                } else if (nextToken == THINK_END_TOKEN_ID) {
                    inThinkBlock = false
                } else if (!inThinkBlock) {
                    val tokenText = tokenizer.decodeToken(nextToken)

                    if (tokenText.isNotEmpty()) {
                        generatedText.append(tokenText)
                        listener?.onToken(nextToken, tokenText)
                    }
                }

                if (shouldStop()) { finishStopped(); return }
                if (step == ModelConstants.MAX_NEW_TOKENS - 1) break
                val decodeStart = SystemClock.elapsedRealtimeNanos()
                val decodeResult = runDecode(
                    tokenId = nextToken,
                    inputPos = promptLen + step,
                    validCacheLen = validCacheLen,
                    pastK = pastK,
                    pastV = pastV
                )
                if (shouldStop()) { finishStopped(); return }
                nextToken = argmaxLmHead(
                    hidden = decodeResult.hiddenFinal,
                    hiddenOffset = 0
                )

                pastK = decodeResult.pastK
                pastV = decodeResult.pastV
                validCacheLen = minOf(validCacheLen + 1, ModelConstants.MAX_CACHE_LEN)
            }

            if (shouldStop()) { finishStopped(); return }
            val elapsed = SystemClock.uptimeMillis() - start
            listener?.onDone(generatedText.toString(), elapsed)

        } catch (e: Exception) {
            listener?.onError(Log.getStackTraceString(e))
        }
    }

    private fun isEosToken(tokenId: Int): Boolean {
        return tokenId == EOS_TOKEN_ID || tokenId == IM_END_TOKEN_ID
    }

    private fun runPrefill(promptIds: IntArray, promptLen: Int): PrefillResult {
        require(promptLen <= ModelConstants.PREFILL_SEQ) {
            "promptLen=$promptLen exceeds PREFILL_SEQ=${ModelConstants.PREFILL_SEQ}"
        }

        val paddedIds = IntArray(ModelConstants.PREFILL_SEQ) { ModelConstants.PAD_TOKEN_ID }
        for (i in promptIds.indices) {
            paddedIds[i] = promptIds[i]
        }

        val inputEmbeds = buildInputEmbeds(paddedIds)
        val attentionMask = buildPrefillMask(promptLen)

        ennMemcpyHostToDevice(prefillP1.bufferSet, 0, floatsToBytes(inputEmbeds))
        ennMemcpyHostToDevice(prefillP1.bufferSet, 1, floatsToBytes(attentionMask))
        ennMemcpyHostToDevice(prefillP1.bufferSet, 2, floatsToBytes(cosPrefill))
        ennMemcpyHostToDevice(prefillP1.bufferSet, 3, floatsToBytes(sinPrefill))
        ennExecute(prefillP1.modelId)


        val hiddenMid = readOutputFloats(prefillP1, 0, "p1_hidden_mid")
        val p1KFlat = readOutputFloats(prefillP1, 1, "p1_k_flat")
        val p1VFlat = readOutputFloats(prefillP1, 2, "p1_v_flat")

        checkHiddenSize(hiddenMid, ModelConstants.PREFILL_SEQ, "p1_hidden_mid")
        checkFlatCacheSize(p1KFlat, ModelConstants.SPLIT_INDEX, "p1_k_flat")
        checkFlatCacheSize(p1VFlat, ModelConstants.SPLIT_INDEX, "p1_v_flat")

        ennMemcpyHostToDevice(prefillP2.bufferSet, 0, floatsToBytes(hiddenMid))
        ennMemcpyHostToDevice(prefillP2.bufferSet, 1, floatsToBytes(attentionMask))
        ennMemcpyHostToDevice(prefillP2.bufferSet, 2, floatsToBytes(cosPrefill))
        ennMemcpyHostToDevice(prefillP2.bufferSet, 3, floatsToBytes(sinPrefill))
        ennExecute(prefillP2.modelId)

        val p2Out0 = readOutputFloats(prefillP2, 0, "p2_out0")
        val p2Out1 = readOutputFloats(prefillP2, 1, "p2_out1")
        val p2Out2 = readOutputFloats(prefillP2, 2, "p2_out2")
        val hiddenFinal = p2Out0
        val p2KFlat = p2Out1
        val p2VFlat = p2Out2
        val p2LayerCount = ModelConstants.NUM_LAYERS - ModelConstants.SPLIT_INDEX

        checkHiddenSize(hiddenFinal, ModelConstants.PREFILL_SEQ, "p2_hidden_final")
        checkFlatCacheSize(p2KFlat, p2LayerCount, "p2_k_flat")
        checkFlatCacheSize(p2VFlat, p2LayerCount, "p2_v_flat")

        val pastK = Array(ModelConstants.NUM_LAYERS) {
            FloatArray(oneLayerCacheSize())
        }

        val pastV = Array(ModelConstants.NUM_LAYERS) {
            FloatArray(oneLayerCacheSize())
        }

        for (i in 0 until ModelConstants.SPLIT_INDEX) {
            pastK[i] = packPrefillCacheFront(sliceLayerCache(p1KFlat, i), promptLen)
            pastV[i] = packPrefillCacheFront(sliceLayerCache(p1VFlat, i), promptLen)
        }

        for (i in 0 until p2LayerCount) {
            val dstLayer = ModelConstants.SPLIT_INDEX + i
            pastK[dstLayer] = packPrefillCacheFront(sliceLayerCache(p2KFlat, i), promptLen)
            pastV[dstLayer] = packPrefillCacheFront(sliceLayerCache(p2VFlat, i), promptLen)
        }
        return PrefillResult(hiddenFinal, pastK, pastV)
    }

    private fun runDecode(
        tokenId: Int,
        inputPos: Int,
        validCacheLen: Int,
        pastK: Array<FloatArray>,
        pastV: Array<FloatArray>
    ): DecodeResult {
        val inputEmbeds = buildSingleInputEmbed(tokenId)
        val attentionMask = buildDecodeMask(validCacheLen)
        val rope = buildDecodeRope(inputPos)

        ennMemcpyHostToDevice(decode.bufferSet, 0, floatsToBytes(inputEmbeds))
        ennMemcpyHostToDevice(decode.bufferSet, 1, floatsToBytes(attentionMask))
        ennMemcpyHostToDevice(decode.bufferSet, 2, floatsToBytes(rope.first))
        ennMemcpyHostToDevice(decode.bufferSet, 3, floatsToBytes(rope.second))

        val cacheBytes = decodeCacheUpload
        val cacheFloats = decodeCacheUploadFloats

        for (i in 0 until ModelConstants.NUM_LAYERS) {
            cacheFloats.clear()
            cacheFloats.put(pastK[i])
            ennMemcpyHostToDevice(decode.bufferSet, 4 + 2 * i, cacheBytes)
            cacheFloats.clear()
            cacheFloats.put(pastV[i])
            ennMemcpyHostToDevice(decode.bufferSet, 4 + 2 * i + 1, cacheBytes)
        }

        val executeStart = SystemClock.elapsedRealtimeNanos()

        ennExecute(decode.modelId)
        val executeMs = (SystemClock.elapsedRealtimeNanos() - executeStart) / 1e6
        val readStart = SystemClock.elapsedRealtimeNanos()
        val hiddenFinal = readOutputFloats(decode, 0, "decode_hidden_final")

        for (i in 0 until ModelConstants.NUM_LAYERS) {
            readPresentInto(1 + 2 * i, pastK[i], validCacheLen)
            readPresentInto(2 + 2 * i, pastV[i], validCacheLen)
        }
        return DecodeResult(hiddenFinal, pastK, pastV)
    }

    private val decodeCacheUpload by lazy { ByteArray(oneLayerCacheSize() * Float.SIZE_BYTES) }
    private val decodeCacheUploadFloats by lazy {
        ByteBuffer.wrap(decodeCacheUpload).order(ByteOrder.nativeOrder()).asFloatBuffer()
    }

    private fun readPresentInto(outputIndex: Int, dst: FloatArray, validLen: Int) {
        require(outputIndex in 0 until decode.nOut)
        val bytes = ennMemcpyDeviceToHost(decode.bufferSet, decode.nIn + outputIndex)
        val max = ModelConstants.MAX_CACHE_LEN
        val total = ModelConstants.DECODE_TOTAL_LEN
        val dim = ModelConstants.HEAD_DIM
        val heads = ModelConstants.NUM_KV_HEADS
        require(bytes.size == heads * total * dim * Float.SIZE_BYTES)
        val src = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val keep = minOf(validLen, max - 1)
        val shift = if (validLen >= max) 1 else 0
        for (h in 0 until heads) {
            val base = h * max * dim
            src.position((h * total + shift) * dim)
            src.get(dst, base, keep * dim)
            src.position((h * total + max) * dim)
            src.get(dst, base + keep * dim, dim)
            java.util.Arrays.fill(dst, base + (keep + 1) * dim, base + max * dim, 0f)
        }
    }

    private fun readOutputFloats(m: EnnModel, outputIndex: Int, name: String): FloatArray {
        require(outputIndex in 0 until m.nOut) {
            "${m.filename} invalid outputIndex=$outputIndex, nOut=${m.nOut}, name=$name"
        }

        val layerNumber = m.nIn + outputIndex
        val bytes = ennMemcpyDeviceToHost(m.bufferSet, layerNumber)

        require(bytes.isNotEmpty()) {
            "${m.filename} output is empty: name=$name, layerNumber=$layerNumber"
        }
        return bytesToFloats(bytes)
    }

    private fun buildInputEmbeds(tokenIds: IntArray): FloatArray {
        val out = FloatArray(ModelConstants.PREFILL_SEQ * ModelConstants.HIDDEN)

        for (t in 0 until ModelConstants.PREFILL_SEQ) {
            copyEmbeddingRow(
                tokenId = tokenIds[t],
                dst = out,
                dstOffset = t * ModelConstants.HIDDEN
            )
        }

        return out
    }

    private fun buildSingleInputEmbed(tokenId: Int): FloatArray {
        val out = FloatArray(ModelConstants.HIDDEN)
        copyEmbeddingRow(tokenId, out, 0)
        return out
    }

    private fun copyEmbeddingRow(tokenId: Int, dst: FloatArray, dstOffset: Int) {
        require(tokenId in 0 until ModelConstants.VOCAB) {
            "Invalid token id: $tokenId, vocab=${ModelConstants.VOCAB}"
        }

        val srcOffset = tokenId * ModelConstants.HIDDEN

        for (i in 0 until ModelConstants.HIDDEN) {
            dst[dstOffset + i] = embeddingBuffer.get(srcOffset + i)
        }
    }

    private fun buildPrefillMask(promptLen: Int): FloatArray {
        val seq = ModelConstants.PREFILL_SEQ
        val mask = FloatArray(seq * seq) { -10000.0f }

        for (i in 0 until seq) {
            for (j in 0..i) {
                mask[i * seq + j] = 0.0f
            }
        }

        if (promptLen < seq) {
            for (i in 0 until seq) {
                for (j in promptLen until seq) {
                    mask[i * seq + j] = -10000.0f
                }
            }
        }
        return mask
    }

    private fun buildDecodeMask(validCacheLen: Int): FloatArray {
        val totalLen = ModelConstants.DECODE_TOTAL_LEN
        val mask = FloatArray(totalLen) { -10000.0f }

        val copyLen = validCacheLen.coerceAtMost(ModelConstants.MAX_CACHE_LEN)
        for (i in 0 until copyLen) {
            mask[i] = 0.0f
        }

        mask[ModelConstants.MAX_CACHE_LEN] = 0.0f
        return mask
    }

    private fun buildDecodeRope(position: Int): Pair<FloatArray, FloatArray> {
        val headDim = ModelConstants.HEAD_DIM
        val halfDim = headDim / 2

        val cosOut = FloatArray(headDim)
        val sinOut = FloatArray(headDim)

        for (i in 0 until halfDim) {
            val exponent = (2.0 * i.toDouble()) / headDim.toDouble()
            val invFreq = 1.0 / ROPE_THETA.pow(exponent)
            val freq = position.toDouble() * invFreq

            val c = cos(freq).toFloat()
            val s = sin(freq).toFloat()

            cosOut[i] = c
            cosOut[i + halfDim] = c

            sinOut[i] = s
            sinOut[i + halfDim] = s
        }

        return Pair(cosOut, sinOut)
    }

    private fun oneLayerCacheSize(): Int {
        return ModelConstants.NUM_KV_HEADS *
                ModelConstants.MAX_CACHE_LEN *
                ModelConstants.HEAD_DIM
    }

    private fun sliceLayerCache(flat: FloatArray, layerIdx: Int): FloatArray {
        val one = oneLayerCacheSize()
        val start = layerIdx * one

        require(start + one <= flat.size) {
            "sliceLayerCache out of range: layerIdx=$layerIdx, start=$start, one=$one, flat.size=${flat.size}"
        }

        val out = FloatArray(one)
        System.arraycopy(flat, start, out, 0, one)
        return out
    }

    private fun checkHiddenSize(hidden: FloatArray, seq: Int, name: String) {
        val expected = seq * ModelConstants.HIDDEN
        require(hidden.size == expected) {
            "$name size mismatch: actual=${hidden.size}, expected=$expected"
        }
    }

    private fun checkFlatCacheSize(cache: FloatArray, layerCount: Int, name: String) {
        val expected = layerCount * oneLayerCacheSize()
        require(cache.size == expected) {
            "$name size mismatch: actual=${cache.size}, expected=$expected, layerCount=$layerCount"
        }
    }

    private fun packPrefillCacheFront(src: FloatArray, validLen: Int): FloatArray {
        val out = FloatArray(oneLayerCacheSize())
        val copyLen = validLen.coerceAtMost(ModelConstants.MAX_CACHE_LEN)

        for (h in 0 until ModelConstants.NUM_KV_HEADS) {
            for (t in 0 until copyLen) {
                val base = ((h * ModelConstants.MAX_CACHE_LEN + t) * ModelConstants.HEAD_DIM)
                System.arraycopy(src, base, out, base, ModelConstants.HEAD_DIM)
            }
        }

        return out
    }

    private fun argmaxLmHead(hidden: FloatArray, hiddenOffset: Int): Int {
        val start = SystemClock.elapsedRealtimeNanos()
        val id = nativeArgmaxLmHead(hidden, hiddenOffset)
        Log.i(TAG, "LM_HEAD_NATIVE ms=${(SystemClock.elapsedRealtimeNanos() - start) / 1e6} id=$id")
        return id
    }

    private fun floatsToBytes(data: FloatArray): ByteArray {
        val bb = ByteBuffer.allocate(data.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        bb.asFloatBuffer().put(data)
        return bb.array()
    }

    private fun bytesToFloats(data: ByteArray): FloatArray {
        require(data.size % Float.SIZE_BYTES == 0) {
            "Invalid byte size for FloatArray: ${data.size}"
        }

        val bb = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        val out = FloatArray(fb.remaining())
        fb.get(out)
        return out
    }

    private fun mmapFloatFile(path: String): FloatBuffer {
        val file = File(path)
        require(file.exists()) { "File not found: $path" }
        require(file.length() % Float.SIZE_BYTES == 0L) {
            "Invalid float file size: $path size=${file.length()}"
        }

        FileInputStream(file).channel.use { channel ->
            val mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            mapped.order(ByteOrder.nativeOrder())
            return mapped.asFloatBuffer()
        }
    }

    private fun loadFloatBinFromFile(path: String): FloatArray {
        val file = File(path)
        require(file.exists()) { "File not found: $path" }
        require(file.length() % Float.SIZE_BYTES == 0L) {
            "Invalid float file size: $path size=${file.length()}"
        }

        val bytes = file.readBytes()
        return bytesToFloats(bytes)
    }

    private fun modelPath(filename: String): String {
        return "${ModelConstants.MODEL_DIR}/$filename"
    }

    private fun commonPath(filename: String): String {
        return "${ModelConstants.COMMON_DIR}/$filename"
    }

    private fun checkRequiredFiles() {
        val files = listOf(
            modelPath(ModelConstants.PREFILL_P1_MODEL),
            modelPath(ModelConstants.PREFILL_P2_MODEL),
            modelPath(ModelConstants.DECODE_MODEL),

            commonPath(ModelConstants.EMBEDDING_BIN),
            commonPath(ModelConstants.LM_HEAD_BIN),
            commonPath(ModelConstants.COS_PREFILL_BIN),
            commonPath(ModelConstants.SIN_PREFILL_BIN),
            commonPath(ModelConstants.COS_DECODE_BIN),
            commonPath(ModelConstants.SIN_DECODE_BIN),

            commonPath(ModelConstants.TOKENIZER_JSON),
            commonPath(ModelConstants.TOKENIZER_CONFIG_JSON),
            commonPath(ModelConstants.VOCAB_JSON)
        )

        for (p in files) {
            val f = File(p)
            require(f.exists()) { "Required file not found: $p" }
            Log.i(TAG, "Found: $p size=${f.length()}")
        }
    }

    interface ExecutorListener {
        fun onToken(tokenId: Int, text: String)
        fun onDone(fullText: String, inferenceTimeMs: Long)
        fun onStopped(fullText: String, inferenceTimeMs: Long) {
            onDone(fullText, inferenceTimeMs)
        }
        fun onError(error: String)
    }

    private class QwenTokenizer(
        tokenizerJsonPath: String,
        tokenizerConfigPath: String,
        vocabJsonPath: String
    ) {
        private val idToToken = HashMap<Int, String>()
        private val byteDecoder = buildByteDecoder()
        private val pendingBytes = ByteArrayOutputStream()
        private val tokenToId = HashMap<String, Int>()

        init {
            val tokenizerFile = File(tokenizerJsonPath)
            val configFile = File(tokenizerConfigPath)
            val vocabFile = File(vocabJsonPath)

            require(tokenizerFile.exists()) { "tokenizer.json not found: $tokenizerJsonPath" }
            require(configFile.exists()) { "tokenizer_config.json not found: $tokenizerConfigPath" }
            require(vocabFile.exists()) { "vocab.json not found: $vocabJsonPath" }

            loadTokenizerJson(tokenizerFile)
            loadVocabJsonIfNeeded(vocabFile)

            Log.i(TAG, "Tokenizer loaded. vocab size=${idToToken.size}")
        }

        fun reset() {
            pendingBytes.reset()
        }

        fun decodeToken(tokenId: Int): String {
            val token = idToToken[tokenId] ?: return "[$tokenId]"

            if (isSpecialToken(token)) {
                return ""
            }

            val bytes = tokenToBytes(token)
            pendingBytes.reset()
            pendingBytes.write(bytes)

            return try {
                String(pendingBytes.toByteArray(), StandardCharsets.UTF_8)
            } catch (e: Exception) {
                ""
            }
        }

        private fun loadTokenizerJson(file: File) {
            val root = JSONObject(file.readText(Charsets.UTF_8))

            if (root.has("model")) {
                val model = root.getJSONObject("model")
                if (model.has("vocab")) {
                    val vocab = model.getJSONObject("vocab")
                    val keys = vocab.keys()
                    while (keys.hasNext()) {
                        val token = keys.next()
                        val id = vocab.getInt(token)
                        idToToken[id] = token
                        tokenToId[token] = id
                    }
                }
            }

            if (root.has("added_tokens")) {
                val added = root.getJSONArray("added_tokens")
                for (i in 0 until added.length()) {
                    val obj = added.getJSONObject(i)
                    val id = obj.optInt("id", -1)
                    val content = obj.optString("content", "")
                    if (id >= 0 && content.isNotEmpty()) {
                        idToToken[id] = content
                        tokenToId[content] = id
                    }
                }
            }
        }

        private fun loadVocabJsonIfNeeded(file: File) {
            if (idToToken.isNotEmpty()) return

            val vocab = JSONObject(file.readText(Charsets.UTF_8))
            val keys = vocab.keys()
            while (keys.hasNext()) {
                val token = keys.next()
                val id = vocab.getInt(token)
                idToToken[id] = token
                tokenToId[token] = id
            }
        }

        private fun isSpecialToken(token: String): Boolean {
            return token.startsWith("<|") && token.endsWith("|>")
        }

        private fun tokenToBytes(token: String): ByteArray {
            val out = ByteArrayOutputStream()

            for (ch in token) {
                val b = byteDecoder[ch.code]
                if (b != null) {
                    out.write(b.toInt() and 0xFF)
                } else {
                    val raw = ch.toString().toByteArray(StandardCharsets.UTF_8)
                    out.write(raw)
                }
            }

            return out.toByteArray()
        }

        private fun buildByteDecoder(): HashMap<Int, Byte> {
            val bs = mutableListOf<Int>()

            for (i in 33..126) bs.add(i)
            for (i in 161..172) bs.add(i)
            for (i in 174..255) bs.add(i)

            val cs = bs.toMutableList()
            var n = 0

            for (b in 0..255) {
                if (!bs.contains(b)) {
                    bs.add(b)
                    cs.add(256 + n)
                    n++
                }
            }

            val decoder = HashMap<Int, Byte>()
            for (i in bs.indices) {
                decoder[cs[i]] = bs[i].toByte()
            }

            return decoder
        }

        fun encodeChatPrompt(
            userText: String,
            enableThinking: Boolean = false
        ): IntArray {
            val text = buildString {
                append("<|im_start|>user\n")
                append(userText)
                append("\n<|im_end|>\n")
                append("<|im_start|>assistant\n")

                if (!enableThinking) {
                    append("<think>\n\n</think>\n\n")
                }
            }

            return encode(text)
        }

        fun encode(text: String): IntArray {
            val ids = ArrayList<Int>()
            var i = 0

            while (i < text.length) {
                val special = matchSpecialToken(text, i)
                if (special != null) {
                    ids.add(special.second)
                    i += special.first.length
                    continue
                }

                val nextSpecialPos = findNextSpecialTokenPos(text, i)
                val normalText = if (nextSpecialPos >= 0) {
                    text.substring(i, nextSpecialPos)
                } else {
                    text.substring(i)
                }

                ids.addAll(encodeNormalText(normalText))
                i += normalText.length
            }

            return ids.toIntArray()
        }

        private fun matchSpecialToken(text: String, pos: Int): Pair<String, Int>? {
            val candidates = listOf(
                "<|im_start|>",
                "<|im_end|>",
                "<|endoftext|>",
                "<think>",
                "</think>"
            )

            for (token in candidates) {
                if (text.startsWith(token, pos)) {
                    val id = tokenToId[token]
                    if (id != null) {
                        return Pair(token, id)
                    }
                }
            }
            return null
        }

        private fun findNextSpecialTokenPos(text: String, start: Int): Int {
            val candidates = listOf(
                "<|im_start|>",
                "<|im_end|>",
                "<|endoftext|>",
                "<think>",
                "</think>"
            )

            var best = -1

            for (token in candidates) {
                val idx = text.indexOf(token, start)
                if (idx >= 0 && (best < 0 || idx < best)) {
                    best = idx
                }
            }
            return best
        }

        private fun encodeNormalText(text: String): List<Int> {
            val result = ArrayList<Int>()

            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            val encodedChars = StringBuilder()

            for (b in bytes) {
                val unsigned = b.toInt() and 0xFF
                encodedChars.append(byteToUnicode(unsigned))
            }

            val encoded = encodedChars.toString()

            var i = 0
            while (i < encoded.length) {
                var bestToken: String? = null
                var bestId: Int? = null
                var bestLen = 0

                val maxLen = encoded.length - i

                for (len in maxLen downTo 1) {
                    val sub = encoded.substring(i, i + len)
                    val id = tokenToId[sub]
                    if (id != null) {
                        bestToken = sub
                        bestId = id
                        bestLen = len
                        break
                    }
                }

                if (bestToken == null || bestId == null) {
                    throw IllegalStateException(
                        "Tokenizer encode failed at index=$i char='${encoded[i]}' text='$text'"
                    )
                }

                result.add(bestId)
                i += bestLen
            }
            return result
        }

        private fun byteToUnicode(byte: Int): Char {
            val bs = mutableListOf<Int>()

            for (i in 33..126) bs.add(i)
            for (i in 161..172) bs.add(i)
            for (i in 174..255) bs.add(i)

            val cs = bs.toMutableList()
            var n = 0

            for (b in 0..255) {
                if (!bs.contains(b)) {
                    bs.add(b)
                    cs.add(256 + n)
                    n++
                }
            }

            val idx = bs.indexOf(byte)
            require(idx >= 0) {
                "byteToUnicode failed: byte=$byte"
            }
            return cs[idx].toChar()
        }
    }

    companion object {
        private const val TAG = "QwenExecutor"
        private const val ROPE_THETA = 1000000.0
        private const val EOS_TOKEN_ID = 151643
        private const val IM_END_TOKEN_ID = 151645
        private const val THINK_START_TOKEN_ID = 151667
        private const val THINK_END_TOKEN_ID = 151668
    }
}
