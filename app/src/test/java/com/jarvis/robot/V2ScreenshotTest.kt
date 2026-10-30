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
        "api/mood" to """{"ok": true, "current": "calm", "moods": [{"id": "normal", "label": "Normal", "emotion": "", "prompt": ""}, {"id": "cheerful", "label": "Cheerful", "emotion": "happy", "prompt": "Be upbeat."},
            {"id": "calm", "label": "Calm", "emotion": "relaxed", "prompt": "Speak softly and slowly, in short sentences."}, {"id": "serious", "label": "Serious", "emotion": "neutral", "prompt": "Stay factual."}, {"id": "dry", "label": "Dry", "emotion": "smirk", "prompt": "Dry humour."}]}""",
        "api/key-check" to """{"ha_url": {"state": "ok", "msg": "Home Assistant found at this address"}, "ha_token": {"state": "ok", "msg": "works"}}""",
        "api/ha-log" to """{"rows": [{"t": 1793370000, "service": "light.turn_off", "entity": "light.kitchen", "ok": true}, {"t": 1793369000, "service": "cover.open_cover", "entity": "cover.living_room", "ok": false, "error": "not allowed"}]}""",
        "api/test-ha" to """{"ok": true, "ms": 143, "entities": 214, "domains": {"sensor": 80, "light": 24, "switch": 18, "cover": 6, "climate": 3, "media_player": 4, "automation": 9}}""",
        "api/notify" to """{"configured": true, "server": "ntfy.sh", "events": {}}""",
        "api/state" to """{"keys": {"ha_url": true, "ha_token": true}, "choice": {"ears": "groq", "brain": "groq", "voice": "piper"}, "params": [{"id": "ha_allowed", "value": "light,switch,fan,scene,script,climate,input_boolean"}, {"id": "ha_confirm", "value": "cover"}]}""",
        "api/telemetry" to """{"robot": {"online": true, "last_seen": 1793370000, "samples": 1440, "up_pct": 100,
            "now": {"uptime": 11520, "ip": "192.168.1.50", "rssi": -58, "heap": 31, "psram": 6200, "ram": 72, "temp": 52.4, "wdisc": 3, "mic": -14, "mic_rms": -38, "clip": 0, "power": "battery", "napping": false},
            "rssi_series": [[1400, -72], [1250, -70], [1100, -66], [950, -64], [800, -61], [650, -60], [500, -63], [350, -58], [200, -57], [100, -59], [30, -58]],
            "heap_series": [[1400, 44], [1250, 41], [1100, 38], [950, 36], [800, 33], [650, 35], [500, 31], [350, 30], [200, 32], [100, 31], [30, 31]], "rssi": {"min": -78, "avg": -64}, "ping": {"avg": 9.5, "max": 40}, "heap": {"min": 28}, "temp": {"max": 52, "min": 38},
            "reboots_day": 1, "wifi_drops_day": 2, "mic": {"clip_minutes": 3}, "reboots": [{"t": 1793300000, "gap_s": 190, "reason": "software reset", "flags": ["weak Wi-Fi before it"]}]},
          "system": {"services": [{"name": "Server: voice link (websocket)", "port": 8000, "up": true, "expected": true, "note": "", "mem_mb": 410}, {"name": "Piper voice", "port": 8095, "up": false, "expected": false, "note": "only used with a Piper voice", "mem_mb": null}],
            "pc": {"cpu": 12, "ram_pct": 61, "disk_free_gb": 180}},
          "speed": {"day": {"all": {"n": 14, "ears": {"p50": 420, "p90": 800}, "brain": {"p50": 900, "p90": 1900}, "voice": {"p50": 380, "p90": 700}, "total": {"p50": 1700, "p90": 3100}},
              "brain": {"n": 9, "total": {"p50": 2100, "p90": 3300}}, "instant": {"n": 5, "total": {"p50": 700, "p90": 900}}}, "week": {"all": {"n": 0}},
            "recent": [{"t": 1793370000, "path": "brain", "ears": 400, "brain": 950, "voice": 360, "total": 1710}, {"t": 1793369900, "path": "instant", "ears": 420, "brain": 20, "voice": 260, "total": 700}]},
          "conv": {"day": {"wakes": 12, "sessions": 9, "false_wakes": 2, "drops": 0, "heard": 20, "fallbacks": {"voice_edge_piper": 1}}, "week": {"wakes": 40, "sessions": 30, "false_wakes": 4, "drops": 1, "heard": 90, "fallbacks": {}}, "names": {"voice_edge_piper": "Voice: Edge failed, Piper spoke"}}}""",
        "api/watchdog" to """{"watching": true, "gave_up": false, "restarts_24h": 0, "events": [{"t": 1793360000, "text": "Server restarted by the watchdog"}]}""",
        "api/brain" to """{"main": "openai/gpt-oss-120b", "last": "openai/gpt-oss-120b", "last_t": 1793370000, "backup": false, "failed": null}""",
        "api/usage" to """{"providers": {"groq": {"series": [{"requests": 3}, {"requests": 10}, {"requests": 25}, {"requests": 40}, {"requests": 12}, {"requests": 0}, {"requests": 33}], "today": {"requests": 33, "tokens": 51200, "credits": 0},
            "models": [{"model": "openai/gpt-oss-120b", "today": {"requests": 33, "tokens": 51200}, "limits": {"tpm": 8000, "rpd": 1000, "tpd": 200000}, "est": {"tokens": 70000, "limit": 200000}}]},
          "gemini": {"series": [{"requests": 2}, {"requests": 4}], "today": {"requests": 4, "tokens": 3000, "credits": 0}, "models": [{"model": "gemini-3.5-flash", "today": {"requests": 4, "tokens": 3000}, "limits": {"rpd": 20}}]},
          "tavily": {"series": [{"requests": 1}, {"requests": 3}], "today": {"requests": 3, "tokens": 0, "credits": 3}, "models": []}},
          "show": [], "failover": {"answer": {"day": {"requests": 40, "backups": 1, "failovers": 1, "reasons": [{"model": "openai/gpt-oss-120b", "reason": "rate limit", "n": 1}]}}},
          "live": {"tavily": {"ok": true, "plan": "Researcher", "plan_usage": 137, "plan_limit": 1000}, "groq": {"ok": true, "limit_requests": 1000, "remaining_requests": 960}}}""",
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
    @Test fun v2PageMood() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2PageHost(V2Page.MOOD, online, panelActions, {}, {}) {} } }
    @Test fun v2PageHome() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2PageHost(V2Page.HOME, online, panelActions, {}, {}) {} } }
    @Test fun v2PageHealth() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2PageHost(V2Page.HEALTH, online, panelActions, {}, {}) {} } }
    @Test fun v2PageHealthDutch() {
        I18n.restore("nl")
        try {
            paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2HealthPage(panelActions, {}, {}) } }
        } finally {
            I18n.restore("en")
        }
    }
    @Test fun v2PageHealthSpeed() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2HealthPage(panelActions, {}, {}, initialTab = 1) } }
    @Test fun v2PageHealthUsage() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2HealthPage(panelActions, {}, {}, initialTab = 2) } }
    @Test fun v2PageHealthSystem() = paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2HealthPage(panelActions, {}, {}, initialTab = 3) } }
    @Test fun v2PageHomeDutch() {
        I18n.restore("nl")
        try {
            paparazzi.snapshot { JarvisTheme(ThemeMode.DARK) { V2PageHost(V2Page.HOME, online, panelActions, {}, {}) {} } }
        } finally {
            I18n.restore("en")
        }
    }
}
