package `in`.kavach.live

import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.*

class LiveModelClient(private val endpoint: String, private val token: String) {
    private val cancelled = AtomicBoolean(false)
    @Volatile private var connection: HttpURLConnection? = null
    fun cancel() { cancelled.set(true); connection?.disconnect() }
    fun assessAudio(pcm: ByteArray, session: String, chunk: Long): AudioObservation {
        require(pcm.size == AudioPolicy.SAMPLES * 2)
        val body = buildJsonObject {
            put("schema_version", 1); put("session_id", session); put("chunk_id", chunk)
            put("audio_pcm", Base64.encodeToString(pcm, Base64.NO_WRAP))
        }.toString().toByteArray(Charsets.UTF_8)
        return AudioResponseParser.parse(post(body, "/v1/audio", 60_000), session, chunk)
    }
    fun assess(jpeg: ByteArray, session: String, frame: Long): LiveObservation {
        check(!cancelled.get())
        require(jpeg.size <= LivePolicy.MAX_JPEG_BYTES)
        val body = buildJsonObject {
            put("schema_version", 1); put("session_id", session); put("frame_id", frame)
            put("image_jpeg", Base64.encodeToString(jpeg, Base64.NO_WRAP))
        }.toString().toByteArray(Charsets.UTF_8)
        return LiveResponseParser.parse(post(body, "/v1/frames", 30_000), session, frame)
    }
    private fun post(body: ByteArray, path: String, timeout: Int): String {
        check(!cancelled.get())
        val conn = URL("$endpoint$path").openConnection() as HttpURLConnection
        connection = conn
        try {
            check(!cancelled.get())
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 5_000; conn.readTimeout = timeout
            conn.requestMethod = "POST"; conn.doOutput = true; conn.useCaches = false
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            check(conn.responseCode == 200) { "The server could not assess this image. Check server setup and token." }
            require(conn.contentType?.substringBefore(';') == "application/json")
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    require(out.size() + n <= LivePolicy.MAX_RESPONSE_BYTES)
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            return bytes.toString(Charsets.UTF_8)
        } finally { body.fill(0); conn.disconnect(); connection = null }
    }
}
