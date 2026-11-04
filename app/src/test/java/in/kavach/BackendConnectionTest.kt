package `in`.kavach

import `in`.kavach.inference.*
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import java.net.InetSocketAddress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BackendConnectionTest {
    private val token = "a".repeat(43)
    private val health = """{"schema_version":1,"configured":true,"model_id":"muse-spark-1.3","model_provider":"meta-model-api","upstream_verified":false}"""
    @Test fun acceptsCopiedEnvValuesWithoutWeakeningTokenRules() {
        for (value in listOf(token, "  $token\n", "\"$token\"", "KAVACH_LIVE_TOKEN='$token'", "export KAVACH_LIVE_TOKEN=\"$token\"")) {
            assertEquals(token, BackendInput.token(value))
        }
        assertTrue(runCatching { BackendInput.token(token + "\r\nHeader: x") }.isFailure)
        assertTrue(runCatching { BackendInput.token("short") }.isFailure)
    }
    @Test fun apiKeyAndPlaceholderGetSpecificErrors() {
        for (value in listOf("LLM_synthetic_not_a_real_key", "META_API_KEY=synthetic", "\"LLM_synthetic_not_a_real_key\"")) {
            assertTrue(runCatching { BackendInput.token(value) }.exceptionOrNull()!!.message!!.contains("Meta API key"))
        }
        assertTrue(runCatching { BackendInput.token("REPLACE_WITH_RANDOM_TOKEN") }.isFailure)
    }
    @Test fun addressWhitespaceIsTrimmed() { assertEquals("http://127.0.0.1:8765", BackendInput.endpoint(" http://127.0.0.1:8765/ \n")) }
    @Test fun rejectsUnrelatedOrOversizedHealthResponses() {
        BackendConnection.validateHealth(health)
        for (value in listOf(health.replace("muse-spark-1.3", "other"), health.replace("true", "false"), health + " ".repeat(4096)))
            assertTrue(runCatching { BackendConnection.validateHealth(value) }.isFailure)
    }
    private fun server(status: Int, body: String = health, block: (String, () -> String?) -> Unit) {
        val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val authorization = AtomicReference<String?>()
        val failure = AtomicReference<Throwable?>()
        val worker = thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().bufferedReader()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Authorization:", ignoreCase = true)) authorization.set(line.substringAfter(':').trim())
                    }
                    val bytes = body.toByteArray()
                    val out = socket.getOutputStream()
                    out.write("HTTP/1.1 $status Result\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(bytes); out.flush()
                }
            } catch (error: Throwable) { if (!server.isClosed) failure.set(error) }
        }
        try { block("http://127.0.0.1:${server.localPort}") { authorization.get() } }
        finally { server.close(); worker.join(2000) }
        failure.get()?.let { throw it }
    }

    @Test fun connectionTestsAuthenticationWithoutInference() = server(200) { origin, auth ->
        runTest { BackendConnection.verify(BackendConfig(origin, token)) }
        assertEquals("Bearer $token", auth())
    }
    @Test fun rejectedTokenAndRedirectDoNotLookConnected() {
        for (status in listOf(401, 302)) server(status, "{}") { origin, _ ->
            val error = runCatching { runTest { BackendConnection.verify(BackendConfig(origin, token)) } }.exceptionOrNull()
            assertNotNull(error)
            assertTrue(error!!.message!!.contains(if (status == 401) "Token rejected" else "HTTP 302"))
            assertFalse(error.message!!.contains(token))
        }
    }
}
