package com.jarvis.robot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Rule
import org.junit.Test

/** The Galaxy Z Fold7 in both shapes: the cover screen (single column) and the unfolded inner screen (menu + content). */
class FoldScreenshotTest {
    private val online = SampleData.online

    // Cover: 1080x2520 px at 420 dpi = 411 x 960 dp.
    private val cover = DeviceConfig.PIXEL_5.copy(screenWidth = 1080, screenHeight = 2520, xdpi = 420, ydpi = 420, density = Density.create(420))

    @get:Rule val coverRule = Paparazzi(deviceConfig = cover, theme = "android:Theme.Material.NoActionBar", showSystemUi = false)

    @Test fun coverSplash() = coverRule.snapshot { JarvisTheme(ThemeMode.DARK) { SplashScreen() } }

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
    @Test fun innerRemote() = shot("Remote control")
    @Test fun innerBackend() = shot("Backend")
    @Test fun innerSplash() = rule.snapshot { JarvisTheme(ThemeMode.DARK, AppPalette.BLUEBERRY) { SplashScreen() } }
    @Test fun innerCommands() = shot("Voice commands", palette = AppPalette.CLASSIC)
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
