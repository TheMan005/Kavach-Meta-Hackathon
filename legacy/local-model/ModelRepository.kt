package `in`.kavach.models

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import `in`.kavach.domain.string
import `in`.kavach.domain.InferenceUnavailable

const val RUNTIME_VERSION = "1.5.1"
data class ModelFiles(val dir: File, val manifest: JsonObject) {
    val model get() = File(dir, "model.pte")
    val tokenizer get() = File(dir, "tokenizer.model")
    val id get() = manifest.string("model_id", 100)
    val version get() = manifest.string("model_version", 100)
    val context get() = manifest["context_length"]!!.jsonPrimitive.int
}

class ModelRepository(private val context: Context) {
    private val root = File(context.noBackupFilesDir, "models").apply { mkdirs() }
    private val pointer = AtomicFile(File(root, "active"))
    private val mutex = Mutex()
    private fun active(): ModelFiles? {
        if (!pointer.baseFile.exists()) return null
        val name = pointer.openRead().use { it.readBytes().toString(Charsets.UTF_8) }
        require(name.matches(Regex("[a-f0-9-]{36}")))
        val dir = File(root, name)
        return ModelFiles(dir, Json.parseToJsonElement(File(dir, "manifest.json").readText()).jsonObject)
    }
    suspend fun status(): String = withContext(Dispatchers.IO) {
        mutex.withLock { runCatching { active()?.let { "Imported • ${it.id} • runtime load required" } ?: "Model not installed" }.getOrDefault("Model files need repair; import again") }
    }
    suspend fun <T> withModel(block: (ModelFiles) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val files = active() ?: throw InferenceUnavailable("Model not installed. Import a compatible model and tokenizer in Model setup.")
            verify(files)
            block(files)
        }
    }
    suspend fun delete() = withContext(Dispatchers.IO) {
        mutex.withLock { pointer.delete(); root.listFiles()?.forEach { it.deleteRecursively() }; Unit }
    }
    suspend fun importFiles(manifestUri: Uri, modelUri: Uri, tokenizerUri: Uri, probe: (ModelFiles) -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val stage = File(root, UUID.randomUUID().toString()).apply { mkdir() }
            var committed = false
            try {
                copy(manifestUri, File(stage, "manifest.json"), 16384)
                val manifest = Json.parseToJsonElement(File(stage, "manifest.json").readText()).jsonObject
                validateManifest(manifest)
                val modelSize = manifest["model_size"]!!.jsonPrimitive.long
                val tokenizerSize = manifest["tokenizer_size"]!!.jsonPrimitive.long
                require(root.usableSpace > modelSize + tokenizerSize + 128 * 1024 * 1024) { "Not enough free storage. Keep space for the current model and the replacement." }
                copy(modelUri, File(stage, "model.pte"), modelSize)
                copy(tokenizerUri, File(stage, "tokenizer.model"), tokenizerSize)
                val files = ModelFiles(stage, manifest)
                verify(files)
                probe(files) // Only activate a pair the pinned runtime can initialize.
                val output = pointer.startWrite()
                try { output.write(stage.name.toByteArray()); pointer.finishWrite(output); committed = true }
                catch (e: Exception) { pointer.failWrite(output); throw e }
                root.listFiles()?.filter { it.isDirectory && it != stage }?.forEach { it.deleteRecursively() }
            } catch (e: Throwable) {
                if (!committed) stage.deleteRecursively()
                throw e
            }
        }
    }
    private fun validateManifest(m: JsonObject) {
        require(m["schema_version"] == JsonPrimitive(1))
        require(m.string("family", 32) == "llama3.2")
        require(m.string("model_id", 100) == "Llama-3.2-1B-Instruct") { "Only the 1B Instruct profile is supported in this prototype." }
        m.string("model_version", 100)
        require(m.string("runtime", 32) == RUNTIME_VERSION) { "Export for ExecuTorch $RUNTIME_VERSION." }
        require(m.string("tokenizer_format", 32) == "llama3-tiktoken")
        require(m.string("export_recipe", 100) == "xnnpack-qlora-int4-eo8")
        m.string("export_commit", 64)
        require(m["context_length"]!!.jsonPrimitive.int in 4096..8192) { "Export with a 4096–8192 token context." }
        require(m["model_size"]!!.jsonPrimitive.long in 1..3_000_000_000L)
        require(m["tokenizer_size"]!!.jsonPrimitive.long in 1..30_000_000L)
        for (key in listOf("model_sha256", "tokenizer_sha256")) require(m.string(key, 64).matches(Regex("[a-f0-9]{64}")))
    }
    private fun verify(files: ModelFiles) {
        validateManifest(files.manifest)
        for ((file, prefix) in listOf(files.model to "model", files.tokenizer to "tokenizer")) {
            require(file.length() == files.manifest["${prefix}_size"]!!.jsonPrimitive.long) { "Model file size mismatch. Import the complete artifacts again." }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { stream ->
                val buffer = ByteArray(1024 * 1024)
                while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            require(hash == files.manifest.string("${prefix}_sha256", 64)) { "Model checksum mismatch. Import the original pair again." }
        }
    }
    private fun copy(uri: Uri, target: File, cap: Long) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().buffered().use { out ->
                val bytes = ByteArray(1024 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(bytes); if (n < 0) break
                    total += n
                    require(total <= cap) { "Selected file exceeds the expected size." }
                    out.write(bytes, 0, n)
                }
            }
        } ?: error("Cannot read the selected file. Select it again from local storage.")
    }
}
