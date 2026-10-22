package com.jarvis.robot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The demo build's pretend robot and backend, driven through the app's own RobotApi (no network is used). */
class DemoRobotTest {
    private fun api() = RobotApi("192.168.1.50", DemoRobot { byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2) })
    private val panel = "http://192.168.1.10:8090/"

    @Test
    fun statusIsAnOwnBackendRobotWithAPanel() {
        val s = api().status()
        assertTrue(s.serverCustom)
        assertEquals("http://192.168.1.10:8090/", panelUrlOf(s))
        assertTrue(hasPanel(s))
        assertFalse(s.napping)
    }

    @Test
    fun settingsAreSavedAndValidated() {
        val a = api()
        assertTrue(a.settings().isNotEmpty())
        assertTrue(a.save("motor_trim", "5").ok)
        assertFalse(a.save("motor_trim", "99").ok)   // out of range, like the robot
        assertTrue(a.reset())
    }

    @Test
    fun napAndVolumeChangeTheStatus() {
        val a = api()
        assertTrue(a.power("nap").ok)
        assertTrue(a.status().napping)
        assertTrue(a.power("wake").ok)
        assertEquals(30, a.setVolume(30).volume)
        assertEquals(30, a.status().volume)
    }

    @Test
    fun theBackendAnswersMusicPinAndHomeAssistant() {
        val a = api()
        val m = a.musicStatus(panel)
        assertNotNull(m)
        assertTrue(m!!.enabled)
        assertNotNull(m.title)
        assertTrue(a.homeAssistantOk(panel))
        assertEquals("123456", a.backendPin(panel))
        val first = m.title
        assertTrue(a.musicControl(panel, "next"))
        assertTrue(a.musicStatus(panel)!!.title != first)
        assertTrue(a.musicControl(panel, "stop"))
        assertNull(a.musicStatus(panel)!!.title)   // nothing playing until the demo starts a song again
        a.uploadPhoto(panel, byteArrayOf(1))        // the dcim call is accepted
    }

    @Test
    fun cameraBrightnessHasItsOwnGroup() {
        val groups = groupSettings(parseSettings(DemoRobot.defaultSettings()))
        val camera = groups.first { it.first.title == "Camera" }
        assertEquals(listOf("camera_brightness"), camera.second.map { it.key })
        assertEquals("Camera brightness", labelFor("camera_brightness"))
        assertTrue(groups.none { it.first.title == "Other" })   // nothing the robot reports lands in "Other"
    }

    @Test
    fun theCameraGivesAPicture() {
        assertEquals(4, api().photo().size)
    }

    @Test
    fun theLiveViewAsksForASmallPicture() {
        assertEquals(4, api().photo(live = true).size)
    }

    @Test
    fun pingAndDriveAreAnswered() {
        val a = api()
        assertTrue(a.ping() < 1000)
        a.drive(50, 50)
    }
}
