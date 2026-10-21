package com.jarvis.robot

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The calls to the backend's control panel (music status, Stop / Next): the sign-in through /auto and the retries. */
class PanelApiTest {
    private lateinit var robot: MockWebServer
    private lateinit var panel: MockWebServer
    private lateinit var api: RobotApi

    @Before
    fun setUp() {
        robot = MockWebServer().apply { start() }
        panel = MockWebServer().apply { start() }
        api = RobotApi("${robot.hostName}:${robot.port}")
    }

    @After
    fun tearDown() {
        robot.shutdown()
        panel.shutdown()
    }

    private val panelUrl get() = "http://${panel.hostName}:${panel.port}/"
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private val music = """{"enabled":true,"tracks":11,"now":{"title":"Pure Lounge Radio","artist":"radio"}}"""

    @Test
    fun musicStatusIsReadWhenSignedIn() {
        panel.enqueue(json(music))
        val m = api.musicStatus(panelUrl)!!
        assertTrue(m.enabled)
        assertEquals(11, m.tracks)
        assertEquals("Pure Lounge Radio", m.title)
        val req = panel.takeRequest()
        assertEquals("/api/music", req.path)
        assertEquals("close", req.getHeader("Connection"))   // the panel closes every connection: never ask for a reused one
    }

    @Test
    fun a401SignsInThroughAutoWithTheRobotAsReferrerAndAsksAgain() {
        panel.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"sign in with the PIN first"}"""))
        panel.enqueue(MockResponse().setResponseCode(303).setHeader("Location", "/").setHeader("Set-Cookie", "jarvis_panel=abc; Path=/"))
        panel.enqueue(MockResponse().setBody("<html>panel</html>"))
        panel.enqueue(json(music))
        val m = api.musicStatus(panelUrl)!!
        assertTrue(m.enabled)
        val paths = (1..4).map { panel.takeRequest() }
        assertEquals(listOf("/api/music", "/auto", "/", "/api/music"), paths.map { it.path })
        assertEquals("http://${robot.hostName}:${robot.port}/", paths[1].getHeader("Referer"))
        assertTrue(paths[3].getHeader("Cookie").orEmpty().contains("jarvis_panel=abc"))
    }

    @Test
    fun oneBrokenAnswerIsRetriedBeforeItCountsAsNoAnswer() {
        panel.enqueue(MockResponse().setResponseCode(500))
        panel.enqueue(json(music))
        assertTrue(api.musicStatus(panelUrl)!!.enabled)
    }
}
