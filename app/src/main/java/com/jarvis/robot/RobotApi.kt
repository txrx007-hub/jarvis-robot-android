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

    /** action = nap | wake | deepsleep (minutes 0 = until the BOOT button, max 1440). */
    fun power(action: String, minutes: Int = 0): PowerResult {
        val q = if (action == "deepsleep") "action=deepsleep&minutes=$minutes" else "action=$action"
        val o = JSONObject(post("/api/power?$q"))
        return PowerResult(o.optBoolean("ok"), o.optString("message", ""))
    }
}
