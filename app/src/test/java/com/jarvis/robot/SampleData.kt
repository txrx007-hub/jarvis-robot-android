package com.jarvis.robot

import org.json.JSONArray
import org.json.JSONObject

/** Made-up robot data for the screenshots: no real network name, address or MAC. */
object SampleData {
    val status = parseStatus(
        JSONObject(
            """{"board":"wall-e-xiao/2.5.0","board_name":"ESP32 S3 Sense","camera_detected":true,
            "camera_sensor":"OV3660","ssid":"HomeWiFi","ip":"192.168.1.50","mac":"AA:BB:CC:DD:EE:FF","rssi":-51,
            "uptime_s":946,"state":3,"napping":false,"cpu_load":42,"fps":27,"heap_largest_kb":61,
            "audio_in_hz":16000,"audio_out_hz":24000,"power":"battery","cpu_mhz":240,"ram_free_kb":71,
            "ram_total_kb":399,"psram_free_kb":3300,"psram_total_kb":8192,
            "volume":100,"volume_requested":100,"volume_cap":100,"server":"http://192.168.1.20:8003/xiaozhi/ota/",
            "server_custom":true,"panel_url":"http://192.168.1.20:8090/"}""",
        ),
    )

    val settings = parseSettings(
        JSONArray(
            """[
          {"key":"motor_trim","kind":"int","help":"wheel balance: raise it if Jarvis drifts left","min":-20,"max":20,"step":1,"value":0},
          {"key":"turn_ms_per_90","kind":"int","help":"milliseconds for a 90 degree turn at speed 60","min":100,"max":3000,"step":10,"value":600},
          {"key":"motor_a_invert","kind":"bool","help":"reverses motor A (right wheel)","value":false},
          {"key":"motor_b_invert","kind":"bool","help":"reverses motor B (left wheel)","value":true},
          {"key":"motor_swap","kind":"bool","help":"swaps the left and right motors","value":false},
          {"key":"soft_start_ms","kind":"int","help":"ramp-up time that limits current spikes","min":0,"max":1000,"step":10,"value":200},
          {"key":"mic_gain_db","kind":"int","help":"software microphone gain in dB","min":0,"max":24,"step":1,"value":18},
          {"key":"camera_brightness","kind":"int","help":"camera exposure target","min":-5,"max":5,"step":1,"value":2},
          {"key":"wake_threshold","kind":"hundredths","help":"wake word threshold, lower wakes more easily","min":0.5,"max":0.95,"step":0.01,"value":0.52},
          {"key":"wake_chirp","kind":"bool","help":"short chirp right after the wake word","value":true},
          {"key":"idle_clock_min","kind":"int","help":"minutes of idle before the clock","min":0,"max":60,"step":1,"value":5},
          {"key":"quiet_start","kind":"time","help":"night mode start, HH:MM or off","value":"22:30"},
          {"key":"quiet_end","kind":"time","help":"night mode end, HH:MM or off","value":null},
          {"key":"weather_city","kind":"text","help":"city for the weather","max_length":40,"value":"Purmerend"},
          {"key":"visualizer_mode","kind":"enum","help":"what the screen shows while Jarvis speaks","value":"rainbow",
           "options":["off","winamp","scope","radial","vu","mouth","orb","rainbow"]},
          {"key":"log_level","kind":"enum","help":"serial log detail","value":"warn","options":["error","warn","info","debug"]}
        ]""",
        ),
    )

    val online = MainUiState(
        host = "192.168.1.50", online = true, everConnected = true, status = status, pingText = "4 ms",
        settings = settings,
    )
}
