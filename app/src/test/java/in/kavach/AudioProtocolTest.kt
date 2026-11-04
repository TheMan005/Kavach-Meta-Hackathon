package `in`.kavach

import `in`.kavach.live.*
import org.junit.Assert.*
import org.junit.Test

class AudioProtocolTest {
    private val raw = """{"schema_version":1,"session_id":"session","chunk_id":1,"asr_model":"muse-voice-transcribe-1.0","model_id":"muse-spark-1.3","model_provider":"meta-model-api","status":"assessed","reason":null,"transcript":"Send money now","assessment":{"schema_version":1,"status":"assessed","risk":"some_indicators","suspected_pattern":"payment_fraud","evidence":[{"message_id":"m1","quote":"Send money now","indicator":"urgent_transfer"}],"limitations":[]}}"""
    private fun reject(value: String) { assertTrue(runCatching { AudioResponseParser.parse(value, "session", 1) }.isFailure) }
    @Test fun parsesSpeechEvidence() { assertTrue(AudioResponseParser.parse(raw, "session", 1).warning) }
    @Test fun rejectsWrongModelsAndProvider() {
        reject(raw.replace(AudioPolicy.ASR_MODEL, "other-asr")); reject(raw.replace(LivePolicy.MODEL_ID, "other-model")); reject(raw.replace(LivePolicy.PROVIDER, "other-provider"))
    }
    @Test fun bindsSessionAndChunk() { reject(raw.replace("session\"", "other\"")); reject(raw.replace("\"chunk_id\":1", "\"chunk_id\":2")) }
    @Test fun rejectsFabricatedSpeechEvidence() { reject(raw.replace("\"transcript\":\"Send money now\"", "\"transcript\":\"Hello\"")) }
    @Test fun rejectsDuplicateKeys() { reject(raw.replace("\"schema_version\":1,\"session_id\"", "\"schema_version\":1,\"schema_version\":1,\"session_id\"")) }
    @Test fun silenceIsNotSafe() {
        assertTrue(AudioPolicy.silent(ShortArray(AudioPolicy.SAMPLES)))
        assertFalse(AudioPolicy.silent(ShortArray(AudioPolicy.SAMPLES) { 2000 }))
        val result = AudioObservation("", null, "silent_audio")
        assertFalse(result.warning); assertTrue(result.summary.contains("blocking"))
    }
    @Test fun pcmIsLittleEndianAndBounded() {
        val samples = ShortArray(AudioPolicy.SAMPLES); samples[0] = 0x1234; samples[1] = -2
        val pcm = AudioPolicy.pcm(samples)
        assertArrayEquals(byteArrayOf(0x34, 0x12, 0xfe.toByte(), 0xff.toByte()), pcm.copyOfRange(0, 4))
        assertTrue(runCatching { AudioPolicy.pcm(ShortArray(3)) }.isFailure)
    }
    @Test fun stopAndAgeSuppressAudioResults() {
        val gate = LiveSessionGate(AudioPolicy.MAX_AGE_MS); gate.start("one", 0)
        assertFalse(gate.accept("one", 1, 0, AudioPolicy.MAX_AGE_MS + 1))
        gate.stop(); assertFalse(gate.accept("one", 1, 0, 10))
    }
}
