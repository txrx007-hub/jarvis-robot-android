package com.jarvis.robot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAddressTest {
    @Test fun homeAndPrivateAddressesAreAccepted() {
        for (a in listOf("192.168.1.50", "192.168.1.19:80", "10.0.0.7", "172.16.4.2", "172.31.255.1", "100.64.1.2", "169.254.3.4", "jarvis.local", "jarvis", "robot.lan", "pc.tail1234.ts.net", "localhost:8080")) {
            assertTrue(a, NetworkUtil.isHomeAddress(a))
        }
    }

    @Test fun internetAddressesAreRefused() {
        for (a in listOf("8.8.8.8", "172.32.0.1", "172.15.0.1", "100.63.0.1", "100.128.0.1", "example.com", "www.google.com:80", "192.169.1.1", "", "  ")) {
            assertFalse(a, NetworkUtil.isHomeAddress(a))
        }
    }
}
