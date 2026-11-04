package `in`.kavach.live

import `in`.kavach.domain.*
import java.net.URI
import kotlinx.serialization.json.*

object LivePolicy {
    const val MODEL_ID = "muse-spark-1.3"
    const val PROVIDER = "meta-model-api"
    const val FRAME_INTERVAL_MS = 15_000L
    const val MAX_FRAME_AGE_MS = 45_000L
    const val MAX_SESSION_MS = 600_000L
    const val MAX_IMAGE_EDGE = 960
    const val MAX_JPEG_BYTES = 750_000
    const val MAX_RESPONSE_BYTES = 16_384
    fun endpoint(value: String, debug: Boolean): String {
        require(value.length in 1..256) { "Enter the HTTPS address of your own Meta-model server." }
        val uri = URI(value)
        val localDebug = debug && uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1")
        require((uri.scheme == "https" || localDebug) && !uri.host.isNullOrBlank()) { "Use HTTPS. Debug builds also allow localhost HTTP through adb reverse." }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.path in setOf("", "/")) { "Use only a server origin, without a path, embedded credentials or query." }
        require(uri.port == -1 || uri.port in 1..65535)
        return value.trimEnd('/')
    }
    fun token(value: String) { require(value.length in 32..256 && value.all { it.isLetterOrDigit() && it.code < 128 || it in "_-" }) { "Enter a 32–256 character server token using letters, digits, _ or -." } }
}

data class LiveObservation(val assessment: Assessment?, val visibleText: String, val reason: String?, val provider: String) {
    val warning get() = assessment?.risk in setOf(Risk.some_indicators, Risk.strong_indicators)
    val summary get() = when {
        warning -> "Possible coercion in visible text. Pause and verify."
        assessment?.risk == Risk.insufficient_evidence -> "Not enough context in visible text. Call audio is unassessed."
        assessment != null -> "No warning text identified in this image. Call audio is unassessed."
        reason == "invalid_model_output" -> "Unable to assess: Muse Spark returned no valid assessment. Call audio is unassessed."
        reason == "unsupported_language" -> "Unable to assess: live vision currently supports English only."
        else -> "Not enough readable text to assess. Call audio is unassessed."
    }
}

/** This validates consistency with MODEL-READ text, not OCR accuracy or facts about the caller. */
object LiveResponseParser {
    fun parse(raw: String, expectedSession: String, expectedFrame: Long): LiveObservation {
        require(raw.toByteArray(Charsets.UTF_8).size <= LivePolicy.MAX_RESPONSE_BYTES)
        val root = Json.parseToJsonElement(raw).jsonObject
        rejectDuplicateKeys(raw)
        root.keysExactly("schema_version", "session_id", "frame_id", "model_id", "model_provider", "status", "reason", "visible_text", "assessment")
        require(root["schema_version"] == JsonPrimitive(1))
        require(root.string("session_id", 64) == expectedSession)
        require(root["frame_id"] == JsonPrimitive(expectedFrame))
        require(root.string("model_id", 100) == LivePolicy.MODEL_ID) { "The server did not report the supported Meta model." }
        val provider = root.string("model_provider", 40)
        require(provider == LivePolicy.PROVIDER)
        val visible = root["visible_text"] as? JsonPrimitive ?: error("Missing visible text.")
        require(visible.isString && visible.content.length <= 4000)
        val text = visible.content
        val status = root.string("status", 32)
        if (status == "unable_to_assess") {
            val reason = root.string("reason", 40)
            require(reason in setOf("no_readable_text", "unsupported_language", "insufficient_visual_evidence", "invalid_model_output"))
            require(root["assessment"] == JsonNull && text.isEmpty())
            return LiveObservation(null, "", reason, provider)
        }
        require(status == "assessed" && root["reason"] == JsonNull)
        InputPolicy.validate(text)
        require(text.none { it in '\u0900'..'\u097f' }) { "Hindi image input is not supported by this live profile." }
        val assessment = AssessmentParser().parse(root["assessment"].toString(), text)
        return LiveObservation(assessment, text, null, provider)
    }
}

/** Session and freshness checks are independent of the model and transport. */
class LiveSessionGate(private val maxAgeMs: Long = LivePolicy.MAX_FRAME_AGE_MS) {
    private var session: String? = null
    private var startedAt = 0L
    private var lastFrame = 0L
    @Synchronized fun start(id: String, now: Long) { session = id; startedAt = now; lastFrame = 0 }
    @Synchronized fun stop() { session = null; lastFrame = 0 }
    @Synchronized fun active(id: String, now: Long): Boolean = session == id && now >= startedAt && now - startedAt < LivePolicy.MAX_SESSION_MS
    @Synchronized fun accept(id: String, frame: Long, capturedAt: Long, now: Long): Boolean {
        if (!active(id, now) || frame <= lastFrame || capturedAt < startedAt || capturedAt > now || now - capturedAt > maxAgeMs) return false
        lastFrame = frame
        return true
    }
}
