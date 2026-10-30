package com.jarvis.robot

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import java.util.Locale
import kotlin.math.roundToInt
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
fun V2Settings(state: MainUiState, actions: MainActions, openGroup: String? = null, onOpenGroup: (String?) -> Unit = {}) {
    val v = v2Colors()
    var serverSheet by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var resetNote by remember { mutableStateOf<Feedback?>(null) }
    val groups = groupSettings(state.settings)

    val page = openGroup?.let { t -> groups.firstOrNull { it.first.title == t } }
    if (page != null) {
        BackHandler { onOpenGroup(null) }
        V2SettingsGroupPage(page.first.title, page.second, state, actions) { onOpenGroup(null) }
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
                    V2Tile(Modifier.weight(1f).height(128.dp), onClick = { onOpenGroup(g.title) }, padding = 14.dp, onLongClick = { onOpenGroup(g.title) }) {
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
                    V2Row(g.title, onClick = { onOpenGroup(g.title) }, leading = { Icon(groupIcon(g.title), contentDescription = null, tint = v.muted, modifier = Modifier.size(20.dp)) }) {
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
                V2ServerContent(state, actions)
            }
        }
    }
    if (confirmReset) {
        V2Dialog(
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
internal fun V2SettingsGroupPage(title: String, defs: List<SettingDef>, state: MainUiState, actions: MainActions, onBack: () -> Unit) {
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
        V2Card {
            live.forEachIndexed { i, def ->
                if (i > 0) V2Divider()
                V2SettingEditor(def, state.feedback[def.key]) { value -> actions.onSave(def.key, value) }
            }
        }
        Text("Changes save on the robot at once.", color = v.muted, fontSize = 12.sp, modifier = Modifier.padding(4.dp))
        Spacer(Modifier.height(8.dp))
    }
}

private fun v2SliderText(def: SettingDef, x: Float): String =
    if (def.kind == Kind.HUNDREDTHS) "%.2f".format(Locale.US, x) else x.roundToInt().toString()

/**
 * One robot setting in the v2 style, the same saves as the classic SettingCard: a slider (saved when the finger lifts, a small tick per step),
 * a switch, a connected group or chips for a choice, a time with an on/off switch, or a text field (saved on Done or when it loses focus).
 * The robot's answer comes up as a snackbar; only a refusal also stays under the setting.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun V2SettingEditor(def: SettingDef, feedback: Feedback?, onSave: (String) -> Unit) {
    val v = v2Colors()
    val tick = rememberTick()
    val context = LocalContext.current
    var live by remember(def.value) { mutableFloatStateOf(def.value.toFloatOrNull() ?: def.min.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(labelFor(def.key), color = v.text, fontSize = 15.sp)
                if (def.help.isNotEmpty()) Text(def.help, color = v.muted, fontSize = 13.sp)
            }
            when (def.kind) {
                Kind.BOOL -> V2Switch(def.value == "1", { tick(); onSave(if (it) "1" else "0") })
                Kind.INT, Kind.HUNDREDTHS -> Text(fmtValue(def.copy(value = v2SliderText(def, live))), color = v.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Kind.TIME -> V2Switch(def.value.isNotEmpty(), { on -> tick(); onSave(if (on) "22:00" else "off") })
                else -> {}
            }
        }
        when (def.kind) {
            Kind.INT, Kind.HUNDREDTHS -> {
                val span = (def.max - def.min).coerceAtLeast(def.step)
                Slider(
                    value = live.coerceIn(def.min.toFloat(), def.max.toFloat()),
                    onValueChange = { x ->
                        val before = v2SliderText(def, live)
                        live = x
                        if (v2SliderText(def, x) != before) tick()
                    },
                    onValueChangeFinished = { onSave(v2SliderText(def, live)) },
                    valueRange = def.min.toFloat()..def.max.toFloat(),
                    steps = ((span / def.step).roundToInt() - 1).coerceAtLeast(0),
                    modifier = Modifier.semantics { contentDescription = tr(labelFor(def.key)) },
                    colors = SliderDefaults.colors(
                        thumbColor = v.accent, activeTrackColor = v.accent, inactiveTrackColor = v.surfaceHighest,
                        activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                    ),
                )
            }
            Kind.ENUM -> {
                if (def.options.size <= 3 && def.options.all { it.length <= 10 }) {
                    V2Connected(def.options.map { it to it }, def.value, { if (it != def.value) { tick(); onSave(it) } })
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (o in def.options) V2Chip(o, o == def.value) { if (o != def.value) { tick(); onSave(o) } }
                    }
                }
            }
            Kind.TIME -> if (def.value.isNotEmpty()) {
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(v.surfaceHigh)
                        .clickable(role = Role.Button) {
                            val parts = def.value.split(':')
                            TimePickerDialog(context, { _, hh, mm -> onSave("%02d:%02d".format(Locale.US, hh, mm)) },
                                parts.getOrNull(0)?.toIntOrNull() ?: 22, parts.getOrNull(1)?.toIntOrNull() ?: 0, true).show()
                        }.padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(def.value, color = v.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
            Kind.TEXT -> V2TextSetting(def, onSave)
            else -> {}
        }
        if (feedback != null && !feedback.ok) Text(feedback.text, color = v.danger, fontSize = 13.sp)
    }
}

@Composable
private fun V2TextSetting(def: SettingDef, onSave: (String) -> Unit) {
    val v = v2Colors()
    val focus = LocalFocusManager.current
    var text by remember(def.value) { mutableStateOf(def.value) }
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(v.surfaceHigh).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isEmpty()) Text(if (def.key == "weather_city") "e.g. Amsterdam (leave empty for off)" else "(leave empty for off)", color = v.muted, fontSize = 15.sp)
        BasicTextField(
            text, { text = it.take(def.maxLength) }, singleLine = true, cursorBrush = SolidColor(v.accent),
            textStyle = TextStyle(color = v.text, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && text.trim() != def.value) onSave(text.trim()) },
        )
    }
}
