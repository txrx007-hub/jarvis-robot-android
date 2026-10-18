package com.jarvis.robot

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The HTTP calls against a fake robot: right paths, methods, bodies and reply handling. */
class RobotApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: RobotApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = RobotApi("${server.hostName}:${server.port}")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun statusIsAGet() {
        server.enqueue(json("""{"board":"wall-e-xiao/2.5.0","board_name":"ESP32 S3 Sense","uptime_s":7,"state":5}"""))
        val s = api.status()
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/api/status", req.path)
        assertEquals("ESP32 S3 Sense", s.boardName)
        assertEquals("Listening", statusName(s))
    }

    @Test
    fun savePostsKeyAndStringValueAndReadsTheUpdatedSetting() {
        server.enqueue(
            json(
                """{"ok":true,"message":"wake_threshold set to 0.55","settings":[
                {"key":"wake_threshold","kind":"hundredths","help":"w","min":0.5,"max":0.95,"step":0.01,"value":0.55}]}""",
            ),
        )
        val r = api.save("wake_threshold", "0.55")
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/settings", req.path)
        val sent = JSONObject(req.body.readUtf8())
        assertEquals("wake_threshold", sent.getString("key"))
        assertEquals("0.55", sent.getString("value")) // the robot wants a string, not a number
        assertTrue(r.ok)
        assertEquals("0.55", r.updated?.value)
    }

    @Test
    fun rejectedSettingReportsTheRobotsMessage() {
        server.enqueue(json("""{"ok":false,"message":"wake_threshold must be 0.50 to 0.95"}"""))
        val r = api.save("wake_threshold", "2")
        assertFalse(r.ok)
        assertEquals("wake_threshold must be 0.50 to 0.95", r.message)
        assertEquals(null, r.updated)
    }

    @Test
    fun driveIsAPostWithBothWheelsAndClampsToTheRange() {
        server.enqueue(MockResponse().setBody(""))
        api.drive(100, -35)
        server.enqueue(MockResponse().setBody(""))
        api.drive(500, -500)
        assertEquals("/api/drive?l=100&r=-35", server.takeRequest().path)
        val second = server.takeRequest()
        assertEquals("POST", second.method)
        assertEquals("/api/drive?l=100&r=-100", second.path)
    }

    @Test
    fun powerActions() {
        server.enqueue(json("""{"ok":true,"message":"Napping."}"""))
        server.enqueue(json("""{"ok":true,"message":"Waking up."}"""))
        server.enqueue(json("""{"ok":true,"message":"Deep sleep in 2 s."}"""))
        assertEquals("Napping.", api.power("nap").message)
        assertEquals("Waking up.", api.power("wake").message)
        assertTrue(api.power("deepsleep", 205).ok)
        assertEquals("/api/power?action=nap", server.takeRequest().path)
        assertEquals("/api/power?action=wake", server.takeRequest().path)
        assertEquals("/api/power?action=deepsleep&minutes=205", server.takeRequest().path)
    }

    @Test
    fun resetIsAPost() {
        server.enqueue(json("""{"ok":true,"message":"All settings reset to defaults."}"""))
        assertTrue(api.reset())
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/reset", req.path)
    }

    @Test
    fun pingMeasuresTheRoundTrip() {
        server.enqueue(MockResponse().setBody(""))
        val ms = api.ping()
        assertTrue(ms in 0..1500)
        assertEquals("/api/ping", server.takeRequest().path)
    }

    @Test(expected = java.io.IOException::class)
    fun httpErrorsSurfaceAsExceptions() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("bad"))
        api.drive(0, 0)
    }

    @Test
    fun scanRecognizesOnlyTheRobot() {
        val client = okhttp3.OkHttpClient()
        server.enqueue(json("""{"board":"wall-e-xiao/2.5.0","uptime_s":1,"state":3}"""))
        assertTrue(NetworkUtil.looksLikeRobot(client, "${server.hostName}:${server.port}"))
        server.enqueue(json("""{"hello":"some other web server"}"""))
        assertFalse(NetworkUtil.looksLikeRobot(client, "${server.hostName}:${server.port}"))
        server.enqueue(MockResponse().setResponseCode(404))
        assertFalse(NetworkUtil.looksLikeRobot(client, "${server.hostName}:${server.port}"))
    }
}
