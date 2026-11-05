package com.jarvis.robot

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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

/**
 * The home screen widget at the sizes of a Galaxy Z Fold7, largest first: the unfolded inner screen (about 520 x 470 dp of home screen, so widgets up to roughly 500 x 440 dp)
 * and the folded cover screen (about 411 dp wide), dark and light.
 */
class StatsWidgetTest {
    private val inner = DeviceConfig.PIXEL_5.copy(screenWidth = 2184, screenHeight = 3600, xdpi = 420, ydpi = 420, density = Density.create(420))

    @get:Rule val rule = Paparazzi(environment = detectEnvironment().copy(compileSdkVersion = 34), deviceConfig = inner, theme = "android:Theme.Material.NoActionBar", showSystemUi = false)

    private fun wave(base: Double, amp: Double, n: Int = 40, phase: Double = 0.0) = List<Double?>(n) { i -> base + amp * sin(2 * PI * i / 13 + phase) + (i % 7) * amp / 9 }

    private val data = WStats(
        up = true, napping = false, cpu = 27.0, ramFreeKb = 80.0, psramFreeKb = 3046.0, rssi = -61.0, ping = 8.0, temp = 48.0, uptimeS = 10205.0,
        pcCpu = 15.0, pcRam = 71.0, disk = 50.0, voiceServer = true, replyMs = 4359.0, fallbacks = 19, sessions = 13,
        rx = wave(6500.0, 3000.0), tx = wave(1800.0, 700.0, phase = 1.0), cpuSeries = wave(30.0, 12.0, phase = 1.0), ramSeries = wave(84.0, 4.0), hpSeries = wave(37.0, 2.0, phase = 2.0),
        rssiSeries = wave(-62.0, 5.0, phase = 2.0), pingSeries = wave(14.0, 6.0, phase = 3.0), pcSeries = wave(14.0, 8.0), pcRamSeries = wave(70.0, 3.0, phase = 1.5),
        tempSeries = wave(47.0, 2.0, phase = 0.5), micSeries = wave(-30.0, 12.0, phase = 2.5), replySeconds = listOf(3.1, 4.4, 2.8, 5.9, 3.7, 4.1, 2.2, 6.3, 3.9, 4.0),
        services = listOf(WService("Server: voice link (websocket)", true, true), WService("Control panel", true, true), WService("Piper voice", true, false), WService("Local Whisper ears (fallback)", false, false)),
    )

    private fun bmp(w: Float, h: Float, p: Palette): Bitmap = StatsRender.render(rule.context, w, h, data, System.currentTimeMillis(), true, p, 4_000_000f)

    @Composable
    private fun One(w: Float, h: Float, p: Palette) {
        Image(bmp(w, h, p).asImageBitmap(), contentDescription = null, modifier = Modifier.size(w.dp, h.dp))
        Spacer(Modifier.height(12.dp))
    }

    @Test fun widgetUnfoldedDark() = rule.snapshot {
        Column(Modifier.background(Color(0xFF23282B)).padding(12.dp)) {
            val p = paletteFor(AppPalette.CLASSIC, true)
            One(500f, 440f, p); One(500f, 230f, p); One(500f, 130f, p)
        }
    }

    @Test fun widgetFoldedDark() = rule.snapshot {
        Column(Modifier.background(Color(0xFF23282B)).padding(12.dp)) {
            val p = paletteFor(AppPalette.CLASSIC, true)
            One(385f, 560f, p); One(385f, 300f, p); One(385f, 170f, p)
            Row { Image(bmp(180f, 180f, p).asImageBitmap(), null, Modifier.size(180.dp)); Spacer(Modifier.width(12.dp)); Image(bmp(180f, 110f, p).asImageBitmap(), null, Modifier.size(180.dp, 110.dp)) }
        }
    }

    @Test fun widgetLight() = rule.snapshot {
        Column(Modifier.background(Color(0xFFB9C4C9)).padding(12.dp)) {
            val p = paletteFor(AppPalette.PEACH, false)
            One(500f, 440f, p); One(385f, 300f, p)
        }
    }

    @Test fun widgetBlueberryDark() = rule.snapshot {
        Column(Modifier.background(Color(0xFF23282B)).padding(12.dp)) {
            val p = paletteFor(AppPalette.BLUEBERRY, true)
            One(500f, 330f, p)
            Image(StatsRender.render(rule.context, 260f, 120f, null, 0L, false, p, 4_000_000f).asImageBitmap(), null, Modifier.size(260.dp, 120.dp))
        }
    }
}
