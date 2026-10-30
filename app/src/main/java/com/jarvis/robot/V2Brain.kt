package com.jarvis.robot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One section of the backend panel: what it holds, and the element the panel page scrolls to. */
private data class PanelSection(val title: String, val sub: String, val anchor: String, val icon: ImageVector)

private val PANEL_SECTIONS = listOf(
    PanelSection("Hearing", "Ears, speech detector, smart turn", "gEars", V2Icons.Ear),
    PanelSection("Seeing", "Faces, voices, greeter", "faceCard", V2Icons.Eye),
    PanelSection("Thinking", "Brain model, web search", "gBrain", V2Icons.Brain),
    PanelSection("Speaking", "Voice, personality", "gVoice", V2Icons.Speak),
    PanelSection("Mood and memory", "How it sounds, what it remembers", "moodCard", V2Icons.Heart),
    PanelSection("Home and music", "Home Assistant, songs, radio", "haCard", V2Icons.House),
    PanelSection("Health", "Robot, services, alerts", "robotCard", V2Icons.Wave),
    PanelSection("Speed and usage", "Reply speed, fallbacks, quotas", "speedCard", V2Icons.Clock),
)

/** The backend (the voice server on the PC): every panel section as a tile that opens the panel right at that section, the music controls, and the full panel. */
@Composable
fun V2Brain(state: MainUiState, actions: MainActions, onOpenPanel: (String) -> Unit) {
    val v = v2Colors()
    val s = state.status
    val panel = hasPanel(s)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        V2Title("Brain", if (panel) "The voice server on your PC" else "Needs your own voice server")
        if (!panel) {
            V2Tile(color = v.surface) {
                Text(
                    if (s?.server == null) "Jarvis has not said which voice server it uses yet."
                    else "Jarvis talks to the xiaozhi.me cloud now. The brain settings live on your own voice server: set it under Settings › Voice server.",
                    color = v.muted, fontSize = 14.sp,
                )
            }
            return@Column
        }

        // ---- music: the same two buttons as the classic Music card, here even when nothing plays
        val music = state.music
        if (music?.enabled == true) {
            V2Tile(color = v.surface) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    V2IconBadge(V2Icons.Speak, v.warmContainer, v.onWarmContainer)
                    Column(Modifier.weight(1f)) {
                        Text(if (music.title.isNullOrEmpty()) "Nothing playing" else music.title, color = v.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            if (music.title.isNullOrEmpty()) "Say: Jarvis, play something random." else (music.artist?.takeIf { it.isNotEmpty() } ?: "${music.tracks} songs"),
                            color = v.muted, fontSize = 13.sp, maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    V2Button("Next", actions.onMusicNext, Modifier.weight(1f))
                    V2Button("Stop", actions.onMusicStop, Modifier.weight(1f), filled = false)
                }
            }
        }

        // ---- the switches people flip: straight on the voice server (no restart); a row opens its panel card for the details
        LaunchedEffect(panel) { if (panel) actions.onBrainLoad() }
        val b = state.brain
        val asking = if (b == null) "Asking the backend…" else null
        V2Card {
            V2Row("Greeter", asking ?: if (b?.greeter == null) "Not on this backend" else "Good morning when it sees you", onClick = { onOpenPanel("greetCard") }) {
                V2Switch(b?.greeter == true, { actions.onBrainSet("greeter", it) }, enabled = b?.greeter != null)
            }
            V2Divider()
            val voicesSub = when {
                b == null -> asking
                b.voices == null -> "Not on this backend"
                !b.voicesModel -> "The speaker model file is missing"
                b.voicesPeople == 0 -> "Nobody enrolled yet"
                b.voicesPeople == 1 -> "1 voice enrolled"
                else -> "${b.voicesPeople} voices enrolled"
            }
            V2Row("Voice recognition", voicesSub, onClick = { onOpenPanel("voiceCard") }) {
                V2Switch(b?.voices == true, { actions.onBrainSet("voiceprint", it) }, enabled = b?.voices != null)
            }
            V2Divider()
            V2Row("Smart turn", asking ?: if (b?.smartTurn == null) "Not on this backend" else if (b.smartModel) "Waits when you pause mid-sentence" else "The model file is missing", onClick = { onOpenPanel("smartCard") }) {
                V2Switch(b?.smartTurn == true, { actions.onBrainSet("smartturn", it) }, enabled = b?.smartTurn != null)
            }
            V2Divider()
            V2Row("Memory", asking ?: "Notes about the people it talks to", onClick = { onOpenPanel("memCard") }) {
                Text(when (b?.memoryOn) { true -> "On"; false -> "Off"; null -> "--" }, color = v.muted, fontSize = 14.sp)
                Icon(V2Icons.Chevron, contentDescription = null, tint = v.muted, modifier = Modifier.size(16.dp))
            }
        }
        Text("Memory is switched on the panel: it restarts the voice server.", color = v.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))

        // ---- every section of the panel
        for (row in PANEL_SECTIONS.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                for (sec in row) {
                    V2Tile(Modifier.weight(1f).height(148.dp), onClick = { onOpenPanel(sec.anchor) }, padding = 14.dp) {
                        V2IconBadge(sec.icon, v.accentContainer, v.onAccentContainer, size = 36.dp)
                        Spacer(Modifier.weight(1f))
                        Text(sec.title, color = v.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(sec.sub, color = v.muted, fontSize = 12.sp, maxLines = 3)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        V2Button("Open the full panel", { onOpenPanel("") }, Modifier.fillMaxWidth(), filled = false)
        Spacer(Modifier.height(8.dp))
    }
}

/** The panel page over the whole screen, scrolled to [anchor]; back (or the arrow) returns to the tab. */
@Composable
fun V2PanelScreen(state: MainUiState, anchor: String, onClose: () -> Unit) {
    val v = v2Colors()
    Box(Modifier.fillMaxSize()) {
        ThemeBackdrop()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V2IconButton(V2Icons.Back, "Back", onClose)
                Text(PANEL_SECTIONS.firstOrNull { it.anchor == anchor }?.title ?: "Backend panel", color = v.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(V2Icons.Brain, contentDescription = null, tint = v.muted, modifier = Modifier.size(20.dp))
            }
            BackendWeb(panelUrlOf(state.status), state.host, state.palette, Modifier.fillMaxSize(), anchor = anchor)
        }
    }
}
