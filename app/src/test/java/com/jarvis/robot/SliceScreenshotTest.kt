package com.jarvis.robot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class SliceScreenshotTest {
    private val status = SampleData.status
    private val online = SampleData.online

    // Slices of the screen, 1080x1560 instead of the full-height phone: Paparazzi shrinks every image to
    // 1000 px tall, so a shorter slice is shown at a larger scale (readable text on the project page).
    @get:Rule
    val slice = Paparazzi(environment = detectEnvironment().copy(compileSdkVersion = 34), 
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 1560),
        theme = "android:Theme.Material.NoActionBar",
        showSystemUi = false,
    )

    private fun shot(mode: ThemeMode, state: MainUiState = online, scroll: Int = 0, voice: Boolean = false,
                     remote: Boolean = false, open: Set<String> = emptySet()) {
        slice.snapshot {
            JarvisTheme(mode) {
                MainContent(state, MainActions(), initialShowVoice = voice, initialShowRemote = remote,
                    initialOpenGroups = open, initialScrollPx = scroll)
            }
        }
    }

    @Test fun shotTopDark() = shot(ThemeMode.DARK)

    @Test fun shotTopLight() = shot(ThemeMode.LIGHT)

    @Test fun shotRemoteDark() = shot(
        ThemeMode.DARK,
        online.copy(stick = Stick(0.45f, -0.6f), status = status.copy(napping = false)),
        remote = true,
    )

    @Test fun shotSettingsDark() = shot(
        ThemeMode.DARK, scroll = 760,
        open = setOf("Motors", "Audio and wake word"),
    )

    @Test fun shotSettingsLight() = shot(
        ThemeMode.LIGHT, scroll = 760,
        open = setOf("Motors", "Audio and wake word"),
        state = online.copy(feedback = mapOf("motor_trim" to Feedback(true, "Saved"))),
    )

    @Test fun shotVoiceLight() = shot(ThemeMode.LIGHT, voice = true, scroll = 380)

    // The command group for the screens (stocks, space station, NASA picture of the day) opened in the voice commands panel
    @Test fun shotVoiceScreens() = slice.snapshot {
        JarvisTheme(ThemeMode.LIGHT) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(LocalPalette.current.bg).verticalScroll(androidx.compose.foundation.rememberScrollState(2020)).padding(16.dp)) {
                VoiceCommandsPanel(haOk = true, musicOn = true, initialGroup = "Instant: stocks, space and pictures")
            }
        }
    }
}
