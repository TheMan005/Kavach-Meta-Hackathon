package `in`.kavach.live

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.kavach.BuildConfig
import kotlinx.coroutines.delay

fun liveFeatureVisibility(visible: Boolean) { LiveSessionStore.appVisible = visible }
fun setLiveFeatureEnabled(context: Context, enabled: Boolean) {
    LiveSessionStore.enabled = enabled
    if (!enabled) { LiveSessionStore.requestStop(context); AudioSessionStore.requestStop(context) }
}
@Composable fun LiveFeatureLifecycle(enabled: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(enabled) {
        setLiveFeatureEnabled(context, enabled)
    }
}
private data class SessionConfig(val endpoint: String, val token: String)

@Composable fun LiveFeaturePanel(enabled: Boolean, onSetup: () -> Unit = {}) {
    val audioState by AudioSessionStore.state.collectAsStateWithLifecycle()
    val videoState by LiveSessionStore.state.collectAsStateWithLifecycle()
    var audio by remember { mutableStateOf(audioState.active) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { audio = false }, enabled = !audioState.active) { Text("Screen") }
        OutlinedButton(onClick = { audio = true }, enabled = !videoState.active) { Text("Speakerphone") }
    }
    if (audio) AudioFeaturePanel(enabled, onSetup) else LiveVisualPanel(enabled, onSetup)
}
@Composable private fun LiveVisualPanel(enabled: Boolean, onSetup: () -> Unit) {
    val context = LocalContext.current
    val state by LiveSessionStore.state.collectAsStateWithLifecycle()
    val config by `in`.kavach.inference.BackendSettings.state.collectAsStateWithLifecycle()
    val endpoint = config?.endpoint ?: ""
    val token = config?.token ?: ""
    var notice by remember { mutableStateOf<String?>(null) }
    var consent by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<SessionConfig?>(null) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.active) { while (state.active) { now = SystemClock.elapsedRealtime(); delay(1000) } }
    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val config = pending; pending = null
        if (config != null && result.resultCode == Activity.RESULT_OK && result.data != null && LiveSessionStore.enabled && !LiveSessionStore.state.value.active) {
            val intent = Intent(context, LiveCaptureService::class.java).setAction(LiveCaptureService.START)
                .putExtra("endpoint", config.endpoint).putExtra("token", config.token)
                .putExtra("result_code", result.resultCode).putExtra("projection", result.data)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { notice = "Android could not start sharing. Return here and try again." }
        } else notice = "Screen sharing was not started. No image was sent."
    }
    fun askForScreen() {
        if (pending == null || !LiveSessionStore.enabled) { pending = null; return }
        if (!context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
            pending = null; notice = "Enable Kavach Live notifications in Android Settings so you can see sharing status and stop it."
            return
        }
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        projection.launch(manager.createScreenCaptureIntent())
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) askForScreen() else {
            pending = null; notice = "Notifications are required for this live prototype. No screen sharing started."
        }
    }
    Text("Screen check", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
    Text("Checks visible English text. Does not hear the call.")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (state.active) "SCREEN SHARING ACTIVE" else "SCREEN SHARING OFF", fontWeight = FontWeight.Bold)
            Text(state.status)
            if (state.active) {
                Text("${state.frames} image response(s) • session limit 10 minutes\nAudio is never captured. Alerts can be silenced by Android.", fontSize = 12.sp)
                Button(onClick = { LiveSessionStore.requestStop(context) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Outlined.StopCircle, null); Spacer(Modifier.width(8.dp)); Text("Stop sharing & clear")
                }
            }
        }
    }
    val observation = state.observation
    if (observation != null && now - state.capturedAt <= LivePolicy.MAX_FRAME_AGE_MS) {
        Text("Latest image • ${maxOf(0, (now - state.capturedAt) / 1000)}s ago", fontWeight = FontWeight.Medium)
        Text(observation.summary)
        observation.assessment?.evidence?.forEach { evidence ->
            LiveInfo(evidence.indicator.name.replace('_', ' '), "“${evidence.quote}”\nModel-read screen text; not independently verified. No claim about who said it.")
        }
        Text("Meta Muse Spark 1.3 • Meta Model API", fontSize = 12.sp)
        Text("Pause payments and verify claims through an independently obtained official contact. You can end the call and speak to someone you trust.")
    }
    if (!state.active) {
        if (config == null) OutlinedButton(onClick = onSetup) { Text("Connect backend") }
        Button(enabled = enabled && config != null && pending == null, onClick = {
            val valid = runCatching { LivePolicy.endpoint(endpoint, BuildConfig.DEBUG); LivePolicy.token(token) }
            if (valid.isSuccess) { notice = null; consent = true }
            else notice = "Enter a valid HTTPS server origin and a 32–256 character server token. Debug builds allow localhost HTTP through adb reverse."
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Review live sharing") }
        if (!enabled) Text("Checks are paused. Enable them in Privacy before starting.")
    }
    notice?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
    if (consent) AlertDialog(onDismissRequest = { consent = false },
        title = { Text("Share screen images with Meta?") },
        text = { Text("Screen images go through $endpoint to Meta Model API (api.meta.ai). Images may include private messages and faces. Meta processing terms and API charges apply.\n\nOne image every 15 seconds, for up to 10 minutes. Choose only the call app when possible. Audio is not captured and results may be wrong. Use Stop at any time; uploaded images cannot be recalled.") },
        confirmButton = { TextButton(onClick = {
            consent = false
            if (enabled && LiveSessionStore.enabled) {
                pending = SessionConfig(LivePolicy.endpoint(endpoint, BuildConfig.DEBUG), token)
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else askForScreen()
            }
        }) { Text("Continue to Android screen sharing") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("Cancel") } }
    )
}
@Composable private fun LiveInfo(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
        }
    }
}
