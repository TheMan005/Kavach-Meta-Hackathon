package `in`.kavach

import `in`.kavach.inference.TextResponseParser
import org.junit.Assert.*
import org.junit.Test

class TextApiTest {
    private val id = "11111111-1111-4111-8111-111111111111"
    private val raw = """{"schema_version":1,"request_id":"$id","model_id":"muse-spark-1.3","model_provider":"meta-model-api","assessment":{"schema_version":1,"status":"assessed","risk":"some_indicators","suspected_pattern":"payment_fraud","evidence":[{"message_id":"m1","quote":"पैसे भेजो","indicator":"urgent_transfer"}],"limitations":[]}}"""
    @Test fun exactHindiEvidenceIsSupported() { assertTrue(TextResponseParser.parse(raw, id, "पैसे भेजो अभी").contains("पैसे भेजो")) }
    @Test fun fabricatedEvidenceRejected() { assertTrue(runCatching { TextResponseParser.parse(raw, id, "Hello") }.isFailure) }
    @Test fun wrongRequestRejected() { assertTrue(runCatching { TextResponseParser.parse(raw, "other", "पैसे भेजो") }.isFailure) }
    @Test fun modelAndProviderCannotChange() {
        for (value in listOf(raw.replace("muse-spark-1.3", "other"), raw.replace("meta-model-api", "other")))
            assertTrue(runCatching { TextResponseParser.parse(value, id, "पैसे भेजो") }.isFailure)
    }
    @Test fun duplicateKeysRejected() { assertTrue(runCatching { TextResponseParser.parse(raw.replace("\"request_id\"", "\"schema_version\":1,\"request_id\""), id, "पैसे भेजो") }.isFailure) }
    @Test fun oversizedResponseRejected() { assertTrue(runCatching { TextResponseParser.parse(raw + " ".repeat(16384), id, "पैसे भेजो") }.isFailure) }
}
