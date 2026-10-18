package com.jarvis.robot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/**
 * Draws the real screens with made-up sample data, on the PC (no phone or emulator), so the layout can
 * be looked at. `./gradlew recordPaparazziDebug` writes the images to app/src/test/snapshots/images.
 */
class ScreenshotTest {
    private val online = SampleData.online
    private val status = SampleData.status

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar",
        showSystemUi = false,
    )

    @Test
    fun iconUnderCircleMask() {
        // The launcher shows only the middle 2/3 of the 108dp adaptive icon: draw that circle over the photo.
        paparazzi.snapshot {
            Box(Modifier.size(216.dp).background(Color(0xFF444444)), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_photo),
                    contentDescription = null,
                    modifier = Modifier.size(144.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }

    @Test
    fun splash() {
        paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { SplashScreen() } }
    }

    @Test
    fun mainDark() {
        paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { MainContent(online, MainActions()) } }
    }

    @Test
    fun mainLight() {
        paparazzi.snapshot { JarvisTheme(ThemeMode.LIGHT) { MainContent(online, MainActions()) } }
    }

    @Test
    fun mainOpenGroupsAndRemote() {
        val st = online.copy(
            held = listOf(Dir.UP, Dir.RIGHT),
            feedback = mapOf("motor_trim" to Feedback(true, "motor_trim set to 0")),
            status = status.copy(napping = true),
        )
        paparazzi.snapshot {
            JarvisTheme(ThemeMode.DARK) {
                MainContent(
                    st, MainActions(),
                    initialShowRemote = true,
                    initialOpenGroups = setOf("Motors", "Audio and wake word", "Screen and quiet hours"),
                )
            }
        }
    }

    @Test
    fun mainVoiceCommandsLight() {
        paparazzi.snapshot {
            JarvisTheme(ThemeMode.LIGHT) { MainContent(online, MainActions(), initialShowVoice = true) }
        }
    }

    @Test
    fun mainOffline() {
        paparazzi.snapshot {
            JarvisTheme(ThemeMode.DARK) {
                MainContent(MainUiState(host = "192.168.1.50"), MainActions())
            }
        }
    }
}
