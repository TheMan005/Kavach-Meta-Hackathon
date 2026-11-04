package `in`.kavach.live

import `in`.kavach.domain.*
import kotlinx.serialization.json.*
import kotlin.math.sqrt

object AudioPolicy {
    const val ASR_MODEL = "muse-voice-transcribe-1.0"
    const val SAMPLE_RATE = 24_000
    const val SAMPLES = 240_000
    const val MAX_AGE_MS = 90_000L
    fun silent(samples: ShortArray): Boolean {
        require(samples.isNotEmpty())
        return sqrt(samples.sumOf { it.toDouble() * it } / samples.size) < 80
    }
    fun pcm(samples: ShortArray): ByteArray {
        require(samples.size == SAMPLES)
        return ByteArray(samples.size * 2).also { bytes -> samples.forEachIndexed { i, sample ->
            bytes[i * 2] = sample.toByte(); bytes[i * 2 + 1] = (sample.toInt() shr 8).toByte()
        } }
    }
}
data class AudioObservation(val transcript: String, val assessment: Assessment?, val reason: String?) {
    val warning get() = assessment?.risk in setOf(Risk.some_indicators, Risk.strong_indicators)
    val summary get() = when {
        warning -> "Possible coercion in microphone speech. Pause and verify."
        assessment != null -> "No clear warning established in this clip. This does not mean the call is safe."
        reason == "silent_audio" -> "Microphone audio is silent or too quiet. The call may be blocking capture."
        reason == "no_speech" -> "No speech was transcribed. The call is unassessed."
        reason == "unsupported_language" -> "This audio prototype assesses English speech only."
        else -> "Unable to assess this audio. No safety conclusion is available."
    }
}
object AudioResponseParser {
    fun parse(raw: String, session: String, chunk: Long): AudioObservation {
        require(raw.toByteArray().size <= LivePolicy.MAX_RESPONSE_BYTES)
        rejectDuplicateKeys(raw)
        val root = Json.parseToJsonElement(raw).jsonObject
        root.keysExactly("schema_version", "session_id", "chunk_id", "asr_model", "model_id", "model_provider", "status", "reason", "transcript", "assessment")
        require(root["schema_version"] == JsonPrimitive(1) && root["chunk_id"] == JsonPrimitive(chunk))
        require(root.string("session_id", 64) == session)
        require(root.string("asr_model", 100) == AudioPolicy.ASR_MODEL)
        require(root.string("model_id", 100) == LivePolicy.MODEL_ID)
        require(root.string("model_provider", 40) == LivePolicy.PROVIDER)
        val text = root["transcript"] as? JsonPrimitive ?: error("Missing transcript")
        require(text.isString && text.content.length <= 4000)
        if (root.string("status", 32) == "unable_to_assess") {
            val reason = root.string("reason", 40)
            require(reason in setOf("silent_audio", "no_speech", "unsupported_language", "invalid_model_output"))
            require(text.content.isEmpty() && root["assessment"] == JsonNull)
            return AudioObservation("", null, reason)
        }
        require(root["status"] == JsonPrimitive("assessed") && root["reason"] == JsonNull)
        InputPolicy.validate(text.content)
        require(text.content.none { it in '\u0900'..'\u097f' })
        return AudioObservation(text.content, AssessmentParser().parse(root["assessment"].toString(), text.content), null)
    }
}
