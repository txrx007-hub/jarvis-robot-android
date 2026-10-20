package com.jarvis.robot

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Cleans what the user typed: strips a scheme and any path, keeps host[:port]. */
fun normalizeHost(input: String): String =
    input.trim().removePrefix("http://").removePrefix("https://").substringBefore('/').trim()

/**
 * The robot's HTTP API (the same endpoints the landing page uses). All calls block, so call them
 * from a background dispatcher. Timeouts are short on purpose: the robot's WiFi is weak, and the
 * page's drive keep-alive and ping must fail fast instead of piling up.
 */
class RobotApi(host: String) {
    var host: String = normalizeHost(host)

    private val normal: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(4000, TimeUnit.MILLISECONDS)
        .callTimeout(6000, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val fast: OkHttpClient = normal.newBuilder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .callTimeout(2000, TimeUnit.MILLISECONDS)
        .build()

    // A photo takes the robot a second or two (camera, JPEG encoder, then the weak WiFi).
    private val slow: OkHttpClient = normal.newBuilder()
        .readTimeout(12000, TimeUnit.MILLISECONDS)
        .callTimeout(15000, TimeUnit.MILLISECONDS)
        .build()

    private fun url(path: String) = "http://$host$path"

    private fun call(client: OkHttpClient, request: Request): String =
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            r.body?.string() ?: ""
        }

    private fun get(path: String, client: OkHttpClient = normal): String =
        call(client, Request.Builder().url(url(path)).get().build())

    private fun post(path: String, body: RequestBody? = null, client: OkHttpClient = normal): String =
        call(client, Request.Builder().url(url(path)).post(body ?: ByteArray(0).toRequestBody(null)).build())

    /** One picture from the robot's camera as JPEG bytes (the landing page's Camera button). Nothing is analysed or sent anywhere. */
    fun photo(): ByteArray =
        slow.newCall(Request.Builder().url(url("/api/photo")).post(ByteArray(0).toRequestBody(null)).build()).execute().use { r ->
            if (!r.isSuccessful) {
                val why = r.body?.string().orEmpty().trim()
                throw java.io.IOException(if (why.isNotEmpty() && why.length < 120) why else "HTTP ${r.code}")
            }
            r.body?.bytes() ?: ByteArray(0)
        }

    fun status(): Status = parseStatus(JSONObject(get("/api/status")))

    fun settings(): List<SettingDef> = parseSettings(JSONArray(get("/api/settings")))

    fun save(key: String, value: String): SaveResult {
        val body = JSONObject().put("key", key).put("value", value).toString()
            .toRequestBody("application/json".toMediaType())
        val o = JSONObject(post("/api/settings", body))
        val updated = o.optJSONArray("settings")?.optJSONObject(0)?.let { parseSetting(it) }
        return SaveResult(o.optBoolean("ok"), o.optString("message", ""), updated)
    }

    fun reset(): Boolean = JSONObject(post("/api/reset")).optBoolean("ok")

    /** Round-trip time in ms of an empty reply (what the page's ping row measures). */
    fun ping(): Long {
        val start = System.nanoTime()
        get("/api/ping", fast)
        return (System.nanoTime() - start) / 1_000_000
    }

    /** One wheel command; the robot stops by itself 500 ms later unless it is refreshed. */
    fun drive(left: Int, right: Int) {
        post("/api/drive?l=${left.coerceIn(-100, 100)}&r=${right.coerceIn(-100, 100)}", client = fast)
    }

    /** Requested speaker volume 0..100 (night mode may still hold the played volume lower). */
    fun setVolume(value: Int): VolumeResult {
        val o = JSONObject(post("/api/volume?value=${value.coerceIn(0, 100)}"))
        return VolumeResult(o.optBoolean("ok"), o.optInt("volume", value), o.optInt("volume_requested", value), o.optInt("volume_cap", 100))
    }

    /** value = "default" (xiaozhi.me cloud) or a LAN address like 192.168.1.20[:port]. The robot restarts about 2 s later. */
    fun setServer(value: String): PowerResult {
        val body = JSONObject().put("server", value).toString().toRequestBody("application/json".toMediaType())
        val o = JSONObject(post("/api/server", body))
        return PowerResult(o.optBoolean("ok"), o.optString("message", ""))
    }

    /**
     * The backend's PIN for other devices. The backend hands it only to pages opened from the robot (it checks the Referer),
     * so the request says it comes from the robot's page, exactly like the landing page does. "" when it is not offered.
     */
    fun backendPin(panelUrl: String): String {
        val base = if (panelUrl.endsWith("/")) panelUrl else "$panelUrl/"
        val req = Request.Builder().url(base + "pin.js").header("Referer", "http://$host/").get().build()
        // the reply is: window.JARVIS_PANEL_PIN = "123456";  (digits only; empty when the backend does not offer it)
        return call(normal, req).substringAfter("=").substringAfter('"').substringBefore('"').filter { it.isDigit() }
    }

    /**
     * Whether Home Assistant works from the backend (its /ha.js says {"ok": true}); asked like the PIN, as a page of the robot.
     * The Home Assistant commands are listed only while it does.
     */
    fun homeAssistantOk(panelUrl: String): Boolean {
        val base = if (panelUrl.endsWith("/")) panelUrl else "$panelUrl/"
        val req = Request.Builder().url(base + "ha.js").header("Referer", "http://$host/").get().build()
        return Regex("\"ok\"\\s*:\\s*true").containsMatchIn(call(normal, req))
    }

    /** action = nap | wake | deepsleep (minutes 0 = until the BOOT button, max 1440). */
    fun power(action: String, minutes: Int = 0): PowerResult {
        val q = if (action == "deepsleep") "action=deepsleep&minutes=$minutes" else "action=$action"
        val o = JSONObject(post("/api/power?$q"))
        return PowerResult(o.optBoolean("ok"), o.optString("message", ""))
    }
}
