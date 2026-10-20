package com.jarvis.robot

import org.json.JSONArray
import org.json.JSONObject

/** One poll of GET /api/status. Optional fields are null when an older firmware doesn't send them. */
data class Status(
    val boardName: String = "",
    val cameraDetected: Boolean = false,
    val cameraSensor: String = "",
    val mac: String = "",
    val ssid: String = "",
    val ip: String = "",
    val power: String = "",
    val state: Int = 0,
    val napping: Boolean = false,
    val uptimeS: Long = 0,
    val cpuLoad: Int? = null,
    val cpuMhz: Int? = null,
    val fps: Int? = null,
    val ramFreeKb: Int = 0,
    val ramTotalKb: Int = 0,
    val psramFreeKb: Int = 0,
    val heapLargestKb: Int? = null,
    val rssi: Int? = null,
    val audioInHz: Int = 0,
    val audioOutHz: Int = 0,
    // volume card (null on firmware without /api/volume)
    val volume: Int? = null,
    val volumeRequested: Int? = null,
    val volumeCap: Int? = null,
    // voice server card (null on firmware without /api/server)
    val server: String? = null,
    val serverCustom: Boolean = false,
    val panelUrl: String = "",
)

enum class Kind { INT, BOOL, HUNDREDTHS, TIME, ENUM, TEXT }

/**
 * One entry of GET /api/settings. [value] is always a string so the UI has one representation:
 * numbers as text, bool as "1"/"0", time as "HH:MM" or "" (= off), enum/text as is.
 */
data class SettingDef(
    val key: String,
    val kind: Kind,
    val help: String,
    val min: Double,
    val max: Double,
    val step: Double,
    val value: String,
    val options: List<String>,
    val maxLength: Int,
)

data class SaveResult(val ok: Boolean, val message: String, val updated: SettingDef?)

data class PowerResult(val ok: Boolean, val message: String)

data class VolumeResult(val ok: Boolean, val volume: Int, val requested: Int, val cap: Int)

fun parseStatus(o: JSONObject): Status {
    fun intOrNull(k: String): Int? = if (o.has(k) && !o.isNull(k)) o.optInt(k) else null
    return Status(
        boardName = o.optString("board_name", o.optString("board", "").substringBefore('/')),
        cameraDetected = o.optBoolean("camera_detected", false),
        cameraSensor = o.optString("camera_sensor", ""),
        mac = o.optString("mac", ""),
        ssid = o.optString("ssid", ""),
        ip = o.optString("ip", ""),
        power = o.optString("power", ""),
        state = o.optInt("state", 0),
        napping = o.optBoolean("napping", false),
        uptimeS = o.optLong("uptime_s", 0),
        cpuLoad = intOrNull("cpu_load"),
        cpuMhz = intOrNull("cpu_mhz"),
        fps = intOrNull("fps"),
        ramFreeKb = o.optInt("ram_free_kb", 0),
        ramTotalKb = o.optInt("ram_total_kb", 0),
        psramFreeKb = o.optInt("psram_free_kb", 0),
        heapLargestKb = intOrNull("heap_largest_kb"),
        rssi = intOrNull("rssi"),
        audioInHz = o.optInt("audio_in_hz", 0),
        audioOutHz = o.optInt("audio_out_hz", 0),
        volume = intOrNull("volume"),
        volumeRequested = intOrNull("volume_requested"),
        volumeCap = intOrNull("volume_cap"),
        server = if (o.has("server")) o.optString("server") else null,
        serverCustom = o.optBoolean("server_custom", false),
        panelUrl = o.optString("panel_url", ""),
    )
}

fun parseSetting(o: JSONObject): SettingDef? {
    val kind = when (o.optString("kind")) {
        "int" -> Kind.INT
        "bool" -> Kind.BOOL
        "hundredths" -> Kind.HUNDREDTHS
        "time" -> Kind.TIME
        "enum" -> Kind.ENUM
        "text" -> Kind.TEXT
        else -> return null
    }
    val value = when (kind) {
        Kind.BOOL -> if (o.optBoolean("value", false)) "1" else "0"
        Kind.TIME -> if (o.isNull("value")) "" else o.optString("value", "")
        else -> o.optString("value", "")
    }
    val options = mutableListOf<String>()
    o.optJSONArray("options")?.let { arr ->
        for (i in 0 until arr.length()) options.add(arr.optString(i))
    }
    return SettingDef(
        key = o.optString("key"),
        kind = kind,
        help = o.optString("help", ""),
        min = o.optDouble("min", 0.0),
        max = o.optDouble("max", 0.0),
        step = o.optDouble("step", 1.0),
        value = value,
        options = options,
        maxLength = o.optInt("max_length", 40),
    )
}

fun parseSettings(arr: JSONArray): List<SettingDef> {
    val out = mutableListOf<SettingDef>()
    for (i in 0 until arr.length()) parseSetting(arr.getJSONObject(i))?.let { out.add(it) }
    return out
}

// ---------------------------------------------------------------- formatting (mirrors the page)

private val LABELS = mapOf(
    "motor_max_speed" to "Top speed",
    "motor_trim" to "Steering trim",
    "turn_ms_per_90" to "Turn duration (90°)",
    "motor_a_invert" to "Right motor direction",
    "motor_b_invert" to "Left motor direction",
    "motor_swap" to "Swap left/right",
    "soft_start_ms" to "Soft start ramp",
    "mic_gain_db" to "Microphone gain",
    "wake_threshold" to "Wake sensitivity",
    "shutter_sound" to "Camera shutter sound",
    "wake_chirp" to "Wake chirp",
    "idle_clock_min" to "Idle clock after",
    "quiet_start" to "Quiet hours start",
    "quiet_end" to "Quiet hours end",
    "weather_city" to "Weather city",
    "visualizer_mode" to "Speaking screen effect",
    "log_level" to "Log detail",
)

fun labelFor(key: String): String = LABELS[key]
    ?: key.replace('_', ' ').split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

fun fmtValue(def: SettingDef): String = when (def.kind) {
    Kind.BOOL -> if (def.value == "1") "On" else "Off"
    Kind.TIME -> def.value.ifEmpty { "Off" }
    Kind.HUNDREDTHS -> "%.2f".format(java.util.Locale.US, def.value.toDoubleOrNull() ?: 0.0)
    Kind.TEXT -> def.value.ifEmpty { "(not set)" }
    Kind.ENUM -> def.value
    Kind.INT -> def.value + when {
        def.key.contains("_ms") || def.key == "turn_ms_per_90" -> " ms"
        def.key == "mic_gain_db" -> " dB"
        def.key.contains("speed") -> "%"
        def.key == "idle_clock_min" -> " min"
        else -> ""
    }
}

fun fmtUptime(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m ${s}s"
}

fun fmtUsage(usedKb: Int, totalKb: Int, mb: Boolean = false): String {
    val div = if (mb) 1024.0 else 1.0
    val unit = if (mb) " MB" else " KB"
    return "${Math.round(usedKb / div)}/${Math.round(totalKb / div)}$unit"
}

private val STATE_NAMES = listOf(
    "unknown", "starting", "wifi setup", "idle", "connecting", "listening",
    "speaking", "notifying", "upgrading", "activating", "testing audio", "fatal error",
)

fun statusName(s: Status): String {
    val name = if (s.napping) "napping" else STATE_NAMES.getOrElse(s.state) { "state ${s.state}" }
    return name.replaceFirstChar { it.uppercase() }
}

fun fmtAudio(s: Status): String {
    if (s.audioInHz <= 0) return "--"
    val inK = trimKhz(s.audioInHz)
    val outK = trimKhz(s.audioOutHz)
    return if (s.audioOutHz <= 0 || s.audioOutHz == s.audioInHz) "$inK kHz" else "$inK / $outK kHz"
}

private fun trimKhz(hz: Int): String {
    val k = hz / 1000.0
    return if (k == Math.floor(k)) k.toInt().toString() else k.toString()
}

// ---------------------------------------------------------------- section layout (mirrors the page)

/** Settings that exist on the robot but are fixed at their defaults and not offered on the page. */
val HIDDEN_KEYS = setOf("motor_max_speed", "shutter_sound")

data class SettingsGroup(val title: String, val keys: List<String>, val collapsible: Boolean)

val SETTINGS_GROUPS = listOf(
    SettingsGroup(
        "Motors",
        listOf("motor_trim", "turn_ms_per_90", "motor_a_invert", "motor_b_invert", "motor_swap", "soft_start_ms"),
        true,
    ),
    SettingsGroup("Audio and wake word", listOf("mic_gain_db", "wake_threshold", "wake_chirp"), true),
    SettingsGroup("Screen and quiet hours", listOf("idle_clock_min", "quiet_start", "quiet_end"), true),
    SettingsGroup("Weather", listOf("weather_city"), false),
    SettingsGroup("Visualizer", listOf("visualizer_mode"), false),
    SettingsGroup("System", listOf("log_level"), false),
)

/** Groups with the settings the robot actually reported; unlisted keys land in "Other". */
fun groupSettings(defs: List<SettingDef>): List<Pair<SettingsGroup, List<SettingDef>>> {
    val visible = defs.filter { it.key !in HIDDEN_KEYS }
    val byKey = visible.associateBy { it.key }
    val used = mutableSetOf<String>()
    val result = mutableListOf<Pair<SettingsGroup, List<SettingDef>>>()
    for (g in SETTINGS_GROUPS) {
        val present = g.keys.mapNotNull { byKey[it] }
        if (present.isNotEmpty()) {
            used += present.map { it.key }
            result += g to present
        }
    }
    val rest = visible.filter { it.key !in used }
    if (rest.isNotEmpty()) result += SettingsGroup("Other", emptyList(), false) to rest
    return result
}

// ---------------------------------------------------------------- voice commands (same list as the page)

/** [instant] = done by the own backend before the brain (shown in its own colour); the others work with any server. */
data class VoiceCommand(val title: String, val example: String, val instant: Boolean = false, val ha: Boolean = false)

val VOICE_COMMANDS: List<Pair<String, List<VoiceCommand>>> = listOf(
    "Movement" to listOf(
        VoiceCommand("Drive, turn, stop", "“drive forward for two seconds”, “turn around”, “stop”"),
        VoiceCommand("Dance", "“do a little dance” (random, wiggle, spin, or happy)"),
        VoiceCommand("Look around", "“look around the room” (4 photos, one combined answer)"),
    ),
    "Screen" to listOf(
        VoiceCommand(
            "Speaking effect",
            "“switch the screen effect to radial” (off, winamp, rainbow, scope, radial, vu, mouth, orb)",
        ),
        VoiceCommand("Clock / screensaver", "“show the clock”"),
        VoiceCommand("QR: settings page", "“show me a QR code for your settings page” (Jarvis shows a code that opens the settings page)"),
        VoiceCommand("QR: backend", "“show me a QR code for your backend” / “…for your server” (needs your own server)"),
        VoiceCommand("Both QR codes", "“show me your QR codes” / “how do I open your pages?” (one after the other, about a minute)"),
    ),
    "Volume" to listOf(
        VoiceCommand("Read it", "“what is your volume?”, “what is your max volume?”"),
        VoiceCommand("Change it", "“set the volume to 80”, “turn the volume up” (100 is full volume)"),
    ),
    "Faces" to listOf(
        VoiceCommand("Show an expression", "“look sad”, “show a happy face”"),
        VoiceCommand("List expressions", "“what faces can you make?”"),
    ),
    "Audio tuning" to listOf(
        VoiceCommand("Mic gain", "“make the microphone more sensitive”"),
        VoiceCommand("Wake sensitivity", "“wake up more easily”"),
        VoiceCommand("Read current tuning", "“what are your audio settings?”"),
    ),
    "Settings" to listOf(
        VoiceCommand("List / read / change / reset", "“you drift to the left”, “what are your current settings?”"),
    ),
    "Power" to listOf(
        VoiceCommand("Nap", "“take a nap” / “are you napping?”"),
        VoiceCommand("Deep sleep", "“sleep for two hours”, “sleep until 7 o’clock”"),
    ),
    "WiFi" to listOf(
        VoiceCommand("Change network", "“reconfigure your WiFi” (confirm when asked; Jarvis opens its setup hotspot)"),
    ),
    "Timers" to listOf(
        VoiceCommand("Set / list / cancel", "“set a timer for 5 minutes”, “alarm at 7:30”, “what timers are running?”"),
    ),
    "Weather" to listOf(
        VoiceCommand("Check it", "“what’s the weather?” (set the city in Settings first)"),
    ),
    "Diagnostics" to listOf(
        VoiceCommand("List / run a check", "“what can you check?”, “run a diagnostic on the camera”, “check everything”"),
    ),
    "Built into upstream" to listOf(
        VoiceCommand("Volume, photo, status, theme", "“turn the volume up”, “take a photo”, “switch to dark theme”"),
    ),
    "Instant: stop and drive" to listOf(
        VoiceCommand("Stop", "“stop”, “halt”, “freeze”", instant = true),
        VoiceCommand("Drive", "“go forward”, “go back”, “go forward for 3 seconds”", instant = true),
        VoiceCommand("Turn", "“turn left”, “turn right”, “turn around”", instant = true),
        VoiceCommand("Dance", "“dance”", instant = true),
    ),
    "Instant: sound and screen" to listOf(
        VoiceCommand("Volume", "“volume 50”, “volume up”, “volume down”, “louder”, “full volume”", instant = true),
        VoiceCommand("Theme", "“dark mode”, “light mode”", instant = true),
        VoiceCommand("Brightness", "“brightness 30”", instant = true),
    ),
    "Instant: time and timers" to listOf(
        VoiceCommand("Time and date", "“what time is it?”, “what day is it?”", instant = true),
        VoiceCommand("Timer", "“set a timer for 5 minutes”, “cancel all timers”", instant = true),
    ),
    "Instant: moods" to listOf(
        VoiceCommand("Mood", "“be cheerful”, “be calm”, “be serious”, “be dry”, “back to normal”", instant = true),
    ),
    "Instant: explore and briefing" to listOf(
        VoiceCommand("Explore", "“explore the room” (looks around, steps forward, looks again, then tells you what it found)", instant = true),
        VoiceCommand("Briefing", "“good morning” (weather, timers, notes and news on the topics set in the backend)", instant = true),
        VoiceCommand("In Dutch too", "“vooruit”, “hoe laat is het?”, “zet een timer op 10 minuten”, “wees kalm”, “goedemorgen”", instant = true),
    ),
    "Home: lights and switches" to listOf(
        VoiceCommand("On and off", "“turn off the living room light”, “turn on the tv”, “switch off the fan”, “woonkamer lamp aan”, “doe de schuur uit”", ha = true),
        VoiceCommand("Brightness", "“dim the game room to 40”, “zet de lamp op 30 procent”", ha = true),
        VoiceCommand("Whole rooms", "“turn off all lights in the living room”, “lights off” (asks first when it is more than five)", ha = true),
    ),
    "Home: scenes, scripts and heating" to listOf(
        VoiceCommand("Scenes and scripts", "“start the netflix scene”, “run the goodnight script”, “zet de disco scene aan”", ha = true),
        VoiceCommand("Heating", "“set the AC livingroom to 21”, “zet de airco kantoor op 20”, “make it warmer” (with two thermostats it asks which one)", ha = true),
        VoiceCommand("Blinds", "“close the blinds” (asks for a yes first)", ha = true),
    ),
    "Home: questions" to listOf(
        VoiceCommand("State", "“is the living room light on?”, “which lights are on?”, “what is the temperature in the living room?”", ha = true),
        VoiceCommand("Anything else", "“make the lights cosy”: the brain picks the Home Assistant action itself", ha = true),
        VoiceCommand("Never", "locks, alarms and garage doors: Jarvis refuses", ha = true),
    )
)

// ---------------------------------------------------------------- remote control

enum class Dir { UP, DOWN, LEFT, RIGHT }

const val DRIVE_SPEED = 100

/**
 * Wheel command (left, right) for the arrows held right now, as the page computes it: up/down alone
 * drive straight, left/right alone spin in place, a combination arcs by slowing the inner wheel to 35%.
 */
fun wheelCommand(held: Set<Dir>): Pair<Int, Int> {
    val f = (if (Dir.UP in held) 1 else 0) - (if (Dir.DOWN in held) 1 else 0)
    val t = (if (Dir.RIGHT in held) 1 else 0) - (if (Dir.LEFT in held) 1 else 0)
    if (f == 0) return Pair(t * DRIVE_SPEED, -t * DRIVE_SPEED)
    var l = f * DRIVE_SPEED
    var r = f * DRIVE_SPEED
    if (t > 0) r = Math.round(r * 0.35f)
    if (t < 0) l = Math.round(l * 0.35f)
    return Pair(l, r)
}

// ---------------------------------------------------------------- deep sleep helpers

fun sleepMinutes(hours: Int, minutes: Int): Int =
    (hours.coerceIn(0, 24) * 60 + minutes.coerceIn(0, 59)).coerceAtMost(1440)

fun sleepText(total: Int): String {
    if (total == 0) return "until you press the BOOT button"
    val h = total / 60
    val m = total % 60
    val hp = if (h > 0) "$h hr${if (h > 1) "s" else ""}" else ""
    val mp = if (m > 0) "$m min" else ""
    return "for " + listOf(hp, mp).filter { it.isNotEmpty() }.joinToString(" ")
}


/** The backend's music module: is it on (a folder is set), how many songs, and what is playing right now. */
data class MusicStatus(val enabled: Boolean, val tracks: Int, val title: String?, val artist: String?)
