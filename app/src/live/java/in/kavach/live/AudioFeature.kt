package `in`.kavach.live

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.kavach.BuildConfig

private data class AudioConfig(val endpoint: String, val token: String)
@Composable fun AudioFeaturePanel(enabled: Boolean, onSetup: () -> Unit = {}) {
    val context = LocalContext.current
    val state by AudioSessionStore.state.collectAsStateWithLifecycle()
    val config by `in`.kavach.inference.BackendSettings.state.collectAsStateWithLifecycle()
    val endpoint = config?.endpoint ?: ""
    val token = config?.token ?: ""
    var consent by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<AudioConfig?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val currentEnabled by rememberUpdatedState(enabled)
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val config = pending; pending = null
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notificationsGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (config != null && currentEnabled && LiveSessionStore.enabled && LiveSessionStore.appVisible && micGranted && notificationsGranted && !LiveSessionStore.state.value.active && !AudioSessionStore.state.value.active) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, AudioCaptureService::class.java).setAction(AudioCaptureService.START)
                    .putExtra("endpoint", config.endpoint).putExtra("token", config.token))
            }.onFailure { notice = "Microphone could not start. Return to the app and try again." }
        } else notice = "Microphone not started. Both microphone and notification access are required; start while Kavach is visible."
    }
    LaunchedEffect(enabled) { if (!enabled) { pending = null; consent = false } }
    Text("Speakerphone audio", style = MaterialTheme.typography.headlineMedium)
    Text("English speech • experimental")
    Text("Use speakerphone. Android may block audio; speech is missed while clips are processed.")
    Text(if (state.active) "MICROPHONE SESSION ACTIVE" else "MICROPHONE OFF", fontWeight = FontWeight.Bold)
    Text(state.status)
    if (state.active) {
        Button(onClick = { AudioSessionStore.requestStop(context) }, modifier = Modifier.fillMaxWidth()) { Text("Stop microphone & clear") }
    } else {
        if (config == null) OutlinedButton(onClick = onSetup) { Text("Connect backend") }
        Button(enabled = enabled && config != null && pending == null, onClick = {
            if (runCatching { LivePolicy.endpoint(endpoint, BuildConfig.DEBUG); LivePolicy.token(token) }.isSuccess) { consent = true; notice = null }
            else notice = "Enter a valid HTTPS backend and server token (debug USB forwarding also permits localhost HTTP)."
        }, modifier = Modifier.fillMaxWidth()) { Text("Review microphone sharing") }
    }
    state.observation?.let { result ->
        Text(result.summary, fontWeight = FontWeight.Bold)
        result.assessment?.evidence?.forEach { Text("“${it.quote}”\nMachine-transcribed speech; may be wrong. Speaker identity is unknown.") }
        if (result.transcript.isNotBlank()) {
            var showTranscript by remember(result) { mutableStateOf(false) }
            TextButton(onClick = { showTranscript = !showTranscript }) { Text(if (showTranscript) "Hide transcript" else "View transcript") }
            if (showTranscript) Text(result.transcript)
        }
        Text("Muse Voice Transcribe 1.0 + Muse Spark 1.3 • Meta Model API")
        Text("Pause payments and verify through an independently obtained official contact. You can end the call and speak to someone you trust.")
    }
    notice?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Share microphone audio with Meta?") },
        text = { Text("Microphone clips go through $endpoint to Meta Model API (api.meta.ai): Muse Voice Transcribe, then Muse Spark. Meta processing terms and API charges apply.\n\nIncludes your voice and nearby speech. Start only when participants know and agree. Ten-second clips, up to 10 minutes, with gaps during processing. Android may block capture. Silence is not safety.\n\nUse Stop at any time; uploaded audio cannot be recalled.") },
        confirmButton = { TextButton(onClick = {
            consent = false
            if (currentEnabled && LiveSessionStore.enabled) {
                pending = AudioConfig(LivePolicy.endpoint(endpoint, BuildConfig.DEBUG), token)
                permissions.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        }) { Text("Agree and request microphone") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("Cancel") } })
}
