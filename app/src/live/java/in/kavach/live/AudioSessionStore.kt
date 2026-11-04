package `in`.kavach.live

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioState(val active: Boolean = false, val status: String = "Microphone off", val observation: AudioObservation? = null, val capturedAt: Long = 0)
object AudioSessionStore {
    private val mutable = MutableStateFlow(AudioState())
    val state = mutable.asStateFlow()
    val gate = LiveSessionGate(AudioPolicy.MAX_AGE_MS)
    @Synchronized fun begin(id: String) { gate.start(id, SystemClock.elapsedRealtime()); mutable.value = AudioState(true, "Starting microphone") }
    @Synchronized fun status(id: String, text: String, clear: Boolean = false) {
        if (gate.active(id, SystemClock.elapsedRealtime())) mutable.value = mutable.value.copy(status = text, observation = if (clear) null else mutable.value.observation)
    }
    @Synchronized fun result(id: String, chunk: Long, capturedAt: Long, observation: AudioObservation): Boolean {
        if (!gate.accept(id, chunk, capturedAt, SystemClock.elapsedRealtime())) return false
        mutable.value = AudioState(true, observation.summary, observation, capturedAt)
        return true
    }
    @Synchronized fun stop(text: String = "Microphone stopped. Audio and transcript cleared.") { gate.stop(); mutable.value = AudioState(status = text) }
    fun requestStop(context: Context) { stop(); context.stopService(Intent(context, AudioCaptureService::class.java)) }
}
