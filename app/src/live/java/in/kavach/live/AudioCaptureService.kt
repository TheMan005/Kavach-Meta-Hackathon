package `in`.kavach.live

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import `in`.kavach.BuildConfig
import `in`.kavach.MainActivity
import `in`.kavach.R
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/** Foreground microphone only. Never requests privileged call audio or changes audio routing. */
class AudioCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private val stopped = AtomicBoolean(false)
    private var session: String? = null
    @Volatile private var recorder: AudioRecord? = null
    private var client: LiveModelClient? = null
    private var lastAlert = -60_000L
    private val notifications get() = getSystemService(NotificationManager::class.java)
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Microphone assistance status", NotificationManager.IMPORTANCE_LOW))
        notifications.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Possible coercion in speech", NotificationManager.IMPORTANCE_HIGH))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { finish("Microphone stopped by you."); return START_NOT_STICKY }
        if (session != null) return START_NOT_STICKY
        try {
            check(!stopped.get() && LiveSessionStore.enabled && LiveSessionStore.appVisible && !LiveSessionStore.state.value.active)
            check(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            check(notifications.areNotificationsEnabled() && notifications.getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE)
            require(intent?.action == START)
            val endpoint = LivePolicy.endpoint(intent.getStringExtra("endpoint") ?: "", BuildConfig.DEBUG)
            val token = intent.getStringExtra("token") ?: ""; LivePolicy.token(token)
            intent.removeExtra("token"); intent.removeExtra("endpoint")
            startForeground(ID, notification("Microphone assistance starting. Call capture may be blocked."), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            val id = UUID.randomUUID().toString(); session = id
            AudioSessionStore.begin(id); client = LiveModelClient(endpoint, token)
            main.post(tick)
            scope.launch(Dispatchers.IO) { capture(id) }
        } catch (_: Exception) { finish("Microphone could not start. Check microphone/notification permissions and try from the app.") }
        return START_NOT_STICKY
    }
    private val tick = object : Runnable {
        override fun run() {
            val id = session ?: return
            if (stopped.get()) return
            if (!LiveSessionStore.enabled || !AudioSessionStore.gate.active(id, SystemClock.elapsedRealtime()) ||
                getSystemService(KeyguardManager::class.java).isDeviceLocked || !notifications.areNotificationsEnabled() ||
                notifications.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE ||
                ContextCompat.checkSelfPermission(this@AudioCaptureService, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                finish("Microphone session ended: paused, locked, expired or permission revoked."); return
            }
            val state = AudioSessionStore.state.value
            if (state.observation != null && SystemClock.elapsedRealtime() - state.capturedAt > AudioPolicy.MAX_AGE_MS) {
                AudioSessionStore.status(id, "Previous speech assessment expired. Current call is unassessed.", true)
                notifications.cancel(ALERT_ID)
            }
            main.postDelayed(this, 1000)
        }
    }
    @Suppress("MissingPermission") // Runtime grant checked before foreground launch and by the watchdog.
    private suspend fun capture(id: String) {
        try {
            val min = AudioRecord.getMinBufferSize(AudioPolicy.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(min > 0)
            val record = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(AudioPolicy.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(maxOf(min * 2, 9600)).build()
            recorder = record
            check(record.state == AudioRecord.STATE_INITIALIZED)
            var chunk = 0L
            while (currentCoroutineContext().isActive && !stopped.get() && AudioSessionStore.gate.active(id, SystemClock.elapsedRealtime())) {
                val samples = ShortArray(AudioPolicy.SAMPLES)
                val capturedAt = SystemClock.elapsedRealtime()
                try {
                    update(id, "Microphone listening for 10 seconds. Use speakerphone; the other app may block audio.")
                    record.startRecording()
                    check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                    var count = 0
                    while (count < samples.size && !stopped.get()) {
                        currentCoroutineContext().ensureActive()
                        check(SystemClock.elapsedRealtime() - capturedAt < 15_000) { "Capture stalled" }
                        check(record.activeRecordingConfiguration?.isClientSilenced != true) { "Android silenced microphone" }
                        val n = record.read(samples, count, minOf(2400, samples.size - count), AudioRecord.READ_NON_BLOCKING)
                        check(n >= 0)
                        count += n
                        if (n == 0) delay(20)
                    }
                    runCatching { record.stop() }
                    if (stopped.get() || count != samples.size || !LiveSessionStore.enabled || !AudioSessionStore.gate.active(id, SystemClock.elapsedRealtime())) break
                    if (AudioPolicy.silent(samples)) {
                        update(id, "Microphone silent or too quiet; call may block capture. Nothing uploaded. Call unassessed.", true)
                        delay(3000)
                        continue
                    }
                    val pcm = AudioPolicy.pcm(samples)
                    samples.fill(0)
                    try {
                        update(id, "Microphone paused while Meta transcribes and assesses this clip. Speech during this gap is missed.")
                        val result = client!!.assessAudio(pcm, id, ++chunk)
                        withContext(Dispatchers.Main.immediate) {
                            if (!stopped.get() && AudioSessionStore.result(id, chunk, capturedAt, result)) {
                                notifications.notify(ID, notification(result.summary))
                                if (result.warning && SystemClock.elapsedRealtime() - lastAlert >= 60_000) {
                                    lastAlert = SystemClock.elapsedRealtime()
                                    notifications.notify(ALERT_ID, notification("Possible coercion in microphone speech. Pause and verify.", true))
                                } else if (!result.warning) notifications.cancel(ALERT_ID)
                            } else update(id, "Audio result expired. Current call is unassessed.", true)
                        }
                        delay(3000) // Bounded, sequential batches; no continuous-stream claim.
                    } finally { pcm.fill(0) }
                } finally { samples.fill(0); runCatching { record.stop() } }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { withContext(Dispatchers.Main) { finish("Audio unavailable or blocked. Check microphone access, backend and Meta setup; no safety conclusion.") } }
        catch (_: OutOfMemoryError) { main.post { finish("Audio stopped: insufficient memory.") } }
        finally { recorder?.release(); recorder = null }
    }
    private fun update(id: String, text: String, clear: Boolean = false) {
        main.post {
            if (!stopped.get() && AudioSessionStore.gate.active(id, SystemClock.elapsedRealtime())) {
                AudioSessionStore.status(id, text, clear)
                notifications.notify(ID, notification(text))
                if (clear) notifications.cancel(ALERT_ID)
            }
        }
    }
    private fun notification(text: String, alert: Boolean = false): Notification {
        val open = PendingIntent.getActivity(this, 20, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 21, Intent(this, AudioCaptureService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, if (alert) ALERT_CHANNEL else CHANNEL).setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(if (alert) "Kavach • pause and verify" else "Kavach • microphone assistance")
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open)
            .setOngoing(!alert).setOnlyAlertOnce(!alert).setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .addAction(0, "Stop microphone", stop).build()
    }
    private fun finish(text: String) {
        if (!stopped.compareAndSet(false, true)) return
        AudioSessionStore.stop(text); main.removeCallbacks(tick)
        client?.cancel(); scope.cancel(); runCatching { recorder?.stop() }
        notifications.cancel(ALERT_ID); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onTaskRemoved(rootIntent: Intent?) { finish("Microphone stopped when app task closed.") }
    override fun onDestroy() { finish("Microphone stopped. Audio and transcript cleared."); super.onDestroy() }
    companion object {
        const val START = "in.kavach.audio.START"; const val STOP = "in.kavach.audio.STOP"
        private const val CHANNEL = "kavach_microphone"; private const val ALERT_CHANNEL = "kavach_audio_warnings"
        private const val ID = 7201; private const val ALERT_ID = 7202
    }
}
