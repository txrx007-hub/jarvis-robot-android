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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Driving: the live picture on top (off until you start it, it costs Wi-Fi), the joystick below with the Speed slider. Drag the knob to drive, let go to stop. */
@Composable
fun V2Drive(state: MainUiState, actions: MainActions) {
    val v = v2Colors()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        V2Title("Drive", if (state.online) "Drag the knob to drive · let go to stop" else "Jarvis is not reachable")

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

        // ---- the joystick: the knob follows the finger, let go to stop
        Text(
            "Drag the knob to steer 360° (forward/back/spin on the spot), let go to stop.",
            color = v.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Joystick(
                state.stick, actions.onStick, actions.onReleaseAll, accent = v.accent, ink = v.text, enabled = state.online,
                modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
            )
            WheelReadout(state.stick, state.drivePower, v.muted)
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(v.surface).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${tr("Speed:")} ${state.drivePower}%", color = v.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 96.dp))
            Box(Modifier.weight(1f)) { V2Slider(state.drivePower.toFloat(), { actions.onDrivePower(drivePowerOf(it)) }, {}, 10f..100f, "Speed") }
        }
        Spacer(Modifier.height(8.dp))
    }
}
