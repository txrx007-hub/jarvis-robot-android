package com.jarvis.robot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val SECTION_OVERVIEW = "Overview"
private const val SECTION_REMOTE = "Remote control"
private const val SECTION_SOUND = "Volume and voice server"
private const val SECTION_COMMANDS = "Voice commands"
private const val SECTION_LOOK = "Appearance"

private const val NAV_WIDTH_DP = 280
private const val TWO_COLUMNS_FROM_DP = 640 // width of the right pane from which cards sit in two columns

/**
 * The layout for the unfolded phone: a menu on the left, the chosen section on the right, using the whole screen
 * (the way tablet apps look). The phone's single scrolling column is [PhoneContent].
 */
@Composable
fun TabletContent(state: MainUiState, actions: MainActions, initialSection: String = SECTION_OVERVIEW) {
    val p = LocalPalette.current
    val groups = groupSettings(state.settings)
    val sections = listOf(SECTION_OVERVIEW, SECTION_REMOTE, SECTION_SOUND, SECTION_COMMANDS) +
        groups.map { it.first.title } + SECTION_LOOK
    var selected by rememberSaveable { mutableStateOf(initialSection) }
    if (selected !in sections) selected = SECTION_OVERVIEW // e.g. the settings had not loaded yet

    // Driving only while the pad is on screen: leaving the section lets go of every arrow.
    LaunchedEffect(selected) { if (selected != SECTION_REMOTE) actions.onReleaseAll() }

    val paneWidthDp = LocalConfiguration.current.screenWidthDp - NAV_WIDTH_DP - 56
    val columns = if (paneWidthDp >= TWO_COLUMNS_FROM_DP) 2 else 1

    Box(Modifier.fillMaxSize()) {
        ThemeBackdrop()
        Row(Modifier.fillMaxSize().displayCutoutPadding().statusBarsPadding().navigationBarsPadding()) {
            NavPane(state, actions, sections, selected) { selected = it }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(selected, color = p.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                if (!state.online && selected != SECTION_LOOK) OfflineCard(state, actions)
                when (selected) {
                    SECTION_OVERVIEW -> Overview(state, actions, columns)
                    SECTION_REMOTE -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = 460.dp)) { RemotePanel(state.held, actions.onHold) }
                    }
                    SECTION_SOUND -> Columns(columns, listOf(
                        { VolumeCard(state.status, actions.onSetVolume) },
                        { ServerCard(state.status, state.serverFeedback, state.backendPin, actions.onSetServer) },
                    ))
                    SECTION_COMMANDS -> VoiceCommandsPanel(columns)
                    SECTION_LOOK -> Appearance(state, actions)
                    else -> {
                        val defs = groups.firstOrNull { it.first.title == selected }?.second.orEmpty()
                        Columns(columns, defs.map { def -> { SettingCard(def, state.feedback[def.key]) { v -> actions.onSave(def.key, v) } } })
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun NavPane(state: MainUiState, actions: MainActions, sections: List<String>, selected: String, onSelect: (String) -> Unit) {
    val p = LocalPalette.current
    val status = state.status
    Column(
        modifier = Modifier
            .width(NAV_WIDTH_DP.dp)
            .fillMaxHeight()
            .background(if (p.photo != null) p.card.copy(alpha = 0.9f) else p.card)
            .padding(top = 18.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (state.online) p.success else p.danger))
            Column(Modifier.weight(1f)) {
                Text("Jarvis", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                val ssid = status?.ssid.orEmpty()
                val ip = status?.ip.orEmpty().ifEmpty { state.host }
                Text(
                    if (state.online) (if (ssid.isNotEmpty()) "$ssid · $ip" else ip) else "Not reachable",
                    color = p.muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
        TextButton(onClick = actions.onChangeAddress, modifier = Modifier.padding(start = 8.dp)) {
            Text("Change address", color = p.accent, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for ((i, name) in sections.withIndex()) {
                if (i == 4 && name != SECTION_LOOK) NavLabel("Robot settings")
                if (name == SECTION_LOOK) NavLabel("App")
                NavItem(name, name == selected) { onSelect(name) }
            }
        }
        ThemeSwitch(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            compact = true,
            palette = state.palette,
            onPalette = actions.onPalette,
            onChange = actions.onTheme,
        )
    }
}

@Composable
private fun NavLabel(text: String) {
    Text(
        text.uppercase(),
        color = LocalPalette.current.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun NavItem(title: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) p.accent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(width = 3.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) p.accent else androidx.compose.ui.graphics.Color.Transparent))
        Text(
            title,
            color = if (selected) p.accent else p.text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun OfflineCard(state: MainUiState, actions: MainActions) {
    val p = LocalPalette.current
    JCard {
        Text(
            if (state.everConnected) "Lost the connection to Jarvis. It may be napping off WiFi, asleep, or out of range."
            else "No answer from ${state.host}. Check the address and that this device is on the robot's WiFi.",
            color = p.muted,
            fontSize = 13.sp,
        )
        JButton("Change address", Modifier.fillMaxWidth(), onClick = actions.onChangeAddress)
    }
}

/** Cards side by side: [count] columns, the cards dealt out one by one so the columns stay about level. */
@Composable
private fun Columns(count: Int, items: List<@Composable () -> Unit>) {
    if (count <= 1) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { items.forEach { it() } }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for (c in 0 until count) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.filterIndexed { i, _ -> i % count == c }.forEach { it() }
            }
        }
    }
}

@Composable
private fun Overview(state: MainUiState, actions: MainActions, columns: Int) {
    JCard { InfoTable(state.status, state.pingText) }
    Columns(columns, listOf(
        {
            PowerCard(
                status = state.status,
                feedback = state.powerFeedback,
                sleepingNow = state.sleepingNow,
                onNap = actions.onNap,
                onDeepSleep = actions.onDeepSleep,
            )
        },
        { VolumeCard(state.status, actions.onSetVolume) },
    ))
}

@Composable
private fun Appearance(state: MainUiState, actions: MainActions) {
    val p = LocalPalette.current
    var confirmReset by remember { mutableStateOf(false) }
    var resetNote by remember { mutableStateOf<Feedback?>(null) }

    Text("Colour theme", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (option in AppPalette.entries) {
            PaletteTile(option, state.palette == option, Modifier.weight(1f)) { actions.onPalette(option) }
        }
    }
    Text("Day or night", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((mode, label) in listOf(ThemeMode.SYSTEM to "Follow the device", ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light")) {
            val on = state.themeMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) p.accent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
                    .border(1.dp, if (on) p.accent else p.border, RoundedCornerShape(10.dp))
                    .clickable { actions.onTheme(mode) },
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) p.accent else p.text, fontSize = 15.sp) }
        }
    }

    Spacer(Modifier.height(10.dp))
    JButton("Reset all robot settings to defaults", Modifier.fillMaxWidth(), danger = true) { confirmReset = true }
    resetNote?.let { Text(it.text, color = if (it.ok) p.success else p.danger, fontSize = 13.sp) }
    Text(
        "Changes apply and save right away. On your home network only.",
        color = p.muted,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )

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

/** A preview of one colour theme in the current day/night mode: its background photo at 40%, its card and accent. */
@Composable
private fun PaletteTile(option: AppPalette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val preview = paletteFor(option, p.isDark)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) p.accent else p.border, shape)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(110.dp).background(preview.bg)) {
            preview.photo?.let {
                Image(painterResource(it), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(PHOTO_ALPHA))
            }
            Row(Modifier.align(Alignment.BottomStart).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in listOf(preview.card, preview.border, preview.accent)) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(c).border(1.dp, preview.text.copy(alpha = 0.4f), CircleShape))
                }
            }
        }
        Text(
            option.label,
            color = if (selected) p.accent else p.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().background(p.card).padding(12.dp),
        )
    }
}
