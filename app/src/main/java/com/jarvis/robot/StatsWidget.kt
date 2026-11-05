package com.jarvis.robot

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.util.SizeF
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The Jarvis live stats widget for the home screen: the robot and the backend as round counters, small graphs and a few numbers, read from the backend panel's /api/stats
 * (the same data as the panel's live stats window). It is drawn as one picture with Canvas, once per size the launcher offers (folded and unfolded screens of a foldable
 * have different sizes), so the launcher shows the one that fits the screen that is open. The picture follows the app's colour theme and the phone's day or night mode.
 *
 * A widget cannot be live: it is refreshed every 15 minutes, when it is added, when the phone's size changes, and when its refresh button is pressed. The last numbers are kept,
 * so away from home the widget still shows them, marked with the time they are from. Tapping the picture opens the app.
 */
class StatsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        schedule(context)
        draw(context, manager, ids)   // the last numbers at once ...
        refreshAsync(context, ids)    // ... and fresh ones a moment later
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle) {
        draw(context, manager, intArrayOf(id))   // folded or unfolded, resized: draw again from what is stored, no network
    }

    override fun onEnabled(context: Context) = schedule(context)

    override fun onDisabled(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(refreshIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            refreshAsync(context, manager.getAppWidgetIds(ComponentName(context, StatsWidget::class.java)))
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.jarvis.robot.STATS_WIDGET_REFRESH"
        private const val PREFS = "jarvis"
        private const val EVERY_MS = 15 * 60 * 1000L

        private fun refreshIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(context, 1, Intent(context, StatsWidget::class.java).setAction(ACTION_REFRESH), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        private fun schedule(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + EVERY_MS, EVERY_MS, refreshIntent(context))
        }

        /** Asks the backend for the numbers on a thread of its own, keeps them, and draws every widget again. */
        fun refreshAsync(context: Context, ids: IntArray) {
            if (ids.isEmpty()) return
            val app = context.applicationContext
            Thread {
                runCatching { fetch(app) }
                runCatching { draw(app, AppWidgetManager.getInstance(app), ids) }
            }.start()
        }

        private fun fetch(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val host = prefs.getString("host", "").orEmpty()
            if (host.isBlank()) return
            runCatching { NetworkUtil.bindToWifi(context) }
            val api = RobotApi(host)
            var panel = runCatching { panelUrlOf(api.status()) }.getOrDefault("")   // the robot may be napping: then the address it gave last time is used
            if (panel.isEmpty()) panel = prefs.getString("widget_panel", "").orEmpty()
            if (panel.isEmpty()) return
            val now = System.currentTimeMillis() / 1000
            val json = api.panelJson(panel, "api/stats?from=${now - 3600}&to=$now&points=40")
            prefs.edit().putString("widget_panel", panel).putString("widget_stats", json.toString()).putLong("widget_time", System.currentTimeMillis()).apply()
        }

        /** Builds one picture per size the launcher offers and hands them over as the widget. */
        fun draw(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val data = runCatching { WStats.parse(JSONObject(prefs.getString("widget_stats", "") ?: "")) }.getOrNull()
            val fetchedAt = prefs.getLong("widget_time", 0L)
            val hasHost = prefs.getString("host", "").orEmpty().isNotBlank()
            val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val palette = paletteFor(runCatching { AppPalette.valueOf(prefs.getString("palette", "CLASSIC") ?: "CLASSIC") }.getOrDefault(AppPalette.CLASSIC), dark)
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
            for (id in ids) {
                val opts = manager.getAppWidgetOptions(id)
                val sizes: List<SizeF> = if (Build.VERSION.SDK_INT >= 31) {
                    (opts.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES) ?: arrayListOf()).distinct().take(4)
                } else emptyList()
                fun view(w: Float, h: Float): RemoteViews {
                    val rv = RemoteViews(context.packageName, R.layout.widget_stats)
                    rv.setImageViewBitmap(R.id.stats_img, StatsRender.render(context, w, h, data, fetchedAt, hasHost, palette))
                    rv.setOnClickPendingIntent(R.id.stats_img, open)
                    rv.setOnClickPendingIntent(R.id.stats_refresh, refreshIntent(context))
                    return rv
                }
                val views = if (Build.VERSION.SDK_INT >= 31 && sizes.isNotEmpty()) {
                    RemoteViews(sizes.associateWith { view(it.width, it.height) })
                } else {
                    val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).toFloat()
                    val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 130).toFloat()
                    view(w, h)
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}

/** The numbers of /api/stats the widget uses. */
data class WStats(
    val up: Boolean, val napping: Boolean, val cpu: Double?, val ramFreeKb: Double?, val psramFreeKb: Double?, val rssi: Double?, val ping: Double?, val temp: Double?, val uptimeS: Double?,
    val pcCpu: Double?, val pcRam: Double?, val disk: Double?, val voiceServer: Boolean?, val replyMs: Double?, val fallbacks: Int?,
    val rx: List<Double?>, val cpuSeries: List<Double?>, val rssiSeries: List<Double?>, val pingSeries: List<Double?>, val pcSeries: List<Double?>,
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
            return WStats(
                up = row.optInt("up") == 1, napping = row.optInt("nap") == 1, cpu = row.d("cpu"), ramFreeKb = row.d("ram"), psramFreeKb = row.d("ps"), rssi = row.d("rs"), ping = row.d("pg"),
                temp = row.d("tm"), uptimeS = row.d("ut"), pcCpu = row.d("pc"), pcRam = row.d("pr"), disk = row.d("pd"), voiceServer = if (row.has("vs")) row.optInt("vs") == 1 else null,
                replyMs = now.d("reply_p50"), fallbacks = if (now.has("fallbacks") && !now.isNull("fallbacks")) now.optInt("fallbacks") else null,
                rx = s.list("rx"), cpuSeries = s.list("cpu"), rssiSeries = s.list("rs"), pingSeries = s.list("pg"), pcSeries = s.list("pc"),
            )
        }
    }
}

/** Draws the widget as a picture of w x h dp (as sharp as the screen, but never more than about 0.6 megapixels: launchers refuse large pictures). */
object StatsRender {
    private val sans = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun render(context: Context, wDp: Float, hDp: Float, data: WStats?, fetchedAt: Long, hasHost: Boolean, p: Palette): Bitmap {
        val density = context.resources.displayMetrics.density
        var scale = density
        val maxPx = 600_000f
        if (wDp * scale * hDp * scale > maxPx) scale = sqrt(maxPx / (wDp * hDp))
        val bmp = Bitmap.createBitmap(max(1, (wDp * scale).toInt()), max(1, (hDp * scale).toInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        paint(c, wDp, hDp, data, fetchedAt, hasHost, p)
        return bmp
    }

    /** Drawing in dp: the canvas is scaled before. Also used by the screenshot test. */
    fun paint(c: Canvas, w: Float, h: Float, data: WStats?, fetchedAt: Long, hasHost: Boolean, p: Palette) {
        val card = p.card.toArgb(); val line = p.border.toArgb(); val text = p.text.toArgb(); val muted = p.muted.toArgb(); val accent = p.accent.toArgb()
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card }
        c.drawRoundRect(RectF(0f, 0f, w, h), 26f, 26f, bg)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; color = line }
        c.drawRoundRect(RectF(0.6f, 0.6f, w - 0.6f, h - 0.6f), 25.4f, 25.4f, stroke)
        fun tp(size: Float, color: Int, face: Typeface = sans, align: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face; textAlign = align }

        val pad = 12f
        // header: status dot, name, time of the numbers, refresh button
        val stale = fetchedAt == 0L || System.currentTimeMillis() - fetchedAt > 35 * 60 * 1000
        val dot = when { data == null -> line; data.up -> p.success.toArgb(); data.napping -> p.instant.toArgb(); else -> p.danger.toArgb() }
        c.drawCircle(pad + 5f, pad + 9f, 4.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dot })
        val title = tp(15f, text, medium)
        c.drawText("Jarvis", pad + 16f, pad + 14f, title)
        if (w >= 190f) c.drawText("live stats", pad + 16f + title.measureText("Jarvis ") + 2f, pad + 14f, tp(13f, accent, medium))
        val stamp = if (fetchedAt == 0L) "" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(fetchedAt))
        val stampP = tp(11f, if (stale) p.danger.toArgb() else muted, sans, Paint.Align.RIGHT)
        if (w >= 150f && stamp.isNotEmpty()) c.drawText(stamp, w - pad - 28f, pad + 13.5f, stampP)
        drawRefresh(c, w - pad - 10f, pad + 9f, 8f, muted)

        val top = pad + 30f
        val bottom = h - pad
        if (data == null) {
            val msg = if (!hasHost) listOf("Open Jarvis once", "to connect to the robot") else listOf("No numbers yet", "Tap the arrows to load them")
            msg.forEachIndexed { i, s -> c.drawText(s, w / 2, (top + bottom) / 2 + i * 18f - 4f, tp(14f, if (i == 0) text else muted, if (i == 0) medium else sans, Paint.Align.CENTER)) }
            return
        }
        val wide = w >= 300f
        // rings
        data class R(val label: String, val v: Double?, val sub: String, val warn: Double = 70.0, val bad: Double = 85.0)
        val rings = listOf(
            R("CPU", if (data.up) data.cpu else null, if (data.up) "robot" else "asleep"),
            R("Memory", data.ramFreeKb?.let { 100 - 100 * it / 399 }.takeIf { data.up }, data.ramFreeKb?.let { "${it.toInt()} KB free" }.orEmpty().takeIf { data.up } ?: "", 85.0, 95.0),
            R("Wi-Fi", data.rssi?.let { (2 * (it + 100)).coerceIn(0.0, 100.0) }.takeIf { data.up }, data.rssi?.let { "${it.toInt()} dBm" }.orEmpty().takeIf { data.up } ?: ""),
            R("PC", data.pcCpu, "backend CPU"),
            R("PSRAM", data.psramFreeKb?.let { 100 - 100 * it / 8192 }.takeIf { data.up }, data.psramFreeKb?.let { "%.1f MB free".format(it / 1024) }.orEmpty().takeIf { data.up } ?: ""),
            R("RAM", data.pcRam, "backend"),
        )
        val bodyW = w - 2 * pad
        val n = when { bodyW < 150f -> 2; bodyW < 230f -> 3; bodyW < 330f -> 4; else -> min(6, 4 + ((bodyW - 330f) / 80f).toInt()) }.coerceAtMost(rings.size)
        val colW = bodyW / n
        val availH = bottom - top
        val ringR = min(colW / 2f - 6f, if (availH > 150f) 34f else (availH - 18f) / 2f).coerceIn(14f, 34f)
        val showSub = ringR >= 26f
        val ringsH = 2 * ringR + 8f + 12f + if (showSub) 12f else 0f
        for (i in 0 until n) {
            val r = rings[i]
            val cx = pad + colW * (i + 0.5f); val cy = top + ringR + 2f
            drawRing(c, cx, cy, ringR, r.v, r.warn, r.bad, p, tp(if (ringR >= 26f) 15f else 12f, text, medium, Paint.Align.CENTER))
            c.drawText(r.label, cx, cy + ringR + 14f, tp(11f, text, sans, Paint.Align.CENTER))
            if (showSub && r.sub.isNotEmpty()) c.drawText(r.sub, cx, cy + ringR + 26f, tp(10f, muted, sans, Paint.Align.CENTER))
        }
        var y = top + ringsH + 6f
        // chips
        if (bottom - y >= 40f) {
            val chips = listOf("Uptime" to (data.uptimeS?.let { dur(it) } ?: "–"), "Ping" to (data.ping?.let { "${it.toInt()} ms" } ?: "–"),
                "Reply" to (data.replyMs?.let { "%.1f s".format(it / 1000) } ?: "–"), "Chip" to (data.temp?.let { "${it.toInt()}°C" } ?: "–"),
                "Fallbacks" to (data.fallbacks?.toString() ?: "–"))
            val cn = if (bodyW < 230f) 2 else if (bodyW < 330f) 3 else min(5, 4 + ((bodyW - 330f) / 120f).toInt())
            val gap = 6f; val cw = (bodyW - gap * (cn - 1)) / cn
            for (i in 0 until cn) {
                val x = pad + i * (cw + gap)
                c.drawRoundRect(RectF(x, y, x + cw, y + 36f), 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (p.field.toArgb()) })
                c.drawText(chips[i].first, x + 8f, y + 14f, tp(10f, muted))
                c.drawText(chips[i].second, x + 8f, y + 29f, tp(14f, text, medium))
            }
            y += 36f + 8f
        }
        // graphs
        val sparks = listOf(Triple("Wi-Fi traffic", data.rx.map { it?.let { v -> v / 1000 } }, "kB/s"), Triple("Robot CPU", data.cpuSeries, "%"), Triple("Wi-Fi signal", data.rssiSeries, "dBm"),
            Triple("Ping", data.pingSeries, "ms"), Triple("Backend CPU", data.pcSeries, "%"))
        val sh = 58f; val cols = if (wide) 2 else 1
        var k = 0
        while (bottom - y >= sh && k < sparks.size) {
            val gap = 8f; val sw = (bodyW - gap * (cols - 1)) / cols
            for (col in 0 until cols) {
                if (k >= sparks.size) break
                val x = pad + col * (sw + gap)
                val (name, series, unit) = sparks[k++]
                drawSpark(c, RectF(x, y, x + sw, y + sh), name, series, unit, p, accent, tp(11f, muted), tp(13f, text, medium, Paint.Align.RIGHT))
            }
            y += sh + 6f
        }
    }

    private fun dur(s: Double): String { val m = (s / 60).toInt(); val d = m / 1440; val h = m % 1440 / 60; return if (d > 0) "${d}d ${h}h" else if (h > 0) "${h}h ${m % 60}m" else "${m}m" }

    private fun drawRing(c: Canvas, cx: Float, cy: Float, r: Float, v: Double?, warn: Double, bad: Double, p: Palette, num: Paint) {
        val sw = max(4f, r * 0.2f)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = sw; color = p.border.toArgb() }
        val box = RectF(cx - r + sw / 2, cy - r + sw / 2, cx + r - sw / 2, cy + r - sw / 2)
        c.drawArc(box, 0f, 360f, false, track)
        if (v != null) {
            val col = if (v > bad) p.danger.toArgb() else if (v > warn) p.instant.toArgb() else p.accent.toArgb()
            c.drawArc(box, -90f, (360 * v.coerceIn(0.0, 100.0) / 100).toFloat(), false, Paint(track).apply { color = col; strokeCap = Paint.Cap.ROUND })
        }
        c.drawText(if (v == null) "–" else v.toInt().toString(), cx, cy + num.textSize * 0.36f, num)
    }

    private fun drawSpark(c: Canvas, box: RectF, name: String, series: List<Double?>, unit: String, p: Palette, accent: Int, label: Paint, value: Paint) {
        c.drawRoundRect(box, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.field.toArgb() })
        c.drawText(name, box.left + 8f, box.top + 14f, label)
        val vals = series.filterNotNull()
        val last = vals.lastOrNull()
        c.drawText(if (last == null) "–" else (if (kotlin.math.abs(last) >= 100 || unit == "ms" || unit == "%" || unit == "dBm") last.toInt().toString() else "%.1f".format(last)) + " " + unit, box.right - 8f, box.top + 15f, value)
        if (vals.size < 2) return
        val lo = vals.min(); val hi = vals.max(); val span = if (hi - lo < 1e-6) 1.0 else hi - lo
        val gx = box.left + 8f; val gw = box.width() - 16f; val gy = box.top + 24f; val gh = box.height() - 32f
        val path = Path(); var started = false; var lastX = gx; var firstX = gx
        series.forEachIndexed { i, v ->
            if (v == null) { started = false; return@forEachIndexed }
            val x = gx + gw * i / (series.size - 1).coerceAtLeast(1); val yy = gy + gh * (1 - ((v - lo) / span)).toFloat()
            if (!started) { path.moveTo(x, yy); started = true; if (firstX == gx) firstX = x } else path.lineTo(x, yy)
            lastX = x
        }
        val fill = Path(path).apply { lineTo(lastX, gy + gh); lineTo(firstX, gy + gh); close() }
        c.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; alpha = 40 })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = accent; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND })
    }

    private fun drawRefresh(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; this.color = color; strokeCap = Paint.Cap.ROUND }
        c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -60f, 280f, false, pt)
        val tri = Path().apply { moveTo(cx + r + 1f, cy - r * 0.15f); lineTo(cx + r - 6f, cy - r * 0.15f); lineTo(cx + r - 1f, cy - r * 0.95f); close() }
        c.drawPath(tri, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color })
    }
}
