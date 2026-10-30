package com.jarvis.robot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

// The backend pages that came with the second round: Mood and Home Assistant (Health and usage is in V2Health.kt).

// ---------------------------------------------------------------------------------------------------------------------------------- mood
@Composable
internal fun V2MoodPage(actions: MainActions, onOpenPanel: (String) -> Unit, onBack: () -> Unit) {
    val v = v2Colors()
    val d = rememberPanelData(actions, "api/mood") { 15000L }
    V2PageFrame("Mood", onBack, "How Jarvis sounds") {
        val m = d.data ?: run { V2Waiting(d); return@V2PageFrame }
        val cur = m.optString("current", "normal")
        val moods = m.optJSONArray("moods").objects()
        for (row in moods.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                for (mo in row) {
                    val id = mo.optString("id")
                    val on = id == cur
                    V2Tile(
                        Modifier.weight(1f).height(88.dp), color = if (on) v.accentContainer else v.surfaceHigh, padding = 14.dp,
                        onClick = { if (!on) panelPost(actions, d, "api/mood", JSONObject().put("mood", id)) },
                    ) {
                        Text(mo.optString("label"), color = if (on) v.onAccentContainer else v.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Text(if (on) "In use" else mo.optString("emotion"), color = if (on) v.onAccentContainer else v.muted, fontSize = 12.sp, maxLines = 1)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        val now = moods.firstOrNull { it.optString("id") == cur }
        V2Card {
            V2Row(now?.optString("label") ?: "Normal", now?.optString("prompt")?.takeIf { it.isNotEmpty() } ?: "Normal: no extra mood instruction.")
        }
        Text("Applies at once, no restart. You can also say â€œbe cheerfulâ€, â€œbe calmâ€, â€œbe seriousâ€, â€œbe dryâ€ or â€œback to normalâ€.", color = v.muted, fontSize = 13.sp)
        V2Button("Open in the panel", { onOpenPanel("moodCard") }, Modifier.fillMaxWidth(), filled = false)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------- faces
/** The gallery: people the greeter knows by name (numbers of their faces on the PC, no pictures). Enrolling takes pictures with the robot's camera, one every 1.2 s. */
@Composable
internal fun V2FacesPage(state: MainUiState, actions: MainActions, onOpenPanel: (String) -> Unit, onBack: () -> Unit) {
    val v = v2Colors()
    var enrolling by remember { mutableStateOf(false) }
    val d = rememberPanelData(actions, "api/faces") { if (enrolling) 1500L else 6000L }
    var confirmForget by remember { mutableStateOf<String?>(null) }
    V2PageFrame("Faces", onBack, "Jarvis greets the people it knows by name") {
        val s = d.data ?: run { V2Waiting(d); return@V2PageFrame }
        fun send(b: JSONObject) = panelPost(actions, d, "api/faces", b)
        val enrol = s.optJSONObject("enrol")
        enrolling = enrol != null
        val owner = s.optInt("owner_samples")
        V2Card {
            V2Row("You", if (owner > 0) "$owner " + tr("face samples") + " Â· " + tr("changed in the panel under Face finding") else "Not enrolled yet: enrol your own face in the panel under Face finding.", onClick = { onOpenPanel("faceCard") }) {
                androidx.compose.material3.Icon(V2Icons.Chevron, contentDescription = null, tint = v.muted, modifier = Modifier.size(16.dp))
            }
        }

        val people = s.optJSONArray("people").objects()
        V2Label("People it knows")
        if (people.isEmpty()) Text("Nobody yet.", color = v.muted, fontSize = 14.sp)
        else V2Card {
            people.forEachIndexed { i, p ->
                if (i > 0) V2Divider()
                val n = p.optString("name")
                val samples = p.optInt("samples")
                V2Row(n, if (samples == 1) "1 face sample" else "$samples " + tr("face samples")) {
                    V2IconButton(V2Icons.Plus, "Add more pictures of this person", { send(JSONObject().put("enrol", n).put("count", 3)) }, size = 40.dp, enabled = enrol == null && state.online)
                    V2IconButton(V2Icons.Close, "Forget this person", { confirmForget = n }, size = 40.dp)
                }
            }
        }

        if (enrol != null) {
            V2Tile(Modifier.fillMaxWidth(), color = v.accentContainer) {
                Text(tr("Taking pictures of") + " ${enrol.optString("name")}: ${enrol.optInt("taken")} " + tr("of") + " ${enrol.optInt("total")}. " + tr("Look at the robot and keep still."), color = v.onAccentContainer, fontSize = 14.sp)
                enrol.strOrNull("note")?.takeIf { it.isNotEmpty() }?.let { Text(tr("last picture") + ": " + tr(it), color = v.onAccentContainer, fontSize = 13.sp) }
                Spacer(Modifier.height(8.dp))
                V2Button("Stop (keep what was taken)", { send(JSONObject().put("cancel", true)) }, Modifier.fillMaxWidth(), filled = false)
            }
        } else {
            s.optJSONObject("enrol_done")?.let { done ->
                val added = done.optInt("added")
                val why = done.optJSONArray("reasons")?.let { r -> (0 until r.length()).joinToString("; ") { tr(r.optString(it)) } }.orEmpty()
                Text(
                    if (added > 0) tr("Added") + " $added " + tr("pictures to") + " ${done.optString("name")}: ${done.optInt("total")} " + tr("face samples") + " " + tr("now") + (if (why.isNotEmpty()) " ($why)" else "")
                    else tr("Nothing added") + (if (why.isNotEmpty()) ": $why" else ""),
                    color = if (added > 0) v.success else v.danger, fontSize = 13.sp,
                )
            }
            var newName by remember { mutableStateOf("") }
            V2Field(newName, { newName = it.take(40) }, "Enrol a new person", "Name", onDone = { if (newName.isNotBlank()) { send(JSONObject().put("enrol", newName.trim())); newName = "" } })
            V2Button("Take 6 pictures", { if (newName.isNotBlank()) { send(JSONObject().put("enrol", newName.trim())); newName = "" } }, Modifier.fillMaxWidth(), enabled = newName.isNotBlank() && s.optBoolean("model_ready") && state.online)
            if (!s.optBoolean("model_ready")) Text("The face model files are missing on the PC.", color = v.danger, fontSize = 13.sp)
        }
        Text(
            "The person stands about one metre in front of the robot, facing the camera. The robot takes 6 pictures, one every 1.2 seconds; only numbers describing the face are kept, no pictures. " +
                "Then choose Known on the Greeter page.",
            color = v.muted, fontSize = 12.sp,
        )
    }
    confirmForget?.let { n ->
        V2Dialog("Forget this person?", "The face samples of $n are deleted. You can enrol again later.", "Forget", onConfirm = {
            confirmForget = null
            panelPost(actions, d, "api/faces", JSONObject().put("forget", n))
        }, onDismiss = { confirmForget = null }, danger = true)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------- Home Assistant
/** The kinds of Home Assistant things Jarvis can switch; a kind the house does not have is left out once the test has counted what is there. */
private val HA_KINDS = listOf("light", "switch", "fan", "scene", "script", "climate", "input_boolean", "cover", "media_player", "vacuum", "humidifier", "button", "input_button", "automation", "water_heater")

/** Never loosened from a phone tap: change these in the panel if you really want them. */
private val HA_LOCKED = setOf("lock", "alarm_control_panel")

private fun haKinds(s: String): List<String> = s.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() && it != "none" }

@Composable
internal fun V2HomeAssistantPage(actions: MainActions, onOpenPanel: (String) -> Unit, onBack: () -> Unit) {
    val v = v2Colors()
    val st = rememberPanelData(actions, "api/state") { 60000L }
    val log = rememberPanelData(actions, "api/ha-log") { 10000L }
    var test by remember { mutableStateOf<JSONObject?>(null) }
    var testing by remember { mutableStateOf(false) }
    var picks by remember { mutableStateOf(emptyMap<String, Int>()) }   // what the person changed: 2 = straight away, 1 = ask first, 0 = refused
    var saveNote by remember { mutableStateOf<String?>(null) }
    var confirmSave by remember { mutableStateOf(false) }

    val s = st.data
    val keys = s?.optJSONObject("keys")
    val configured = keys?.optBoolean("ha_url") == true && keys.optBoolean("ha_token") == true
    fun runTest() {
        testing = true
        actions.onPanel("api/test-ha", JSONObject()) { r -> testing = false; test = r ?: JSONObject().put("ok", false).put("error", "The backend did not answer.") }
    }
    LaunchedEffect(configured) { if (configured) runTest() }

    V2PageFrame("Home Assistant", onBack, "Voice control of the house") {
        if (s == null) { V2Waiting(st); return@V2PageFrame }
        val params = s.optJSONArray("params").objects()
        fun param(id: String) = params.firstOrNull { it.optString("id") == id }?.opt("value")?.toString().orEmpty()
        val allowed = haKinds(param("ha_allowed"))
        val confirm = haKinds(param("ha_confirm"))
        val counts = test?.takeIf { it.optBoolean("ok") }?.optJSONObject("domains")
        val shown = ((if (counts != null) HA_KINDS.filter { counts.has(it) } else HA_KINDS) + allowed + confirm).distinct().filter { it !in HA_LOCKED }
        fun current(k: String) = if (k in allowed) 2 else if (k in confirm) 1 else 0
        fun chosen(k: String) = picks[k] ?: current(k)
        val dirty = shown.any { chosen(it) != current(it) }

        // ---- is it reachable?
        val ok = test?.optBoolean("ok") == true
        V2Tile(Modifier.fillMaxWidth(), color = v.surface) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                V2IconBadge(V2Icons.House, if (ok) v.accentContainer else v.warmContainer, if (ok) v.onAccentContainer else v.onWarmContainer, size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        when { !configured -> "Not set up"; testing -> "Checkingâ€¦"; ok -> "Connected"; test == null -> "Not checked yet"; else -> "Not reachable" },
                        color = v.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    )
                    val t = test
                    Text(
                        when {
                            !configured -> "The address and the access token go under API keys in the panel."
                            testing || t == null -> "Asking Home Assistantâ€¦"
                            ok -> "${t.optInt("ms")} ms Â· ${t.optInt("entities")} " + tr("things in your home")
                            else -> t.optString("error")
                        },
                        color = v.muted, fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (configured) V2Button("Test connection", { runTest() }, Modifier.fillMaxWidth(), filled = false, enabled = !testing)
            else V2Button("Open the panel", { onOpenPanel("haCard") }, Modifier.fillMaxWidth())
        }

        // ---- what Jarvis may do
        V2Label("What Jarvis may do")
        V2Card {
            shown.forEachIndexed { i, k ->
                if (i > 0) V2Divider()
                V2Row(k, counts?.takeIf { it.has(k) }?.let { "${it.optInt(k)} " + tr("in your home") }) {
                    V2Connected(listOf(2 to "Now", 1 to "Ask", 0 to "No"), chosen(k), { picks = picks + (k to it) }, Modifier.width(160.dp), height = 36.dp)
                }
            }
        }
        Text(
            "Now = switched straight away Â· Ask = only after you say yes Â· No = refused. Locks and alarms are always refused; that can only be changed in the panel.",
            color = v.muted, fontSize = 12.sp,
        )
        if (dirty) V2Button("Save and restart the voice server", { confirmSave = true }, Modifier.fillMaxWidth())
        saveNote?.let { Text(it, color = v.muted, fontSize = 13.sp) }

        // ---- what it did
        val rows = log.data?.optJSONArray("rows").objects().take(8)
        if (rows.isNotEmpty()) {
            V2Label("Latest actions")
            V2Card {
                rows.forEachIndexed { i, r ->
                    if (i > 0) V2Divider()
                    V2Row("${r.optString("service")} Â· ${r.optString("entity")}", hhmm(r.optLong("t")) + if (r.optBoolean("ok")) "" else " Â· " + tr("failed") + " " + r.optString("error")) {
                        Text(if (r.optBoolean("ok")) "done" else "failed", color = if (r.optBoolean("ok")) v.success else v.danger, fontSize = 13.sp)
                    }
                }
            }
        }
        Text("Try: â€œJarvis, turn off the kitchen light.â€", color = v.muted, fontSize = 13.sp)
        V2Button("Open in the panel", { onOpenPanel("haCard") }, Modifier.fillMaxWidth(), filled = false)

        if (confirmSave) {
            V2Dialog(
                "Save and restart?", "The voice server restarts to use the new rules. That takes about 10 seconds; the robot reconnects by itself.", "Save",
                onConfirm = {
                    confirmSave = false
                    fun join(level: Int): String {
                        val keep = (if (level == 2) allowed else confirm).filter { it in HA_LOCKED }
                        val list = (keep + shown.filter { chosen(it) == level }).distinct()
                        return if (list.isEmpty()) "none" else list.joinToString(", ")   // an empty box would mean "use the default"; "none" matches nothing
                    }
                    val ps = JSONObject()
                    for (p in params) {
                        val id = p.optString("id")
                        val value: Any? = when (id) { "ha_allowed" -> join(2); "ha_confirm" -> join(1); else -> p.opt("value") }
                        if (value != null && value != JSONObject.NULL) ps.put(id, value)
                    }
                    val ch = s.optJSONObject("choice")
                    val body = JSONObject().put("ears", ch?.optString("ears")).put("brain", ch?.optString("brain")).put("voice", ch?.optString("voice")).put("params", ps).put("restart", true)
                    saveNote = "Savingâ€¦"
                    actions.onPanel("api/apply", body) { r ->
                        saveNote = if (r?.optBoolean("ok") == true) "Saved. The voice server restarts now, about 10 seconds." else tr("Could not save") + ": " + (r?.optString("error") ?: tr("no answer"))
                        if (r?.optBoolean("ok") == true) { picks = emptyMap(); actions.onPanel("api/state", null) { r2 -> if (r2 != null) st.data = r2 } }
                    }
                },
                onDismiss = { confirmSave = false },
            )
        }
    }
}
