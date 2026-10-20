package com.jarvis.robot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import androidx.compose.runtime.CompositionLocalProvider
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import org.junit.Rule
import org.junit.Test

/** The Galaxy Z Fold7 in both shapes: the cover screen (single column) and the unfolded inner screen (menu + content). */
/** A made-up 640x480 "photo" so the camera window can be drawn without a robot. */
fun samplePhoto(): androidx.compose.ui.graphics.ImageBitmap {
    val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint()
    p.color = 0xFF3A4A44.toInt(); c.drawRect(0f, 0f, 640f, 480f, p)
    p.color = 0xFF7FA08A.toInt(); c.drawCircle(320f, 220f, 120f, p)
    p.color = 0xFFCDB89A.toInt(); c.drawRect(0f, 380f, 640f, 480f, p)
    return bmp.asImageBitmap()
}

val withPhoto = SampleData.online.copy(photo = samplePhoto(), photoTime = "20:14:05")

class FoldScreenshotTest {
    private val online = SampleData.online

    // Cover: 1080x2520 px at 420 dpi = 411 x 960 dp.
    private val cover = DeviceConfig.PIXEL_5.copy(screenWidth = 1080, screenHeight = 2520, xdpi = 420, ydpi = 420, density = Density.create(420))

    @get:Rule val coverRule = Paparazzi(deviceConfig = cover, theme = "android:Theme.Material.NoActionBar", showSystemUi = false)

    @Test fun coverSplash() = coverRule.snapshot { JarvisTheme(ThemeMode.DARK) { SplashScreen() } }

    @Test fun coverCamera() = coverRule.snapshot {
        JarvisTheme(ThemeMode.DARK, AppPalette.BLUEBERRY) { MainContent(withPhoto.copy(palette = AppPalette.BLUEBERRY), MainActions(), initialShowCamera = true) }
    }

    @Test fun coverClassicDark() = coverRule.snapshot { JarvisTheme(ThemeMode.DARK) { MainContent(online.copy(palette = AppPalette.CLASSIC), MainActions()) } }

    @Test fun coverBlueberryDark() = coverRule.snapshot {
        JarvisTheme(ThemeMode.DARK, AppPalette.BLUEBERRY) { MainContent(online.copy(palette = AppPalette.BLUEBERRY), MainActions()) }
    }

    @Test fun coverSucculentLight() = coverRule.snapshot {
        JarvisTheme(ThemeMode.LIGHT, AppPalette.SUCCULENT) { MainContent(online.copy(palette = AppPalette.SUCCULENT), MainActions()) }
    }
}

class FoldInnerScreenshotTest {
    private val online = SampleData.online
    private val inner = DeviceConfig.PIXEL_5.copy(screenWidth = 2184, screenHeight = 1968, xdpi = 320, ydpi = 320, density = Density.XHIGH,
        orientation = ScreenOrientation.LANDSCAPE)

    @get:Rule val rule = Paparazzi(deviceConfig = inner, theme = "android:Theme.Material.NoActionBar", showSystemUi = false)

    private fun shot(section: String, mode: ThemeMode = ThemeMode.DARK, palette: AppPalette = AppPalette.BLUEBERRY) = rule.snapshot {
        CompositionLocalProvider(LocalLiveWeb provides false) {
            JarvisTheme(mode, palette) { MainContent(online.copy(palette = palette, themeMode = mode), MainActions(), initialSection = section) }
        }
    }

    @Test fun innerOverview() = shot(SECTION_OVERVIEW)
    @Test fun innerOverviewSucculentLight() = shot(SECTION_OVERVIEW, ThemeMode.LIGHT, AppPalette.SUCCULENT)
    @Test fun innerCamera() = rule.snapshot {
        JarvisTheme(ThemeMode.DARK, AppPalette.SUCCULENT) { MainContent(withPhoto.copy(palette = AppPalette.SUCCULENT), MainActions(), initialSection = "Camera") }
    }
    @Test fun innerRemote() = shot("Remote control")
    @Test fun innerBackend() = shot("Backend")
    @Test fun innerSplash() = rule.snapshot { JarvisTheme(ThemeMode.DARK, AppPalette.BLUEBERRY) { SplashScreen() } }
    @Test fun innerCommands() = shot("Voice commands", palette = AppPalette.CLASSIC)
    @Test fun innerCommandsHome() = rule.snapshot {
        JarvisTheme(ThemeMode.DARK, AppPalette.CLASSIC) { MainContent(online.copy(haOk = true), MainActions(), initialSection = "Voice commands") }
    }
    @Test fun innerMotors() = shot("Motors")
    @Test fun innerAppearance() = shot("Appearance")
}

class FoldInnerPortraitScreenshotTest {
    private val online = SampleData.online
    private val portrait = DeviceConfig.PIXEL_5.copy(screenWidth = 1968, screenHeight = 2184, xdpi = 320, ydpi = 320, density = Density.XHIGH)

    @get:Rule val rule = Paparazzi(deviceConfig = portrait, theme = "android:Theme.Material.NoActionBar", showSystemUi = false)

    @Test fun innerPortraitBackend() = rule.snapshot {
        CompositionLocalProvider(LocalLiveWeb provides false) {
            JarvisTheme(ThemeMode.DARK, AppPalette.SUCCULENT) { MainContent(online.copy(palette = AppPalette.SUCCULENT), MainActions(), initialSection = "Backend") }
        }
    }

    @Test fun innerPortraitOverview() = rule.snapshot {
        JarvisTheme(ThemeMode.DARK, AppPalette.SUCCULENT) { MainContent(online.copy(palette = AppPalette.SUCCULENT), MainActions()) }
    }

    @Test fun innerPortraitMotors() = rule.snapshot {
        JarvisTheme(ThemeMode.DARK, AppPalette.BLUEBERRY) { MainContent(online.copy(palette = AppPalette.BLUEBERRY), MainActions(), initialSection = "Motors") }
    }
}
