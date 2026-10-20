package com.jarvis.robot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Everything the main screen shows, as plain data (so it can be drawn without a ViewModel). */
data class MainUiState(
    val host: String = "",
    val online: Boolean = false,
    val everConnected: Boolean = false,
    val status: Status? = null,
    val pingText: String = "-- ms",
    val settings: List<SettingDef> = emptyList(),
    val settingsError: String? = null,
    val feedback: Map<String, Feedback> = emptyMap(),
    val held: List<Dir> = emptyList(),
    val powerFeedback: Feedback? = null,
    val sleepingNow: Boolean = false,
    val serverFeedback: Feedback? = null,
    val backendPin: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val photo: ImageBitmap? = null,
    val photoTime: String? = null,
    val photoBusy: Boolean = false,
    val photoError: String? = null,
    val palette: AppPalette = AppPalette.CLASSIC,
    val haOk: Boolean = false,
)

/** What the main screen can ask for; the ViewModel implements these, tests and previews pass no-ops. */
class MainActions(
    val onChangeAddress: () -> Unit = {},
    val onNap: (currentlyNapping: Boolean) -> Unit = {},
    val onDeepSleep: (minutes: Int) -> Unit = {},
    val onHold: (Dir, Boolean) -> Unit = { _, _ -> },
    val onReleaseAll: () -> Unit = {},
    val onSave: (key: String, value: String) -> Unit = { _, _ -> },
    val onReset: ((Boolean) -> Unit) -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onPalette: (AppPalette) -> Unit = {},
    val onPhoto: () -> Unit = {},
    val onSetVolume: (Int) -> Unit = {},
    val onSetServer: (String) -> Unit = {},
)

@Composable
fun MainScreen(vm: RobotViewModel, onChangeAddress: () -> Unit) {
    val state = MainUiState(
        host = vm.host,
        online = vm.online,
        everConnected = vm.everConnected,
        status = vm.status,
        pingText = vm.pingText,
        settings = vm.settings,
        settingsError = vm.settingsError,
        feedback = vm.feedback,
        held = vm.held,
        powerFeedback = vm.powerFeedback,
        sleepingNow = vm.sleepingNow,
        serverFeedback = vm.serverFeedback,
        backendPin = vm.backendPin,
        themeMode = vm.themeMode,
        photo = vm.photo,
        photoTime = vm.photoTime,
        photoBusy = vm.photoBusy,
        photoError = vm.photoError,
        haOk = vm.haOk,
        palette = vm.palette,
    )
    val actions = remember(vm) {
        MainActions(
            onChangeAddress = onChangeAddress,
            onNap = vm::nap,
            onDeepSleep = vm::deepSleep,
            onHold = vm::hold,
            onReleaseAll = vm::releaseAll,
            onSave = vm::save,
            onReset = vm::resetAll,
            onTheme = vm::setTheme,
            onPalette = vm::choosePalette,
            onPhoto = vm::takePhoto,
            onSetVolume = vm::setVolume,
            onSetServer = vm::setServer,
        )
    }
    MainContent(state, actions)
}

/** Width from which the screen is laid out as a tablet (the Fold's inner screen); below it the single column of the phone. */
const val TABLET_MIN_DP = 600

/** Picks the layout from the window's width, so folding or unfolding the phone switches it live. */
@Composable
fun MainContent(
    state: MainUiState,
    actions: MainActions,
    initialShowVoice: Boolean = false,
    initialShowRemote: Boolean = false,
    initialOpenGroups: Set<String> = emptySet(),
    initialScrollPx: Int = 0,
    initialSection: String = SECTION_OVERVIEW,
    initialShowCamera: Boolean = false,
) {
    if (LocalConfiguration.current.screenWidthDp >= TABLET_MIN_DP) {
        TabletContent(state, actions, initialSection)
    } else {
        PhoneContent(state, actions, initialShowVoice, initialShowRemote, initialOpenGroups, initialScrollPx, initialShowCamera)
    }
}

@Composable
fun PhoneContent(
    state: MainUiState,
    actions: MainActions,
    initialShowVoice: Boolean = false,
    initialShowRemote: Boolean = false,
    initialOpenGroups: Set<String> = emptySet(),
    initialScrollPx: Int = 0,
    initialShowCamera: Boolean = false,
) {
    val p = LocalPalette.current
    var showVoice by rememberSaveable { mutableStateOf(initialShowVoice) }
    var showRemote by rememberSaveable { mutableStateOf(initialShowRemote) }
    val openGroups = remember { mutableStateMapOf<String, Boolean>().apply { initialOpenGroups.forEach { put(it, true) } } }
    var confirmReset by remember { mutableStateOf(false) }
    var resetNote by remember { mutableStateOf<Feedback?>(null) }
    var showBackend by rememberSaveable { mutableStateOf(false) }
    var showCamera by rememberSaveable { mutableStateOf(initialShowCamera) }
    val status = state.status

    Box(Modifier.fillMaxSize()) {
        ThemeBackdrop()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState(initialScrollPx))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---- header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (state.online) p.success else p.danger))
                Column(Modifier.weight(1f)) {
                    Text("Jarvis settings", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    val ssid = status?.ssid.orEmpty()
                    val ip = status?.ip.orEmpty().ifEmpty { state.host }
                    Text(
                        if (state.online) (if (ssid.isNotEmpty()) "$ssid · $ip" else ip) else "Could not reach Jarvis",
                        color = p.muted,
                        fontSize = 13.sp,
                    )
                }
                TextButton(onClick = actions.onChangeAddress) { Text("Address", color = p.accent) }
            }

            // On a theme with a photo the table sits on a card, so the small text stays readable.
            if (p.photo != null) JCard { InfoTable(status, state.pingText) } else InfoTable(status, state.pingText)

            if (!state.online) {
                JCard {
                    Text(
                        if (state.everConnected) "Lost the connection to Jarvis. It may be napping off WiFi, asleep, or out of range."
                        else "No answer from ${state.host}. Check the address and that this phone is on the robot's WiFi.",
                        color = p.muted,
                        fontSize = 13.sp,
                    )
                    JButton("Change address", Modifier.fillMaxWidth(), onClick = actions.onChangeAddress)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                JButton(if (showVoice) "Hide commands" else "Voice commands", Modifier.weight(1f)) {
                    showVoice = !showVoice
                }
                JButton(if (showRemote) "Hide remote" else "Remote control", Modifier.weight(1f)) {
                    showRemote = !showRemote
                    if (!showRemote) actions.onReleaseAll()
                }
            }
            JButton(if (state.photoBusy) "Taking photo..." else "Camera", Modifier.fillMaxWidth()) {
                showCamera = true
                actions.onPhoto()
            }
            if (showCamera) CameraPanel(state, onHide = { showCamera = false })
            if (showVoice) VoiceCommandsPanel(haOk = state.haOk)

            PowerCard(
                status = status,
                feedback = state.powerFeedback,
                sleepingNow = state.sleepingNow,
                onNap = actions.onNap,
                onDeepSleep = actions.onDeepSleep,
            )

            VolumeCard(status, actions.onSetVolume)
            ServerCard(status, state.serverFeedback, state.backendPin, actions.onSetServer, onOpenBackend = { showBackend = true })

            if (showRemote) RemotePanel(state.held, actions.onHold)

            // ---- settings
            if (state.settings.isEmpty()) {
                Text(
                    state.settingsError ?: if (state.online) "Loading settings..." else "",
                    color = p.muted,
                )
            }
            for ((group, defs) in groupSettings(state.settings)) {
                if (group.collapsible) {
                    val open = openGroups[group.title] == true
                    GroupToggle(group.title, open) { openGroups[group.title] = !open }
                    if (open) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (def in defs) SettingCard(def, state.feedback[def.key]) { v -> actions.onSave(def.key, v) }
                        }
                    }
                } else {
                    SectionTitle(group.title)
                    for (def in defs) SettingCard(def, state.feedback[def.key]) { v -> actions.onSave(def.key, v) }
                }
            }

            Spacer(Modifier.height(8.dp))
            JButton("Reset all to defaults", Modifier.fillMaxWidth(), danger = true) { confirmReset = true }
            resetNote?.let { Text(it.text, color = if (it.ok) p.success else p.danger, fontSize = 13.sp) }
            Text(
                "Changes apply and save right away. On your home network only.",
                color = p.muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Spacer(Modifier.height(64.dp))
        }

        ThemeSwitch(
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(14.dp),
            palette = state.palette,
            onPalette = actions.onPalette,
            onChange = actions.onTheme,
        )

        // The backend page opens over this screen (back returns here, the scroll position is kept).
        if (showBackend) BackendScreen(state) { showBackend = false }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all settings?") },
            text = { Text("Every setting goes back to its default. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    actions.onReset { ok ->
                        resetNote = Feedback(ok, if (ok) "All settings reset to defaults." else "Could not reset the settings.")
                    }
                }) { Text("Reset", color = p.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ConnectScreen(vm: RobotViewModel, canCancel: Boolean, onDone: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(vm.host) }
    var checking by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanNote by remember { mutableStateOf<String?>(null) }
    val found = remember { mutableStateListOf<String>() }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        ThemeBackdrop()
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp) // on the unfolded screen the form stays a readable width in the middle
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Connect to Jarvis", color = p.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Enter the robot's IP address (shown on its screen status and by !status over USB), " +
                    "or scan your WiFi network for it. This phone must be on the same WiFi as the robot.",
                color = p.muted,
                fontSize = 14.sp,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                label = { Text("Robot address") },
                placeholder = { Text("192.168.1.50") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                JButton(if (checking) "Checking..." else "Connect", Modifier.weight(1f)) {
                    val clean = normalizeHost(text)
                    if (clean.isEmpty() || checking) return@JButton
                    checking = true
                    scope.launch {
                        val ok = vm.probe(clean)
                        checking = false
                        if (ok) {
                            vm.connectTo(clean)
                            onDone()
                        } else {
                            error = "No Jarvis answered at $clean. Check the address and the WiFi."
                        }
                    }
                }
                JButton(if (scanning) "Scanning..." else "Scan network", Modifier.weight(1f)) {
                    if (scanning) return@JButton
                    val prefix = NetworkUtil.subnetPrefix(context)
                    if (prefix == null) {
                        scanNote = "This phone isn't on a WiFi network."
                        return@JButton
                    }
                    scanning = true
                    scanNote = null
                    found.clear()
                    scope.launch {
                        val hits = NetworkUtil.scanForRobots(prefix) { host -> found.add(host) }
                        scanning = false
                        scanNote = if (hits.isEmpty()) "No robot found on ${prefix}x." else null
                    }
                }
            }
            error?.let {
                Text(it, color = p.danger, fontSize = 13.sp)
                TextButton(onClick = {
                    val clean = normalizeHost(text)
                    if (clean.isNotEmpty()) {
                        vm.connectTo(clean)
                        onDone()
                    }
                }) { Text("Use this address anyway", color = p.accent) }
            }
            scanNote?.let { Text(it, color = p.muted, fontSize = 13.sp) }
            for (host in found) {
                JCard(Modifier.clickable { text = host; error = null }) {
                    Text(host, color = p.text, fontWeight = FontWeight.Medium)
                    Text("Found a Jarvis here — tap to use it", color = p.muted, fontSize = 13.sp)
                }
            }
            if (canCancel) {
                TextButton(onClick = onDone) { Text("Cancel", color = p.muted) }
            }
        }
    }
}
