package com.jarvis.robot

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    // A real /api/status reply captured from the robot (build 27).
    private val realStatus = """{"board":"wall-e-xiao/2.5.0","board_name":"ESP32 S3 Sense","camera_detected":true,
        "camera_sensor":"OV3660","ssid":"HomeWiFi","ip":"192.168.1.50","mac":"AA:BB:CC:DD:EE:FF","rssi":-87,
        "uptime_s":30,"state":3,"napping":false,"cpu_load":42,"fps":11,"heap_largest_kb":32,"audio_in_hz":16000,
        "audio_out_hz":24000,"power":"console","cpu_mhz":240,"ram_free_kb":69,"ram_total_kb":399,
        "psram_free_kb":3537,"psram_total_kb":8192}"""

    @Test
    fun parsesRealStatus() {
        val s = parseStatus(JSONObject(realStatus))
        assertEquals("ESP32 S3 Sense", s.boardName)
        assertTrue(s.cameraDetected)
        assertEquals("OV3660", s.cameraSensor)
        assertEquals("AA:BB:CC:DD:EE:FF", s.mac)
        assertEquals("console", s.power)
        assertEquals(42, s.cpuLoad)
        assertEquals(240, s.cpuMhz)
        assertEquals(11, s.fps)
        assertEquals(32, s.heapLargestKb)
        assertEquals(-87, s.rssi)
        assertEquals("Idle", statusName(s))
        assertEquals("", panelUrlOf(s))   // the cloud has no Advanced page
        assertEquals("http://10.1.2.3:8090/", panelUrlOf(s.copy(serverCustom = true, server = "http://10.1.2.3:8003/xiaozhi/ota/")))
        assertEquals("http://pc:9000/", panelUrlOf(s.copy(serverCustom = true, panelUrl = "http://pc:9000/")))
        assertEquals("Idle", statusName(s, musicPlaying = true))   // music mode only replaces "Speaking"
        assertEquals("Music mode", statusName(s.copy(state = 6), musicPlaying = true))
        assertEquals("Speaking", statusName(s.copy(state = 6), musicPlaying = false))
        assertEquals("16 / 24 kHz", fmtAudio(s))
        assertEquals("399".toInt() - 69, s.ramTotalKb - s.ramFreeKb)
    }

    @Test
    fun olderFirmwareWithoutNewFieldsStillParses() {
        val s = parseStatus(JSONObject("""{"board":"wall-e-xiao/2.5.0","uptime_s":5,"state":3}"""))
        assertEquals("wall-e-xiao", s.boardName)
        assertFalse(s.cameraDetected)
        assertNull(s.cpuLoad)
        assertNull(s.fps)
        assertNull(s.heapLargestKb)
        assertEquals("--", fmtAudio(s))
    }

    @Test
    fun nappingOverridesTheStateName() {
        val s = parseStatus(JSONObject("""{"state":3,"napping":true}"""))
        assertEquals("Napping", statusName(s))
    }

    private val settingsJson = """[
      {"key":"motor_trim","kind":"int","help":"balance","min":-20,"max":20,"step":1,"value":0},
      {"key":"wake_threshold","kind":"hundredths","help":"wake","min":0.5,"max":0.95,"step":0.01,"value":0.52},
      {"key":"motor_swap","kind":"bool","help":"swap","value":false},
      {"key":"quiet_start","kind":"time","help":"night","value":null},
      {"key":"quiet_end","kind":"time","help":"night","value":"07:30"},
      {"key":"visualizer_mode","kind":"enum","help":"fx","value":"rainbow","options":["off","winamp","rainbow"]},
      {"key":"weather_city","kind":"text","help":"city","max_length":40,"value":"Purmerend"},
      {"key":"motor_max_speed","kind":"int","help":"hidden","min":20,"max":100,"step":1,"value":100},
      {"key":"future_thing","kind":"int","help":"unknown key","min":0,"max":9,"step":1,"value":3},
      {"key":"weird","kind":"mystery","help":"unknown kind","value":1}
    ]"""

    @Test
    fun parsesEverySettingKind() {
        val defs = parseSettings(JSONArray(settingsJson))
        assertEquals(9, defs.size) // the unknown kind is skipped, not fatal
        val byKey = defs.associateBy { it.key }
        assertEquals("0", byKey.getValue("motor_trim").value)
        assertEquals(-20.0, byKey.getValue("motor_trim").min, 0.0)
        assertEquals("0.52", byKey.getValue("wake_threshold").value)
        assertEquals("0", byKey.getValue("motor_swap").value)
        assertEquals("", byKey.getValue("quiet_start").value)
        assertEquals("07:30", byKey.getValue("quiet_end").value)
        assertEquals(listOf("off", "winamp", "rainbow"), byKey.getValue("visualizer_mode").options)
        assertEquals("Purmerend", byKey.getValue("weather_city").value)
    }

    @Test
    fun formatsValuesLikeThePage() {
        val byKey = parseSettings(JSONArray(settingsJson)).associateBy { it.key }
        assertEquals("0", fmtValue(byKey.getValue("motor_trim")))
        assertEquals("0.52", fmtValue(byKey.getValue("wake_threshold")))
        assertEquals("Off", fmtValue(byKey.getValue("motor_swap")))
        assertEquals("Off", fmtValue(byKey.getValue("quiet_start")))
        assertEquals("07:30", fmtValue(byKey.getValue("quiet_end")))
        assertEquals("100%", fmtValue(byKey.getValue("motor_max_speed")))
        assertEquals("Turn duration (90°)", labelFor("turn_ms_per_90"))
        assertEquals("Future Thing", labelFor("future_thing"))
    }

    @Test
    fun hiddenSettingsAreNotShownAndUnknownOnesLandInOther() {
        val groups = groupSettings(parseSettings(JSONArray(settingsJson)))
        val titles = groups.map { it.first.title }
        assertTrue("Motors" in titles)
        assertTrue("Other" in titles)
        val all = groups.flatMap { it.second }.map { it.key }
        assertFalse("motor_max_speed" in all)
        assertTrue("future_thing" in all)
        assertTrue(groups.first { it.first.title == "Motors" }.first.collapsible)
        assertFalse(groups.first { it.first.title == "Weather" }.first.collapsible)
    }

    @Test
    fun chipTemperatureAndTrafficRate() {
        assertEquals(44.3, parseStatus(JSONObject("""{"chip_temp_c":44.3}""")).chipTempC!!, 0.001)
        assertNull(parseStatus(JSONObject("{}")).chipTempC)   // older firmware: no row
        val r = netRate(NetCounters(100, 200, 1000), NetCounters(110, 230, 3000))!!
        assertEquals(5.0, r.txPps, 0.001)
        assertEquals(15.0, r.rxPps, 0.001)
        assertNull(netRate(null, NetCounters(1, 1, 1)))
        assertNull(netRate(NetCounters(500, 500, 9000), NetCounters(3, 4, 200)))   // the robot restarted in between
    }

    @Test
    fun parsesVolumeAndServerFields() {
        val s = parseStatus(JSONObject("""{"volume":78,"volume_requested":90,"volume_cap":78,"server":"http://192.168.1.20:8003/xiaozhi/ota/",
            "server_custom":true,"panel_url":"http://192.168.1.20:8090/"}"""))
        assertEquals(78, s.volume)
        assertEquals(90, s.volumeRequested)
        assertEquals(78, s.volumeCap)
        assertTrue(s.serverCustom)
        assertEquals("http://192.168.1.20:8090/", s.panelUrl)
        // firmware older than build 30: no volume / server card
        val old = parseStatus(JSONObject(realStatus))
        assertNull(old.volumeRequested)
        assertNull(old.server)
        assertFalse(old.serverCustom)
    }

    @Test
    fun maxVolumeIsGoneFromTheSettingsLayout() {
        assertFalse(SETTINGS_GROUPS.any { "max_volume" in it.keys })
        assertTrue(VOICE_COMMANDS.any { it.first == "Volume" })
    }

    @Test
    fun uptimeAndUsageFormats() {
        assertEquals("2m 5s", fmtUptime(125))
        assertEquals("15m 46s", fmtUptime(946))
        assertEquals("1h 1m", fmtUptime(3700))
        assertEquals("328/399 KB", fmtUsage(328, 399))
    }

    @Test
    fun driveMath() {
        assertEquals(Pair(100, 100), wheelCommand(setOf(Dir.UP)))
        assertEquals(Pair(-100, -100), wheelCommand(setOf(Dir.DOWN)))
        assertEquals(Pair(-100, 100), wheelCommand(setOf(Dir.LEFT)))
        assertEquals(Pair(100, -100), wheelCommand(setOf(Dir.RIGHT)))
        assertEquals(Pair(100, 35), wheelCommand(setOf(Dir.UP, Dir.RIGHT)))
        assertEquals(Pair(35, 100), wheelCommand(setOf(Dir.UP, Dir.LEFT)))
        assertEquals(Pair(-100, -35), wheelCommand(setOf(Dir.DOWN, Dir.RIGHT)))
        assertEquals(Pair(0, 0), wheelCommand(emptySet()))
        assertEquals(Pair(0, 0), wheelCommand(setOf(Dir.LEFT, Dir.RIGHT)))
        assertEquals(Pair(0, 0), wheelCommand(setOf(Dir.UP, Dir.DOWN)))
    }

    @Test
    fun deepSleepDurations() {
        assertEquals(0, sleepMinutes(0, 0))
        assertEquals(205, sleepMinutes(3, 25))
        assertEquals(1440, sleepMinutes(24, 59))
        assertEquals(1440, sleepMinutes(99, 0))
        assertEquals("until you press the BOOT button", sleepText(0))
        assertEquals("for 3 hrs 25 min", sleepText(205))
        assertEquals("for 1 hr", sleepText(60))
        assertEquals("for 45 min", sleepText(45))
    }

    @Test
    fun normalizesTheAddress() {
        assertEquals("192.168.1.50", normalizeHost("  http://192.168.1.50/ "))
        assertEquals("jarvis.local", normalizeHost("jarvis.local/api/status"))
        assertEquals("192.168.1.50:8080", normalizeHost("192.168.1.50:8080"))
    }
}
