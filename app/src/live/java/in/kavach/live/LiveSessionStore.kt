package `in`.kavach.live

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveState(val active: Boolean = false, val status: String = "Not sharing", val observation: LiveObservation? = null, val capturedAt: Long = 0, val frames: Int = 0)
object LiveSessionStore {
    private val mutable = MutableStateFlow(LiveState())
    val state = mutable.asStateFlow()
    val gate = LiveSessionGate()
    @Volatile var enabled = true
    @Volatile var appVisible = true
    @Synchronized fun begin(id: String) { gate.start(id, SystemClock.elapsedRealtime()); mutable.value = LiveState(true, "Waiting for the selected app. Audio is not captured.") }
    @Synchronized fun status(id: String, status: String, clear: Boolean = false) {
        if (gate.active(id, SystemClock.elapsedRealtime())) mutable.value = mutable.value.copy(status = status, observation = if (clear) null else mutable.value.observation)
    }
    @Synchronized fun result(id: String, frame: Long, capturedAt: Long, observation: LiveObservation): Boolean {
        if (!gate.accept(id, frame, capturedAt, SystemClock.elapsedRealtime())) return false
        mutable.value = mutable.value.copy(status = observation.summary, observation = observation, capturedAt = capturedAt, frames = mutable.value.frames + 1)
        return true
    }
    @Synchronized fun stop(status: String = "Stopped. Screen images and observations are not retained.") {
        gate.stop(); mutable.value = LiveState(status = status)
    }
    fun requestStop(context: Context) {
        stop() // Invalidate results before waiting for the service's lifecycle callback.
        context.stopService(Intent(context, LiveCaptureService::class.java))
    }
}
