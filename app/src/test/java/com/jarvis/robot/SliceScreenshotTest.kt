package com.jarvis.robot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class SliceScreenshotTest {
    private val status = SampleData.status
    private val online = SampleData.online

    // Slices of the screen, 1080x1560 instead of the full-height phone: Paparazzi shrinks every image to
    // 1000 px tall, so a shorter slice is shown at a larger scale (readable text on the project page).
    @get:Rule
    val slice = Paparazzi(
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
        online.copy(held = listOf(Dir.UP, Dir.RIGHT), status = status.copy(napping = false)),
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
}
