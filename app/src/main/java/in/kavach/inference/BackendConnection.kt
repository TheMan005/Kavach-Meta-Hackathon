package `in`.kavach.inference

import `in`.kavach.BuildConfig
import `in`.kavach.live.LivePolicy
import `in`.kavach.domain.rejectDuplicateKeys
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

object BackendInput {
    fun endpoint(raw: String): String = LivePolicy.endpoint(raw.trim(), BuildConfig.DEBUG)
    fun token(raw: String): String {
        var value = raw.trim().removePrefix("export ").trim()
        require(!value.startsWith("META_API_KEY=") && !value.startsWith("MODEL_API_KEY=") && !value.startsWith("LLM_")) {
            "Use KAVACH_LIVE_TOKEN from .env, not the Meta API key."
        }
        if (value.startsWith("KAVACH_LIVE_TOKEN=")) value = value.substringAfter('=').trim()
        if (value.length >= 2 && ((value.first() == '\'' && value.last() == '\'') || (value.first() == '"' && value.last() == '"'))) value = value.substring(1, value.lastIndex).trim()
        require(!value.startsWith("LLM_")) { "Use KAVACH_LIVE_TOKEN, not the Meta API key." }
        require(value != "REPLACE_WITH_RANDOM_TOKEN") { "Replace KAVACH_LIVE_TOKEN in .env with a generated server token first." }
        LivePolicy.token(value)
        return value
    }
}
object BackendConnection {
    fun validateHealth(raw: String) {
        require(raw.toByteArray().size <= 4096)
        rejectDuplicateKeys(raw)
        val obj = Json.parseToJsonElement(raw).jsonObject
        require(obj["schema_version"] == JsonPrimitive(1) && obj["configured"] == JsonPrimitive(true))
        require(obj["model_id"] == JsonPrimitive(LivePolicy.MODEL_ID) && obj["model_provider"] == JsonPrimitive(LivePolicy.PROVIDER))
    }
    suspend fun verify(config: BackendConfig) = withContext(Dispatchers.IO) {
        val conn = URL("${config.endpoint}/health").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"; conn.instanceFollowRedirects = false; conn.useCaches = false
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            conn.setRequestProperty("Authorization", "Bearer ${config.token}")
            conn.setRequestProperty("Accept", "application/json")
            when (conn.responseCode) {
                200 -> Unit
                401 -> error("Token rejected by server. Copy KAVACH_LIVE_TOKEN and restart the backend after changing .env.")
                else -> error("Backend returned HTTP ${conn.responseCode}. Check the server address and version.")
            }
            require(conn.contentType?.substringBefore(';') == "application/json") { "This address did not return a Kavach backend response." }
            val raw = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val bytes = ByteArray(1024)
                while (true) {
                    val count = input.read(bytes); if (count < 0) break
                    require(out.size() + count <= 4096) { "Unexpected backend response." }
                    out.write(bytes, 0, count)
                }
                out.toString("UTF-8")
            }
            try { validateHealth(raw) } catch (_: Exception) { error("Backend version not recognized. Restart the latest Kavach server.") }
            ensureActive()
        } catch (e: java.io.IOException) {
            error("Cannot reach backend. Start the server and check the address. For USB, run adb reverse tcp:8765 tcp:8765.")
        } finally { conn.disconnect() }
    }
}
