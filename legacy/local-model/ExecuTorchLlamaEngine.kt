package `in`.kavach.inference

import android.os.SystemClock
import `in`.kavach.BuildConfig
import `in`.kavach.domain.*
import `in`.kavach.models.*
import org.pytorch.executorch.extension.llm.*
import java.util.concurrent.atomic.AtomicLong

class ExecuTorchLlamaEngine(private val models: ModelRepository) : InferenceEngine {
    @Volatile private var current: LlmModule? = null
    private val generation = AtomicLong()
    override fun stop() { generation.incrementAndGet(); current?.stop() }
    override suspend fun assess(text: String, sender: Sender, onLoaded: () -> Unit): EngineOutput {
        if (!BuildConfig.NATIVE_PRIVACY_REVIEWED) throw InferenceUnavailable("Real-text inference is locked until the private runtime is installed. See Model setup; synthetic samples are available.")
        val request = generation.get()
        val prompt = Prompt.build(text, sender)
        val started = SystemClock.elapsedRealtime()
        return models.withModel { files ->
            check(request == generation.get()) { "Cancelled." }
            Prompt.enforceBudget(prompt, files.context)
            LlmModule(files.model.path, files.tokenizer.path, 0.0f).use { module ->
                current = module
                try {
                    module.load() // 1.5.1 throws ExecutorchRuntimeException on failure.
                    check(request == generation.get()) { "Cancelled." }
                    onLoaded()
                    val raw = StringBuilder()
                    var failed = false
                    var generatedTokens = 0
                    val config = LlmGenerationConfig.create().echo(false).temperature(0.0f)
                        .seqLen(files.context).maxNewTokens(Prompt.OUTPUT_TOKENS).build()
                    module.generate(prompt, config, object : LlmCallback {
                        override fun onResult(result: String) {
                            if (++generatedTokens > Prompt.OUTPUT_TOKENS || request != generation.get() || raw.length + result.length > 12000) {
                                failed = true; module.stop()
                            } else raw.append(result)
                        }
                        override fun onError(errorCode: Int, message: String) { failed = true }
                    })
                    check(!failed && request == generation.get()) { "Generation stopped or failed. Try a shorter excerpt." }
                    EngineOutput(raw.toString(), files.id, files.version, SystemClock.elapsedRealtime() - started)
                } finally { current = null }
            }
        }
    }
    companion object {
        fun probe(files: ModelFiles) {
            LlmModule(files.model.path, files.tokenizer.path, 0.0f).use {
                it.load()
            }
        }
    }
}
