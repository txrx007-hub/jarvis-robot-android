package com.jarvis.robot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Driving: the live picture on top (off until you start it, it costs Wi-Fi), the arrows below. Hold an arrow to drive, let go to stop. */
@Composable
fun V2Drive(state: MainUiState, actions: MainActions) {
    val v = v2Colors()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        V2Title("Drive", if (state.online) "Hold an arrow to drive · let go to stop" else "Jarvis is not reachable")

        // ---- the live view
        Box(
            Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(28.dp)).background(Color(0xFF050A0B)),
            contentAlignment = Alignment.Center,
        ) {
            val img = state.liveImage
            if (state.liveOn && img != null) {
                Image(img, contentDescription = tr("Live view from the robot's camera"), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(V2Icons.Eye, contentDescription = null, tint = v.muted, modifier = Modifier.size(32.dp))
                    Text(if (state.liveOn) "Waiting for the first picture…" else "Live view is off", color = v.muted, fontSize = 14.sp)
                }
            }
            Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xB8141D20)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (state.liveOn) Color(0xFFFF6B5E) else v.muted))
                    Text(if (state.liveOn) "Live" else "Off", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V2IconButton(V2Icons.Camera, "Take a photo", actions.onPhoto, bg = Color(0xB8141D20), fg = Color.White)
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                V2Button(if (state.liveOn) "Stop live view" else "Start live view", actions.onToggleLive, filled = !state.liveOn, enabled = state.online)
            }
        }
        state.liveNote?.let { Text(it, color = v.muted, fontSize = 13.sp) }

        // ---- the arrows: hold to drive
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            V2HoldButton(Dir.UP, V2Icons.Up, "Forward", state, actions, primary = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                V2HoldButton(Dir.LEFT, V2Icons.Left, "Turn left", state, actions)
                Box(
                    Modifier.size(width = 84.dp, height = 72.dp).clip(RoundedCornerShape(36.dp)).background(v.danger)
                        .semantics { role = Role.Button; contentDescription = tr("Stop") }
                        .pointerInput(Unit) { detectTapGestures { actions.onReleaseAll() } },
                    contentAlignment = Alignment.Center,
                ) { Text("STOP", color = v.bg, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                V2HoldButton(Dir.RIGHT, V2Icons.Right, "Turn right", state, actions)
            }
            V2HoldButton(Dir.DOWN, V2Icons.Down, "Backward", state, actions)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** One arrow: pressed = the wheels turn that way, released (or the finger slides off) = they stop. */
@Composable
private fun V2HoldButton(dir: Dir, icon: ImageVector, label: String, state: MainUiState, actions: MainActions, primary: Boolean = false) {
    val v = v2Colors()
    val held = dir in state.held
    val bg = when {
        held -> v.accent
        primary -> v.accentContainer
        else -> v.surfaceHigh
    }
    val fg = if (held) v.onAccent else if (primary) v.onAccentContainer else v.text
    Box(
        Modifier.size(width = 84.dp, height = 72.dp).clip(RoundedCornerShape(24.dp)).background(bg)
            .semantics { role = Role.Button; contentDescription = tr(label) }
            .pointerInput(dir, state.online) {
                if (!state.online) return@pointerInput
                detectTapGestures(onPress = {
                    actions.onHold(dir, true)
                    try { tryAwaitRelease() } finally { actions.onHold(dir, false) }
                })
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = fg.copy(alpha = if (state.online) 1f else 0.4f), modifier = Modifier.size(30.dp)) }
}
