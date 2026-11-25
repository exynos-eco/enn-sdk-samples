package com.samsung.textgeneration

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.samsung.textgeneration.databinding.ActivityMainBinding
import com.samsung.textgeneration.executor.ModelExecutor
import androidx.appcompat.app.AlertDialog
import com.samsung.textgeneration.data.ModelConstants
import java.io.File
import java.util.concurrent.Executors
import android.util.Log
import android.view.View

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var executor: ModelExecutor? = null
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var destroyed = false
    @Volatile private var stopRequested = false
    private var running = false

    private fun resetButton() {
        running = false
        binding.btnStart.text = "START"
        binding.btnStart.isEnabled = true
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        binding.btnStart.setOnClickListener {
            if (running) {
                stopRequested = true
                binding.btnStart.text = "STOPPING..."
                binding.btnStart.isEnabled = false
                return@setOnClickListener
            }
            val invalid = findInvalidFiles()
            if (invalid.isNotEmpty()) {
                showMissingFiles(invalid)
                return@setOnClickListener
            }

            val prompt = binding.etPrompt.text.toString()
            stopRequested = false
            running = true
            binding.btnStart.text = "STOP"
            binding.btnStart.isEnabled = true
            binding.tvOutput.text = ""

            worker.execute {
                if (destroyed) return@execute
                try {
                    val active = executor ?: createExecutor().also { executor = it }
                    active.generate(prompt) { stopRequested || destroyed }
                } catch (e: Exception) {
                    Log.e("QwenApp", "Model initialization or inference failed", e)
                    runOnUiThread {
                        if (!destroyed) {
                            resetButton()
                            val missing = findInvalidFiles()
                            if (missing.isNotEmpty()) showMissingFiles(missing)
                            else AlertDialog.Builder(this)
                                .setTitle("Model initialization failed")
                                .setMessage("Please verify the model files and common package, and follow the README instructions.\n\n${e.message ?: "Unknown error"}")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }
            }
        }
    }

    private fun findInvalidFiles(): List<File> {
        val models = listOf(ModelConstants.PREFILL_P1_MODEL, ModelConstants.PREFILL_P2_MODEL, ModelConstants.DECODE_MODEL)
        val common = listOf(
            ModelConstants.EMBEDDING_BIN, ModelConstants.LM_HEAD_BIN,
            ModelConstants.COS_PREFILL_BIN, ModelConstants.SIN_PREFILL_BIN,
            ModelConstants.COS_DECODE_BIN, ModelConstants.SIN_DECODE_BIN,
            ModelConstants.TOKENIZER_JSON, ModelConstants.TOKENIZER_CONFIG_JSON, ModelConstants.VOCAB_JSON
        )
        return (models.map { File(ModelConstants.MODEL_DIR, it) } +
            common.map { File(ModelConstants.COMMON_DIR, it) }).filter { file ->
            try { !file.isFile || !file.canRead() || file.length() <= 0L }
            catch (_: SecurityException) { true }
        }
    }

    private fun showMissingFiles(files: List<File>) {
        val names = files.joinToString("\n") { "• ${it.parentFile?.name}/${it.name}" }
        AlertDialog.Builder(this)
            .setTitle("Required files missing or invalid")
            .setMessage("Please download the Qwen3-0.6B FP16 model files and common package from the Samsung Semiconductor Developer Portal and follow the Getting Started instructions in the README to transfer the files to the device.\n\nMissing or invalid files (${files.size}):\n$names")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun createExecutor(): ModelExecutor = ModelExecutor(
            context = this,
            listener = object : ModelExecutor.ExecutorListener {
                override fun onToken(tokenId: Int, text: String) {
                    runOnUiThread {
                        if (destroyed) return@runOnUiThread
                        binding.tvOutput.append(text)

                        binding.scrollOutput.post {
                            binding.scrollOutput.fullScroll(View.FOCUS_DOWN)
                        }
                    }
                }

                override fun onDone(fullText: String, inferenceTimeMs: Long) {
                    runOnUiThread {
                        if (destroyed) return@runOnUiThread
                        resetButton()
                        binding.tvOutput.append("\n\n[DONE] ${inferenceTimeMs} ms")

                        binding.scrollOutput.post {
                            binding.scrollOutput.fullScroll(View.FOCUS_DOWN)
                        }
                    }
                }

                override fun onStopped(fullText: String, inferenceTimeMs: Long) {
                    runOnUiThread {
                        if (destroyed) return@runOnUiThread
                        resetButton()
                        binding.tvOutput.append("\n\n[STOPPED] ${inferenceTimeMs} ms")
                        binding.scrollOutput.post {
                            binding.scrollOutput.fullScroll(View.FOCUS_DOWN)
                        }
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        if (destroyed) return@runOnUiThread
                        resetButton()
                        binding.tvOutput.text = error
                    }
                }
            }
        )

    override fun onDestroy() {
        destroyed = true
        stopRequested = true
        // Serialize cleanup with inference; never release native buffers mid-run.
        worker.execute {
            try { executor?.close() }
            catch (e: Exception) { Log.e("QwenApp", "Cleanup failed", e) }
            finally { executor = null }
        }
        worker.shutdown()
        super.onDestroy()
    }
}
