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
    fun `server repository has default servers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serverRepo = ServerRepository(context)
        val servers = serverRepo.servers.value
        assertTrue(servers.isNotEmpty())
        assertTrue(servers.any { it.countryCode == "DE" })
    }
}
