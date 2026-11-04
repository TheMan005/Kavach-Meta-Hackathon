package `in`.kavach.domain

import kotlinx.serialization.json.*

class InferenceUnavailable(message: String) : IllegalStateException(message)

enum class Risk { strong_indicators, some_indicators, no_clear_indicators, insufficient_evidence }
enum class Pattern { digital_arrest, impersonation, payment_fraud, account_takeover, other, unclear }
enum class Indicator { authority_impersonation, arrest_threat, enforced_availability, isolation, urgent_transfer, otp_request, remote_access, account_linking }
enum class Sender { unspecified, known, unknown }
data class Evidence(val messageId: String, val quote: String, val indicator: Indicator, val occurrences: List<IntRange>)
data class Assessment(val risk: Risk, val pattern: Pattern, val evidence: List<Evidence>, val limitations: List<String>)
data class EngineOutput(val raw: String, val modelId: String, val version: String, val elapsedMs: Long)
data class Result(val assessment: Assessment, val source: String, val modelId: String, val version: String, val elapsedMs: Long, val text: String)

object InputPolicy {
    const val MAX_UNITS = 8000
    fun validate(text: String) {
        require(text.isNotBlank()) { "Paste or type a conversation first." }
        require(text.length <= MAX_UNITS) { "Select a shorter excerpt (up to 8,000 characters). Nothing was truncated." }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (Character.isHighSurrogate(c)) {
                require(i + 1 < text.length && Character.isLowSurrogate(text[i + 1])) { "Text contains an incomplete Unicode character." }
                i += 2
            } else {
                require(!Character.isLowSurrogate(c)) { "Text contains an incomplete Unicode character." }
                i++
            }
        }
    }
}

object EvidenceValidator {
    fun positions(text: String, quote: String): List<IntRange> {
        require(quote.isNotEmpty() && quote.length <= 400) { "Invalid evidence length." }
        InputPolicy.validate(quote)
        val positions = mutableListOf<IntRange>()
        var next = text.indexOf(quote)
        while (next >= 0) {
            // Return every occurrence; the model cannot select an arbitrary duplicate.
            positions += next until next + quote.length
            next = text.indexOf(quote, next + 1)
        }
        require(positions.isNotEmpty()) { "The model quoted text that was not supplied." }
        return positions
    }
}

class AssessmentParser {
    private val json = Json { isLenient = false }
    fun parse(raw: String, text: String): Assessment {
        require(raw.length <= 12000) { "Response exceeded its size limit." }
        val root = json.parseToJsonElement(raw) as? JsonObject ?: error("Expected an object.")
        rejectDuplicateKeys(raw)
        root.keysExactly("schema_version", "status", "risk", "suspected_pattern", "evidence", "limitations")
        require(root["schema_version"] == JsonPrimitive(1))
        require(root.string("status", 32) == "assessed") { "Unable to assess this excerpt." }
        val risk = enumValueOf<Risk>(root.string("risk", 32))
        val pattern = enumValueOf<Pattern>(root.string("suspected_pattern", 32))
        val items = root["evidence"] as? JsonArray ?: error("Expected evidence array.")
        require(items.size <= 8)
        val evidence = items.map { item ->
            val e = item as? JsonObject ?: error("Invalid evidence.")
            e.keysExactly("message_id", "quote", "indicator")
            val id = e.string("message_id", 16)
            require(id == "m1") { "Evidence referred to an unknown message." }
            val quote = e.string("quote", 400)
            Evidence(id, quote, enumValueOf(e.string("indicator", 32)), EvidenceValidator.positions(text, quote))
        }
        require(risk !in setOf(Risk.strong_indicators, Risk.some_indicators) || evidence.isNotEmpty())
        require(risk != Risk.no_clear_indicators || evidence.isEmpty())
        val limitations = root["limitations"] as? JsonArray ?: error("Expected limitations array.")
        require(limitations.size <= 4)
        val notes = limitations.map {
            require(it is JsonPrimitive && it.isString && it.content.length <= 200)
            it.content
        }
        return Assessment(risk, pattern, evidence, notes)
    }
}

fun JsonObject.keysExactly(vararg keys: String) = require(this.keys == keys.toSet()) { "Unexpected schema fields." }
fun JsonObject.string(key: String, max: Int): String {
    val p = get(key) as? JsonPrimitive ?: error("Missing $key.")
    require(p.isString && p.content.length in 1..max) { "Invalid $key." }
    return p.content
}

object AdviceCatalog {
    fun nextStep(a: Assessment): String = when {
        a.evidence.any { it.indicator == Indicator.otp_request || it.indicator == Indicator.account_linking } ->
            "Do not share an OTP or link your account on request. Open the service yourself and verify through its official support channel."
        a.evidence.any { it.indicator == Indicator.remote_access } ->
            "Pause the exchange. Do not install remote-access software on request. Verify using an independently obtained official contact."
        a.risk in setOf(Risk.strong_indicators, Risk.some_indicators) ->
            "Pause before sending money or personal details. Contact someone you trust and verify the claim through an independently obtained official channel."
        else -> "Keep checking the context. This result does not establish that the sender or conversation is safe. Verify unusual requests independently."
    }
    fun label(risk: Risk) = when (risk) {
        Risk.strong_indicators -> "Strong warning signs"
        Risk.some_indicators -> "Some warning signs"
        Risk.no_clear_indicators -> "No clear indicators"
        Risk.insufficient_evidence -> "Not enough context"
    }
}

/** JSON syntax is parsed first; this pass rejects duplicate object keys that JSON trees overwrite. */
internal fun rejectDuplicateKeys(raw: String) {
    val scopes = mutableListOf<MutableSet<String>?>()
    var i = 0
    while (i < raw.length) {
        when (raw[i]) {
            '{' -> scopes.add(mutableSetOf())
            '[' -> scopes.add(null)
            '}', ']' -> scopes.removeAt(scopes.lastIndex)
            '"' -> {
                val start = i++
                while (i < raw.length) {
                    if (raw[i] == '\\') i += 2
                    else if (raw[i] == '"') break
                    else i++
                }
                val end = i + 1
                var after = end
                while (after < raw.length && raw[after].isWhitespace()) after++
                if (after < raw.length && raw[after] == ':') {
                    val key = Json.parseToJsonElement(raw.substring(start, end)).jsonPrimitive.content
                    require(scopes.last()!!.add(key)) { "Duplicate response field." }
                }
            }
        }
        i++
    }
}
