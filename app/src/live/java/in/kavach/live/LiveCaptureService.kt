package `in`.kavach.live

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import `in`.kavach.BuildConfig
import `in`.kavach.MainActivity
import `in`.kavach.R
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/** User-started, visible, bounded screen sharing. No microphone, overlay or accessibility access. */
class LiveCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private val captureThread = HandlerThread("KavachScreenFrames")
    private lateinit var capture: Handler
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var client: LiveModelClient? = null
    private var session: String? = null
    private val stopped = AtomicBoolean(false)
    private val inFlight = AtomicBoolean(false)
    private var lastSample = -LivePolicy.FRAME_INTERVAL_MS
    private var frameNumber = 0L
    private var lastAlert = -60_000L
    private var warned = false
    @Volatile private var contentVisible = true
    private var currentSize = Pair(0, 0)
    private val notifications get() = getSystemService(NotificationManager::class.java)
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { finish("Android ended screen sharing. No call assessment is active.") }
        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            contentVisible = isVisible
            if (!isVisible) session?.let { update(it, "The shared app is not visible. Current content is unassessed.", clear = true) }
        }
        override fun onCapturedContentResize(width: Int, height: Int) {
            if (!stopped.get() && width > 0 && height > 0) capture.post { runCatching { resize(width, height) }.onFailure { main.post { finish("Screen resizing failed. Start a fresh session.") } } }
        }
    }
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        captureThread.start(); capture = Handler(captureThread.looper)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Live screen-sharing status", NotificationManager.IMPORTANCE_LOW))
        notifications.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Possible coercion in visible text", NotificationManager.IMPORTANCE_HIGH))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { finish("Stopped by you. No screen sharing is active."); return START_NOT_STICKY }
        if (session != null) return START_NOT_STICKY
        try {
            check(LiveSessionStore.enabled && !AudioSessionStore.state.value.active && notifications.areNotificationsEnabled())
            check(notifications.getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE)
            require(intent?.action == START)
            val endpoint = LivePolicy.endpoint(intent.getStringExtra("endpoint") ?: "", BuildConfig.DEBUG)
            val token = intent.getStringExtra("token") ?: ""
            LivePolicy.token(token)
            @Suppress("DEPRECATION")
            val consent = intent.getParcelableExtra<Intent>("projection") ?: error("Missing Android consent")
            require(intent.getIntExtra("result_code", 0) == Activity.RESULT_OK)
            intent.removeExtra("token"); intent.removeExtra("endpoint"); intent.removeExtra("projection")
            startForeground(NOTIFICATION_ID, notification("Starting screen sharing. Audio is not captured."), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            val id = UUID.randomUUID().toString(); session = id
            LiveSessionStore.begin(id)
            client = LiveModelClient(endpoint, token)
            projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK, consent)
            projection!!.registerCallback(callback, main)
            val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            capture.post { if (!stopped.get() && display == null) runCatching { resize(bounds.width(), bounds.height()) }.onFailure { main.post { finish("Screen capture could not start. Try sharing the app again.") } } }
            main.post(tick)
        } catch (_: Exception) { finish("Screen sharing could not start. Check notifications, server settings and Android consent.") }
        return START_NOT_STICKY // Never restart without a new disclosure and system grant.
    }
    private val tick = object : Runnable {
        override fun run() {
            val id = session ?: return
            if (stopped.get()) return
            if (!LiveSessionStore.enabled || !LiveSessionStore.gate.active(id, SystemClock.elapsedRealtime())) {
                finish("Session ended. Start again with fresh consent if needed."); return
            }
            if (getSystemService(KeyguardManager::class.java).isDeviceLocked || !notifications.areNotificationsEnabled() || notifications.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE) {
                finish("Sharing stopped because the phone locked or notifications were disabled."); return
            }
            val state = LiveSessionStore.state.value
            if (state.observation != null && SystemClock.elapsedRealtime() - state.capturedAt > LivePolicy.MAX_FRAME_AGE_MS) {
                update(id, "Previous observation expired. Current call content is unassessed.", clear = true)
                notifications.cancel(ALERT_ID)
            }
            main.postDelayed(this, 1000)
        }
    }
    // Runs only on the capture handler, so old readers are closed in the same order as callbacks.
    private fun resize(width: Int, height: Int) {
        if (stopped.get()) return
        val scale = minOf(1.0, LivePolicy.MAX_IMAGE_EDGE.toDouble() / maxOf(width, height))
        val w = maxOf(1, (width * scale).toInt()); val h = maxOf(1, (height * scale).toInt())
        if (currentSize == Pair(w, h)) return
        currentSize = Pair(w, h)
        val next = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        next.setOnImageAvailableListener({ source -> onImage(source) }, capture)
        val density = resources.configuration.densityDpi
        if (display == null) {
            display = projection!!.createVirtualDisplay("Kavach consented screen sharing", w, h, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, next.surface, null, capture)
        } else {
            display!!.resize(w, h, density); display!!.surface = next.surface
        }
        val old = reader; reader = next; old?.close()
    }
    private fun onImage(source: ImageReader) {
        val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return
        val id = session
        val now = SystemClock.elapsedRealtime()
        if (id == null || stopped.get() || !LiveSessionStore.enabled || !LiveSessionStore.gate.active(id, now)) { image.close(); return }
        if (!contentVisible) { image.close(); return }
        if (LiveSessionStore.appVisible) {
            image.close(); update(id, "Paused while Kavach is visible. Return to the shared app to continue."); return
        }
        if (now - lastSample < LivePolicy.FRAME_INTERVAL_MS || !inFlight.compareAndSet(false, true)) { image.close(); return }
        lastSample = now
        val frame = ++frameNumber
        val jpeg = try { image.use { encode(it) } }
            catch (_: OutOfMemoryError) { main.post { finish("Not enough memory. Screen sharing stopped.") }; null }
            catch (_: Exception) { null }
        if (jpeg == null) {
            inFlight.set(false); update(id, "Screen unavailable or blank. Unable to assess; call audio is not captured.", clear = true); return
        }
        update(id, "Checking a screen image with Muse Spark via Meta Model API. Audio is unassessed.", clear = true)
        scope.launch(Dispatchers.IO) {
            try {
                if (stopped.get() || !LiveSessionStore.gate.active(id, SystemClock.elapsedRealtime())) return@launch
                val observation = client!!.assess(jpeg, id, frame)
                withContext(Dispatchers.Main.immediate) {
                if (LiveSessionStore.result(id, frame, now, observation)) {
                    notifyStatus(observation.summary)
                    if (observation.warning && !warned && SystemClock.elapsedRealtime() - lastAlert >= 60_000) {
                        lastAlert = SystemClock.elapsedRealtime()
                        notifications.notify(ALERT_ID, notification("Possible coercion in visible text. Pause and verify. Audio is unassessed.", alert = true))
                    }
                    warned = observation.warning
                } else update(id, "Result arrived too late. Current call content is unassessed.", clear = true)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { update(id, "Unable to assess this image. Check the server connection and model setup.", clear = true) }
            catch (_: OutOfMemoryError) { main.post { finish("Not enough memory. Screen sharing stopped.") } }
            finally { jpeg.fill(0); inFlight.set(false) }
        }
    }
    private fun encode(image: Image): ByteArray? {
        val plane = image.planes[0]
        require(plane.pixelStride == 4)
        val pixels = rgbaPixels(plane.buffer, image.width, image.height, plane.rowStride, plane.pixelStride)
        val bitmap = Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
        pixels.fill(0)
        try {
            val cropped = bitmap
            var min = 255; var max = 0
            for (y in 0 until cropped.height step maxOf(1, cropped.height / 32)) {
                for (x in 0 until cropped.width step maxOf(1, cropped.width / 32)) {
                    val color = cropped.getPixel(x, y)
                    val value = (((color shr 16) and 255) + ((color shr 8) and 255) + (color and 255)) / 3
                    min = minOf(min, value); max = maxOf(max, value)
                }
            }
            if (max - min < 8) return null
            return ByteArrayOutputStream().use { out ->
                check(cropped.compress(Bitmap.CompressFormat.JPEG, 65, out))
                require(out.size() <= LivePolicy.MAX_JPEG_BYTES)
                out.toByteArray()
            }
        } finally { bitmap.recycle() }
    }
    private fun update(id: String, text: String, clear: Boolean = false) {
        main.post {
            if (!stopped.get() && LiveSessionStore.gate.active(id, SystemClock.elapsedRealtime())) {
                val before = LiveSessionStore.state.value
                LiveSessionStore.status(id, text, clear)
                if (before.status != text) notifyStatus(text)
                if (clear) notifications.cancel(ALERT_ID)
            }
        }
    }
    private fun notifyStatus(text: String) {
        if (!stopped.get() && LiveSessionStore.state.value.active) notifications.notify(NOTIFICATION_ID, notification(text))
    }
    private fun notification(text: String, alert: Boolean = false): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, LiveCaptureService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, if (alert) ALERT_CHANNEL else CHANNEL)
            .setSmallIcon(R.drawable.ic_shield).setContentTitle(if (alert) "Kavach • pause and verify" else "Kavach Live • visual only")
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setOngoing(!alert).setOnlyAlertOnce(!alert)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .addAction(0, "Stop sharing", stop).build()
    }
    private fun finish(message: String) {
        if (!stopped.compareAndSet(false, true)) return
        LiveSessionStore.stop(message)
        main.removeCallbacks(tick)
        client?.cancel(); scope.cancel()
        val old = projection; projection = null
        old?.unregisterCallback(callback); runCatching { old?.stop() }
        capture.post {
            display?.release(); display = null; reader?.close(); reader = null
            captureThread.quitSafely()
        }
        notifications.cancel(ALERT_ID)
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onTaskRemoved(rootIntent: Intent?) { finish("App closed. Screen sharing stopped.") }
    override fun onDestroy() { finish("Screen sharing stopped."); super.onDestroy() }
    companion object {
        const val START = "in.kavach.live.START"
        const val STOP = "in.kavach.live.STOP"
        private const val CHANNEL = "kavach_live_status"
        private const val ALERT_CHANNEL = "kavach_live_alerts"
        private const val NOTIFICATION_ID = 701
        private const val ALERT_ID = 702
    }
}
