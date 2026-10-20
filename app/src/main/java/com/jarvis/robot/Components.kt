package com.jarvis.robot

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

// ------------------------------------------------------------------ basics

@Composable
fun jSwitchColors(): SwitchColors {
    val p = LocalPalette.current
    return SwitchDefaults.colors(
        checkedThumbColor = if (p.isDark) p.bg else Color.White,
        checkedTrackColor = p.accent,
        checkedBorderColor = p.accent,
        uncheckedThumbColor = p.muted,
        uncheckedTrackColor = p.field,
        uncheckedBorderColor = p.border,
    )
}

@Composable
fun JCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (p.photo != null) p.card.copy(alpha = 0.9f) else p.card)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun JButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    val tint = if (danger) p.danger else p.accent
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, tint),
    ) {
        Text(text, color = tint, fontSize = 15.sp)
    }
}

/** The Motors / Audio / Screen section buttons: tap to expand the settings below. */
@Composable
fun GroupToggle(title: String, open: Boolean, onToggle: () -> Unit) {
    val p = LocalPalette.current
    OutlinedButton(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, p.accent),
    ) {
        Text(title, color = p.accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(if (open) "▼" else "▶", color = p.accent, fontSize = 14.sp)
    }
}

@Composable
fun SectionTitle(text: String) {
    val p = LocalPalette.current
    Text(
        text.uppercase(Locale.getDefault()),
        color = p.muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
    )
}

// ------------------------------------------------------------------ device info table

@Composable
fun InfoTable(status: Status?, pingText: String) {
    val s = status
    val left = listOf(
        "Board" to (s?.boardName.orEmpty().ifEmpty { "--" }),
        "Camera detected" to when {
            s == null -> "--"
            s.cameraDetected -> "YES" + if (s.cameraSensor.isNotEmpty()) " (${s.cameraSensor})" else ""
            else -> "NO"
        },
        "MAC address" to (s?.mac.orEmpty().ifEmpty { "--" }),
        "Ping" to pingText,
        "Mode" to when (s?.power) {
            "console" -> "Console"
            null, "" -> "--"
            else -> "Battery"
        },
        "Status" to (s?.let { statusName(it) } ?: "--"),
        "Uptime" to (s?.let { fmtUptime(it.uptimeS) } ?: "--"),
    )
    val right = listOf(
        "FPS" to (s?.fps?.toString() ?: "--"),
        "CPU" to ((s?.cpuLoad?.let { "$it%" } ?: "--") + (s?.cpuMhz?.let { " · $it MHz" } ?: "")),
        "PSRAM" to (s?.let { "%.1f MB free".format(Locale.US, it.psramFreeKb / 1024.0) } ?: "--"),
        "RAM" to (s?.let { fmtUsage(it.ramTotalKb - it.ramFreeKb, it.ramTotalKb) } ?: "--"),
        "HEAP" to (s?.heapLargestKb?.let { "$it KB" } ?: "--"),
        "WIFI" to (s?.rssi?.let { "$it dBm" } ?: "--"),
        "AUDIO" to (s?.let { fmtAudio(it) } ?: "--"),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoColumn(left, labelWidth = 104, highlight = "Status", modifier = Modifier.weight(1.6f))
        InfoColumn(right, labelWidth = 46, highlight = null, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InfoColumn(rows: List<Pair<String, String>>, labelWidth: Int, highlight: String?, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        for ((k, v) in rows) {
            Row {
                Text(k, color = p.muted, fontSize = 11.5.sp, maxLines = 1, modifier = Modifier.width(labelWidth.dp))
                Text(
                    v,
                    color = if (k == highlight) p.accent else p.text,
                    fontWeight = if (k == highlight) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ voice commands

@Composable
fun VoiceCommandsPanel(columns: Int = 1, haOk: Boolean = false) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Original commands (cyan): understood by the brain, work with any server.", color = p.accent, fontSize = 12.sp)
        Text("Instant commands (orange): only with your own server. Done at once, no brain, no waiting; they keep working when the brain is down.", color = p.instant, fontSize = 12.sp)
        if (haOk) Text("Home commands (purple): only while your Home Assistant answers (Home Assistant card in the backend).", color = p.ha, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (c in 0 until columns) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((group, items) in VOICE_COMMANDS.filter { haOk || it.second.none { c -> c.ha } }.filterIndexed { i, _ -> i % columns == c }) {
                        val instant = items.any { it.instant }
                        val home = items.any { it.ha }
                        JCard {
                            Text(group, color = if (home) p.ha else if (instant) p.instant else p.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            for (item in items) {
                                Column {
                                    Text(item.title, color = p.text, fontSize = 14.sp)
                                    Text(item.example, color = p.muted, fontSize = 13.sp, fontStyle = FontStyle.Italic)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ power card

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PowerCard(
    status: Status?,
    feedback: Feedback?,
    sleepingNow: Boolean,
    onNap: (currentlyNapping: Boolean) -> Unit,
    onDeepSleep: (minutes: Int) -> Unit,
) {
    val p = LocalPalette.current
    var panelOpen by remember { mutableStateOf(false) }
    var hours by remember { mutableStateOf("1") }
    var minutes by remember { mutableStateOf("0") }
    var confirm by remember { mutableStateOf(false) }
    val total = sleepMinutes(hours.toIntOrNull() ?: 0, minutes.toIntOrNull() ?: 0)
    val napping = status?.napping == true

    JCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Power", fontWeight = FontWeight.SemiBold, color = p.text)
            Text(
                if (sleepingNow) "Going to sleep..." else "Status: " + (status?.let { statusName(it) } ?: "--"),
                color = p.accent,
                fontSize = 13.sp,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            JButton(if (napping) "Wake up" else "Nap", Modifier.weight(1f)) { onNap(napping) }
            JButton("Deep sleep…", Modifier.weight(1f)) { panelOpen = !panelOpen }
        }
        if (panelOpen) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sleep for", color = p.muted, fontSize = 14.sp)
                NumberField(hours, { hours = it.take(2) }, "Hours")
                Text("hrs", color = p.muted, fontSize = 14.sp)
                NumberField(minutes, { minutes = it.take(2) }, "Minutes")
                Text("min", color = p.muted, fontSize = 14.sp)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((label, mins) in listOf("30 min" to 30, "1 hr" to 60, "2 hrs" to 120, "8 hrs" to 480, "Until BOOT button" to 0)) {
                    ChipButton(label) {
                        hours = (mins / 60).toString()
                        minutes = (mins % 60).toString()
                    }
                }
            }
            Text(
                "Wi-Fi and the wake word switch off, so the robot stops responding. Jarvis wakes " +
                    sleepText(total) + (if (total > 0) " or when you press the BOOT button" else "") +
                    ". 0 hrs 0 min = until the BOOT button.",
                color = p.muted,
                fontSize = 13.sp,
            )
            JButton("Go to deep sleep", Modifier.fillMaxWidth(), danger = true) { confirm = true }
        }
        feedback?.let { Text(it.text, color = if (it.ok) p.success else p.danger, fontSize = 13.sp) }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Deep sleep") },
            text = { Text("Deep sleep ${sleepText(total)}? Wi-Fi turns off and the robot stops responding.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    panelOpen = false
                    onDeepSleep(total)
                }) { Text("Sleep", color = p.danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, description: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(76.dp),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        placeholder = { Text(description, fontSize = 11.sp) },
    )
}

@Composable
private fun ChipButton(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, p.accent),
        modifier = Modifier.height(36.dp),
    ) { Text(text, color = p.accent, fontSize = 13.sp) }
}

// ------------------------------------------------------------------ volume card

/** The slider sends the value when the finger lifts; the speaker plays the lower of this and the night-mode limit. */
@Composable
fun VolumeCard(status: Status?, onSet: (Int) -> Unit) {
    val p = LocalPalette.current
    val requested = status?.volumeRequested ?: return
    val played = status.volume ?: requested
    val cap = status.volumeCap ?: 100
    var live by remember { mutableFloatStateOf(requested.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    if (!dragging && live.roundToInt() != requested) live = requested.toFloat()
    JCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Volume", fontWeight = FontWeight.SemiBold, color = p.text)
            Text("${if (dragging) live.roundToInt() else played}%", color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = live,
            onValueChange = { dragging = true; live = it },
            onValueChangeFinished = { onSet(live.roundToInt()); dragging = false },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = p.accent,
                activeTrackColor = p.accent,
                inactiveTrackColor = p.border,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Text(
            if (requested > cap) "Asked for $requested%, but night mode (quiet hours) limits it to $cap%." else "100% is full volume.",
            color = p.muted,
            fontSize = 13.sp,
        )
    }
}

// ------------------------------------------------------------------ voice server card

@Composable
fun ServerCard(
    status: Status?,
    feedback: Feedback?,
    backendPin: String,
    onSetServer: (String) -> Unit,
    onOpenBackend: (() -> Unit)? = null, // null: open the page in the browser; the tablet shows it right below instead (showOpen = false)
    showOpen: Boolean = true,
) {
    val p = LocalPalette.current
    val server = status?.server ?: return
    val own = status.serverCustom
    val hostPart = Regex("^[a-z]+://([^/]+)", RegexOption.IGNORE_CASE).find(server)?.groupValues?.get(1) ?: server
    var editing by remember { mutableStateOf(false) }
    var address by remember(hostPart, own) { mutableStateOf(if (own) hostPart.removeSuffix(":8003") else "") }
    var confirm by remember { mutableStateOf<String?>(null) }   // the value waiting for a yes
    val uri = LocalUriHandler.current

    JCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Voice server", fontWeight = FontWeight.SemiBold, color = p.text)
            Text(if (own) "Own server" else "Cloud (xiaozhi.me)", color = p.accent, fontSize = 13.sp)
        }
        Text(if (own) "Talking to $hostPart" else "Using the built-in default server.", color = p.muted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            JButton("Own server…", Modifier.weight(1f)) { editing = !editing }
            JButton("Use cloud", Modifier.weight(1f)) { if (own) confirm = "default" }
        }
        if (own && status.panelUrl.isNotEmpty()) {
            if (showOpen) {
                JButton(if (onOpenBackend != null) "Open Jarvis backend" else "Open Jarvis backend ↗", Modifier.fillMaxWidth()) {
                    if (onOpenBackend != null) onOpenBackend() else uri.openUri(status.panelUrl)
                }
            }
            if (backendPin.isNotEmpty()) {
                Text("Backend PIN for other devices: $backendPin", color = p.muted, fontSize = 13.sp)
            }
        }
        if (editing) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.trim() },
                singleLine = true,
                label = { Text("192.168.x.y or name.local") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Port 8003 is assumed; add :port to change it. Only addresses on your own network are accepted.", color = p.muted, fontSize = 13.sp)
            JButton("Save and restart Jarvis", Modifier.fillMaxWidth()) { if (address.isNotEmpty()) confirm = address }
        }
        feedback?.let { Text(it.text, color = if (it.ok) p.success else p.danger, fontSize = 13.sp) }
    }
    confirm?.let { value ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Change the voice server") },
            text = {
                Text(
                    if (value == "default") "Go back to the xiaozhi.me cloud? Jarvis restarts."
                    else "Use your own server at $value? Jarvis restarts and talks to it.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirm = null; editing = false; onSetServer(value) }) { Text("Save", color = p.accent) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

// ------------------------------------------------------------------ camera

/** The camera window: the last picture the robot took (it stays until the next one), when it was taken, or why there is none. */
@Composable
fun CameraPanel(state: MainUiState, onHide: (() -> Unit)? = null, onTake: (() -> Unit)? = null) {
    val p = LocalPalette.current
    JCard {
        if (onTake != null) {
            JButton(if (state.photoBusy) "Taking photo..." else if (state.photo == null) "Take a picture" else "Take another", Modifier.fillMaxWidth(), onClick = onTake)
        }
        val photo = state.photo
        if (photo != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Image(
                    bitmap = photo,
                    contentDescription = "Photo from Jarvis's camera",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, p.border, RoundedCornerShape(10.dp)),
                )
            }
            state.photoTime?.let { Text("Taken at $it. Tap Camera again for a new one.", color = p.muted, fontSize = 13.sp) }
        } else if (state.photoBusy) {
            Text("Taking a picture...", color = p.muted, fontSize = 13.sp)
        }
        state.photoError?.let { Text("No picture: $it", color = p.danger, fontSize = 13.sp) }
        if (onHide != null) TextButton(onClick = onHide) { Text("Hide picture", color = p.muted) }
    }
}

// ------------------------------------------------------------------ music

/** The backend's music player: what is playing (a short clip of a song, chosen by voice) and Stop / Next. Shown while the module is on. */
@Composable
fun MusicCard(music: MusicStatus, onStop: () -> Unit, onNext: () -> Unit) {
    val p = LocalPalette.current
    JCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Music", fontWeight = FontWeight.SemiBold, color = p.text)
            Text("${music.tracks} songs", color = p.muted, fontSize = 13.sp)
        }
        val playing = !music.title.isNullOrEmpty()
        Text(
            if (playing) "Playing: ${music.title}" + (music.artist?.takeIf { it.isNotEmpty() }?.let { " - $it" } ?: "")
            else "Nothing playing. Say: Jarvis, play something random.",
            color = if (playing) p.accent else p.muted,
            fontSize = 14.sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            JButton("Stop", Modifier.weight(1f), danger = true, onClick = onStop)
            JButton("Next song", Modifier.weight(1f), onClick = onNext)
        }
    }
}

// ------------------------------------------------------------------ remote control

@Composable
fun RemotePanel(held: List<Dir>, onHold: (Dir, Boolean) -> Unit) {
    val p = LocalPalette.current
    // Keep the screen awake while the pad is open, so the phone doesn't lock mid-drive.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    JCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PadButton("▲", Dir.UP in held) { onHold(Dir.UP, it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PadButton("◀", Dir.LEFT in held) { onHold(Dir.LEFT, it) }
                Spacer(Modifier.size(76.dp))
                PadButton("▶", Dir.RIGHT in held) { onHold(Dir.RIGHT, it) }
            }
            PadButton("▼", Dir.DOWN in held) { onHold(Dir.DOWN, it) }
            Text(
                "Hold an arrow to drive (full speed), let go to stop. Two arrows together turn in an arc.",
                color = p.muted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun PadButton(glyph: String, pressed: Boolean, onHold: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(shape)
            .background(if (pressed) p.accent.copy(alpha = 0.22f) else p.card)
            .border(1.dp, p.accent, shape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onHold(true)
                    try {
                        tryAwaitRelease()
                    } finally {
                        onHold(false)
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = p.accent, fontSize = 30.sp)
    }
}

// ------------------------------------------------------------------ one setting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingCard(def: SettingDef, feedback: Feedback?, onSave: (String) -> Unit) {
    val p = LocalPalette.current
    var live by remember(def.value) { mutableFloatStateOf(def.value.toFloatOrNull() ?: def.min.toFloat()) }
    val shown = when (def.kind) {
        Kind.INT, Kind.HUNDREDTHS -> def.copy(value = sliderValueText(def, live))
        else -> def
    }
    JCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(labelFor(def.key), color = p.text, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(fmtValue(shown), color = p.accent, fontWeight = FontWeight.SemiBold)
        }
        when (def.kind) {
            Kind.INT, Kind.HUNDREDTHS -> {
                val span = (def.max - def.min).coerceAtLeast(def.step)
                val steps = ((span / def.step).roundToInt() - 1).coerceAtLeast(0)
                Slider(
                    value = live.coerceIn(def.min.toFloat(), def.max.toFloat()),
                    onValueChange = { live = it },
                    onValueChangeFinished = { onSave(sliderValueText(def, live)) },
                    valueRange = def.min.toFloat()..def.max.toFloat(),
                    steps = steps,
                    colors = SliderDefaults.colors(
                        thumbColor = p.accent,
                        activeTrackColor = p.accent,
                        inactiveTrackColor = p.border,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
                )
            }
            Kind.BOOL -> Switch(checked = def.value == "1", onCheckedChange = { onSave(if (it) "1" else "0") }, colors = jSwitchColors())
            Kind.ENUM -> EnumPicker(def.value, def.options, onSave)
            Kind.TIME -> TimeRow(def.value, onSave)
            Kind.TEXT -> TextRow(def, onSave)
        }
        Text(def.help, color = p.muted, fontSize = 13.sp)
        feedback?.let { Text(it.text, color = if (it.ok) p.success else p.danger, fontSize = 13.sp) }
    }
}

private fun sliderValueText(def: SettingDef, v: Float): String =
    if (def.kind == Kind.HUNDREDTHS) "%.2f".format(Locale.US, v) else v.roundToInt().toString()

@Composable
private fun EnumPicker(current: String, options: List<String>, onSave: (String) -> Unit) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, p.border),
        ) {
            Text(current, color = p.text, modifier = Modifier.weight(1f))
            Text("▼", color = p.muted, fontSize = 12.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (option in options) {
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    open = false
                    if (option != current) onSave(option)
                })
            }
        }
    }
}

@Composable
private fun TimeRow(value: String, onSave: (String) -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val off = value.isEmpty()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = {
                val parts = value.split(':')
                val h = parts.getOrNull(0)?.toIntOrNull() ?: 22
                val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                TimePickerDialog(context, { _, hh, mm -> onSave("%02d:%02d".format(Locale.US, hh, mm)) }, h, m, true).show()
            },
            enabled = !off,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, p.border),
            modifier = Modifier.weight(1f).height(46.dp),
        ) { Text(if (off) "--:--" else value, color = if (off) p.muted else p.text) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = off, onCheckedChange = { checked -> onSave(if (checked) "off" else "22:00") })
            Text("Off", color = p.muted)
        }
    }
}

@Composable
private fun TextRow(def: SettingDef, onSave: (String) -> Unit) {
    val focus = LocalFocusManager.current
    var text by remember(def.value) { mutableStateOf(def.value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(def.maxLength) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && text.trim() != def.value) onSave(text.trim()) },
        placeholder = { Text(if (def.key == "weather_city") "e.g. Amsterdam (leave empty for off)" else "(leave empty for off)") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
    )
}

// ------------------------------------------------------------------ theme switch

@Composable
fun ThemeSwitch(
    modifier: Modifier = Modifier,
    palette: AppPalette? = null,
    compact: Boolean = false, // narrower spacing, for the tablet menu
    onPalette: (AppPalette) -> Unit = {},
    onChange: (ThemeMode) -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(p.card)
            .border(1.dp, p.border, shape)
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp),
    ) {
        if (palette != null) {
            for (option in AppPalette.entries) PaletteDot(option, selected = option == palette) { onPalette(option) }
            Spacer(Modifier.width(2.dp))
        }
        Text("Dark", color = p.muted, fontSize = 12.sp, maxLines = 1, softWrap = false)
        Switch(checked = !p.isDark, onCheckedChange = { onChange(if (it) ThemeMode.LIGHT else ThemeMode.DARK) }, colors = jSwitchColors())
        Text("Light", color = p.muted, fontSize = 12.sp, maxLines = 1, softWrap = false)
    }
}

/** A small round swatch of a colour theme (its dark accent, ringed when it is the chosen one). */
@Composable
fun PaletteDot(option: AppPalette, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val accent = paletteFor(option, true).accent
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) p.text else p.border, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(accent)
            .clickable(onClick = onClick),
    )
}
