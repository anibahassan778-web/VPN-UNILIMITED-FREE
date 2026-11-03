package com.example

import com.example.model.VpnServer
import com.example.model.VpnSettings
import com.example.model.VpnStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnStatsAndLeakPolicyTest {

    @Test
    fun testFormattedDuration() {
        val stats1 = VpnStats(durationSeconds = 0)
        assertEquals("00:00:00", stats1.formattedDuration)

        val stats2 = VpnStats(durationSeconds = 65)
        assertEquals("00:01:05", stats2.formattedDuration)

        val stats3 = VpnStats(durationSeconds = 3661)
        assertEquals("01:01:01", stats3.formattedDuration)
    }

    @Test
    fun testFormatBytesAndSpeed() {
        assertEquals("500 B", VpnStats.formatBytes(500))
        assertEquals("1.0 KB", VpnStats.formatBytes(1024))
        assertEquals("1.0 MB", VpnStats.formatBytes(1024 * 1024))
        assertEquals("2.00 GB", VpnStats.formatBytes(2L * 1024 * 1024 * 1024))

        assertEquals("500 B/s", VpnStats.formatSpeed(500))
        assertEquals("2.5 KB/s", VpnStats.formatSpeed(2560))
        assertEquals("3.0 MB/s", VpnStats.formatSpeed(3 * 1024 * 1024))
    }

    @Test
    fun testLeakProtectionSettingsDefaults() {
        val settings = VpnSettings()
        // DNS leak and IPv6 leak protection should be safely enabled by default
        assertTrue(settings.dnsLeakProtection)
        assertTrue(settings.ipv6LeakProtection)
        // Kill switch is optional/user-toggleable
        assertFalse(settings.killSwitchEnabled)
    }

    @Test
    fun testServerFlagEmoji() {
        val serverDe = VpnServer(
            id = "de-1",
            name = "Germany Node",
            country = "Germany",
            countryCode = "DE",
            city = "Frankfurt",
            endpointHost = "de.example.com",
            endpointPort = 51820,
            publicKey = "key="
        )
        // Flag for DE should be 🇩🇪
        assertEquals("🇩🇪", serverDe.flagEmoji)

        val serverUs = serverDe.copy(countryCode = "US")
        assertEquals("🇺🇸", serverUs.flagEmoji)
    }

    @Test
    fun testNetworkMonitorStateAndDropDetection() {
        val defaultState = com.example.model.NetworkMonitorState()
        assertFalse(defaultState.isMonitoring)
        assertFalse(defaultState.isDropDetected)
        assertFalse(defaultState.isTrafficBlocked)
        assertEquals(0, defaultState.totalDropsBlocked)

        val droppedState = defaultState.copy(
            isDropDetected = true,
            isTrafficBlocked = true,
            dropReason = "Socket closed unexpectedly",
            totalDropsBlocked = 1
        )
        assertTrue(droppedState.isDropDetected)
        assertTrue(droppedState.isTrafficBlocked)
        assertEquals("Socket closed unexpectedly", droppedState.dropReason)
        assertEquals(1, droppedState.totalDropsBlocked)

        // Verify VpnState.BLOCKED exists and can be distinguished from DISCONNECTED
        val blockedState = com.example.model.VpnState.BLOCKED
        assertEquals("BLOCKED", blockedState.name)
    }
}
