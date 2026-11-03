package com.example

import com.example.model.WireGuardConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardConfigTest {

    @Test
    fun testParseValidConfigFile() {
        val rawConf = """
            [Interface]
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
            Address = 10.66.66.5/32
            DNS = 1.1.1.1, 8.8.8.8
            MTU = 1420

            [Peer]
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
            PresharedKey = cccccccccccccccccccccccccccccccccccccccccccc=
            Endpoint = vpn.example.com:51820
            AllowedIPs = 0.0.0.0/0, ::/0
            PersistentKeepalive = 25
        """.trimIndent()

        val parsed = WireGuardConfig.parse(rawConf)
        assertNotNull(parsed)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=", parsed?.privateKey)
        assertEquals("10.66.66.5/32", parsed?.address)
        assertEquals("1.1.1.1, 8.8.8.8", parsed?.dns)
        assertEquals(1420, parsed?.mtu)
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=", parsed?.peerPublicKey)
        assertEquals("vpn.example.com:51820", parsed?.peerEndpoint)
        assertEquals("0.0.0.0/0, ::/0", parsed?.peerAllowedIps)
        assertEquals("cccccccccccccccccccccccccccccccccccccccccccc=", parsed?.presharedKey)
        assertEquals(25, parsed?.persistentKeepalive)
    }

    @Test
    fun testParseInvalidConfigFile() {
        val invalidConf = "some random non-wireguard text without peer or endpoint"
        val parsed = WireGuardConfig.parse(invalidConf)
        assertNull(parsed)
    }

    @Test
    fun testToConfigFileGeneration() {
        val config = WireGuardConfig(
            privateKey = "privKey123=",
            address = "10.0.0.2/32",
            dns = "1.1.1.1",
            mtu = 1280,
            peerPublicKey = "pubKey456=",
            peerEndpoint = "192.0.2.1:51820",
            peerAllowedIps = "0.0.0.0/0",
            persistentKeepalive = 25
        )

        val output = config.toConfigFile()
        assertTrue(output.contains("[Interface]"))
        assertTrue(output.contains("PrivateKey = privKey123="))
        assertTrue(output.contains("MTU = 1280"))
        assertTrue(output.contains("[Peer]"))
        assertTrue(output.contains("PublicKey = pubKey456="))
        assertTrue(output.contains("Endpoint = 192.0.2.1:51820"))
    }
}
