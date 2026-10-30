package com.jarvis.robot

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import org.junit.Rule
import org.junit.Test

/**
 * Jarvis v3 (the Material 3 Expressive look) with the made-up sample data of [V2ScreenshotTest]:
 * `./gradlew recordPaparazziDebug --tests com.jarvis.robot.V3ScreenshotTest` writes them to app/src/test/snapshots/images.
 */
class V3ScreenshotTest {
    private val data = V2ScreenshotTest()

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar", showSystemUi = false, environment = detectEnvironment().copy(compileSdkVersion = 34))   // Compose 1.8 takes its Android 15 text path on an API 35 environment, which Paparazzi's layoutlib does not have

    private fun shot(content: @Composable () -> Unit) {
        V3.on = true
        try {
            paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { content() } }
        } finally {
            V3.on = BuildConfig.UI_V3
        }
    }

    @Test fun v3Home() = shot { V2App(data.playing, MainActions()) }
    @Test fun v3Brain() = shot { V2App(data.playing.copy(brain = data.brain), MainActions(), startTab = V2Tab.BRAIN) }
    @Test fun v3Settings() = shot { V2App(data.online, MainActions(), startTab = V2Tab.SETTINGS) }
    @Test fun v3PageGreeter() = shot { V2PageHost(V2Page.GREETER, data.online, data.panelActions, {}, {}) {} }
    @Test fun v3PageVolume() = shot { V2PageHost(V2Page.VOLUME, data.online, data.panelActions, {}, {}) {} }
    @Test fun v3PageMood() = shot { V2PageHost(V2Page.MOOD, data.online, data.panelActions, {}, {}) {} }
    @Test fun v3PageHealth() = shot { V2PageHost(V2Page.HEALTH, data.online, data.panelActions, {}, {}) {} }
    @Test fun v3PageFaces() = shot { V2PageHost(V2Page.FACES, data.online, data.panelActions, {}, {}) {} }
}
