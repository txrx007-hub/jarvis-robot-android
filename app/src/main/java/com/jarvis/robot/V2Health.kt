package com.jarvis.robot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Health and usage: what the panel shows in "Robot health", "Reply speed", "Usage", "Conversations & fallbacks", "Services & this PC" and "Watchdog & phone alerts",
// in four tabs so the page stays calm. Read-only apart from the Groq check, the test alert and the server restart.

private val GOOD = Color(0xFF43A047)
private val WARN = Color(0xFFFFB300)
private val BAD = Color(0xFFE53935)

private fun JSONObject?.dbl(k: String): Double? = if (this != null && has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null

private fun fmtMs(ms: Double?): String = when {
    ms == null -> "–"
    ms >= 1000 -> "%.1f s".format(Locale.US, ms / 1000)
    else -> "${ms.roundToInt()} ms"
}

/** Green under 2.5 s, amber under 4.5 s, red above: the panel's own thresholds. */
private fun speedTone(ms: Double?): Color? = when {
    ms == null -> null
    ms < 2500 -> GOOD
    ms < 4500 -> WARN
    else -> BAD
}

private fun fmtDur(sec: Double): String {
    if (sec.isNaN()) return "–"
    val s = sec.roundToInt()
    if (s < 90) return "$s s"
    val m = (s / 60.0).roundToInt()
    if (m < 90) return "$m min"
    val h = s / 3600.0
    return if (h < 48) "%.1f h".format(Locale.US, h) else "${(h / 24).roundToInt()} " + tr("days")
}

private fun compact(n: Double): String {
    val r = n.roundToInt()
    return when {
        r >= 1_000_000 -> "%.1fM".format(Locale.US, n / 1e6)
        r >= 10_000 -> "${(n / 1e3).roundToInt()}K"
        r >= 1_000 -> "%.1fK".format(Locale.US, n / 1e3)
        else -> "$r"
    }
}

private fun whenText(epochS: Long): String = SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(epochS * 1000))

private enum class HealthTab(val label: String) { ROBOT("Robot"), SPEED("Speed"), USAGE("Usage"), SYSTEM("System") }

@Composable
internal fun V2HealthPage(actions: MainActions, onOpenPanel: (String) -> Unit, onBack: () -> Unit, initialTab: Int = 0) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val t = rememberPanelData(actions, "api/telemetry") { 10000L }
    V2PageFrame("Health and usage", onBack, "Robot, reply speed, free quotas, services") {
        V2Connected(HealthTab.entries.map { it.ordinal to it.label }, tab, { tab = it })
        val tel = t.data
        when (HealthTab.entries[tab]) {
            HealthTab.USAGE -> HealthUsage(actions)
            else -> {
                if (tel == null) { V2Waiting(t); return@V2PageFrame }
                if (tel.has("error")) { Text(tel.optString("error"), color = v2Colors().danger, fontSize = 14.sp); return@V2PageFrame }
                when (HealthTab.entries[tab]) {
                    HealthTab.ROBOT -> HealthRobot(tel.optJSONObject("robot") ?: JSONObject())
                    HealthTab.SPEED -> HealthSpeed(tel.optJSONObject("speed") ?: JSONObject())
                    else -> HealthSystem(tel, actions)
                }
            }
        }
        V2Button("Open in the panel", { onOpenPanel(when (HealthTab.entries[tab]) { HealthTab.SPEED -> "speedCard"; HealthTab.USAGE -> "usageCard"; HealthTab.SYSTEM -> "sysCard"; else -> "robotCard" }) }, Modifier.fillMaxWidth(), filled = false)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------- small pieces
/** Two big numbers side by side, as many rows as needed. */
@Composable
private fun KpiGrid(items: List<Triple<String, String, Color?>>) {
    for (row in items.chunked(2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for ((value, label, tone) in row) V2Kpi(value, label, Modifier.weight(1f).fillMaxHeight(), tone = tone)
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** A labelled bar: how much of something is used; green, amber from 70 %, red from 90 %. [limit] null or 0: just the number. */
@Composable
private fun Meter(label: String, used: Double, limit: Double?, note: String? = null, tone: Color? = null, showValue: Boolean = true) {
    val v = v2Colors()
    val has = limit != null && limit > 0
    val frac = if (has) (used / limit!!).coerceIn(0.0, 1.0).toFloat() else 0f
    val color = tone ?: when { !has -> v.muted; frac >= 0.9f -> BAD; frac >= 0.7f -> WARN; else -> v.accent }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, color = v.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (showValue) Text(compact(used) + if (has) " / " + compact(limit!!) else "", color = v.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(v.surfaceHighest)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(if (used > 0 && has) frac.coerceAtLeast(0.02f) else 0f).clip(CircleShape).background(color))
        }
        if (!note.isNullOrEmpty()) Text(note, color = v.muted, fontSize = 12.sp)
    }
}

/** A line over the last 24 hours; [series] is the panel's [[minutes ago, value], ...]. A gap of more than 10 minutes breaks the line (the robot was away). */
@Composable
private fun Spark(series: JSONArray?, lo: Double, hi: Double, color: Color, modifier: Modifier = Modifier, height: Dp = 64.dp) {
    val v = v2Colors()
    val pts = (0 until (series?.length() ?: 0)).mapNotNull { i -> series?.optJSONArray(i)?.let { a -> a.optDouble(0) to a.optDouble(1) } }.filter { it.first <= 1440 }.sortedByDescending { it.first }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val grid = v.outline.copy(alpha = 0.6f)
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        if (pts.size < 2) return@Canvas
        val min = minOf(lo, pts.minOf { it.second })
        val max = maxOf(hi, pts.maxOf { it.second })
        val pad = 3.dp.toPx()
        fun x(age: Double) = (size.width * (1 - age / 1440.0)).toFloat()
        fun y(value: Double) = (size.height - pad - (value - min) / (max - min).coerceAtLeast(1e-6) * (size.height - 2 * pad)).toFloat()
        val path = Path()
        var last: Double? = null
        for ((age, value) in pts) {
            if (last == null || last - age > 10) path.moveTo(x(age), y(value)) else path.lineTo(x(age), y(value))
            last = age
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Little columns, one per day (the usage of the last 14 days); today's is full strength. */
@Composable
private fun DayBars(series: JSONArray?, key: String, color: Color) {
    val v = v2Colors()
    val vals = (0 until (series?.length() ?: 0)).map { series?.optJSONObject(it).dbl(key) ?: 0.0 }
    val max = (vals.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        drawLine(v.outline.copy(alpha = 0.6f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        if (vals.isEmpty()) return@Canvas
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (vals.size - 1)) / vals.size
        vals.forEachIndexed { i, x ->
            val h = if (x > 0) (x / max * (size.height - 4.dp.toPx())).toFloat().coerceAtLeast(2.dp.toPx()) else 0f
            drawRoundRect(color.copy(alpha = if (i == vals.lastIndex) 1f else 0.55f), Offset(i * (w + gap), size.height - h), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }
    }
}

@Composable
private fun Dot(color: Color, size: Dp = 10.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
private fun Legend(color: Color, text: String) {
    val v = v2Colors()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Dot(color, 8.dp)
        Text(text, color = v.muted, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------- robot
@Composable
private fun ColumnScope.HealthRobot(r: JSONObject) {
    val v = v2Colors()
    if (r.optInt("samples") == 0) {
        Text("No readings yet. The panel looks at the robot once a minute while it runs.", color = v.muted, fontSize = 14.sp)
        return
    }
    val n = r.optJSONObject("now")
    val online = r.optBoolean("online")
    V2Tile(Modifier.fillMaxWidth(), color = v.surface) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Dot(if (online) GOOD else BAD, 12.dp)
            Column {
                Text(if (online) "Online" else "Not answering", color = v.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                val lastSeen = r.dbl("last_seen")
                Text(
                    if (online) listOfNotNull(
                        n.dbl("uptime")?.let { tr("running for") + " " + fmtDur(it) }, n?.strOrNull("ip"),
                        if (n?.optBoolean("napping") == true) tr("Napping") else null,
                        n?.strOrNull("power")?.let { tr(if (it == "console") "on USB" else "on battery") },
                    ).joinToString(" · ")
                    else (if (lastSeen != null) tr("since") + " " + hhmm(lastSeen.toLong()) + " (" + fmtDur(System.currentTimeMillis() / 1000.0 - lastSeen) + ") · " else "") +
                        tr("asleep, napping off Wi-Fi, or out of range"),
                    color = v.muted, fontSize = 13.sp,
                )
            }
        }
    }

    val items = mutableListOf<Triple<String, String, Color?>>()
    if (n != null) {
        val rssi = n.dbl("rssi")
        items += Triple(rssi?.let { "${it.toInt()} dBm" } ?: "–", tr("Wi-Fi signal") + " · " + tr("average") + " ${r.optJSONObject("rssi").dbl("avg")?.toInt() ?: "–"}, " + tr("worst") + " ${r.optJSONObject("rssi").dbl("min")?.toInt() ?: "–"}", rssi?.let { if (it > -67) GOOD else if (it > -80) WARN else BAD })
        val heap = n.dbl("heap")
        items += Triple(heap?.let { "${it.toInt()} KB" } ?: "–", tr("Largest free block") + " · " + tr("lowest in 24 h") + " ${r.optJSONObject("heap").dbl("min")?.toInt() ?: "–"}", heap?.let { if (it >= 40) GOOD else if (it >= 20) WARN else BAD })
        items += Triple(n.dbl("psram")?.let { "%.1f MB".format(Locale.US, it / 1024) } ?: "–", tr("Free PSRAM") + " · " + tr("free RAM") + " ${n.dbl("ram")?.toInt() ?: "–"} KB", null)
    }
    val up = r.dbl("up_pct")
    items += Triple(up?.let { "${it.toInt()}%" } ?: "–", tr("of the last 24 h it answered"), up?.let { if (it >= 90) GOOD else if (it >= 60) WARN else BAD })
    items += Triple("${r.optInt("reboots_day")}", tr("restarts in 24 h (sleep wake-ups count)"), if (r.optInt("reboots_day") > 0) WARN else GOOD)
    val temp = n.dbl("temp")
    if (temp != null) items += Triple("%.1f °C".format(Locale.US, temp), tr("Chip temperature") + " · 24 h " + (r.optJSONObject("temp").dbl("min")?.let { "%.0f".format(Locale.US, it) } ?: "–") + "–" + (r.optJSONObject("temp").dbl("max")?.let { "%.0f".format(Locale.US, it) } ?: "–"), if (temp < 70) GOOD else if (temp < 80) WARN else BAD)
    if (n.dbl("wdisc") != null) items += Triple("${r.optInt("wifi_drops_day")}", tr("Wi-Fi drops in 24 h"), if (r.optInt("wifi_drops_day") > 0) WARN else GOOD)
    KpiGrid(items)

    // ---- the microphone over the last minute
    val peak = n.dbl("mic")
    if (peak != null) {
        val clip = n.dbl("clip")?.toInt() ?: 0
        val mic = r.optJSONObject("mic")
        val (word, tone) = when { clip > 0 || peak >= -3 -> (if (clip > 0) "clipping" else "too loud") to BAD; peak >= -12 -> "loud" to WARN; else -> "fine" to GOOD }
        V2Card {
            Meter(tr("Microphone, last minute") + ": ${"%.0f".format(Locale.US, peak)} dBFS (" + tr(word) + ")", (peak + 60).coerceIn(0.0, 60.0), 60.0,
                "$clip " + tr("samples clipped") + " · 24 h: ${mic?.optInt("clip_minutes") ?: 0} min " + tr("clipping") + ", ${mic?.optInt("loud_minutes") ?: 0} min " + tr("too loud"), tone, showValue = false)
        }
    }

    // ---- 24 hours
    V2Label("Last 24 hours")
    V2Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row { Text("Wi-Fi signal", color = v.text, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text("higher is better", color = v.muted, fontSize = 12.sp) }
            Spark(r.optJSONArray("rssi_series"), -90.0, -40.0, v.accent)
        }
        V2Divider()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row { Text("Largest free memory block", color = v.text, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text("below 20 KB is trouble", color = v.muted, fontSize = 12.sp) }
            Spark(r.optJSONArray("heap_series"), 0.0, 80.0, v.warm)
        }
    }

    // ---- restarts
    val boots = r.optJSONArray("reboots").objects().take(4)
    if (boots.isNotEmpty()) {
        V2Label("Latest restarts")
        V2Card {
            boots.forEachIndexed { i, b ->
                if (i > 0) V2Divider()
                val reason = b.optString("reason").ifEmpty { "reason not reported" }
                V2Row(whenText(b.optLong("t")), (b.dbl("gap_s")?.let { tr("after") + " " + fmtDur(it) + " " + tr("away") } ?: "") + (b.optJSONArray("flags")?.let { f -> (0 until f.length()).joinToString("") { " · " + tr(f.optString(it)) } } ?: "")) {
                    Text(reason, color = v.muted, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------- speed
@Composable
private fun ColumnScope.HealthSpeed(sp: JSONObject) {
    val v = v2Colors()
    var win by rememberSaveable { mutableStateOf("day") }
    V2Connected(listOf("day" to "24 hours", "week" to "7 days"), win, { win = it })
    val w = sp.optJSONObject(win) ?: JSONObject()
    val all = w.optJSONObject("all") ?: JSONObject()
    val brain = w.optJSONObject("brain") ?: JSONObject()
    val instant = w.optJSONObject("instant") ?: JSONObject()
    if (all.optInt("n") == 0) {
        Text("No spoken turns recorded in this period yet. Each time you talk to Jarvis, the time from the end of your sentence to the first sound shows up here.", color = v.muted, fontSize = 14.sp)
        return
    }
    val useBrain = brain.optInt("n") > 0
    val stages = if (useBrain) brain else all
    val total = stages.optJSONObject("total").dbl("p50")
    fun p50(k: String) = stages.optJSONObject(k).dbl("p50") ?: 0.0
    val sum = (p50("ears") + p50("brain") + p50("voice")).takeIf { it > 0 } ?: 1.0
    V2Tile(Modifier.fillMaxWidth(), color = v.surface) {
        Text(fmtMs(total), color = speedTone(total) ?: v.text, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Text((if (useBrain) "typical answer from the brain" else "typical reply") + " (" + tr("median of") + " ${stages.optInt("n")})", color = v.muted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for ((k, c) in listOf("ears" to v.accent, "brain" to v.warm, "voice" to v.success)) {
                Box(Modifier.weight((p50(k) / sum).toFloat().coerceAtLeast(0.02f)).fillMaxHeight().background(c))
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Legend(v.accent, tr("hearing you") + ": " + fmtMs(p50("ears")))
            Legend(v.warm, tr("thinking, first word") + ": " + fmtMs(p50("brain")))
            Legend(v.success, tr("voice, first sound") + ": " + fmtMs(p50("voice")))
        }
    }
    V2Card {
        var i = 0
        for ((name, s) in listOf("Brain answers" to brain, "Instant commands" to instant, "All" to all)) {
            if (s.optInt("n") == 0) continue
            if (i++ > 0) V2Divider()
            val t = s.optJSONObject("total")
            V2Row(name, "${s.optInt("n")} " + tr("turns") + " · " + tr("slowest 10%") + " ${fmtMs(t.dbl("p90"))}") {
                Text(fmtMs(t.dbl("p50")), color = speedTone(t.dbl("p50")) ?: v.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    val recent = sp.optJSONArray("recent").objects().take(6)
    if (recent.isNotEmpty()) {
        V2Label("Latest turns")
        V2Card {
            recent.forEachIndexed { i, r ->
                if (i > 0) V2Divider()
                V2Row(hhmm(r.optLong("t")) + " · " + tr(if (r.optString("path") == "instant") "instant" else "brain"),
                    tr("hearing") + " ${fmtMs(r.dbl("ears"))} · " + tr("thinking") + " ${fmtMs(r.dbl("brain"))} · " + tr("voice") + " ${fmtMs(r.dbl("voice"))}") {
                    Text(fmtMs(r.dbl("total")), color = speedTone(r.dbl("total")) ?: v.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    Text("Measured on the PC from the end of your sentence to the first sound sent to the robot. Green under 2.5 s, amber under 4.5 s.", color = v.muted, fontSize = 12.sp)
}

// ---------------------------------------------------------------------------------------------------------------------------------- usage
private val EXTRA_TITLES = mapOf("cerebras" to "Cerebras · brain", "anthropic" to "Claude · brain (paid)")

@Composable
private fun ColumnScope.HealthUsage(actions: MainActions) {
    val v = v2Colors()
    val d = rememberPanelData(actions, "api/usage") { 15000L }
    val u = d.data ?: run { V2Waiting(d); return }
    val providers = u.optJSONObject("providers")
    if (providers == null) { Text(u.strOrNull("error") ?: "No data", color = v.muted, fontSize = 14.sp); return }

    // ---- warnings: a free limit nearly used up (80 % amber, 95 % red)
    val warns = mutableListOf<Pair<Boolean, String>>()
    for (prov in providers.keys()) {
        for (m in providers.optJSONObject(prov).optJSONArray("models").objects()) {
            for ((lim, key, what) in listOf(Triple("rpd", "requests", "requests today"), Triple("tpd", "tokens", "tokens today"))) {
                var cap = m.optJSONObject("limits").dbl(lim)
                var used = m.optJSONObject("today").dbl(key)
                val est = m.optJSONObject("est")
                if (lim == "tpd" && est != null) { cap = est.dbl("limit"); used = est.dbl("tokens") }
                if (cap == null || cap <= 0 || used == null) continue
                val pct = (used / cap * 100).roundToInt()
                if (pct >= 80) warns += (pct >= 95) to "$prov ${m.optString("model")}: $pct% " + tr("of the free") + " " + tr(what) + " (${compact(used)} / ${compact(cap)})"
            }
        }
    }
    val tav = u.optJSONObject("live")?.optJSONObject("tavily")
    if (tav?.optBoolean("ok") == true && (tav.dbl("plan_limit") ?: 0.0) > 0) {
        val pct = ((tav.dbl("plan_usage") ?: 0.0) / tav.dbl("plan_limit")!! * 100).roundToInt()
        if (pct >= 80) warns += (pct >= 95) to "Tavily: $pct% " + tr("of this month's credits")
    }
    for ((bad, text) in warns) {
        V2Tile(Modifier.fillMaxWidth(), color = if (bad) BAD.copy(alpha = 0.22f) else v.warmContainer, padding = 14.dp) {
            Text(if (bad) "Almost used up" else "Getting full", color = if (bad) BAD else v.onWarmContainer, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = v.text, fontSize = 13.sp)
        }
    }

    // ---- brain failovers
    val fo = u.optJSONObject("failover")?.optJSONObject("answer")?.optJSONObject("day")
    if (fo != null && fo.optInt("requests") > 0) {
        val pct = (fo.optInt("backups") * 100.0 / fo.optInt("requests")).roundToInt()
        V2Card {
            V2Row("Brain failovers, 24 h", "${fo.optInt("failovers")} " + tr("failed") + " · ${fo.optInt("backups")} " + tr("of") + " ${fo.optInt("requests")} " + tr("answers from a backup brain")) {
                Text("$pct%", color = if (pct < 5) GOOD else if (pct < 20) WARN else BAD, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // ---- Tavily
    val tp = providers.optJSONObject("tavily")
    if (tp != null) ProviderCard("Tavily · web search", tav?.takeIf { it.optBoolean("ok") }?.strOrNull("plan") ?: "no data") {
        if (tav?.optBoolean("ok") == true) Meter("Credits this billing cycle", tav.dbl("plan_usage") ?: 0.0, tav.dbl("plan_limit"))
        else Text(tav?.strOrNull("error") ?: "no data", color = v.muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            V2Kpi("${tp.optJSONObject("today").dbl("credits")?.toInt() ?: 0}", "credits used by Jarvis today", Modifier.weight(1f))
            V2Kpi("${tp.optJSONObject("today").dbl("requests")?.toInt() ?: 0}", "searches today", Modifier.weight(1f))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DayBars(tp.optJSONArray("series"), "credits", v.accent)
            Text("Credits per day, last 14 days", color = v.muted, fontSize = 12.sp)
        }
    }

    // ---- Groq
    val gp = providers.optJSONObject("groq")
    if (gp != null) {
        val live = u.optJSONObject("live")?.optJSONObject("groq")
        val ok = live?.optBoolean("ok") == true
        val age = u.dbl("groq_age")
        ProviderCard("Groq · brain", if (ok) tr("live") + " " + (if (age == null || age < 90) tr("just now") else "${(age / 60).roundToInt()} min " + tr("ago")) else "not checked yet") {
            if (ok) {
                Meter("Requests today (Groq live)", (live!!.dbl("limit_requests") ?: 0.0) - (live.dbl("remaining_requests") ?: 0.0), live.dbl("limit_requests"))
                Meter("Tokens this minute (Groq live)", (live.dbl("limit_tokens") ?: 0.0) - (live.dbl("remaining_tokens") ?: 0.0), live.dbl("limit_tokens"))
            } else {
                Text("Check Groq's live numbers now. This costs 1 of the 1,000 free requests a day.", color = v.muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                V2Button("Check Groq limits now", { actions.onPanel("api/usage?probe=force", null) { r -> if (r != null) d.data = r } }, Modifier.fillMaxWidth(), filled = false)
            }
            ModelMeters(gp)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DayBars(gp.optJSONArray("series"), "tokens", v.accent)
                Text("Tokens per day, last 14 days", color = v.muted, fontSize = 12.sp)
            }
        }
    }

    // ---- Gemini
    val ge = providers.optJSONObject("gemini")
    if (ge != null) ProviderCard("Gemini · brain and camera", "${ge.optJSONObject("today").dbl("requests")?.toInt() ?: 0} " + tr("requests today")) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            V2Kpi(compact(ge.optJSONObject("today").dbl("tokens") ?: 0.0), "tokens today", Modifier.weight(1f))
            V2Kpi("${ge.optJSONObject("today").dbl("requests")?.toInt() ?: 0}", "requests today", Modifier.weight(1f))
        }
        for (m in ge.optJSONArray("models").objects().take(4)) {
            val lim = m.optJSONObject("limits")
            Text(m.optString("model") + if (m.optString("kind") == "vision") " (" + tr("camera") + ")" else "", color = v.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            Meter("requests today", m.optJSONObject("today").dbl("requests") ?: 0.0, lim.dbl("rpd"))
            Meter("requests this minute", m.dbl("rpm") ?: 0.0, lim.dbl("rpm"))
            Meter("tokens this minute", m.dbl("tpm") ?: 0.0, lim.dbl("tpm"))
            m.optJSONObject("cost")?.let { Text(tr("Estimated cost") + ": $" + "%.4f".format(Locale.US, it.optDouble("today")) + " " + tr("today"), color = v.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        if (ge.optBoolean("paid")) Text("Paid tier: the free daily limits do not apply.", color = v.muted, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DayBars(ge.optJSONArray("series"), "requests", v.accent)
            Text("Requests per day, last 14 days", color = v.muted, fontSize = 12.sp)
        }
    }

    // ---- Cerebras / Claude: only once they are used or selected
    val shown = u.optJSONArray("show")
    for (i in 0 until (shown?.length() ?: 0)) {
        val prov = shown!!.optString(i)
        val p = providers.optJSONObject(prov) ?: continue
        ProviderCard(EXTRA_TITLES[prov] ?: prov, "${p.optJSONObject("today").dbl("requests")?.toInt() ?: 0} " + tr("requests in 24 h")) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V2Kpi(compact(p.optJSONObject("today").dbl("tokens") ?: 0.0), "tokens in the last 24 h", Modifier.weight(1f))
            }
            ModelMeters(p)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DayBars(p.optJSONArray("series"), "tokens", v.accent)
                Text("Tokens per day, last 14 days", color = v.muted, fontSize = 12.sp)
            }
        }
    }
    Text("Counts what goes through Jarvis. Tavily and Groq also show their own live numbers, which include everything on those keys.", color = v.muted, fontSize = 12.sp)
}

/** The model lines of a provider: tokens and requests against the free limits (the estimate when the panel has one). */
@Composable
private fun ModelMeters(p: JSONObject) {
    val v = v2Colors()
    for (m in p.optJSONArray("models").objects().take(4)) {
        val lim = m.optJSONObject("limits")
        val est = m.optJSONObject("est")
        val tokens = est?.dbl("tokens") ?: m.optJSONObject("today").dbl("tokens") ?: 0.0
        Text(m.optString("model"), color = v.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        Meter(if (est != null) "tokens, estimated in use" else "tokens, last 24 h", tokens, est?.dbl("limit") ?: lim.dbl("tpd"))
        if (lim.dbl("rpd") != null) Meter("requests, last 24 h", m.optJSONObject("today").dbl("requests") ?: 0.0, lim.dbl("rpd"))
        m.optJSONObject("cost")?.let { Text(tr("Estimated cost") + ": $" + "%.4f".format(Locale.US, it.optDouble("today")) + " " + tr("last 24 h"), color = v.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
    }
}

@Composable
private fun ProviderCard(title: String, pill: String, content: @Composable ColumnScope.() -> Unit) {
    val v = v2Colors()
    V2Card {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = v.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(Modifier.clip(CircleShape).background(v.accentContainer).padding(horizontal = 10.dp, vertical = 3.dp)) { Text(pill, color = v.onAccentContainer, fontSize = 12.sp) }
        }
        content()
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------- system
@Composable
private fun ColumnScope.HealthSystem(tel: JSONObject, actions: MainActions) {
    val v = v2Colors()
    val sys = tel.optJSONObject("system") ?: JSONObject()

    // ---- services and this PC
    V2Label("Services on the PC")
    V2Card {
        sys.optJSONArray("services").objects().forEachIndexed { i, s ->
            if (i > 0) V2Divider()
            val up = s.optBoolean("up")
            val expected = s.optBoolean("expected")
            V2Row(s.optString("name"), "port ${s.optInt("port")}" + (s.optString("note").takeIf { it.isNotEmpty() }?.let { " · " + tr(it) } ?: ""), leading = { Dot(if (up) GOOD else if (expected) BAD else v.muted) }) {
                Text(
                    if (up) tr("running") + (s.dbl("mem_mb")?.let { " · ${it.toInt()} MB" } ?: "") else if (expected) tr("DOWN") else tr("not needed now"),
                    color = if (!up && expected) BAD else v.muted, fontSize = 13.sp, fontWeight = if (!up && expected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
    val pc = sys.optJSONObject("pc")
    if (pc != null) {
        val cpu = pc.dbl("cpu")
        val ram = pc.dbl("ram_pct")
        val disk = pc.dbl("disk_free_gb")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            V2Kpi(cpu?.let { "${it.toInt()}%" } ?: "–", "this PC: processor", Modifier.weight(1f), tone = cpu?.let { if (it >= 90) BAD else if (it >= 70) WARN else GOOD })
            V2Kpi(ram?.let { "${it.toInt()}%" } ?: "–", tr("memory in use") + " (${pc.dbl("ram_free_gb") ?: "–"} GB " + tr("free") + ")", Modifier.weight(1f), tone = ram?.let { if (it >= 90) BAD else if (it >= 75) WARN else GOOD })
            V2Kpi(disk?.let { "${it.toInt()} GB" } ?: "–", "free on this drive", Modifier.weight(1f), tone = disk?.let { if (it < 10) BAD else null })
        }
    }

    // ---- conversations and fallbacks
    val conv = tel.optJSONObject("conv")
    if (conv != null) {
        var win by rememberSaveable { mutableStateOf("day") }
        V2Label("Conversations")
        V2Connected(listOf("day" to "24 hours", "week" to "7 days"), win, { win = it })
        val w = conv.optJSONObject(win) ?: JSONObject()
        val sessions = w.optInt("sessions")
        val falseWakes = w.optInt("false_wakes")
        KpiGrid(
            listOf(
                Triple("${w.optInt("wakes")}", "wake-ups (wake word heard)", null),
                Triple("$sessions", tr("conversations") + " · ${w.optInt("heard")} " + tr("sentences heard"), null),
                Triple("$falseWakes" + if (sessions > 0) " (${(100.0 * falseWakes / sessions).roundToInt()}%)" else "", "false wakes: woke but heard nothing", if (sessions > 0 && falseWakes.toDouble() / sessions > 0.3) WARN else GOOD),
                Triple("${w.optInt("drops")}", "conversations cut off (the connection broke)", if (w.optInt("drops") > 0) WARN else GOOD),
                Triple("${w.optInt("robot_wifi_drops")}", "Wi-Fi drops reported by the robot", if (w.optInt("robot_wifi_drops") > 0) WARN else GOOD),
            ),
        )
        val names = conv.optJSONObject("names")
        val fb = w.optJSONObject("fallbacks")
        V2Card {
            if (fb == null || fb.length() == 0) V2Row("Fallbacks", "Nothing needed a backup in this period.")
            else {
                var i = 0
                for (k in fb.keys()) {
                    if (i++ > 0) V2Divider()
                    V2Row(tr(names?.optString(k)?.ifEmpty { k } ?: k)) { Text("${fb.optInt(k)}", color = WARN, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    // ---- watchdog and phone alerts
    val wd = rememberPanelData(actions, "api/watchdog") { 15000L }
    val nt = rememberPanelData(actions, "api/notify") { 30000L }
    var testNote by remember { mutableStateOf<String?>(null) }
    var confirmRestart by remember { mutableStateOf(false) }
    var restartNote by remember { mutableStateOf<String?>(null) }
    V2Label("Watchdog and phone alerts")
    V2Card {
        val w = wd.data
        val n = nt.data
        V2Row("Server watchdog", "Restarts the voice server when it is down or stops answering (at most 3 times in 15 minutes).", leading = { Dot(if (w == null) v.muted else if (w.optBoolean("gave_up")) BAD else if (w.optBoolean("watching")) GOOD else v.muted) }) {
            Text(
                when { w == null -> "--"; w.optBoolean("gave_up") -> "gave up"; w.optBoolean("watching") -> "watching"; else -> "stopped on purpose" },
                color = v.muted, fontSize = 13.sp,
            )
        }
        V2Divider()
        val on = n?.optBoolean("configured") == true
        V2Row("Phone alerts", if (n == null) null else if (on) tr("through ntfy") + " (${n.optString("server")})" else "Not set up: events are only written to the list below.", leading = { Dot(if (on) GOOD else v.muted) }) {
            Text(if (on) "On" else "Off", color = v.muted, fontSize = 13.sp)
        }
    }
    if (nt.data?.optBoolean("configured") == true) {
        V2Button("Send a test alert", {
            testNote = "…"
            actions.onPanel("api/notify", JSONObject().put("action", "test")) { r -> testNote = if (r?.optBoolean("ok") == true) "Sent: it should arrive on the phone in a few seconds." else "The test could not be sent." }
        }, Modifier.fillMaxWidth(), filled = false)
        testNote?.takeIf { it != "…" }?.let { Text(it, color = v.muted, fontSize = 13.sp) }
    }
    val events = wd.data?.optJSONArray("events").objects().take(6)
    if (events.isNotEmpty()) {
        V2Card {
            events.forEachIndexed { i, e ->
                if (i > 0) V2Divider()
                V2Row(e.optString("text"), whenText(e.optLong("t")))
            }
        }
    }
    Text("The alert switches (which events reach the phone) are in the panel.", color = v.muted, fontSize = 12.sp)

    // ---- the voice server itself
    V2Label("Voice server")
    V2Button("Restart the voice server", { confirmRestart = true }, Modifier.fillMaxWidth(), filled = false, danger = true)
    restartNote?.let { Text(it, color = v.muted, fontSize = 13.sp) }
    if (confirmRestart) {
        V2Dialog("Restart the voice server?", "Jarvis stops talking for about 10 seconds and reconnects by itself.", "Restart", onConfirm = {
            confirmRestart = false
            restartNote = "Restarting…"
            actions.onPanel("api/server", JSONObject().put("action", "restart")) { r -> restartNote = if (r?.optBoolean("ok") == true) "Restarting. The robot reconnects by itself in about 10 seconds." else "Could not restart the voice server." }
        }, onDismiss = { confirmRestart = false }, danger = true)
    }
}
