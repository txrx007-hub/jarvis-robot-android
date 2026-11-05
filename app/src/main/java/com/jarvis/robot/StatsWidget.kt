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
                // A launcher refuses an update whose pictures take too much memory (about 1.5 x the screen's pixels x 4 bytes in all, every size counts): keep to 1.1 x the screen's pixels in all.
                val screen = context.resources.displayMetrics
                val budgetPx = screen.widthPixels.toFloat() * screen.heightPixels * 1.1f / max(1, sizes.size)
                fun view(w: Float, h: Float): RemoteViews {
                    val rv = RemoteViews(context.packageName, R.layout.widget_stats)
                    rv.setImageViewBitmap(R.id.stats_img, StatsRender.render(context, w, h, data, fetchedAt, hasHost, palette, budgetPx))
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

