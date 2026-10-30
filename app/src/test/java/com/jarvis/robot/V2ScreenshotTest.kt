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
    private val brain = BrainSwitches(greeter = false, voices = true, voicesPeople = 1, voicesModel = true, smartTurn = true, smartModel = true, memoryOn = true)
    @Test fun v2Brain() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { Tab { V2Brain(playing.copy(brain = brain), MainActions()) } } }
    @Test fun v2Settings() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { Tab { V2Settings(online, MainActions()) } } }
    @Test fun v2SettingsGroups() = paparazzi.snapshot {
        JarvisTheme(ThemeMode.DARK) {
            Tab {
                val all = groupSettings(online.settings)
                val g = all.maxBy { it.second.size }   // the group with the most settings shows the most kinds of editor
                V2SettingsGroupPage(g.first.title, g.second, online, MainActions()) {}
            }
        }
    }

    @Test fun v2HomeDutch() {
        I18n.restore("nl")
        try {
            paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2App(playing, MainActions()) } }
        } finally {
            I18n.restore("en")
        }
    }

    @Test fun v2BrainDutch() {
        I18n.restore("nl")
        try {
            paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { Tab { V2Brain(playing.copy(brain = brain), MainActions()) } } }
        } finally {
            I18n.restore("en")
        }
    }

    @Test fun v2Offline() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2App(online.copy(online = false, everConnected = true), MainActions()) } }

    // ---- the app's own backend pages, fed with made-up panel answers
    private val panelAnswers = mapOf(
        "api/greeter" to """{"enabled": true, "who": "owner", "name": "", "name_default": "Alex", "cooldown_h": 4.0, "per_day": 3, "quiet_from": "22:00", "quiet_to": "07:00",
            "min_match": 0.45, "turn": true, "quiet_now": false, "example": "Good afternoon, Alex. It's 16:20 currently.",
            "today": {"greeted": 1, "looks": 37, "tests": 2, "skip": {"cooldown": 30, "chat": 4}},
            "recent": [{"t": 1793370000, "who": "owner", "text": "Good afternoon, Alex. It's 16:16 currently.", "ok": true, "message": "Turning to you, then speaking.", "test": true}]}""",
        "api/voiceprint" to """{"enabled": true, "threshold": 0.7, "model": "wespeaker.onnx", "model_ready": true, "people": [{"name": "Alex", "samples": 4}, {"name": "Sam", "samples": 1}],
            "enrol": null, "enrol_done": null, "checks": 12, "known": 9, "unknown": 2, "short": 1, "late": 0, "avg_ms": 64.0,
            "recent": [{"score": 0.87, "who": "Alex", "known": true}, {"score": 0.64, "who": "Alex", "known": false}, {"score": 0.82, "who": "Alex", "known": true}]}""",
        "api/music" to """{"enabled": true, "dir": "music", "exists": true, "tracks": 11, "now": {"title": "JOE", "artist": "Radio"}}""",
        "api/memory" to """{"ok": true, "on": true, "memory": [{"device": "aa:bb", "notes": {"people": {"Alex": ["likes jazz radio", "dentist on Thursday"]}, "preferences": ["short answers"], "open_items": []}}]}""",
    )
    private val panelActions = MainActions(onPanel = { path, _, done -> done(panelAnswers[path]?.let { org.json.JSONObject(it) }) })

    @Test fun v2PageGreeter() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2PageHost(V2Page.GREETER, online, panelActions, {}, {}) {} } }
    @Test fun v2PageVoices() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2PageHost(V2Page.VOICES, online, panelActions, {}, {}) {} } }
    @Test fun v2PageMusic() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2PageHost(V2Page.MUSIC, online, panelActions, {}, {}) {} } }
    @Test fun v2PageMemory() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2PageHost(V2Page.MEMORY, online, panelActions, {}, {}) {} } }
    @Test fun v2PageVoicesDutch() {
        I18n.restore("nl")
        try {
            paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2PageHost(V2Page.VOICES, online, panelActions, {}, {}) {} } }
        } finally {
            I18n.restore("en")
        }
    }

    @Test fun v2PageVolume() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2PageHost(V2Page.VOLUME, online, panelActions, {}, {}) {} } }
}
