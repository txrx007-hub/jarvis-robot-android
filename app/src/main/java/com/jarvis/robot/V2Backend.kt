package com.jarvis.robot

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The app's own detail pages (tap and hold a tile, or a row on the Brain tab); they open over the tabs, back closes them. */
enum class V2Page { GREETER, VOICES, MUSIC, MEMORY, VOLUME, MOOD, HOME, HEALTH, FACES }

@Composable
fun V2PageHost(page: V2Page, state: MainUiState, actions: MainActions, onOpenGroup: (String) -> Unit, onOpenPanel: (String) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    when (page) {
        V2Page.GREETER -> V2GreeterPage(state, actions, onOpenPanel, onClose)
        V2Page.VOICES -> V2VoicesPage(actions, onOpenPanel, onClose)
        V2Page.MUSIC -> V2MusicPage(state, actions, onOpenPanel, onClose)
        V2Page.MEMORY -> V2MemoryPage(state, actions, onOpenPanel, onClose)
        V2Page.VOLUME -> V2VolumePage(state, actions, onOpenGroup, onClose)
        V2Page.MOOD -> V2MoodPage(actions, onOpenPanel, onClose)
        V2Page.HOME -> V2HomeAssistantPage(actions, onOpenPanel, onClose)
        V2Page.HEALTH -> V2HealthPage(actions, onOpenPanel, onClose)
        V2Page.FACES -> V2FacesPage(state, actions, onOpenPanel, onClose)
    }
}

/** One page: back arrow, title, the content scrolling below; the theme's background behind. */
@Composable
internal fun V2PageFrame(title: String, onBack: () -> Unit, sub: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val v = v2Colors()
    Box(Modifier.fillMaxSize()) {
        ThemeBackdrop()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                V2IconButton(V2Icons.Back, "Back", onBack)
                Column {
                    Text(title, color = v.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    if (!sub.isNullOrEmpty()) Text(sub, color = v.muted, fontSize = 13.sp)
                }
            }
            content()
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A panel answer that is asked again every [everyMs] while the page is open. */
internal class PanelData {
    var data by mutableStateOf<JSONObject?>(null)
    var failed by mutableStateOf(false)
}

@Composable
internal fun rememberPanelData(actions: MainActions, path: String, everyMs: () -> Long): PanelData {
    // The first question goes out the moment the page opens (not a frame later), then again every everyMs() while it stays open.
    val d = remember(path) { PanelData().also { d -> actions.onPanel(path, null) { r -> if (r != null) { d.data = r; d.failed = false } else d.failed = true } } }
    LaunchedEffect(path) {
        while (true) {
            delay(everyMs())
            actions.onPanel(path, null) { r -> if (r != null) { d.data = r; d.failed = false } else d.failed = true }
        }
    }
    return d
}

internal fun panelPost(actions: MainActions, d: PanelData, path: String, body: JSONObject, after: (JSONObject) -> Unit = {}) =
    actions.onPanel(path, body) { r -> if (r != null) { d.data = r; after(r) } else d.failed = true }

@Composable
internal fun V2Waiting(d: PanelData) {
    val v = v2Colors()
    Text(if (d.failed) "Could not reach the backend. It is asked again in a moment." else "Asking the backend…", color = v.muted, fontSize = 14.sp)
}

internal fun JSONObject.strOrNull(k: String): String? = if (has(k) && !isNull(k)) optString(k) else null
internal fun hhmm(epochS: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochS * 1000))
internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

// ---------------------------------------------------------------------------------------------------------------------------------- greeter
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun V2GreeterPage(state: MainUiState, actions: MainActions, onOpenPanel: (String) -> Unit, onBack: () -> Unit) {
    val v = v2Colors()
    val context = LocalContext.current
    val d = rememberPanelData(actions, "api/greeter") { 5000L }
    var testNote by remember { mutableStateOf<String?>(null) }
    V2PageFrame("Greeter", onBack, "Jarvis says good morning when it sees you") {
        val g = d.data ?: run { V2Waiting(d); return@V2PageFrame }
        fun set(k: String, value: Any) = panelPost(actions, d, "api/greeter", JSONObject().put(k, value))
        V2Card {
            V2Row("Greet when it sees you", "A small picture about every 20 s while it is idle; two sure looks and it speaks") {
                V2Switch(g.optBoolean("enabled"), { set("enabled", it) })
            }
            V2Divider()
            V2Row("Turn to me first", "Turns until your face is in the middle, then speaks") {
                V2Switch(g.optBoolean("turn", true), { set("turn", it) })
            }
        }
        V2Label("Who")
        V2Connected(listOf("owner" to "Only me", "known" to "People I know", "anyone" to "Anyone"), g.optString("who", "owner"), { set("who", it) })
        if (g.optString("who") == "known" || g.optString("who") == "anyone") {
            val people = g.optJSONArray("people")
            Text(
                if (people == null || people.length() == 0) "Nobody is enrolled yet: add people on the Faces page." else tr("Greets you and") + " " + (0 until people.length()).joinToString(", ") { people.optString(it) },
                color = v.muted, fontSize = 13.sp,
            )
        }
        var name by remember(g.optString("name")) { mutableStateOf(g.optString("name")) }
        V2Field(name, { name = it }, "Name", g.optString("name_default").ifEmpty { "your name" }, onDone = { set("name", name.trim()) })
        Text("Example: ${g.optString("example")}", color = v.muted, fontSize = 13.sp)

        V2Label("Limits")
        V2Card {
            V2Row("Once per", "hours, for the same person") {
                val h = g.optDouble("cooldown_h", 4.0)
                V2Stepper("%.1f h".format(Locale.US, h), { set("cooldown_h", (h - 0.5).coerceAtLeast(0.5)) }, { set("cooldown_h", (h + 0.5).coerceAtMost(24.0)) })
            }
            V2Divider()
            V2Row("At most per day") {
                val n = g.optInt("per_day", 3)
                V2Stepper("$n", { set("per_day", (n - 1).coerceAtLeast(1)) }, { set("per_day", (n + 1).coerceAtMost(20)) })
            }
            V2Divider()
            V2Row("Quiet hours", if (g.optBoolean("quiet_now")) "Quiet now" else "No greeting in this time") {
                for (k in listOf("quiet_from", "quiet_to")) {
                    val t = g.optString(k)
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(v.surfaceHigh).clickable(role = Role.Button) {
                            val p = t.split(':')
                            TimePickerDialog(context, { _, hh, mm -> set(k, "%02d:%02d".format(Locale.US, hh, mm)) }, p.getOrNull(0)?.toIntOrNull() ?: 22, p.getOrNull(1)?.toIntOrNull() ?: 0, true).show()
                        }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(t, color = v.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
            V2Divider()
            var match by remember(g.optDouble("min_match")) { mutableFloatStateOf(g.optDouble("min_match", 0.45).toFloat()) }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row { Text("Sure match from", color = v.text, fontSize = 15.sp, modifier = Modifier.weight(1f)); Text("%.2f".format(Locale.US, match), color = v.accent, fontWeight = FontWeight.SemiBold) }
                Slider(
                    match, { match = it }, onValueChangeFinished = { set("min_match", Math.round(match * 100) / 100.0) }, valueRange = 0.35f..0.9f,
                    modifier = Modifier.semantics { contentDescription = tr("Sure match from") },
                    colors = SliderDefaults.colors(thumbColor = v.accent, activeTrackColor = v.accent, inactiveTrackColor = v.surfaceHighest),
                )
                Text("How sure the face finder must be that it is you (it calls 0.35 and up you).", color = v.muted, fontSize = 12.sp)
            }
        }

        V2Button("Greet now (test)", {
            testNote = "…"
            panelPost(actions, d, "api/greeter", JSONObject().put("test", true)) { r ->
                val t = r.optJSONObject("test")
                testNote = if (t?.optBoolean("ok") == true) "Said: ${t.optString("text")}" else "Did not work: ${t?.optString("message") ?: "?"}"
            }
        }, Modifier.fillMaxWidth(), enabled = state.online)
        testNote?.takeIf { it != "…" }?.let { Text(it, color = v.muted, fontSize = 13.sp) }

        val today = g.optJSONObject("today")
        V2Label("Today")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            V2Kpi("${today?.optInt("greeted") ?: 0}", "greeted", Modifier.weight(1f), accent = (today?.optInt("greeted") ?: 0) > 0)
            V2Kpi("${today?.optInt("looks") ?: 0}", "looks", Modifier.weight(1f))
            V2Kpi("${today?.optInt("tests") ?: 0}", "tests", Modifier.weight(1f))
        }
        val skip = today?.optJSONObject("skip")
        if (skip != null && skip.length() > 0) {
            Text("Stayed silent because of", color = v.muted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in skip.keys()) V2Chip("${tr(GREET_SKIP[k] ?: k)} ${skip.optInt(k)}", false) {}
            }
        }
        val recent = g.optJSONArray("recent").objects().reversed()
        if (recent.isNotEmpty()) {
            V2Label("Last greetings")
            V2Card {
                recent.forEachIndexed { i, r ->
                    if (i > 0) V2Divider()
                    V2Row(r.optString("text"), "${hhmm(r.optLong("t"))}${if (r.optBoolean("test")) " · test" else ""}${if (r.optBoolean("ok")) "" else " · " + r.optString("message")}")
                }
            }
        }
        V2Button("Open in the panel", { onOpenPanel("greetCard") }, Modifier.fillMaxWidth(), filled = false)
    }
}

private val GREET_SKIP = mapOf(
    "quiet" to "quiet hours", "cap" to "daily cap reached", "cooldown" to "waiting for the cooldown", "robot" to "robot not reachable", "busy" to "robot busy (talking, listening)",
    "napping" to "napping", "muted" to "microphone muted", "follow" to "follow mode on", "music" to "music or radio playing", "chat" to "just talked to Jarvis",
    "nobody" to "nobody seen", "unsure" to "not sure it is you", "not_you" to "somebody else", "error" to "a look failed",
)

// ---------------------------------------------------------------------------------------------------------------------------------- voices
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun V2VoicesPage(actions: MainActions, onOpenPanel: (String) -> Unit, onBack: () -> Unit) {
    val v = v2Colors()
    var enrolling by remember { mutableStateOf(false) }
    val d = rememberPanelData(actions, "api/voiceprint") { if (enrolling) 2000L else 5000L }
    var confirmForget by remember { mutableStateOf<String?>(null) }
    V2PageFrame("Voice recognition", onBack, "Who is speaking: label only, nothing is blocked") {
        val s = d.data ?: run { V2Waiting(d); return@V2PageFrame }
        fun send(body: JSONObject) = panelPost(actions, d, "api/voiceprint", body)
        val enrol = s.optJSONObject("enrol")
        enrolling = enrol != null
        V2Card {
            V2Row("Recognise voices", if (s.optBoolean("model_ready")) "A small model on the PC compares each sentence with the voices below" else "The speaker model file is missing") {
                V2Switch(s.optBoolean("enabled"), { send(JSONObject().put("enabled", it)) }, enabled = s.optBoolean("model_ready"))
            }
        }
        val people = s.optJSONArray("people").objects()
        V2Label("Enrolled voices")
        if (people.isEmpty()) Text("Nobody enrolled yet", color = v.muted, fontSize = 14.sp)
        V2Card {
            people.forEachIndexed { i, p ->
                if (i > 0) V2Divider()
                val n = p.optString("name")
                val samples = p.optInt("samples")
                V2Row(n, if (samples == 1) "1 voice sample" else "$samples voice samples") {
                    V2IconButton(V2Icons.Plus, "Add a sentence to this voice", { send(JSONObject().put("enrol", n).put("count", 1)) }, size = 40.dp, enabled = enrol == null)
                    V2IconButton(V2Icons.Close, "Forget this voice", { confirmForget = n }, size = 40.dp)
                }
            }
        }
        if (enrol != null) {
            V2Tile(Modifier.fillMaxWidth(), color = v.accentContainer) {
                Text("Enrolling ${enrol.optString("enrol")}: ${enrol.optInt("taken")} of ${enrol.optInt("total")} taken. Say a sentence of 1.2 s or longer, as you normally talk to Jarvis.", color = v.onAccentContainer, fontSize = 14.sp)
                enrol.strOrNull("note")?.takeIf { it.isNotEmpty() }?.let { Text("last sentence: $it", color = v.onAccentContainer, fontSize = 13.sp) }
                Spacer(Modifier.height(8.dp))
                V2Button("Stop (keep what was learned)", { send(JSONObject().put("cancel", true)) }, Modifier.fillMaxWidth(), filled = false)
            }
        } else {
            s.optJSONObject("enrol_done")?.let { Text("Added to ${it.optString("enrol")}: ${it.optInt("samples")} voice samples now.", color = v.success, fontSize = 13.sp) }
            var newName by remember { mutableStateOf("") }
            V2Field(newName, { newName = it.take(40) }, "Enrol a new voice", "Name", onDone = { if (newName.isNotBlank()) { send(JSONObject().put("enrol", newName.trim())); newName = "" } })
            V2Button("Enrol (6 sentences)", { if (newName.isNotBlank()) { send(JSONObject().put("enrol", newName.trim())); newName = "" } }, Modifier.fillMaxWidth(), enabled = newName.isNotBlank() && s.optBoolean("model_ready"))
        }

        V2Label("Strictness")
        var thr by remember(s.optDouble("threshold")) { mutableFloatStateOf(s.optDouble("threshold", 0.7).toFloat()) }
        V2Card {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row { Text("Match needed", color = v.text, fontSize = 15.sp, modifier = Modifier.weight(1f)); Text("%.2f".format(Locale.US, thr), color = v.accent, fontWeight = FontWeight.SemiBold) }
                Slider(
                    thr, { thr = it }, onValueChangeFinished = { send(JSONObject().put("threshold", Math.round(thr * 100) / 100.0)) }, valueRange = 0.4f..0.95f,
                    modifier = Modifier.semantics { contentDescription = tr("Match needed") },
                    colors = SliderDefaults.colors(thumbColor = v.accent, activeTrackColor = v.accent, inactiveTrackColor = v.surfaceHighest),
                )
                Text("Higher = fewer strangers get your name, but more of your own sentences stay unlabelled.", color = v.muted, fontSize = 12.sp)
            }
        }
        val recent = s.optJSONArray("recent").objects().reversed()
        if (recent.isNotEmpty()) {
            Text("Recent matches", color = v.muted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in recent) {
                    val known = r.optBoolean("known")
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (known) v.accentContainer else v.warmContainer).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("%.2f".format(Locale.US, r.optDouble("score")) + if (known) " " + r.optString("who") else "", color = if (known) v.onAccentContainer else v.onWarmContainer, fontSize = 13.sp)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            V2Kpi("${s.optInt("known") + s.optInt("unknown")}", "sentences checked", Modifier.weight(1f))
            V2Kpi("${s.optInt("known")}", "a known voice", Modifier.weight(1f), accent = s.optInt("known") > 0)
            V2Kpi(if (s.isNull("avg_ms")) "–" else "${s.optDouble("avg_ms").toInt()} ms", "per check", Modifier.weight(1f))
        }
        V2Button("Open in the panel", { onOpenPanel("voiceCard") }, Modifier.fillMaxWidth(), filled = false)
    }
    confirmForget?.let { n ->
        V2Dialog("Forget this voice?", "All voice samples of $n are deleted. You can enrol again later.", "Forget", onConfirm = {
            confirmForget = null
            actions.onPanel("api/voiceprint", JSONObject().put("forget", n)) { r -> if (r != null) d.data = r }
        }, onDismiss = { confirmForget = null }, danger = true)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------- music
@Composable
private fun V2MusicPage(state: MainUiState, actions: MainActions, onOpenPanel: (String) -> Unit, onBack: () -> Unit) {
    val v = v2Colors()
    val d = rememberPanelData(actions, "api/music") { 4000L }
    V2PageFrame("Music and radio", onBack) {
        val m = d.data ?: run { V2Waiting(d); return@V2PageFrame }
        val now = m.optJSONObject("now")
        V2Tile(Modifier.fillMaxWidth(), color = v.surface) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                V2IconBadge(V2Icons.Music, v.warmContainer, v.onWarmContainer, size = 56.dp)
                Column(Modifier.weight(1f)) {
                    Text(now?.strOrNull("title") ?: "Nothing playing", color = v.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text(now?.strOrNull("artist") ?: "Say: Jarvis, play something random.", color = v.muted, fontSize = 13.sp, maxLines = 2)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V2Button("Next", { actions.onMusicNext() }, Modifier.weight(1f), enabled = m.optBoolean("enabled"))
                V2Button("Stop", { actions.onMusicStop() }, Modifier.weight(1f), filled = false)
            }
        }
        V2Card {
            V2Row("Music folder", if (m.optBoolean("enabled")) "${m.optInt("tracks")} songs" else "Off: no folder set on the PC")
        }
        V2Label("Try saying")
        V2Card {
            for ((i, line) in listOf("“play something random”", "“play a song by Queen”", "“play jazz radio”", "“next song”, “stop the music”").withIndex()) {
                if (i > 0) V2Divider()
                V2Row(line)
            }
        }
        V2Button("Open in the panel", { onOpenPanel("musicCard") }, Modifier.fillMaxWidth(), filled = false)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------- memory
@Composable
private fun V2MemoryPage(state: MainUiState, actions: MainActions, onOpenPanel: (String) -> Unit, onBack: () -> Unit) {
    val v = v2Colors()
    val d = rememberPanelData(actions, "api/memory") { 15000L }
    var confirm by remember { mutableStateOf<String?>(null) }
    V2PageFrame("Memory", onBack, "What Jarvis remembers about the people it talks to") {
        val m = d.data ?: run { V2Waiting(d); return@V2PageFrame }
        V2Card { V2Row("Memory", if (m.optBoolean("on")) "On · switched in the panel (it restarts the voice server)" else "Off · switched in the panel") }
        val robots = m.optJSONArray("memory").objects()
        if (robots.isEmpty()) Text("Nothing remembered yet.", color = v.muted, fontSize = 14.sp)
        for (r in robots) {
            val notes = r.optJSONObject("notes") ?: JSONObject()
            val people = notes.optJSONObject("people")
            if (people != null) for (name in people.keys()) {
                val facts = people.optJSONArray(name)
                V2Label(if (name == "someone") "Someone" else name)
                V2Card {
                    for (i in 0 until (facts?.length() ?: 0)) {
                        if (i > 0) V2Divider()
                        V2Row(facts!!.optString(i))
                    }
                }
            }
            for ((key, label) in listOf("preferences" to "Preferences", "open_items" to "Open items")) {
                val arr = notes.optJSONArray(key) ?: continue
                if (arr.length() == 0) continue
                V2Label(label)
                V2Card {
                    for (i in 0 until arr.length()) {
                        if (i > 0) V2Divider()
                        V2Row(arr.optString(i))
                    }
                }
            }
            V2Button("Forget these notes", { confirm = r.optString("device") }, Modifier.fillMaxWidth(), filled = false, danger = true)
        }
        V2Button("Open in the panel", { onOpenPanel("memCard") }, Modifier.fillMaxWidth(), filled = false)
    }
    confirm?.let { dev ->
        V2Dialog("Forget the notes?", "Everything Jarvis noted about the people it talks to is deleted. It starts again with the next conversation.", "Forget", onConfirm = {
            confirm = null
            actions.onPanel("api/memory/forget", JSONObject().put("device", dev)) { _ -> actions.onPanel("api/memory", null) { r2 -> if (r2 != null) d.data = r2 } }
        }, onDismiss = { confirm = null }, danger = true)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------- volume
@Composable
private fun V2VolumePage(state: MainUiState, actions: MainActions, onOpenGroup: (String) -> Unit, onBack: () -> Unit) {
    val v = v2Colors()
    val s = state.status
    V2PageFrame("Volume", onBack) {
        val robot = s?.volume
        if (robot == null) { Text("This robot's firmware has no volume control.", color = v.muted, fontSize = 14.sp); return@V2PageFrame }
        var live by remember(robot) { mutableFloatStateOf(robot.toFloat()) }
        V2Tile(Modifier.fillMaxWidth(), color = v.surface) {
            Text("${live.toInt()}", color = v.text, fontSize = 64.sp, fontWeight = FontWeight.Bold)
            Slider(
                live, { live = it }, onValueChangeFinished = { actions.onSetVolume(live.toInt()) }, valueRange = 0f..100f,
                modifier = Modifier.semantics { contentDescription = tr("Volume") },
                colors = SliderDefaults.colors(thumbColor = v.accent, activeTrackColor = v.accent, inactiveTrackColor = v.surfaceHighest),
            )
            V2Connected(listOf(25 to "25", 50 to "50", 75 to "75", 100 to "100"), live.toInt().takeIf { it in setOf(25, 50, 75, 100) }, { live = it.toFloat(); actions.onSetVolume(it) })
        }
        val cap = s.volumeCap
        if (cap != null && cap < 100) Text("Quiet hours now: at most $cap.", color = v.muted, fontSize = 13.sp)
        V2Card {
            V2Row("Quiet hours", "When Jarvis is quieter at night", onClick = { onOpenGroup("Screen and quiet hours") }) {
                Icon(V2Icons.Chevron, contentDescription = null, tint = v.muted, modifier = Modifier.size(16.dp))
            }
        }
    }
}
