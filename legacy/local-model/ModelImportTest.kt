package `in`.kavach

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import `in`.kavach.models.ModelRepository
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelImportTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun files(runtime: String = "1.5.1", wrongHash: Boolean = false): List<Uri> {
        val folder = File(context.cacheDir, "fixture-${System.nanoTime()}").apply { mkdirs() }
        val model = File(folder, "model.pte").apply { writeText("synthetic model bytes for import tests only") }
        val tokenizer = File(folder, "tokenizer.model").apply { writeText("synthetic tokenizer bytes") }
        val manifest = buildJsonObject {
            put("schema_version", 1); put("family", "llama3.2"); put("model_id", "Llama-3.2-1B-Instruct")
            put("model_version", "test"); put("runtime", runtime); put("tokenizer_format", "llama3-tiktoken")
            put("export_recipe", "xnnpack-qlora-int4-eo8"); put("export_commit", "test"); put("context_length", 4096)
            put("model_size", model.length()); put("tokenizer_size", tokenizer.length())
            put("model_sha256", if (wrongHash) "0".repeat(64) else hash(model.readBytes()))
            put("tokenizer_sha256", hash(tokenizer.readBytes()))
        }
        val metadata = File(folder, "manifest.json").apply { writeText(manifest.toString()) }
        return listOf(metadata, model, tokenizer).map(Uri::fromFile)
    }
    @Test fun incompatibleRuntimeRejectedBeforeNativeProbe() = runTest {
        val models = ModelRepository(context); models.delete(); val f = files(runtime = "0.0.0")
        var called = false
        assertTrue(runCatching { models.importFiles(f[0], f[1], f[2]) { called = true } }.isFailure)
        assertFalse(called); assertEquals("Model not installed", models.status())
    }
    @Test fun checksumMismatchRejectedBeforeNativeProbe() = runTest {
        val models = ModelRepository(context); models.delete(); val f = files(wrongHash = true)
        var called = false
        assertTrue(runCatching { models.importFiles(f[0], f[1], f[2]) { called = true } }.isFailure)
        assertFalse(called)
    }
    @Test fun failedInitializationPreservesPreviousAtomicSelection() = runTest {
        val models = ModelRepository(context); models.delete(); val first = files()
        // Only the native probe is replaced: this test exercises real file copying, hashes and pointer switching.
        models.importFiles(first[0], first[1], first[2]) { }
        val original = models.withModel { it.dir.name }
        val replacement = files()
        assertTrue(runCatching { models.importFiles(replacement[0], replacement[1], replacement[2]) { error("Simulated native initialization failure") } }.isFailure)
        assertEquals(original, models.withModel { it.dir.name })
        models.delete()
        assertEquals("Model not installed", models.status())
    }
}
