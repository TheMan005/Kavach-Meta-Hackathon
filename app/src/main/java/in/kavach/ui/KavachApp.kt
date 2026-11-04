package `in`.kavach.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.kavach.domain.*
import `in`.kavach.BuildConfig
import `in`.kavach.live.LiveFeaturePanel
import `in`.kavach.live.LiveFeatureLifecycle
import `in`.kavach.live.setLiveFeatureEnabled
import `in`.kavach.inference.BackendSettings
import `in`.kavach.inference.BackendInput
import `in`.kavach.inference.BackendConfig
import `in`.kavach.inference.BackendConnection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch

private val Mint = Color(0xFFA6E3C2)
private val Ink = Color(0xFF0B1014)
private val Surface = Color(0xFF141D23)
private val Muted = Color(0xFF9AA9B2)
private val Amber = Color(0xFFF1C784)
private enum class Page { Check, Live, Samples, Backend, Privacy }

@Composable
fun KavachApp(vm: AssessmentViewModel, demos: DemoScenarioRepository) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    LiveFeatureLifecycle(state.enabled)
    var page by remember { mutableStateOf(Page.Check) }
    var demoResult by remember { mutableStateOf<Result?>(null) }
    val backend by BackendSettings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.text) { if (state.text.isNotEmpty()) { page = Page.Check; demoResult = null } }
    BackHandler(page != Page.Check || demoResult != null || state.result != null) {
        page = Page.Check; demoResult = null
        if (state.result != null) vm.discard()
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Mint, onPrimary = Ink, background = Ink,
        surface = Surface, onSurface = Color(0xFFEAF0F3), onSurfaceVariant = Muted,
        secondary = Amber, outline = Color(0xFF33434B)), typography = Typography()) {
        Scaffold(containerColor = Ink, bottomBar = {
            NavigationBar(containerColor = Ink) {
                (listOf(Page.Check to Icons.Outlined.Shield) +
                    (if (BuildConfig.LIVE_FEATURE) listOf(Page.Live to Icons.Outlined.Videocam) else emptyList()) +
                    listOf(Page.Backend to Icons.Outlined.Settings)).forEach { (item, icon) ->
                    NavigationBarItem(selected = page == item, onClick = { page = item; demoResult = null },
                        enabled = !state.busy,
                        icon = { Icon(icon, contentDescription = null) }, label = { Text(if (item == Page.Backend) "Settings" else item.name) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Surface, selectedIconColor = Mint))
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(Mint, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Shield, "Kavach shield", tint = Ink)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("kavach", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
                    }
                    Text("PROTOTYPE", color = Mint, fontSize = 10.sp, modifier = Modifier.border(1.dp, Color(0xFF334B42), CircleShape).padding(horizontal = 10.dp, vertical = 7.dp))
                }
                when (page) {
                    Page.Check -> {
                        val result = state.result
                        if (result != null) ResultScreen(result, onDone = vm::discard)
                        else CheckScreen(state, backend != null, vm) { page = Page.Backend }
                    }
                    Page.Live -> LiveFeaturePanel(state.enabled) { page = Page.Backend }
                    Page.Samples -> {
                        if (demoResult != null) ResultScreen(demoResult!!, onDone = { demoResult = null })
                        else {
                            Heading("A safe place\nto explore.", "12 fictional conversations. Every result here is scripted, not generated by AI.")
                            StatusTag("Scripted demo — no AI assessment", Icons.Outlined.Science, Amber)
                            demos.scenarios.forEach { demo ->
                                OutlinedCard(onClick = { demoResult = demos.result(demo.id) }, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(demo.title, fontWeight = FontWeight.Medium)
                                            Text("Synthetic • ${demo.language}${if (demo.language == "hinglish") " • experimental" else ""}", color = Muted, fontSize = 12.sp)
                                        }
                                        Icon(Icons.Outlined.ChevronRight, "Open synthetic sample")
                                    }
                                }
                            }
                        }
                    }
                    Page.Backend -> BackendScreen(onSamples = { page = Page.Samples }, onPrivacy = { page = Page.Privacy }, beforeChange = {
                        vm.cancel()
                        setLiveFeatureEnabled(context, false)
                        setLiveFeatureEnabled(context, state.enabled)
                    })
                    Page.Privacy -> PrivacyScreen(state.enabled) { enabled ->
                        vm.enable(enabled); setLiveFeatureEnabled(context, enabled)
                    }
                }
            }
        }
        if (state.phase == Phase.ConsentRequired) AlertDialog(
            onDismissRequest = vm::dismissConsent,
            icon = { Icon(Icons.Outlined.Lock, null) },
            title = { Text("Check this excerpt?") },
            text = { Text("Your backend: ${backend?.endpoint ?: "Not configured"}\n\nYour reviewed text and sender context will be sent to this backend, then to Meta Model API (api.meta.ai) for Muse Spark analysis. API charges and Meta processing/retention terms apply. Kavach does not save a history.\n\nModel errors are possible. Cancellation cannot retract text already uploaded.") },
            confirmButton = { TextButton(onClick = vm::consent) { Text("Send to Meta & check") } },
            dismissButton = { TextButton(onClick = vm::dismissConsent) { Text("Keep editing") } }
        )
    }
}

@Composable private fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
        Text(subtitle, color = Muted, fontSize = 15.sp, lineHeight = 23.sp)
    }
}
@Composable private fun StatusTag(label: String, icon: ImageVector, tint: Color = Mint) {
    Row(Modifier.background(tint.copy(alpha = .08f), RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, color = tint, fontSize = 12.sp)
    }
}
@Composable private fun Panel(title: String, text: String, icon: ImageVector = Icons.Outlined.Info) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Mint, modifier = Modifier.size(20.dp)); Text(title, fontWeight = FontWeight.Medium)
            }
            Text(text, color = Muted, lineHeight = 23.sp, fontSize = 14.sp)
        }
    }
}
@Composable private fun CheckScreen(state: CheckState, configured: Boolean, vm: AssessmentViewModel, onSetup: () -> Unit) {
    val context = LocalContext.current
    Heading("Check a message", "Paste a message to check for scam warning signs.")
    if (!configured) OutlinedButton(onClick = onSetup, modifier = Modifier.fillMaxWidth()) { Text("Connect backend") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR CONVERSATION", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            TextButton(enabled = !state.busy, onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text else null
                if (text == null) vm.error("The clipboard does not contain text.") else if (text.length > InputPolicy.MAX_UNITS) vm.error("Clipboard text is too long. Select up to 8,000 characters.") else vm.edit(text.toString())
            }) { Icon(Icons.Outlined.ContentPaste, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Paste") }
        }
        OutlinedTextField(value = state.text, onValueChange = vm::edit,
            enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
            placeholder = { Text("Paste or type the messages here.\n\nYou decide what gets checked.", color = Muted) },
            shape = RoundedCornerShape(18.dp), minLines = 6,
            supportingText = { Text("${state.text.length} / 8,000 • Review and edit before checking") })
        Text("Sender context (optional)", color = Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sender.entries.forEach { sender -> FilterChip(selected = state.sender == sender, onClick = { vm.sender(sender) }, enabled = !state.busy,
                label = { Text(when (sender) { Sender.unspecified -> "Not sure"; Sender.known -> "Known"; Sender.unknown -> "Unknown" }) }) }
        }
        
    }
    state.notice?.let { StatusTag(it, Icons.Outlined.Info, Amber) }
    if (state.busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Mint)
        Text(if (state.phase == Phase.LoadingModel) "Preparing API request…" else "Checking with Muse Spark via your backend…", color = Muted)
        OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Cancel assessment") }
    } else {
        Button(onClick = vm::review, enabled = BuildConfig.LIVE_FEATURE && configured && state.enabled && state.text.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Outlined.Shield, null); Spacer(Modifier.width(10.dp)); Text("Review & check", fontWeight = FontWeight.SemiBold)
        }
        if (state.text.isNotEmpty()) TextButton(onClick = vm::discard, modifier = Modifier.fillMaxWidth()) { Text("Discard this conversation") }
    }
    Text("Sent to Meta only after you confirm.", color = Muted, fontSize = 12.sp)
}

@Composable private fun ResultScreen(result: Result, onDone: () -> Unit) {
    val a = result.assessment
    val tint = if (a.risk in setOf(Risk.some_indicators, Risk.strong_indicators)) Amber else Mint
    StatusTag(result.source, if (result.source.startsWith("Scripted")) Icons.Outlined.Science else Icons.Outlined.Memory)
    Heading(AdviceCatalog.label(a.risk), "${a.pattern.name.replace('_', ' ').replaceFirstChar { it.uppercase() }} • An indication, not a verdict.")
    Panel("Your next step", AdviceCatalog.nextStep(a), Icons.Outlined.PanTool)
    Text("WHAT SUPPORTS THIS RESULT", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
    if (a.evidence.isEmpty()) Text("No specific warning evidence was returned. This does not mean the conversation is safe.", color = Muted)
    a.evidence.forEach { evidence ->
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(evidence.indicator.name.replace('_', ' ').replaceFirstChar { it.uppercase() }, color = tint, fontSize = 12.sp)
                Text("“${evidence.quote}”", fontSize = 17.sp, lineHeight = 25.sp)
                Text("Exact match in your excerpt • ${evidence.occurrences.size} occurrence(s)", color = Muted, fontSize = 11.sp)
            }
        }
    }
    var expanded by remember(result) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide reviewed excerpt" else "View reviewed excerpt & highlights") }
    if (expanded) {
        Text(buildAnnotatedString {
            append(result.text)
            a.evidence.flatMap { it.occurrences }.forEach { addStyle(SpanStyle(background = Color(0xFF3D3B2B), color = Amber), it.first, it.last + 1) }
        }, modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(14.dp)).padding(16.dp), lineHeight = 25.sp)
    }
    var details by remember(result) { mutableStateOf(false) }
    TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "Details & help") }
    if (details) {
        Panel("Limits of this check", "Kavach cannot verify identity, documents, or whether a claim is true. Context may be missing. English and Hindi are evaluation targets; Hinglish is experimental. Model accuracy has not been established.")
        a.limitations.forEach { Text("Model-reported limitation: $it", color = Muted, fontSize = 12.sp) }
        Text("${result.modelId} • ${result.version}${if (result.elapsedMs > 0) " • ${result.elapsedMs} ms including network" else ""}", color = Muted, fontSize = 11.sp)
        HelpActions()
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Done & clear") }
}

@Composable private fun HelpActions() {
    val context = LocalContext.current
    var error by remember { mutableStateOf(false) }
    fun open(intent: Intent) { error = runCatching { context.startActivity(intent) }.isFailure }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Need help in India?", fontWeight = FontWeight.Medium)
        OutlinedButton(onClick = { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("1930 • open phone dialer") }
        OutlinedButton(onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://cybercrime.gov.in"))) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Official reporting portal • open browser") }
        Text("These actions leave Kavach. No conversation is sent. To block or report a sender, return to the original app.", color = Muted, fontSize = 12.sp)
        if (error) Text("No compatible app found. Use 1930 or cybercrime.gov.in manually.", color = Amber)
    }
}

@Composable internal fun BackendScreen(
    onSamples: () -> Unit = {}, onPrivacy: () -> Unit = {}, beforeChange: () -> Unit = {},
    verify: suspend (BackendConfig) -> Unit = BackendConnection::verify
) {
    val config by BackendSettings.state.collectAsStateWithLifecycle()
    var endpoint by remember { mutableStateOf(config?.endpoint ?: if (BuildConfig.DEBUG) "http://127.0.0.1:8765" else "") }
    var token by remember { mutableStateOf(config?.token ?: "") }
    var addressError by remember { mutableStateOf<String?>(null) }
    var tokenError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Heading("Settings", "Connect once for text, screen and audio checks.")
    OutlinedTextField(endpoint, { if (it.length <= 512) endpoint = it; addressError = null; notice = null }, label = { Text("Backend address") }, singleLine = true, enabled = !busy, isError = addressError != null,
        supportingText = { addressError?.let { Text(it) } }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(token, { if (it.length <= 512) token = it; tokenError = null; notice = null }, label = { Text("Server token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy, isError = tokenError != null,
        supportingText = { Text(tokenError ?: "KAVACH_LIVE_TOKEN from .env") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = {
        addressError = null; tokenError = null; notice = null
        val origin = runCatching { BackendInput.endpoint(endpoint) }.getOrElse { addressError = "Enter an HTTPS address, or http://127.0.0.1:8765 for USB."; null }
        val secret = runCatching { BackendInput.token(token) }.getOrElse { tokenError = it.message ?: "Invalid server token."; null }
        if (origin != null && secret != null) {
            beforeChange(); BackendSettings.clear(); busy = true
            scope.launch {
                try {
                    verify(BackendConfig(origin, secret))
                    BackendSettings.save(origin, secret); endpoint = origin; token = secret
                    notice = "Connected. Open Check or Live to continue."
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { notice = e.message ?: "Connection failed. Check your backend." }
                finally { busy = false }
            }
        }
    }, enabled = BuildConfig.LIVE_FEATURE && !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Connecting…" else "Connect") }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    notice?.let { Text(it, color = if (config != null) Mint else Amber) }
    if (config != null) TextButton(enabled = !busy, onClick = { beforeChange(); BackendSettings.clear(); token = ""; notice = "Disconnected." }) { Text("Disconnect") }
    TextButton(onClick = { showHelp = !showHelp }) { Text(if (showHelp) "Hide setup help" else "Setup help") }
    if (showHelp) Text("Start the backend on your computer. For USB, enable USB debugging and run adb reverse tcp:8765 tcp:8765. Copy KAVACH_LIVE_TOKEN, not META_API_KEY. Connect tests backend access, not Meta model access. Settings reset when the app process closes.", color = Muted)
    HorizontalDivider()
    TextButton(onClick = onPrivacy) { Text("Privacy & permissions") }
    TextButton(onClick = onSamples) { Text("Try sample messages") }
    if (!BuildConfig.LIVE_FEATURE) Text("Install Kavach Live for API checks.")
}
@Composable private fun PrivacyScreen(enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    Heading("Your messages.\nYour decision.", "A companion for text you deliberately share. No access to your personal inboxes.")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("Enable checks"); Text("Each check still needs consent", color = Muted, fontSize = 12.sp) }
        Switch(checked = enabled, onCheckedChange = onEnabled)
    }
    Panel("Only when you ask", "Paste reads the clipboard only when tapped. Android sharing opens an editable preview. Neither action starts inference.", Icons.Outlined.TouchApp)
    Panel("An ephemeral workspace", "On the phone, conversation text and results stay in memory, without history, logging, analytics or backup. Consented inputs are processed by your backend and Meta; Meta processing/retention terms apply. Done & clear discards app references; this is not a guarantee of forensic memory erasure.", Icons.Outlined.Lock)
    Panel("An honest boundary", "Kavach cannot listen to another app’s calls, authenticate documents, access personal inboxes, or block people for you. The Live edition offers opt-in visual assistance by sending selected screen images through your backend to Meta Model API (Muse Spark). Its optional speakerphone mode samples microphone sound using Muse Voice Transcribe and Muse Spark. Android may block or silence it; there is no direct access to another app’s call audio.")
    Panel("About Kavach", "Powered by Meta Muse Spark and Muse Voice Transcribe through Meta Model API. This independent prototype has no Meta or government endorsement. Synthetic demos do not establish real-world accuracy.")
    HelpActions()
}
