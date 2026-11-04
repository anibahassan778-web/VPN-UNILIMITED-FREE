package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ServerRepository
import com.example.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("WireShield VPN", appName)
    }

    @Test
    fun `settings repository initializes correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settingsRepo = SettingsRepository(context)
        val settings = settingsRepo.settings.value
        assertNotNull(settings)
        assertTrue(settings.dnsLeakProtection)
        assertTrue(settings.ipv6LeakProtection)

        val keyPair = settingsRepo.getOrCreateClientKeyPair()
        assertNotNull(keyPair.privateKey)
        assertNotNull(keyPair.publicKey)
        assertEquals(44, keyPair.publicKey.length)
    }

    @Test
    fun `server repository initializes empty and supports adding custom servers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serverRepo = ServerRepository(context)
        serverRepo.resetToDefaults()
        val servers = serverRepo.servers.value
        // All fake servers are eliminated; initial server list is empty until user imports a .conf
        assertTrue(servers.isEmpty())

        val customServer = com.example.model.VpnServer(
            id = "test-imported-1",
            name = "Test Imported WG Node",
            country = "Custom",
            countryCode = "UN",
            city = "VPS",
            endpointHost = "203.0.113.5",
            endpointPort = 51820,
            publicKey = "0000000000000000000000000000000000000000000=",
            clientPrivateKey = "1111111111111111111111111111111111111111111=",
            clientIp = "10.66.66.2/32",
            dnsServers = "1.1.1.1",
            isCustom = true
        )
        serverRepo.addCustomServer(customServer)
        val updatedServers = serverRepo.servers.value
        assertEquals(1, updatedServers.size)
        assertEquals("test-imported-1", updatedServers.first().id)
        assertEquals("1111111111111111111111111111111111111111111=", updatedServers.first().clientPrivateKey)

        serverRepo.removeCustomServer("test-imported-1")
        assertTrue(serverRepo.servers.value.isEmpty())
    }
}
