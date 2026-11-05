package com.jarvis.robot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class WService(val name: String, val up: Boolean, val expected: Boolean)

/** The numbers of /api/stats the widget uses (the newest sample, the last hour as series, a few summaries). */
data class WStats(
    val up: Boolean, val napping: Boolean, val cpu: Double?, val ramFreeKb: Double?, val psramFreeKb: Double?, val rssi: Double?, val ping: Double?, val temp: Double?, val uptimeS: Double?,
    val pcCpu: Double?, val pcRam: Double?, val disk: Double?, val voiceServer: Boolean?, val replyMs: Double?, val fallbacks: Int?, val sessions: Int? = null,
    val rx: List<Double?> = emptyList(), val tx: List<Double?> = emptyList(), val cpuSeries: List<Double?> = emptyList(), val ramSeries: List<Double?> = emptyList(),
    val hpSeries: List<Double?> = emptyList(), val rssiSeries: List<Double?> = emptyList(), val pingSeries: List<Double?> = emptyList(), val pcSeries: List<Double?> = emptyList(),
    val pcRamSeries: List<Double?> = emptyList(), val tempSeries: List<Double?> = emptyList(), val micSeries: List<Double?> = emptyList(),
    val replySeconds: List<Double?> = emptyList(), val services: List<WService> = emptyList(),
) {
    companion object {
        private fun JSONObject.d(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null
        private fun JSONObject.list(key: String): List<Double?> {
            val a: JSONArray = optJSONArray(key) ?: return emptyList()
            return List(a.length()) { i -> if (a.isNull(i)) null else a.optDouble(i).takeIf { !it.isNaN() } }
        }

        fun parse(o: JSONObject): WStats {
            val now = o.optJSONObject("now") ?: JSONObject()
            val row = now.optJSONObject("row") ?: JSONObject()
            val s = o.optJSONObject("series") ?: JSONObject()
            val turns = o.optJSONArray("turns")
            val reply = if (turns == null) emptyList() else List(turns.length()) { i -> turns.optJSONObject(i)?.d("total")?.let { it / 1000.0 } }
            val svc = now.optJSONArray("services")
            val services = if (svc == null) emptyList() else List(svc.length()) { i -> svc.getJSONObject(i).let { WService(it.optString("name"), it.optBoolean("up"), it.optBoolean("expected")) } }
            return WStats(
                up = row.optInt("up") == 1, napping = row.optInt("nap") == 1, cpu = row.d("cpu"), ramFreeKb = row.d("ram"), psramFreeKb = row.d("ps"), rssi = row.d("rs"), ping = row.d("pg"),
                temp = row.d("tm"), uptimeS = row.d("ut"), pcCpu = row.d("pc"), pcRam = row.d("pr"), disk = row.d("pd"), voiceServer = if (row.has("vs")) row.optInt("vs") == 1 else null,
                replyMs = now.d("reply_p50"), fallbacks = if (now.has("fallbacks") && !now.isNull("fallbacks")) now.optInt("fallbacks") else null,
                sessions = if (now.has("sessions") && !now.isNull("sessions")) now.optInt("sessions") else null,
                rx = s.list("rx"), tx = s.list("tx"), cpuSeries = s.list("cpu"), ramSeries = s.list("ram"), hpSeries = s.list("hp"), rssiSeries = s.list("rs"), pingSeries = s.list("pg"),
                pcSeries = s.list("pc"), pcRamSeries = s.list("pr"), tempSeries = s.list("tm"), micSeries = s.list("mp"), replySeconds = reply, services = services,
            )
        }
    }
}

/**
 * Draws the Jarvis live stats widget as one picture of w x h dp. The content is planned from the space it gets: a small widget shows a header and the main round counters;
 * more height adds the numbers row, graphs, the services strip and the backend counters; more width adds columns. Whatever space is left is shared out, so nothing sits empty
 * on a big (unfolded) screen and nothing is cut off on a small (folded) one.
 */
object StatsRender {
    private val sans: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    /** The picture in pixels: as sharp as the screen, but no bigger than [budgetPx] pixels (launchers refuse an update whose pictures take too much memory). */
    fun render(context: Context, wDp: Float, hDp: Float, data: WStats?, fetchedAt: Long, hasHost: Boolean, p: Palette, budgetPx: Float = 1_000_000f): Bitmap {
        var scale = context.resources.displayMetrics.density
        if (wDp * scale * hDp * scale > budgetPx) scale = sqrt(budgetPx / (wDp * hDp))
        val bmp = Bitmap.createBitmap(max(1, (wDp * scale).toInt()), max(1, (hDp * scale).toInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        paint(c, wDp, hDp, data, fetchedAt, hasHost, p)
        return bmp
    }

    // ------------------------------------------------------------------------------------------------------------------------- colours and small helpers
    private class K(p: Palette) {
        val card = p.card.toArgb(); val bg = p.bg.toArgb(); val line = p.border.toArgb(); val text = p.text.toArgb(); val muted = p.muted.toArgb(); val accent = p.accent.toArgb()
        val danger = p.danger.toArgb(); val ok = p.success.toArgb(); val warn = p.instant.toArgb(); val field = p.field.toArgb(); val violet = p.ha.toArgb(); val pink = p.music.toArgb(); val dark = p.isDark
    }

    private fun alpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch((a shr 16) and 255, (b shr 16) and 255) shl 16) or (ch((a shr 8) and 255, (b shr 8) and 255) shl 8) or ch(a and 255, b and 255)
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private fun stroke(color: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = w; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface = sans, align: Paint.Align = Paint.Align.LEFT, spacing: Float = 0f) {
        c.drawText(s, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face; textAlign = align; letterSpacing = spacing; fontFeatureSettings = "tnum" })
    }

    private fun measure(s: String, size: Float, face: Typeface = sans) = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = face }.measureText(s)

    private fun num(v: Double?, d: Int = 0) = if (v == null) "–" else if (d == 0) v.toInt().toString() else "%.${d}f".format(Locale.US, v)

    private fun dur(s: Double): String {
        val m = (s / 60).toInt(); val d = m / 1440; val h = m % 1440 / 60
        return if (d > 0) "${d}d ${h}h" else if (h > 0) "${h}h ${m % 60}m" else "${m}m"
    }

    // ------------------------------------------------------------------------------------------------------------------------- the whole widget
    fun paint(c: Canvas, w: Float, h: Float, data: WStats?, fetchedAt: Long, hasHost: Boolean, p: Palette) {
        val k = K(p)
        val radius = 28f
        // card: a slightly lighter top edge fading into the card colour, and a hairline border
        c.drawRoundRect(RectF(0f, 0f, w, h), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w * 0.35f, h, mix(k.card, k.text, if (k.dark) 0.06f else 0f), mix(k.card, k.bg, 0.35f), Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), radius - 0.5f, radius - 0.5f, stroke(alpha(k.text, 28), 1f))

        val pad = if (w < 200f) 10f else 14f
        val tall = h >= 150f
        val bodyTop = header(c, w, pad, tall, data, fetchedAt, k) + if (tall) 12f else 8f
        val body = RectF(pad, bodyTop, w - pad, h - pad)
        if (data == null) {
            val msg = if (!hasHost) listOf("Open Jarvis once", "to connect to the robot") else listOf("No numbers yet", "Tap the arrows to load them")
            msg.forEachIndexed { i, s -> text(c, s, w / 2, (body.top + body.bottom) / 2 + i * 19f - 2f, 14f, if (i == 0) k.text else k.muted, if (i == 0) medium else sans, Paint.Align.CENTER) }
            return
        }
        content(c, body, data, k)
    }

    /** Logo, name, robot state, time of the numbers and the refresh button. Returns where the header ends. */
    private fun header(c: Canvas, w: Float, pad: Float, tall: Boolean, d: WStats?, fetchedAt: Long, k: K): Float {
        val stale = fetchedAt == 0L || System.currentTimeMillis() - fetchedAt > 35 * 60 * 1000
        val r = if (tall) 14f else 11f
        val cx = pad + r; val cy = pad + r
        c.drawCircle(cx, cy, r, fill(alpha(k.accent, 40)))
        val beat = Path().apply { moveTo(cx - r * 0.62f, cy); lineTo(cx - r * 0.2f, cy); lineTo(cx, cy - r * 0.5f); lineTo(cx + r * 0.22f, cy + r * 0.45f); lineTo(cx + r * 0.4f, cy); lineTo(cx + r * 0.65f, cy) }
        c.drawPath(beat, stroke(k.accent, 1.8f))
        val tx = pad + 2 * r + 9f
        val nameSize = if (tall) 17f else 15f
        val nameY = if (tall) cy - 1f else cy + 5f
        text(c, "Jarvis", tx, nameY + 1f, nameSize, k.text, medium)
        if (w >= 200f) text(c, "live stats", tx + measure("Jarvis ", nameSize, medium) + 2f, nameY + 1f, nameSize - 3f, k.accent, medium)
        val (dot, label) = when {
            d == null -> k.line to "Not connected yet"
            d.up -> k.ok to ("Robot online" + (if (d.napping) " · napping" else " · awake") + (if (w >= 330f && d.uptimeS != null) " · up ${dur(d.uptimeS)}" else ""))
            else -> k.danger to "Robot not answering"
        }
        if (tall && w >= 190f) {
            c.drawCircle(tx + 3.5f, cy + 14.5f, 3.2f, fill(dot))
            text(c, label, tx + 11f, cy + 18f, 11.5f, k.muted)
        } else if (!tall && w >= 360f) {   // short widget: the state sits next to the name, there is no second line
            val sx = tx + measure("Jarvis ", nameSize, medium) + measure("live stats", nameSize - 3f, medium) + 22f
            c.drawCircle(sx + 3.5f, cy, 3.2f, fill(dot))
            text(c, label.substringBefore(" · up"), sx + 11f, cy + 4f, 11.5f, k.muted)
        }
        // refresh button (the tap area is the top right corner of the widget)
        val bx = w - pad - 13f; val by = pad + 13f
        c.drawCircle(bx, by, 13f, fill(alpha(k.text, 18)))
        c.drawCircle(bx, by, 13f, stroke(alpha(k.text, 40), 1f))
        refreshIcon(c, bx, by, 5.2f, k.text)
        val stamp = if (fetchedAt == 0L) "" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(fetchedAt))
        if (stamp.isNotEmpty() && w >= 170f) {
            val s = if (w >= 330f) (if (stale) "old data from $stamp" else "updated $stamp") else stamp
            text(c, s, bx - 19f, by + 4f, 11f, if (stale) k.danger else k.muted, sans, Paint.Align.RIGHT)
        }
        return pad + 2 * r
    }

    private fun refreshIcon(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -50f, 270f, false, stroke(color, 1.8f))
        val tri = Path().apply { moveTo(cx + r + 2.4f, cy - r * 0.05f); lineTo(cx + r - 3.4f, cy - r * 0.05f); lineTo(cx + r - 0.2f, cy - r * 0.95f); close() }
        c.drawPath(tri, fill(color))
    }

    // ------------------------------------------------------------------------------------------------------------------------- planning
    private enum class Sec { RINGS_A, RINGS_B, RINGS_AB, STRIP, KPI, GRAPHS, SERVICES }
    private class Item(val sec: Sec, val min: Float, val max: Float, val weight: Float, val row: Int = 0) { var h = min }

    private fun content(c: Canvas, body: RectF, d: WStats, k: K) {
        val bw = body.width(); val bh = body.height()
        val gap = 10f
        val ringCols = when { bw < 165f -> 2; bw < 250f -> 3; else -> 4 }
        val gCols = when { bw >= 600f -> 3; bw >= 330f -> 2; else -> 1 }
        val kCols = when { bw < 250f -> 2; bw < 400f -> 3; bw < 560f -> 4; else -> 5 }
        val ringMin = if (bw < 200f) 56f else if (bw / ringCols >= 88f) 104f else 94f
        val graphs = graphList(d, k)
        val maxGraphRows = (graphs.size + gCols - 1) / gCols

        // what to show, most important first; each is added only while it still fits (a wide widget shows robot and backend counters side by side)
        val wide = bw >= 460f
        val ringsH = if (wide) 98f else ringMin
        val wish = ArrayList<Item>()
        if (bh < ringsH && bw >= 200f) wish += Item(Sec.STRIP, 46f, 64f, 0.5f)
        else if (wide) wish += Item(Sec.RINGS_AB, ringsH, 150f, 1f) else wish += Item(Sec.RINGS_A, ringMin, 168f, 1f)
        wish += Item(Sec.KPI, 46f, 68f, 0.35f)
        wish += Item(Sec.GRAPHS, 88f, 230f, 2f, 0)
        if (wide) {
            if (maxGraphRows > 1) wish += Item(Sec.GRAPHS, 88f, 230f, 2f, 1)
            wish += Item(Sec.SERVICES, 26f, 34f, 0.1f)
            for (r in 2 until maxGraphRows) wish += Item(Sec.GRAPHS, 88f, 230f, 2f, r)
        } else {
            wish += Item(Sec.SERVICES, 26f, 34f, 0.1f)
            wish += Item(Sec.RINGS_B, ringMin, 168f, 1f)
            for (r in 1 until maxGraphRows) wish += Item(Sec.GRAPHS, 88f, 230f, 2f, r)
        }

        val plan = ArrayList<Item>()
        var used = 0f
        for (it in wish) {
            val need = used + it.min + if (plan.isEmpty()) 0f else gap
            if (need <= bh + 0.5f && !(it.sec == Sec.SERVICES && d.services.isEmpty())) { plan += it; used = need }
        }
        if (plan.isEmpty()) plan += Item(Sec.STRIP, min(46f, bh), 64f, 1f).also { it.h = bh }
        // share the spare height out by weight, never beyond a section's maximum; what is left over widens the gaps a little
        var spare = bh - used
        repeat(6) {
            val open = plan.filter { it.h < it.max - 0.1f }
            if (spare < 0.5f || open.isEmpty()) return@repeat
            val tw = open.sumOf { it.weight.toDouble() }.toFloat()
            var given = 0f
            for (it in open) { val add = min(it.max - it.h, spare * it.weight / tw); it.h += add; given += add }
            spare -= given
        }
        val gapFinal = if (plan.size > 1) gap + min(10f, max(0f, spare) / (plan.size - 1)) else gap
        var y = body.top + if (plan.size == 1) max(0f, spare) / 2f else 0f
        var graphIndex = 0
        for (it in plan) {
            val box = RectF(body.left, y, body.right, y + it.h)
            when (it.sec) {
                Sec.RINGS_A -> ringGroup(c, box, ringSpecs(d, false, ringCols), k, backend = false, label = bh >= 190f)
                Sec.RINGS_B -> ringGroup(c, box, ringSpecs(d, true, ringCols), k, backend = true, label = true)
                Sec.RINGS_AB -> {
                    val half = (box.width() - 12f) / 2f
                    ringGroup(c, RectF(box.left, box.top, box.left + half, box.bottom), ringSpecs(d, false, 4), k, backend = false, label = true)
                    ringGroup(c, RectF(box.right - half, box.top, box.right, box.bottom), ringSpecs(d, true, 4), k, backend = true, label = true)
                }
                Sec.STRIP -> strip(c, box, d, k, max(2, min(4, (bw / 105f).toInt())))
                Sec.KPI -> kpis(c, box, d, k, kCols)
                Sec.SERVICES -> services(c, box, d, k)
                Sec.GRAPHS -> {
                    val cw = (bw - gap * (gCols - 1)) / gCols
                    for (col in 0 until gCols) {
                        if (graphIndex >= graphs.size) break
                        val x = body.left + col * (cw + gap)
                        graph(c, RectF(x, box.top, x + cw, box.bottom), graphs[graphIndex++], k)
                    }
                }
            }
            y += it.h + gapFinal
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------- round counters
    private class RingSpec(val label: String, val v: Double?, val sub: String, val warn: Double = 70.0, val bad: Double = 85.0, val center: String? = null, val good: Boolean = false)

    private fun ringSpecs(d: WStats, backend: Boolean, cols: Int): List<RingSpec> {
        val ramPct = d.ramFreeKb?.let { 100 - 100 * it / 399 }
        val psPct = d.psramFreeKb?.let { 100 - 100 * it / 8192 }
        val wifi = d.rssi?.let { (2 * (it + 100)).coerceIn(0.0, 100.0) }
        val robot = d.up
        val all = if (!backend) listOf(
            RingSpec("CPU", if (robot) d.cpu else null, if (robot) "robot" else "asleep"),
            RingSpec("Memory", if (robot) ramPct else null, if (robot && d.ramFreeKb != null) "${num(d.ramFreeKb)} KB free" else "", 85.0, 95.0),
            RingSpec("PSRAM", if (robot) psPct else null, if (robot && d.psramFreeKb != null) "${num(d.psramFreeKb / 1024, 1)} MB free" else ""),
            RingSpec("Wi-Fi", if (robot) wifi else null, if (robot && d.rssi != null) "${num(d.rssi)} dBm" else "", 60.0, 80.0),
        ) else listOf(
            RingSpec("CPU", d.pcCpu, "this PC"),
            RingSpec("RAM", d.pcRam, "this PC", 80.0, 92.0),
            RingSpec("Disk", d.disk, "used", 85.0, 95.0),
            RingSpec("Voice server", if (d.voiceServer == true) 100.0 else if (d.voiceServer == false) 0.0 else null, if (d.voiceServer == true) "running" else "not running",
                center = if (d.voiceServer == true) "UP" else if (d.voiceServer == false) "OFF" else null, good = d.voiceServer == true),
        )
        return if (cols >= 4) all else if (!backend) listOf(all[0], all[1], all[3]).take(cols) else all.take(cols)
    }

    private fun ringGroup(c: Canvas, box: RectF, specs: List<RingSpec>, k: K, backend: Boolean, label: Boolean) {
        var top = box.top
        if (label && box.height() >= 98f && box.width() >= 150f) {
            text(c, if (backend) "BACKEND  ·  THIS PC" else "ROBOT", box.left + 2f, top + 8f, 10.5f, k.muted, medium, spacing = 0.12f)
            top += 20f
        }
        val cellW = box.width() / specs.size
        val showSub = box.bottom - top >= 88f
        val labelH = if (showSub) 30f else 17f
        val r = min(cellW / 2f - 6f, (box.bottom - top - labelH) / 2f).coerceIn(13f, 56f)
        val groupH = 2 * r + labelH
        val cy = top + (box.bottom - top - groupH) / 2f + r
        specs.forEachIndexed { i, s ->
            val cx = box.left + cellW * (i + 0.5f)
            ring(c, cx, cy, r, s, k, inverse = !backend && s.label == "Wi-Fi")   // the Wi-Fi ring reads "how good": amber and red when it is LOW, the others when the load is HIGH
            text(c, s.label, cx, cy + r + 14f, if (cellW < 70f) 11f else 12f, k.text, medium, Paint.Align.CENTER)
            if (showSub && s.sub.isNotEmpty()) text(c, s.sub, cx, cy + r + 27f, 10.5f, k.muted, sans, Paint.Align.CENTER)
        }
    }

    /** The short widget (one row): small rings with their name and detail beside them. */
    private fun strip(c: Canvas, box: RectF, d: WStats, k: K, cols: Int) {
        val robot = ringSpecs(d, false, 4)
        val pc = ringSpecs(d, true, 4)
        val specs = listOf(robot[0], robot[1], robot[3], pc[0]).take(cols)
        val cellW = box.width() / specs.size
        val r = (box.height() / 2f - 1f).coerceIn(14f, 28f)
        specs.forEachIndexed { i, s ->
            val cx = box.left + cellW * i + r + 2f
            val cy = box.centerY()
            ring(c, cx, cy, r, s, k, inverse = s.label == "Wi-Fi")
            val tx = cx + r + 8f
            text(c, if (i == 3) "PC CPU" else s.label, tx, cy - 1f, 12f, k.text, medium)
            if (s.sub.isNotEmpty() && cellW - (r * 2 + 12f) >= 52f) text(c, s.sub, tx, cy + 13f, 10.5f, k.muted)
        }
    }

    private fun ring(c: Canvas, cx: Float, cy: Float, r: Float, s: RingSpec, k: K, inverse: Boolean) {
        val sw = (r * 0.2f).coerceIn(4.5f, 11f)
        val box = RectF(cx - r + sw / 2, cy - r + sw / 2, cx + r - sw / 2, cy + r - sw / 2)
        c.drawArc(box, 0f, 360f, false, stroke(alpha(k.text, if (k.dark) 26 else 34), sw))
        val v = s.v
        if (v != null && v > 0.0) {
            val frac = (v.coerceIn(0.0, 100.0) / 100).toFloat()
            val level = if (inverse) 100 - v else v
            val base = if (s.good) k.ok else if (level > s.bad) k.danger else if (level > s.warn) k.warn else k.accent
            val bright = mix(base, if (k.dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), if (k.dark) 0.32f else 0.12f)
            val sweep = Paint(stroke(base, sw)).apply {
                shader = SweepGradient(cx, cy, intArrayOf(alpha(base, 150), bright, bright), floatArrayOf(0f, frac.coerceAtLeast(0.02f), 1f))
            }
            c.save(); c.rotate(-90f, cx, cy)
            c.drawArc(box, 0f, 360f * frac, false, sweep)
            c.restore()
        }
        val txt = s.center ?: if (v == null) "–" else v.toInt().toString()
        val size = (r * if (s.center != null) 0.5f else 0.66f).coerceIn(12f, 30f)
        text(c, txt, cx, cy + size * 0.36f, size, k.text, medium, Paint.Align.CENTER)
        if (s.center == null && v != null && r >= 30f) text(c, "%", cx, cy + size * 0.36f + 12f, 10.5f, k.muted, sans, Paint.Align.CENTER)
    }

    // ------------------------------------------------------------------------------------------------------------------------- the numbers row
    private fun kpis(c: Canvas, box: RectF, d: WStats, k: K, cols: Int) {
        val items = listOf(
            Triple("clock", "Uptime", d.uptimeS?.let { dur(it) } ?: "–"),
            Triple("ping", "Ping", d.ping?.let { "${num(it)} ms" } ?: "–"),
            Triple("reply", "Reply time", d.replyMs?.let { "${num(it / 1000, 1)} s" } ?: "–"),
            Triple("temp", "Chip", d.temp?.let { "${num(it)}°C" } ?: "–"),
            Triple("alert", "Fallbacks", d.fallbacks?.toString() ?: "–"),
        ).take(cols)
        val gap = 8f
        val tw = (box.width() - gap * (items.size - 1)) / items.size
        items.forEachIndexed { i, (icon, label, value) ->
            val x = box.left + i * (tw + gap)
            val r = RectF(x, box.top, x + tw, box.bottom)
            c.drawRoundRect(r, 14f, 14f, fill(alpha(k.field, if (k.dark) 255 else 255)))
            c.drawRoundRect(RectF(r.left + 0.5f, r.top + 0.5f, r.right - 0.5f, r.bottom - 0.5f), 13.5f, 13.5f, stroke(alpha(k.text, 16), 1f))
            val showIcon = tw >= 92f
            val ix = r.left + 10f + 12f
            if (showIcon) {
                c.drawCircle(ix, r.centerY(), 12f, fill(alpha(k.accent, 36)))
                icon(c, icon, ix, r.centerY(), 6.2f, k.accent)
            }
            val tx = if (showIcon) ix + 12f + 8f else r.left + 10f
            text(c, label, tx, r.centerY() - 3f, 10.5f, k.muted)
            text(c, value, tx, r.centerY() + 14f, if (tw < 100f) 15f else 17f, k.text, medium)
        }
    }

    private fun icon(c: Canvas, kind: String, cx: Float, cy: Float, r: Float, color: Int) {
        val s = stroke(color, 1.7f)
        when (kind) {
            "clock" -> { c.drawCircle(cx, cy, r, s); c.drawLine(cx, cy, cx, cy - r * 0.6f, s); c.drawLine(cx, cy, cx + r * 0.45f, cy + r * 0.2f, s) }
            "ping" -> {
                for (i in 1..3) c.drawArc(RectF(cx - r * i / 3f * 1.15f, cy - r * i / 3f * 1.15f + r * 0.5f, cx + r * i / 3f * 1.15f, cy + r * i / 3f * 1.15f + r * 0.5f), -135f, 90f, false, s)
                c.drawCircle(cx, cy + r * 0.55f, 1.2f, fill(color))
            }
            "reply" -> {
                c.drawRoundRect(RectF(cx - r, cy - r * 0.8f, cx + r, cy + r * 0.45f), 3f, 3f, s)
                c.drawPath(Path().apply { moveTo(cx - r * 0.45f, cy + r * 0.45f); lineTo(cx - r * 0.55f, cy + r * 0.95f); lineTo(cx + r * 0.05f, cy + r * 0.45f) }, s)
            }
            "temp" -> { c.drawLine(cx, cy - r, cx, cy + r * 0.2f, s); c.drawCircle(cx, cy + r * 0.62f, r * 0.42f, s) }
            else -> { c.drawPath(Path().apply { moveTo(cx, cy - r); lineTo(cx + r, cy + r * 0.8f); lineTo(cx - r, cy + r * 0.8f); close() }, s); c.drawLine(cx, cy - r * 0.25f, cx, cy + r * 0.25f, s); c.drawCircle(cx, cy + r * 0.55f, 0.9f, fill(color)) }
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------- services strip
    private fun shortName(n: String): String = when {
        n.contains("voice link", true) -> "Voice server"
        n.contains("photos", true) || n.contains("OTA", true) -> "Setup and photos"
        n.contains("Piper", true) -> "Piper voice"
        n.contains("Whisper", true) -> "Whisper ears"
        n.contains("panel", true) -> "Panel"
        else -> n.removePrefix("Server: ")
    }

    private fun services(c: Canvas, box: RectF, d: WStats, k: K) {
        var x = box.left + 2f
        val y = box.centerY() + 4f
        for (s in d.services) {
            if (!s.up && !s.expected) continue   // not used with the current voice: nothing to report
            val name = shortName(s.name)
            val wText = measure(name, 11.5f)
            if (x + 12f + wText > box.right) break
            c.drawCircle(x + 3.5f, y - 4f, 3.4f, fill(if (s.up) k.ok else k.danger))
            text(c, name, x + 11f, y, 11.5f, if (s.up) k.muted else k.danger)
            x += 11f + wText + 14f
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------- graphs
    private class Line(val name: String, val color: Int, val values: List<Double?>)
    private class G(val title: String, val unit: String, val lines: List<Line>, val decimals: Int = 0, val min: Double? = null, val max: Double? = null, val warn: Double? = null)

    private fun graphList(d: WStats, k: K): List<G> {
        fun scaled(v: List<Double?>, f: Double) = v.map { it?.let { x -> x * f } }
        return listOf(
            G("Wi-Fi traffic", "kB/s", listOf(Line("in", k.accent, scaled(d.rx, 0.001)), Line("out", k.warn, scaled(d.tx, 0.001))), 1, min = 0.0),
            G("Robot CPU", "%", listOf(Line("CPU", k.accent, d.cpuSeries)), 0, 0.0, 100.0, warn = 85.0),
            G("Wi-Fi signal", "dBm", listOf(Line("RSSI", k.ok, d.rssiSeries)), 0),
            G("Ping", "ms", listOf(Line("ping", k.pink, d.pingSeries)), 0, min = 0.0),
            G("Robot memory", "KB", listOf(Line("free", k.accent, d.ramSeries), Line("largest block", k.violet, d.hpSeries)), 0, min = 0.0),
            G("Backend load", "%", listOf(Line("CPU", k.accent, d.pcSeries), Line("RAM", k.warn, d.pcRamSeries)), 0, 0.0, 100.0),
            G("Reply time", "s", listOf(Line("total", k.violet, d.replySeconds)), 1, min = 0.0),
            G("Microphone", "dBFS", listOf(Line("peak", k.warn, d.micSeries)), 0, -90.0, 0.0),
            G("Chip temperature", "°C", listOf(Line("chip", k.danger, d.tempSeries)), 1),
        )
    }

    private fun graph(c: Canvas, box: RectF, g: G, k: K) {
        c.drawRoundRect(box, 16f, 16f, fill(k.field))
        c.drawRoundRect(RectF(box.left + 0.5f, box.top + 0.5f, box.right - 0.5f, box.bottom - 0.5f), 15.5f, 15.5f, stroke(alpha(k.text, 16), 1f))
        val inner = 10f
        val big = box.height() >= 112f
        val lastOf: (Line) -> Double? = { l -> l.values.lastOrNull { it != null } }
        // title, current value and legend
        text(c, g.title, box.left + inner, box.top + inner + 9f, 11.5f, k.muted, medium)
        val first = g.lines.first()
        val cur = lastOf(first)
        var plotTop = box.top + inner + 16f
        if (big) {
            val vs = if (cur == null) "–" else num(cur, g.decimals)
            text(c, vs, box.left + inner, box.top + inner + 34f, 24f, k.text, medium)
            text(c, g.unit, box.left + inner + measure(vs, 24f, medium) + 4f, box.top + inner + 34f, 11.5f, k.muted)
            if (g.lines.size > 1) {
                var lx = box.right - inner
                for (l in g.lines.reversed()) {
                    val wTxt = measure(l.name, 10.5f)
                    text(c, l.name, lx, box.top + inner + 9f, 10.5f, k.muted, sans, Paint.Align.RIGHT)
                    c.drawCircle(lx - wTxt - 6f, box.top + inner + 6f, 3f, fill(l.color))
                    lx -= wTxt + 17f
                }
            }
            plotTop = box.top + inner + 42f
        } else {
            val vs = (if (cur == null) "–" else num(cur, g.decimals)) + " " + g.unit
            text(c, vs, box.right - inner, box.top + inner + 9f, 12f, k.text, medium, Paint.Align.RIGHT)
        }
        val axis = box.height() >= 128f
        val plot = RectF(box.left + inner, plotTop, box.right - inner, box.bottom - inner - if (axis) 12f else 0f)
        if (plot.height() < 18f || plot.width() < 30f) return
        val all = g.lines.flatMap { it.values }.filterNotNull()
        if (all.isEmpty()) { text(c, "no data yet", plot.centerX(), plot.centerY() + 4f, 11.5f, k.muted, sans, Paint.Align.CENTER); return }
        var lo = g.min ?: all.min(); var hi = g.max ?: all.max()
        if (hi - lo < 1e-6) { hi = lo + 1; if (g.min == null) lo -= 1 }
        val padV = (hi - lo) * 0.1
        if (g.min == null) lo -= padV
        if (g.max == null) hi += padV
        fun yOf(v: Double) = plot.bottom - plot.height() * ((v - lo) / (hi - lo)).toFloat()
        // grid
        val grid = Paint(stroke(alpha(k.text, 22), 1f)).apply { pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 4f), 0f); strokeCap = Paint.Cap.BUTT }
        for (i in 0..2) { val gy = plot.top + plot.height() * i / 2f; c.drawLine(plot.left, gy, plot.right, gy, grid) }
        if (axis) {
            text(c, num(hi, if (hi - lo < 10) 1 else 0), plot.right, plot.top - 2f, 10f, alpha(k.muted, 200), sans, Paint.Align.RIGHT)
            text(c, num(lo, if (hi - lo < 10) 1 else 0), plot.right, plot.bottom - 2f, 10f, alpha(k.muted, 200), sans, Paint.Align.RIGHT)
            text(c, "1 h ago", plot.left, box.bottom - inner + 1f, 10f, k.muted)
            text(c, "now", plot.right, box.bottom - inner + 1f, 10f, k.muted, sans, Paint.Align.RIGHT)
        }
        g.warn?.let { wv -> if (wv in lo..hi) c.drawLine(plot.left, yOf(wv), plot.right, yOf(wv), Paint(stroke(alpha(k.danger, 140), 1f)).apply { pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f, 4f), 0f); strokeCap = Paint.Cap.BUTT }) }
        g.lines.forEachIndexed { li, line ->
            val n = line.values.size
            if (n == 0) return@forEachIndexed
            val pts = line.values.mapIndexed { i, v -> if (v == null) null else PointF(plot.left + plot.width() * i / max(1, n - 1), yOf(v)) }
            var seg = ArrayList<PointF>()
            val segs = ArrayList<List<PointF>>()
            for (pt in pts) { if (pt == null) { if (seg.isNotEmpty()) segs += seg; seg = ArrayList() } else seg += pt }
            if (seg.isNotEmpty()) segs += seg
            for (sg in segs) {
                if (sg.size == 1) { c.drawCircle(sg[0].x, sg[0].y, 2.6f, fill(line.color)); continue }
                val path = Path().apply {
                    moveTo(sg[0].x, sg[0].y)
                    for (i in 1 until sg.size) { val mx = (sg[i - 1].x + sg[i].x) / 2; val my = (sg[i - 1].y + sg[i].y) / 2; if (i == 1) lineTo(mx, my) else quadTo(sg[i - 1].x, sg[i - 1].y, mx, my) }
                    lineTo(sg.last().x, sg.last().y)
                }
                if (li == 0) {
                    val area = Path(path).apply { lineTo(sg.last().x, plot.bottom); lineTo(sg[0].x, plot.bottom); close() }
                    c.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, plot.top, 0f, plot.bottom, alpha(line.color, 92), alpha(line.color, 0), Shader.TileMode.CLAMP) })
                }
                c.drawPath(path, stroke(line.color, 2f))
            }
            val lastPt = pts.lastOrNull { it != null }
            if (lastPt != null) { c.drawCircle(lastPt.x, lastPt.y, 5.5f, fill(alpha(line.color, 70))); c.drawCircle(lastPt.x, lastPt.y, 2.8f, fill(line.color)); c.drawCircle(lastPt.x, lastPt.y, 2.8f, stroke(k.field, 1f)) }
        }
    }
}
