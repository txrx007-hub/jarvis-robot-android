package com.jarvis.robot

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import com.android.resources.Density
import org.junit.Rule
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** The home screen widget at the sizes of a Galaxy Z Fold7: folded (cover screen) and unfolded (inner screen), dark and light. */
class StatsWidgetTest {
    private val device = DeviceConfig.PIXEL_5.copy(screenWidth = 1260, screenHeight = 3400, xdpi = 420, ydpi = 420, density = Density.create(420))

    @get:Rule val rule = Paparazzi(environment = detectEnvironment().copy(compileSdkVersion = 34), deviceConfig = device, theme = "android:Theme.Material.NoActionBar", showSystemUi = false)

    private fun wave(base: Double, amp: Double, n: Int = 40, phase: Double = 0.0) = List<Double?>(n) { i -> base + amp * sin(2 * PI * i / 13 + phase) + (i % 7) * amp / 9 }

    private val data = WStats(
        up = true, napping = false, cpu = 27.0, ramFreeKb = 80.0, psramFreeKb = 3046.0, rssi = -61.0, ping = 8.0, temp = 48.0, uptimeS = 10205.0,
        pcCpu = 15.0, pcRam = 71.0, disk = 50.0, voiceServer = true, replyMs = 4359.0, fallbacks = 19,
        rx = wave(6500.0, 3000.0), cpuSeries = wave(30.0, 12.0, phase = 1.0), rssiSeries = wave(-62.0, 5.0, phase = 2.0), pingSeries = wave(14.0, 6.0, phase = 3.0), pcSeries = wave(14.0, 8.0),
    )

    private fun sized(w: Float, h: Float, p: Palette): Bitmap = StatsRender.render(rule.context, w, h, data, System.currentTimeMillis(), true, p)

    @Composable
    private fun One(w: Float, h: Float, p: Palette) {
        Image(sized(w, h, p).asImageBitmap(), contentDescription = null, modifier = Modifier.size(w.dp, h.dp))
        Spacer(Modifier.height(10.dp))
    }

    @Test fun widgetDark() = rule.snapshot {
        Column(Modifier.background(Color(0xFF2A2F33)).padding(12.dp)) {
            val p = paletteFor(AppPalette.CLASSIC, true)
            One(150f, 130f, p); One(260f, 120f, p); One(260f, 190f, p); One(400f, 200f, p); One(400f, 330f, p); One(400f, 120f, p)
        }
    }

    @Test fun widgetLight() = rule.snapshot {
        Column(Modifier.background(Color(0xFFB9C4C9)).padding(12.dp)) {
            val p = paletteFor(AppPalette.PEACH, false)
            One(260f, 120f, p); One(400f, 200f, p); One(150f, 130f, p)
        }
    }

    @Test fun widgetEmpty() = rule.snapshot {
        Column(Modifier.background(Color(0xFF2A2F33)).padding(12.dp)) {
            val p = paletteFor(AppPalette.BLUEBERRY, true)
            Image(StatsRender.render(rule.context, 260f, 120f, null, 0L, false, p).asImageBitmap(), null, Modifier.size(260.dp, 120.dp))
            Spacer(Modifier.height(10.dp))
            Image(StatsRender.render(rule.context, 260f, 120f, null, 0L, true, p).asImageBitmap(), null, Modifier.size(260.dp, 120.dp))
        }
    }
}
