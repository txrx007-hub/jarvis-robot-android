package com.jarvis.robot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun groupIcon(title: String): ImageVector = when (title) {
    "Face and movement" -> V2Icons.Face
    "Motors" -> V2Icons.Motors
    "Audio and wake word" -> V2Icons.Mic
    "Screen and quiet hours" -> V2Icons.Screen
    "Weather" -> V2Icons.Sun
    "Camera" -> V2Icons.Camera
    "Visualizer" -> V2Icons.Wave
    else -> V2Icons.Gear
}

/** Settings: the robot's setting groups as tiles (big) and rows (small), each opening its own page; the voice server; the app's look and language; reset. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2Settings(state: MainUiState, actions: MainActions) {
    val v = v2Colors()
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var serverSheet by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var resetNote by remember { mutableStateOf<Feedback?>(null) }
    val groups = groupSettings(state.settings)

    val page = openGroup?.let { t -> groups.firstOrNull { it.first.title == t } }
    if (page != null) {
        BackHandler { openGroup = null }
        V2SettingsGroupPage(page.first.title, page.second, state, actions) { openGroup = null }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        V2Title("Settings")
        if (state.settings.isEmpty()) {
            Text(state.settingsError ?: if (state.online) "Loading settings…" else "Settings show once Jarvis is reachable.", color = v.muted, fontSize = 14.sp)
        }

        // ---- the big groups as tiles
        val big = groups.filter { it.first.collapsible }
        for (row in big.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                for ((g, defs) in row) {
                    V2Tile(Modifier.weight(1f).height(128.dp), onClick = { openGroup = g.title }, padding = 14.dp) {
                        V2IconBadge(groupIcon(g.title), if (g.title == "Face and movement") v.warmContainer else v.accentContainer, if (g.title == "Face and movement") v.onWarmContainer else v.onAccentContainer, size = 38.dp)
                        Spacer(Modifier.weight(1f))
                        Text(g.title, color = v.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text("${defs.size} settings", color = v.muted, fontSize = 12.sp)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // ---- the small groups and the voice server as rows
        val small = groups.filter { !it.first.collapsible }
        if (small.isNotEmpty() || state.status?.server != null) {
            V2Card {
                small.forEachIndexed { i, (g, defs) ->
                    if (i > 0) V2Divider()
                    val summary = if (defs.size == 1) defs[0].value.ifEmpty { "not set" } else "${defs.size} settings"
                    V2Row(g.title, onClick = { openGroup = g.title }, leading = { Icon(groupIcon(g.title), contentDescription = null, tint = v.muted, modifier = Modifier.size(20.dp)) }) {
                        Text(summary, color = v.muted, fontSize = 14.sp, maxLines = 1)
                        Icon(V2Icons.Chevron, contentDescription = null, tint = v.muted, modifier = Modifier.size(16.dp))
                    }
                }
                val st = state.status
                if (st?.server != null) {
                    if (small.isNotEmpty()) V2Divider()
                    V2Row("Voice server", onClick = { serverSheet = true }, leading = { Icon(V2Icons.Brain, contentDescription = null, tint = v.muted, modifier = Modifier.size(20.dp)) }) {
                        Text(if (st.serverCustom) "Own server" else "xiaozhi.me cloud", color = v.muted, fontSize = 14.sp, maxLines = 1)
                        Icon(V2Icons.Chevron, contentDescription = null, tint = v.muted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // ---- the app's own look and language
        V2Label("This app")
        V2Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Look", color = v.text, fontSize = 15.sp)
                V2Connected(listOf(ThemeMode.SYSTEM to "Auto", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"), state.themeMode, actions.onTheme)
                Text("Colours", color = v.text, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (pal in AppPalette.entries) {
                        val c = paletteFor(pal, LocalPalette.current.isDark).accent
                        val on = pal == state.palette
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(c)
                                .let { if (on) it.border(3.dp, v.text, CircleShape) else it }
                                .clickable(role = Role.RadioButton, onClickLabel = tr(pal.label)) { actions.onPalette(pal) },
                        )
                    }
                }
                Text("Language", color = v.text, fontSize = 15.sp)
                V2Connected(listOf(Lang.EN to "English", Lang.NL to "Nederlands"), I18n.lang, { I18n.choose(it) })
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            AppVersionLabel(Modifier.weight(1f), state.status?.revision ?: 0)
            Text(
                "Reset robot settings…", color = v.danger, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(CircleShape).clickable(role = Role.Button) { confirmReset = true }.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        resetNote?.let { Text(it.text, color = if (it.ok) v.success else v.danger, fontSize = 13.sp) }
        Text("Changes apply and save right away. On your home network only.", color = v.muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
    }

    if (serverSheet) {
        ModalBottomSheet(onDismissRequest = { serverSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = v.surface) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Voice server", color = v.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                ServerCard(state.status, state.serverFeedback, state.backendPin, actions.onSetServer)
            }
        }
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Reset all settings?",
            text = "Every setting goes back to its default. This cannot be undone.",
            confirmLabel = "Reset",
            danger = true,
            onConfirm = {
                confirmReset = false
                actions.onReset { ok -> resetNote = Feedback(ok, if (ok) "All settings reset to defaults." else "Could not reset the settings.") }
            },
            onDismiss = { confirmReset = false },
        )
    }
}

/** One setting group on its own page: a back arrow, the title, then each setting with the same editor as the classic app. */
@Composable
private fun V2SettingsGroupPage(title: String, defs: List<SettingDef>, state: MainUiState, actions: MainActions, onBack: () -> Unit) {
    val v = v2Colors()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            V2IconButton(V2Icons.Back, "Back", onBack)
            Text(title, color = v.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        // the current values come from the robot: when a save changes one, the list here is the robot's own answer
        val live = defs.map { d -> state.settings.firstOrNull { it.key == d.key } ?: d }
        for (def in live) SettingCard(def, state.feedback[def.key]) { value -> actions.onSave(def.key, value) }
        Text("Changes save on the robot at once.", color = v.muted, fontSize = 12.sp, modifier = Modifier.padding(4.dp))
        Spacer(Modifier.height(8.dp))
    }
}
