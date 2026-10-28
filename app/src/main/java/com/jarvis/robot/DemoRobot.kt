package com.jarvis.robot

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * The demo build ("Jarvis Demo Mel"): a pretend robot and a pretend backend, so the whole app can be tried without a robot or a PC.
 * An OkHttp interceptor answers every call the app makes (the robot's api calls, the backend's music, PIN and Home Assistant calls) with made-up data;
 * nothing goes onto the network. Settings, volume, nap and the music card react to what you do, like the real ones. The Advanced page is served by [DemoWeb].
 * Only used when the build says so (BuildConfig.DEMO); the normal app never installs it.
 */
class DemoRobot(private val photo: () -> ByteArray = { ByteArray(0) }) {
    val interceptor = Interceptor { chain -> respond(chain.request()) }

    private val started = System.currentTimeMillis()
    private var settings: JSONArray = defaultSettings()
    private var napping = false
    private var muted = false
    private var follow = false
    private var volume = 70
    private var musicOn = true
    private var song = 0
    private var musicResumeAt = 0L

    private fun json(body: String, code: Int = 200, type: String = "application/json") = body to (code to type)

    @Synchronized
    private fun respond(req: Request): Response {
        val path = req.url.encodedPath
        val q = req.url
        val post = req.method == "POST"
        val (body, meta) = when {
            // ---- the backend's control panel (the app asks it for music, the PIN, Home Assistant and photos)
            path.endsWith("/auto") -> json("")
            path.endsWith("/pin.js") -> json("window.JARVIS_PANEL_PIN = \"123456\";", type = "application/javascript")
            path.endsWith("/ha.js") -> json("window.JARVIS_HA = {\"ok\": true};", type = "application/javascript")
            path.endsWith("/api/music/control") -> json(musicControl(req))
            path.endsWith("/api/music") -> json(musicJson())
            path.endsWith("/api/dcim") -> json("{\"ok\":true}")
            // ---- the robot
            path == "/api/status" -> json(status().toString())
            path == "/api/net" -> {   // made-up traffic that grows like a robot talking now and then
                val t = System.currentTimeMillis() - started
                json("{\"tx_pkts\":${t / 120 + (t / 1000) % 3},\"rx_pkts\":${t / 70 + (t / 1000) % 5},\"tx_bytes\":${t * 2},\"rx_bytes\":${t * 4},\"t_ms\":$t}")
            }
            path == "/api/settings" && !post -> json(settings.toString())
            path == "/api/settings" -> json(saveSetting(req))
            path == "/api/reset" -> { settings = defaultSettings(); json("{\"ok\":true,\"message\":\"All settings reset to defaults.\"}") }
            path == "/api/ping" -> { Thread.sleep((6..22).random().toLong()); json("", type = "text/plain") }
            path == "/api/drive" -> json("", type = "text/plain")
            path == "/api/stop" -> { musicOn = false; json("{\"ok\":true}") }
            path == "/api/volume" -> {
                volume = (q.queryParameter("value")?.toIntOrNull() ?: volume).coerceIn(0, 100)
                json("{\"ok\":true,\"volume_requested\":$volume,\"volume\":$volume,\"volume_cap\":100}")
            }
            path == "/api/power" -> json(power(q.queryParameter("action").orEmpty()))
            path == "/api/server" -> json("{\"ok\":true,\"message\":\"Demo: the server of this pretend robot is not changed.\"}")
            path == "/api/photo" -> { Thread.sleep(700); return answer(req, 200, "image/jpeg", photo()) }
            else -> json("not found", 404, "text/plain")
        }
        if (path != "/api/ping" && path != "/api/photo") Thread.sleep((8..30).random().toLong())
        return answer(req, meta.first, meta.second, body.toByteArray(Charsets.UTF_8))
    }

    private fun answer(req: Request, code: Int, type: String, bytes: ByteArray): Response =
        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message(if (code == 200) "OK" else "Not Found")
            .header("Content-Type", type).body(bytes.toResponseBody(type.toMediaType())).build()

    // ------------------------------------------------------------------ robot status
    private fun status(): JSONObject = JSONObject()
        .put("board", "wall-e-xiao/2.5.0").put("board_name", "ESP32 S3 Sense").put("camera_detected", true).put("camera_sensor", "OV3660")
        .put("ssid", "HomeWiFi").put("ip", "192.168.1.50").put("mac", "AA:BB:CC:DD:EE:FF").put("rssi", -52 + (System.currentTimeMillis() / 4000 % 5).toInt())
        .put("uptime_s", (System.currentTimeMillis() - started) / 1000 + 86_400 + 5_400).put("state", 3).put("napping", napping)
        .put("cpu_load", if (napping) 6 else 38).put("fps", 27).put("heap_largest_kb", 61).put("audio_in_hz", 16000).put("audio_out_hz", 24000)
        .put("power", "battery").put("cpu_mhz", if (napping) 160 else 240).put("ram_free_kb", 71).put("ram_total_kb", 399)
        .put("psram_free_kb", 3300).put("psram_total_kb", 8192).put("chip_temp_c", 46.5 + (System.currentTimeMillis() / 7000 % 3))
        .put("muted", muted).put("follow", follow).put("server", "http://192.168.1.10:8003/xiaozhi/ota/").put("server_custom", true)
        .put("volume", volume).put("volume_requested", volume).put("volume_cap", 100).put("panel_url", "http://192.168.1.10:8090/")

    private fun power(action: String): String = when (action) {
        "nap" -> { napping = true; "{\"ok\":true,\"message\":\"Napping.\"}" }
        "wake" -> { napping = false; "{\"ok\":true,\"message\":\"Waking up.\"}" }
        "mute", "unmute" -> { muted = action == "mute"; "{\"ok\":true,\"message\":\"" + (if (muted) "Muted." else "Unmuted.") + "\"}" }
        "follow_on", "follow_off" -> { follow = action == "follow_on"; "{\"ok\":true,\"message\":\"" + (if (follow) "Follow mode on: Jarvis turns towards your face." else "Follow mode off.") + "\"}" }
        "face" -> "{\"ok\":true,\"message\":\"Looking for your face.\"}"
        "deepsleep" -> { napping = true; "{\"ok\":true,\"message\":\"Demo: deep sleep is pretended (the robot is napping).\"}" }
        else -> "{\"ok\":false,\"message\":\"action must be nap, wake or deepsleep\"}"
    }

    // ------------------------------------------------------------------ settings
    private fun saveSetting(req: Request): String {
        val buf = okio.Buffer()
        req.body?.writeTo(buf)
        val o = runCatching { JSONObject(buf.readUtf8()) }.getOrNull() ?: return "{\"ok\":false,\"message\":\"bad request\"}"
        val key = o.optString("key")
        val value = o.optString("value")
        for (i in 0 until settings.length()) {
            val s = settings.getJSONObject(i)
            if (s.getString("key") != key) continue
            try {
                when (s.getString("kind")) {
                    "int" -> { val v = value.toInt(); require(v in s.getInt("min")..s.getInt("max")); s.put("value", v) }
                    "hundredths" -> { val v = Math.round(value.toDouble() * 100) / 100.0; require(v >= s.getDouble("min") && v <= s.getDouble("max")); s.put("value", v) }
                    "bool" -> s.put("value", value == "1" || value == "true" || value == "on")
                    "time" -> s.put("value", if (value == "off") JSONObject.NULL else value)
                    else -> s.put("value", value)
                }
            } catch (e: Exception) {
                return "{\"ok\":false,\"message\":\"$key: value out of range\"}"
            }
            return JSONObject().put("ok", true).put("message", "$key set to ${s.opt("value")}").put("settings", JSONArray().put(s)).toString()
        }
        return "{\"ok\":false,\"message\":\"unknown setting\"}"
    }

    // ------------------------------------------------------------------ music (a song starts again some seconds after Stop, so the demo is never empty for long)
    private fun musicJson(): String {
        if (!musicOn && musicResumeAt != 0L && System.currentTimeMillis() > musicResumeAt) { musicOn = true; song++; musicResumeAt = 0L }
        val now = if (musicOn) SONGS[song % SONGS.size].let { JSONObject().put("title", it.first).put("artist", it.second).put("kind", "track") } else JSONObject.NULL
        return JSONObject().put("enabled", true).put("dir", "music").put("exists", true).put("tracks", 24).put("now", now).toString()
    }

    private fun musicControl(req: Request): String {
        val buf = okio.Buffer()
        req.body?.writeTo(buf)
        when (runCatching { JSONObject(buf.readUtf8()).optString("action") }.getOrDefault("")) {
            "stop" -> { musicOn = false; musicResumeAt = System.currentTimeMillis() + 20_000 }
            "next" -> { musicOn = true; song++ }
        }
        return "{\"ok\":true}"
    }

    companion object {
        /** One pretend robot for the whole demo app, so a new RobotApi (a new address) still talks to the same one. Set up by the view model with a picture for the Camera button. */
        val shared = DemoRobot { sharedPhoto() }
        var sharedPhoto: () -> ByteArray = { ByteArray(0) }

        val SONGS = listOf(
            "Sunrise Boulevard" to "Neon Harbour", "Blue Hour" to "The Paper Moons", "Slow Orbit" to "Mara Quinn", "Lantern Street" to "Kite & Compass",
            "Velvet Static" to "Dune Parade", "Cold Coffee Jazz" to "The Night Owls", "Paper Planes at Noon" to "Juno Vale", "After the Rain" to "Harbour Lights",
        )

        fun defaultSettings(): JSONArray = JSONArray(
            """[
            {"key":"motor_trim","kind":"int","help":"wheel balance: raise it if Jarvis drifts left, lower it if it drifts right","min":-20,"max":20,"step":1,"value":0},
            {"key":"turn_ms_per_90","kind":"int","help":"milliseconds for a 90 degree turn at speed 60","min":100,"max":3000,"step":10,"value":600},
            {"key":"motor_a_invert","kind":"bool","help":"reverses motor A (right wheel)","value":false},
            {"key":"motor_b_invert","kind":"bool","help":"reverses motor B (left wheel)","value":false},
            {"key":"motor_swap","kind":"bool","help":"swaps the left and right motors","value":false},
            {"key":"talk_motion","kind":"bool","help":"small left and right body movements while Jarvis is speaking","value":true},
            {"key":"talk_power","kind":"int","help":"power of those talking movements, in percent","min":40,"max":100,"step":1,"value":80},
            {"key":"face_power","kind":"int","help":"power of the turns towards your face and of follow mode, in percent (100 = full power)","min":30,"max":100,"step":1,"value":100},
            {"key":"face_deadzone","kind":"int","help":"how far off-centre your face may be, in percent of half the picture, before Jarvis turns at all","min":5,"max":40,"step":1,"value":18},
            {"key":"face_fine_power","kind":"int","help":"power of the small corrections, in percent","min":80,"max":100,"step":1,"value":80},
            {"key":"follow_ms","kind":"int","help":"follow mode: pause between two looks, in milliseconds","min":100,"max":5000,"step":1,"value":1000},
            {"key":"face_short_ms","kind":"int","help":"turn towards your face: a small correction","min":100,"max":1500,"step":1,"value":200},
            {"key":"face_mid_ms","kind":"int","help":"turn towards your face: a medium correction","min":100,"max":2000,"step":1,"value":350},
            {"key":"face_long_ms","kind":"int","help":"turn towards your face: a big correction","min":100,"max":3000,"step":1,"value":700},
            {"key":"face_invert","kind":"bool","help":"swap left and right if Jarvis turns away from your face","value":true},
            {"key":"soft_start_ms","kind":"int","help":"ramp-up time that limits current spikes","min":0,"max":1000,"step":10,"value":200},
            {"key":"mic_gain_db","kind":"int","help":"software microphone gain in dB","min":0,"max":24,"step":1,"value":18},
            {"key":"wake_threshold","kind":"hundredths","help":"wake word threshold, lower wakes more easily","min":0.5,"max":0.95,"step":0.01,"value":0.52},
            {"key":"camera_brightness","kind":"int","help":"camera exposure target: raise it if photos come out dark, lower it if bright spots wash out (0 = sensor default)","min":-5,"max":5,"step":1,"value":2},
            {"key":"camera_saturation","kind":"int","help":"camera colour saturation: higher for stronger colours, lower for paler ones (0 = sensor default)","min":-4,"max":4,"step":1,"value":0},
            {"key":"wake_chirp","kind":"bool","help":"short chirp right after the wake word","value":true},
            {"key":"idle_clock_min","kind":"int","help":"minutes of idle before the clock shows","min":0,"max":60,"step":1,"value":5},
            {"key":"quiet_start","kind":"time","help":"night mode start, HH:MM or off","value":"22:30"},
            {"key":"quiet_end","kind":"time","help":"night mode end, HH:MM or off","value":null},
            {"key":"weather_city","kind":"text","help":"city for the weather line on the idle clock","max_length":40,"value":"Amsterdam"},
            {"key":"visualizer_mode","kind":"enum","help":"what the screen shows while Jarvis speaks","value":"rainbow","options":["off","winamp","scope","radial","vu","mouth","orb","rainbow"]},
            {"key":"music_icon","kind":"enum","help":"the picture that replaces the eyes while music plays","value":"music icon 1","options":["music icon 1","music icon 2","music icon 3","music icon 4"]},
            {"key":"log_level","kind":"enum","help":"serial log detail","value":"warn","options":["error","warn","info","debug"]}
            ]""",
        )
    }
}
