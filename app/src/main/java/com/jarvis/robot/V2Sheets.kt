package com.jarvis.robot

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

// The parts of the classic screen that v2 first borrowed (voice server, photo, info table, connect screen), rebuilt in the v2 look with the same behaviour.

// ---------------------------------------------------------------------------------------------------------------------------------- voice server
/** Own server or the xiaozhi.me cloud: the same choices as the classic Voice server card, each confirmed first (Jarvis restarts). */
@Composable
fun V2ServerContent(state: MainUiState, actions: MainActions) {
    val v = v2Colors()
    val s = state.status
    val server = s?.server
    if (server == null) {
        Text("Jarvis has not said which voice server it uses yet.", color = v.muted, fontSize = 14.sp)
        return
    }
    val own = s.serverCustom
    val hostPart = Regex("^[a-z]+://([^/]+)", RegexOption.IGNORE_CASE).find(server)?.groupValues?.get(1) ?: server
    var address by remember(hostPart, own) { mutableStateOf(if (own) hostPart.removeSuffix(":8003") else "") }
    var confirm by remember { mutableStateOf<String?>(null) }
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        V2Connected(listOf(true to "Own server", false to "xiaozhi.me cloud"), own, { wantOwn -> if (!wantOwn && own) confirm = "default" })
        Text(if (own) "Talking to $hostPart" else "Using the built-in default server.", color = v.muted, fontSize = 13.sp)
        V2Field(address, { address = it.trim() }, "Own server address", "192.168.x.y or name.local", keyboard = KeyboardType.Uri, onDone = { if (address.isNotEmpty()) confirm = address })
        Text("Port 8003 is assumed; add :port to change it. Only addresses on your own network are accepted.", color = v.muted, fontSize = 12.sp)
        V2Button("Save and restart Jarvis", { if (address.isNotEmpty()) confirm = address }, Modifier.fillMaxWidth(), enabled = address.isNotEmpty() && (!own || address != hostPart.removeSuffix(":8003")))
        if (own && s.panelUrl.isNotEmpty()) {
            V2Button("Open the backend in the browser", { uri.openUri(s.panelUrl) }, Modifier.fillMaxWidth(), filled = false)
            if (state.backendPin.isNotEmpty()) Text("Backend PIN for other devices: ${state.backendPin}", color = v.muted, fontSize = 13.sp)
        }
        state.serverFeedback?.let { Text(it.text, color = if (it.ok) v.success else v.danger, fontSize = 13.sp) }
    }
    confirm?.let { value ->
        V2Dialog(
            title = "Change the voice server",
            text = if (value == "default") "Go back to the xiaozhi.me cloud? Jarvis restarts." else "Use your own server at $value? Jarvis restarts and talks to it.",
            confirmLabel = "Save",
            onConfirm = { confirm = null; actions.onSetServer(value) },
            onDismiss = { confirm = null },
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------- photo
/** The last picture (tap for full screen), when it was taken, why there is none, and Take another. */
@Composable
fun V2PhotoContent(state: MainUiState, actions: MainActions) {
    val v = v2Colors()
    var full by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val photo = state.photo
        Box(
            Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(24.dp)).background(v.surfaceHigh)
                .let { if (photo != null) it.clickable(role = Role.Image, onClickLabel = tr("Show full screen")) { full = true } else it },
            contentAlignment = Alignment.Center,
        ) {
            when {
                photo != null -> Image(photo, contentDescription = tr("Photo from Jarvis's camera"), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                state.photoBusy -> Text("Taking a photo…", color = v.muted, fontSize = 14.sp)
                else -> Icon(V2Icons.Camera, contentDescription = null, tint = v.muted, modifier = Modifier.size(36.dp))
            }
        }
        state.photoTime?.let { Text("Taken at $it · tap the picture for full screen", color = v.muted, fontSize = 13.sp) }
        state.photoError?.let { Text("No picture: $it", color = v.danger, fontSize = 13.sp) }
        V2Button(if (state.photoBusy) "Taking a photo…" else if (photo == null) "Take a photo" else "Take another", actions.onPhoto, Modifier.fillMaxWidth(), enabled = !state.photoBusy && state.online)
        if (full && photo != null) PhotoFullScreen(photo) { full = false }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------- all details
/** Every line of the classic info table, as a list. */
@Composable
fun V2DetailsList(state: MainUiState) {
    val v = v2Colors()
    val s = state.status
    val music = !state.music?.title.isNullOrEmpty()
    val rows = listOfNotNull(
        "Board" to (s?.boardName.orEmpty().ifEmpty { "--" }),
        s?.chipTempC?.let { "Chip temperature" to "%.1f °C".format(Locale.US, it) },
        "Camera detected" to when { s == null -> "--"; s.cameraDetected -> "YES" + if (s.cameraSensor.isNotEmpty()) " (${s.cameraSensor})" else ""; else -> "NO" },
        "MAC address" to (s?.mac.orEmpty().ifEmpty { "--" }),
        "Ping" to state.pingText,
        "Mode" to when (s?.power) { "console" -> "Console"; null, "" -> "--"; else -> "Battery" },
        "Status" to (s?.let { statusName(it, music) } ?: "--"),
        "Uptime" to (s?.let { fmtUptime(it.uptimeS) } ?: "--"),
        "FPS" to (s?.fps?.toString() ?: "--"),
        "CPU" to ((s?.cpuLoad?.let { "$it%" } ?: "--") + (s?.cpuMhz?.let { " · $it MHz" } ?: "")),
        "PSRAM" to (s?.let { "%.1f MB free".format(Locale.US, it.psramFreeKb / 1024.0) } ?: "--"),
        "RAM" to (s?.let { fmtUsage(it.ramTotalKb - it.ramFreeKb, it.ramTotalKb) } ?: "--"),
        "HEAP" to (s?.heapLargestKb?.let { "$it KB" } ?: "--"),
        "WIFI" to (s?.rssi?.let { "$it dBm" } ?: "--"),
        "AUDIO" to (s?.let { fmtAudio(it) } ?: "--"),
        "TX / RX" to (state.netRate?.let { "%.1f / %.1f p/s".format(Locale.US, it.txPps, it.rxPps) } ?: "--"),
    )
    V2Card {
        rows.forEachIndexed { i, (k, value) ->
            if (i > 0) V2Divider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k, color = v.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(value, color = if (k == "Status") v.accent else v.text, fontSize = 14.sp, fontWeight = if (k == "Status") FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------- connect
/** Which robot: type its address or scan the Wi-Fi, the same checks as the classic connect screen (only home-network addresses, a probe first). */
@Composable
fun V2ConnectScreen(vm: RobotViewModel, canCancel: Boolean, onDone: () -> Unit) {
    val v = v2Colors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(vm.host) }
    var checking by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanNote by remember { mutableStateOf<String?>(null) }
    val found = remember { mutableStateListOf<String>() }

    fun connect() {
        val clean = normalizeHost(text)
        if (clean.isEmpty() || checking) return
        if (!NetworkUtil.isHomeAddress(clean)) {
            error = "Only addresses on your own network are accepted (192.168.x.x, 10.x.x.x, 172.16-31.x.x, a .local name, or Tailscale 100.x.x.x)."
            return
        }
        checking = true
        scope.launch {
            val ok = vm.probe(clean)
            checking = false
            if (ok) { vm.connectTo(clean); onDone() } else error = "No Jarvis answered at $clean. Check the address and the WiFi."
        }
    }

    fun scan() {
        if (scanning) return
        if (BuildConfig.DEMO) {
            scanning = true; scanNote = null; found.clear()
            scope.launch { kotlinx.coroutines.delay(1400); found.add("192.168.1.50"); scanning = false }
            return
        }
        val prefix = NetworkUtil.subnetPrefix(context)
        if (prefix == null) { scanNote = "This phone isn't on a WiFi network."; return }
        scanning = true; scanNote = null; found.clear()
        scope.launch {
            val hits = NetworkUtil.scanForRobots(prefix) { host -> found.add(host) }
            scanning = false
            scanNote = if (hits.isEmpty()) "No robot found on ${prefix}x." else null
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        ThemeBackdrop()
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (canCancel) V2IconButton(V2Icons.Back, "Cancel", onDone)
                Text("Connect to Jarvis", color = v.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            V2Eyes(awake = true, modifier = Modifier.size(96.dp).align(Alignment.CenterHorizontally))
            Text("This phone must be on the same Wi-Fi as the robot. Its address is on the robot's screen status, or let the app look for it.", color = v.muted, fontSize = 14.sp)
            V2Field(text, { text = it; error = null }, "Robot address", "192.168.1.50", keyboard = KeyboardType.Uri, onDone = { connect() })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V2Button(if (checking) "Checking…" else "Connect", { connect() }, Modifier.weight(1f), enabled = !checking && text.isNotBlank())
                V2Button(if (scanning) "Scanning…" else "Scan network", { scan() }, Modifier.weight(1f), filled = false, enabled = !scanning)
            }
            error?.let {
                Text(it, color = v.danger, fontSize = 13.sp)
                V2Button("Use this address anyway", { val c = normalizeHost(text); if (c.isNotEmpty()) { vm.connectTo(c); onDone() } }, Modifier.fillMaxWidth(), filled = false)
            }
            scanNote?.let { Text(it, color = v.muted, fontSize = 13.sp) }
            for (host in found) {
                V2Tile(Modifier.fillMaxWidth(), onClick = { text = host; error = null; connect() }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        V2IconBadge(V2Icons.Robot, v.accentContainer, v.onAccentContainer)
                        Column(Modifier.weight(1f)) {
                            Text(host, color = v.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Found a Jarvis here · tap to connect", color = v.muted, fontSize = 13.sp)
                        }
                        Icon(V2Icons.Chevron, contentDescription = null, tint = v.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
