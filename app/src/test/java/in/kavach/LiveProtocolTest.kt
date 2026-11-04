package `in`.kavach

import `in`.kavach.live.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LiveProtocolTest {
    private fun reject(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }
    private val session = "abc"
    private fun response(model: String = LivePolicy.MODEL_ID, frame: Long = 1, text: String = "Send money now") = buildJsonObject {
        put("schema_version", 1); put("session_id", session); put("frame_id", frame)
        put("model_id", model); put("model_provider", LivePolicy.PROVIDER); put("status", "assessed")
        put("reason", JsonNull); put("visible_text", text)
        put("assessment", Json.parseToJsonElement("""{"schema_version":1,"status":"assessed","risk":"some_indicators","suspected_pattern":"payment_fraud","evidence":[{"message_id":"m1","quote":"Send money now","indicator":"urgent_transfer"}],"limitations":[]}"""))
    }.toString()
    @Test fun acceptsOnlyMetaProfile() { assertTrue(LiveResponseParser.parse(response(), session, 1).warning) }
    @Test fun rejectsOtherModel() = reject { LiveResponseParser.parse(response("other/vendor"), session, 1) }
    @Test fun rejectsLegacyLlamaServer() = reject { LiveResponseParser.parse(response("meta-llama/Llama-3.2-11B-Vision-Instruct"), session, 1) }
    @Test fun rejectsWrongProvider() = reject { LiveResponseParser.parse(response().replace(LivePolicy.PROVIDER, "other-provider"), session, 1) }
    @Test fun rejectsWrongSession() = reject { LiveResponseParser.parse(response(), "new-session", 1) }
    @Test fun rejectsWrongFrame() = reject { LiveResponseParser.parse(response(frame = 2), session, 1) }
    @Test fun evidenceMustMatchModelReadText() = reject { LiveResponseParser.parse(response(text = "Hello"), session, 1) }
    @Test fun rejectsHindiVisionAssessment() = reject { LiveResponseParser.parse(response(text = "पैसे भेजो Send money now"), session, 1) }
    @Test fun rejectsDuplicateProvenance() = reject { LiveResponseParser.parse(response().replace("\"schema_version\":1,\"session_id\"", "\"schema_version\":1,\"schema_version\":1,\"session_id\""), session, 1) }
    @Test fun noReadableTextIsNotSafe() {
        val root = Json.parseToJsonElement(response()).jsonObject.toMutableMap()
        root["status"] = JsonPrimitive("unable_to_assess"); root["reason"] = JsonPrimitive("no_readable_text")
        root["visible_text"] = JsonPrimitive(""); root["assessment"] = JsonNull
        val result = LiveResponseParser.parse(JsonObject(root).toString(), session, 1)
        assertNull(result.assessment); assertFalse(result.warning)
        assertTrue(result.summary.contains("Not enough"))
    }
    @Test fun noHttpAwayFromLoopback() = reject { LivePolicy.endpoint("http://192.168.1.2:8765", true) }
    @Test fun debugLoopbackAllowed() { assertEquals("http://127.0.0.1:8765", LivePolicy.endpoint("http://127.0.0.1:8765/", true)) }
    @Test fun releaseRequiresHttps() = reject { LivePolicy.endpoint("http://127.0.0.1:8765", false) }
    @Test fun credentialsAndQueriesRejected() {
        reject { LivePolicy.endpoint("https://user:pass@example.com", true) }
        reject { LivePolicy.endpoint("https://example.com?token=secret", true) }
        reject { LivePolicy.endpoint("https://example.com/path", true) }
    }
    @Test fun tokenCannotInjectHeaders() = reject { LivePolicy.token("a".repeat(40) + "\r\nHeader: x") }
    @Test fun stoppedSessionRejectsLateResult() {
        val gate = LiveSessionGate(); gate.start("one", 100); gate.stop()
        assertFalse(gate.accept("one", 1, 101, 102))
    }
    @Test fun supersededSessionAndOutOfOrderFramesRejected() {
        val gate = LiveSessionGate(); gate.start("one", 100); gate.start("two", 200)
        assertFalse(gate.accept("one", 1, 201, 202))
        assertTrue(gate.accept("two", 2, 201, 202))
        assertFalse(gate.accept("two", 1, 201, 203))
    }
    @Test fun staleOrFutureFrameRejected() {
        val gate = LiveSessionGate(); gate.start("one", 0)
        assertFalse(gate.accept("one", 1, 1, LivePolicy.MAX_FRAME_AGE_MS + 2))
        assertFalse(gate.accept("one", 1, 11, 10))
    }
    @Test fun sessionAutomaticallyExpires() {
        val gate = LiveSessionGate(); gate.start("one", 0)
        assertFalse(gate.active("one", LivePolicy.MAX_SESSION_MS))
    }
}
