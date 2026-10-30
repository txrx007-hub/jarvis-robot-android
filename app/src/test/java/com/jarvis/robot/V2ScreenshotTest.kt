package com.jarvis.robot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/**
 * Jarvis v2's screens with the made-up sample data, drawn on the PC like [ScreenshotTest]:
 * `./gradlew recordPaparazziDebug --tests com.jarvis.robot.V2ScreenshotTest` writes them to app/src/test/snapshots/images.
 */
class V2ScreenshotTest {
    private val online = SampleData.online
    private val playing = online.copy(music = MusicStatus(enabled = true, tracks = 11, title = "JOE", artist = "Radio"))

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar", showSystemUi = false)

    /** One tab on the theme's background, the way V2App shows it (without the navigation bar). */
    @Composable
    private fun Tab(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize()) { ThemeBackdrop(); content() }

    @Test fun v2HomeDark() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2App(playing, MainActions()) } }
    @Test fun v2HomeLight() = paparazzi.snapshot { JarvisTheme(ThemeMode.LIGHT) { V2App(online, MainActions()) } }
    @Test fun v2HomePeach() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK, AppPalette.PEACH) { V2App(online, MainActions()) } }
    @Test fun v2Drive() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { Tab { V2Drive(online, MainActions()) } } }
    @Test fun v2Say() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { Tab { V2Say(online) } } }
    @Test fun v2Brain() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { Tab { V2Brain(playing, MainActions(), onOpenPanel = {}) } } }
    @Test fun v2Settings() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { Tab { V2Settings(online, MainActions()) } } }
    @Test fun v2Offline() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2App(online.copy(online = false, everConnected = true), MainActions()) } }
}
