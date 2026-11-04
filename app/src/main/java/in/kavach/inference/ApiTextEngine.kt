package `in`.kavach.inference

import `in`.kavach.BuildConfig
import `in`.kavach.domain.*
import `in`.kavach.live.LivePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*

data class BackendConfig(val endpoint: String, val token: String)
object BackendSettings {
    private val mutable = MutableStateFlow<BackendConfig?>(null)
    val state = mutable.asStateFlow()
    fun save(endpoint: String, token: String) {
        val origin = BackendInput.endpoint(endpoint)
        val normalized = BackendInput.token(token)
        mutable.value = BackendConfig(origin, normalized)
    }
    fun clear() { mutable.value = null }
}
object TextResponseParser {
    fun parse(raw: String, requestId: String, text: String): String {
        require(raw.toByteArray(Charsets.UTF_8).size <= LivePolicy.MAX_RESPONSE_BYTES)
        rejectDuplicateKeys(raw)
        val root = Json.parseToJsonElement(raw).jsonObject
        root.keysExactly("schema_version", "request_id", "model_id", "model_provider", "assessment")
        require(root["schema_version"] == JsonPrimitive(1))
        require(root.string("request_id", 36) == requestId)
        require(root.string("model_id", 100) == LivePolicy.MODEL_ID)
        require(root.string("model_provider", 40) == LivePolicy.PROVIDER)
        val assessment = root["assessment"].toString()
        AssessmentParser().parse(assessment, text)
        return assessment
    }
}
class ApiTextEngine : InferenceEngine {
    private val generation = AtomicLong()
    @Volatile private var current: HttpURLConnection? = null
    override fun stop() { generation.incrementAndGet(); current?.disconnect() }
    override suspend fun assess(text: String, sender: Sender, onLoaded: () -> Unit): EngineOutput {
        InputPolicy.validate(text)
        if (!BuildConfig.LIVE_FEATURE) throw InferenceUnavailable("Install Kavach Live for API checks. This sample-only build runs no AI models.")
        val config = BackendSettings.state.value ?: throw InferenceUnavailable("Open Backend and enter your backend address and server token first.")
        val id = generation.get()
        val requestId = UUID.randomUUID().toString()
        val started = System.nanoTime()
        return withContext(Dispatchers.IO) {
            ensureActive()
            val body = buildJsonObject {
                put("schema_version", 1); put("request_id", requestId); put("text", text); put("sender", sender.name)
            }.toString().toByteArray(Charsets.UTF_8)
            val conn = URL("${config.endpoint}/v1/text").openConnection() as HttpURLConnection
            current = conn
            try {
                check(id == generation.get())
                conn.instanceFollowRedirects = false; conn.connectTimeout = 5_000; conn.readTimeout = 35_000
                conn.requestMethod = "POST"; conn.doOutput = true; conn.useCaches = false
                conn.setRequestProperty("Authorization", "Bearer ${config.token}")
                conn.setRequestProperty("Content-Type", "application/json"); conn.setRequestProperty("Accept", "application/json")
                conn.setFixedLengthStreamingMode(body.size)
                onLoaded()
                conn.outputStream.use { it.write(body) }
                if (conn.responseCode != 200) throw InferenceUnavailable(when (conn.responseCode) {
                    401 -> "Backend rejected the server token. Check Backend settings."
                    503 -> "Backend busy or Meta API unavailable. Try again after stopping live capture; check API access on the server."
                    else -> "Backend could not assess this text. Check the server version and setup. No risk conclusion was produced."
                })
                require(conn.contentType?.substringBefore(';') == "application/json")
                val response = conn.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        require(out.size() + n <= LivePolicy.MAX_RESPONSE_BYTES)
                        out.write(buffer, 0, n)
                    }
                    out.toString("UTF-8")
                }
                ensureActive(); check(id == generation.get())
                EngineOutput(TextResponseParser.parse(response, requestId, text), LivePolicy.MODEL_ID, "Meta Model API", (System.nanoTime() - started) / 1_000_000)
            } finally {
                body.fill(0); conn.disconnect()
                if (current === conn) current = null
            }
        }
    }
}
