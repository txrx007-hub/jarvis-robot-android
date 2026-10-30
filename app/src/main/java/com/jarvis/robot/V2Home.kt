package com.jarvis.robot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What the robot is doing, in a few words (the device state of /api/status). */
fun v2StateText(state: MainUiState): String {
    val s = state.status ?: return if (state.everConnected) "Not reachable" else "Connecting…"
    if (!state.online) return "Not reachable"
    if (state.sleepingNow) return "Going to sleep"
    if (s.napping) return "Napping"
    return when (s.state) {
        3 -> "Ready to listen"
        4 -> "Connecting to its server"
        5 -> "Listening"
        6 -> "Talking"
        2 -> "Setting up Wi-Fi"
        7 -> "Updating"
        else -> "Starting"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2Home(state: MainUiState, actions: MainActions, nav: V2Nav = V2Nav()) {
    val v = v2Colors()
    val s = state.status
    var robotSheet by remember { mutableStateOf(false) }
    var robotDetails by remember { mutableStateOf(false) }   // the sheet opened by tap and hold: all details open at once
    var sleepSheet by remember { mutableStateOf(false) }
    var photoSheet by remember { mutableStateOf(false) }
    val music = state.music
    val showPlayer = music?.enabled == true && !music.title.isNullOrEmpty()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = if (showPlayer) 84.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- top app bar: the name, online or not, and the robot button (opens its sheet)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(56.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Jarvis", color = v.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (state.online) v.success else v.danger))
                        Text(if (state.online) "Online" else "Offline", color = v.muted, fontSize = 13.sp)
                    }
                }
                Row(
                    Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(v.surfaceHigh).v2Press({ robotDetails = false; robotSheet = true }, { robotDetails = true; robotSheet = true })
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    V2WifiBars(if (state.online) s?.rssi else null)   // offline: no bars, not the last reading
                    s?.chipTempC?.takeIf { state.online }?.let { Text("${it.toInt()}°", color = v.text, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    Icon(V2Icons.ChevronDown, contentDescription = null, tint = v.muted, modifier = Modifier.size(16.dp))
                }
            }

            // ---- not reachable
            if (!state.online) {
                V2Tile(color = v.surface) {
                    Text(if (state.everConnected) "Lost the connection to Jarvis" else "No answer from ${state.host}", color = v.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (state.everConnected) "It may be napping off Wi-Fi, asleep, or out of range. Pull down to try again."
                        else "Check the address and that this phone is on the robot's Wi-Fi.",
                        color = v.muted, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    V2Button("Change address", actions.onChangeAddress, Modifier.fillMaxWidth(), filled = false)
                }
            }

            // ---- hero: the face and what it is doing
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(v.surface).v2Press({ robotDetails = false; robotSheet = true }, { robotDetails = true; robotSheet = true }).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (V3.on) V3Cookie(96.dp) { V2Eyes(awake = state.online && s?.napping != true, modifier = Modifier.size(64.dp)) }
                else V2Eyes(awake = state.online && s?.napping != true, modifier = Modifier.size(88.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(v2StateText(state), color = v.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    val parts = if (!state.online) emptyList() else listOfNotNull(
                        s?.takeIf { it.uptimeS > 0 }?.let { "Up ${fmtUptime(it.uptimeS)}" },
                        s?.server?.let { if (s.serverCustom) "own voice server" else "xiaozhi.me cloud" },
                    )
                    if (parts.isNotEmpty()) Text(parts.joinToString(" · ") { tr(it) }, color = v.muted, fontSize = 13.sp)   // each part on its own: the word list knows the parts, not their mix
                }
            }

            // ---- bento: volume | face me + follow
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(204.dp)) {
                V2VolumeTile(s, actions.onSetVolume, Modifier.weight(1f).fillMaxHeight()) { nav.openPage(V2Page.VOLUME) }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    V2Tile(Modifier.fillMaxWidth().weight(1f), color = v.accent, onClick = actions.onFaceMe, onLongClick = { nav.openGroup("Face and movement") }) {
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(V2Icons.Face, contentDescription = null, tint = v.onAccent, modifier = Modifier.size(30.dp))
                            Text("Face me", color = v.onAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    val follow = s?.follow
                    if (follow != null) {
                        V2Tile(
                            Modifier.fillMaxWidth().weight(1f).semantics { stateDescription = tr(if (follow) "On" else "Off") },
                            onClick = { actions.onFollow(!follow) }, role = Role.Switch, onLongClick = { nav.openGroup("Face and movement") },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("Follow me", color = v.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                V2Switch(follow, { actions.onFollow(it) })
                            }
                            Text(if (follow) "On · keeps turning to you" else "Off · turns to your face", color = v.muted, fontSize = 12.sp)
                        }
                    } else {
                        V2Tile(Modifier.fillMaxWidth().weight(1f), onClick = { actions.onPhoto(); photoSheet = true }, onLongClick = { nav.openGroup("Camera") }) {
                            Icon(V2Icons.Camera, contentDescription = null, tint = v.accent, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.weight(1f))
                            Text("Photo", color = v.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ---- quick actions: a connected group
            Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                val napping = s?.napping == true
                V2QuickButton(if (napping) "Wake up" else "Nap", V2Icons.Moon, RoundedCornerShape(22.dp, 8.dp, 8.dp, 22.dp), Modifier.weight(1f), on = napping, onLong = { nav.openGroup("Screen and quiet hours") }) { actions.onNap(napping) }
                val muted = s?.muted
                if (muted != null) {
                    V2QuickButton(if (muted) "Unmute" else "Mute", if (muted) V2Icons.Mic else V2Icons.MicOff, RoundedCornerShape(8.dp), Modifier.weight(1f), on = muted, onLong = { nav.openGroup("Audio and wake word") }) { actions.onMute(!muted) }
                }
                V2QuickButton("Sleep…", V2Icons.Power, RoundedCornerShape(8.dp, 22.dp, 22.dp, 8.dp), Modifier.weight(1f), enabled = !state.sleepingNow, onLong = { sleepSheet = true }) { sleepSheet = true }
            }

            // ---- photo with the last picture
            if (s?.follow != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(v.surfaceHigh).v2Press({ if (state.photo == null) actions.onPhoto(); photoSheet = true }, { nav.openGroup("Camera") })
                        .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.width(96.dp).height(68.dp).clip(RoundedCornerShape(20.dp)).background(v.surfaceHighest), contentAlignment = Alignment.Center) {
                        val photo = state.photo
                        if (photo != null) Image(photo, contentDescription = tr("Last photo"), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Icon(V2Icons.Camera, contentDescription = null, tint = v.muted, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (state.photoBusy) "Taking a photo…" else "Take a photo", color = v.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        state.photoTime?.let { Text("Last one $it", color = v.muted, fontSize = 12.sp) }
                    }
                    Icon(V2Icons.Camera, contentDescription = null, tint = v.accent, modifier = Modifier.size(24.dp))
                }
            }
        }

        // ---- the music mini player: a standard sheet docked above the navigation bar, only while something plays
        if (showPlayer && music != null) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth().height(60.dp)
                    .clip(RoundedCornerShape(20.dp)).background(v.surfaceHighest).v2Press({ nav.openPage(V2Page.MUSIC) }, { nav.openPage(V2Page.MUSIC) }).padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                V2Equalizer(v.warm)
                Column(Modifier.weight(1f)) {
                    Text(music.title.orEmpty(), color = v.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    music.artist?.takeIf { it.isNotEmpty() }?.let { Text(it, color = v.muted, fontSize = 12.sp, maxLines = 1) }
                }
                V2IconButton(V2Icons.Next, "Next song", actions.onMusicNext, bg = Color.Transparent)
                V2IconButton(V2Icons.Stop, "Stop", actions.onMusicStop, bg = Color.Transparent)
            }
        }
    }

    if (robotSheet) {
        ModalBottomSheet(onDismissRequest = { robotSheet = false }, sheetState = rememberModalBottomSheetState(), containerColor = v.surface) {
            V2RobotSheet(state, actions, robotDetails) { robotSheet = false }
        }
    }
    if (sleepSheet) {
        ModalBottomSheet(onDismissRequest = { sleepSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = v.surface) {
            V2SleepSheet(state) { minutes -> sleepSheet = false; actions.onDeepSleep(minutes) }
        }
    }
    if (photoSheet) {
        ModalBottomSheet(onDismissRequest = { photoSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = v.surface) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Photo", color = v.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                V2PhotoContent(state, actions)
            }
        }
    }
}

/** The robot's two eyes, drawn: open while it is awake, a thin line while napping or offline. */
@Composable
fun V2Eyes(awake: Boolean, modifier: Modifier = Modifier) {
    val v = v2Colors()
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(Color(0xFF050B0C)), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(2) {
                Box(Modifier.width(20.dp).height(if (awake) 34.dp else 4.dp).clip(RoundedCornerShape(10.dp)).background(if (awake) v.accent else v.muted))
            }
        }
    }
}

@Composable
private fun V2Equalizer(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(18.dp)) {
        for (h in listOf(8, 16, 11)) Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(color))
    }
}

@Composable
private fun V2QuickButton(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, shape: RoundedCornerShape, modifier: Modifier,
    on: Boolean = false, enabled: Boolean = true, onLong: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val v = v2Colors()
    Row(
        modifier.fillMaxHeight().clip(if (on) RoundedCornerShape(22.dp) else shape).background(if (on) v.accentContainer else v.surfaceHigh)
            .v2Press(onClick, onLong, enabled = enabled),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (on) v.onAccentContainer else v.text, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (on) v.onAccentContainer else v.text.copy(alpha = if (enabled) 1f else 0.5f), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** The volume tile: the level fills it from below; drag up or down on it, or use − and +. The robot gets the new value when the finger lifts. */
@Composable
private fun V2VolumeTile(s: Status?, onSet: (Int) -> Unit, modifier: Modifier, onLong: () -> Unit) {
    val v = v2Colors()
    val robotVolume = s?.volume
    var shown by remember(robotVolume) { mutableIntStateOf(robotVolume ?: 0) }
    var tileHeight by remember { mutableIntStateOf(1) }
    val enabled = robotVolume != null
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier.clip(RoundedCornerShape(28.dp)).background(v.surfaceHigh)
            .semantics { contentDescription = tr("Volume"); stateDescription = "$shown%" }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onLong() }) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                tileHeight = size.height
                detectVerticalDragGestures(onDragEnd = { onSet(shown) }) { _, dy ->
                    shown = (shown - dy / size.height * 100f).toInt().coerceIn(0, 100)
                }
            },
    ) {
        val frac = shown / 100f
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(frac).background(v.accentContainer))
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(V2Icons.Volume, contentDescription = null, tint = v.muted, modifier = Modifier.size(18.dp))
                Text("Volume", color = v.muted, fontSize = 13.sp)
            }
            Column {
                Text(if (enabled) "$shown" else "--", color = v.text, fontSize = 46.sp, fontWeight = FontWeight.Bold)
                val cap = s?.volumeCap
                if (cap != null && cap < 100) Text("Quiet hours: at most $cap", color = v.muted, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V2StepButton("−", "Quieter", Modifier.weight(1f), enabled) { shown = (shown - 5).coerceAtLeast(0); onSet(shown) }
                V2StepButton("+", "Louder", Modifier.weight(1f), enabled) { shown = (shown + 5).coerceAtMost(100); onSet(shown) }
            }
        }
    }
}

@Composable
fun V2StepButton(sign: String, label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val v = v2Colors()
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(v.bg.copy(alpha = 0.85f)).clickable(enabled = enabled, role = Role.Button, onClickLabel = tr(label), onClick = onClick)
            .semantics { contentDescription = tr(label) },
        contentAlignment = Alignment.Center,
    ) { Text(sign, color = v.text.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 20.sp) }
}

// ---------------------------------------------------------------------------------------------------------------------------------- robot sheet
@Composable
private fun V2RobotSheet(state: MainUiState, actions: MainActions, startDetails: Boolean, onClose: () -> Unit) {
    val v = v2Colors()
    val s = state.status
    var details by remember { mutableStateOf(startDetails) }
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text("Jarvis", color = v.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            val sub = listOfNotNull(
                s?.boardName?.takeIf { it.isNotEmpty() },
                s?.revision?.takeIf { it > 0 }?.let { "revision $it" },
                s?.takeIf { it.uptimeS > 0 }?.let { "up ${fmtUptime(it.uptimeS)}" },
            ).joinToString(" · ") { tr(it) }
            if (sub.isNotEmpty()) Text(sub, color = v.muted, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            V2Ring("CPU", s?.cpuLoad?.let { "$it%" } ?: "--", (s?.cpuLoad ?: 0) / 100f, v.accent, Modifier.weight(1f))
            V2Ring("Memory", s?.heapLargestKb?.let { "$it KB" } ?: "--", ((s?.heapLargestKb ?: 0) / 45f).coerceIn(0f, 1f), if ((s?.heapLargestKb ?: 99) < 20) v.danger else v.accent, Modifier.weight(1f))
            V2Ring("Chip", s?.chipTempC?.let { "${it.toInt()}°" } ?: "--", ((s?.chipTempC ?: 0.0) / 80.0).toFloat().coerceIn(0f, 1f), v.warm, Modifier.weight(1f))
            V2Ring("Wi-Fi dBm", s?.rssi?.toString() ?: "--", (((s?.rssi ?: -100) + 100) / 60f).coerceIn(0f, 1f), v.warm, Modifier.weight(1f))
        }
        V2Card {
            V2Row("Camera", trailing = { Text(if (s?.cameraDetected == true) "${s.cameraSensor.ifEmpty { "found" }}" else "not found", color = v.text, fontSize = 14.sp) })
            V2Divider()
            V2Row("Ping", trailing = { Text(state.pingText, color = v.text, fontSize = 14.sp) })
            V2Divider()
            V2Row(if (details) "Fewer details" else "All details", onClick = { details = !details }, trailing = {
                Icon(if (details) V2Icons.ChevronUp else V2Icons.ChevronDown, contentDescription = null, tint = v.accent, modifier = Modifier.size(18.dp))
            })
        }
        if (details) V2DetailsList(state)
        V2Button("Change robot or address", { onClose(); actions.onChangeAddress() }, Modifier.fillMaxWidth(), filled = false)
    }
}

/** A small ring gauge with its value in the middle and a label under it. */
@Composable
fun V2Ring(label: String, value: String, fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val v = v2Colors()
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(v.surfaceHigh).padding(vertical = 12.dp).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            val track = v.surfaceHighest
            Canvas(Modifier.fillMaxSize()) {
                val w = 6.dp.toPx()
                val d = size.minDimension - w
                val tl = Offset(w / 2, w / 2)
                drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
                drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
            }
            Text(value, color = v.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(label, color = v.muted, fontSize = 12.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------- deep sleep
@Composable
private fun V2SleepSheet(state: MainUiState, onSleep: (Int) -> Unit) {
    val v = v2Colors()
    var minutes by remember { mutableIntStateOf(60) }
    var custom by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Deep sleep", color = v.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("Jarvis turns off Wi-Fi and the screen and wakes up by itself after the time you pick, or when you press its BOOT button.", color = v.muted, fontSize = 13.sp)
        V2Connected(listOf(15 to "15 min", 60 to "1 h", 120 to "2 h", 480 to "8 h"), if (custom || minutes == 0) null else minutes, { minutes = it; custom = false })
        V2Connected(listOf(0 to "Until the button", -1 to "Other time"), if (custom) -1 else if (minutes == 0) 0 else null, { if (it == -1) { custom = true; if (minutes == 0) minutes = 90 } else { custom = false; minutes = 0 } })
        if (custom) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V2StepButton("−", "15 minutes less", Modifier.width(64.dp)) { minutes = (minutes - 15).coerceAtLeast(15) }
                Text(sleepLabel(minutes), color = v.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                V2StepButton("+", "15 minutes more", Modifier.width(64.dp)) { minutes = (minutes + 15).coerceAtMost(1440) }
            }
        }
        V2Button(if (minutes == 0) "Sleep until the button" else "Sleep ${sleepLabel(minutes)}", { onSleep(minutes) }, Modifier.fillMaxWidth(), enabled = !state.sleepingNow && state.online)
    }
}

private fun sleepLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}
