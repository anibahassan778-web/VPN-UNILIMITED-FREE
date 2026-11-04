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
    fun testParseConfigFileWithComments() {
        val rawConf = """
            # WireGuard Configuration Exported from Provider
            ; Another comment line
            [interface] # Header inline comment
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa= # client key
            Address = 10.66.66.5/32, fd00::5/128 # dual stack address
            DNS = 1.1.1.1, 9.9.9.9
            MTU = 1360
            
            [peer]
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
            Endpoint = 198.51.100.1:51820 # server endpoint
            AllowedIPs = 0.0.0.0/0, ::/0
        """.trimIndent()

        val parsed = WireGuardConfig.parse(rawConf)
        assertNotNull(parsed)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=", parsed?.privateKey)
        assertEquals("10.66.66.5/32, fd00::5/128", parsed?.address)
        assertEquals("1.1.1.1, 9.9.9.9", parsed?.dns)
        assertEquals(1360, parsed?.mtu)
        assertEquals("198.51.100.1:51820", parsed?.peerEndpoint)
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

    @Test
    fun testOfficialWireGuardConfigBuilder() {
        val clientKeys = com.example.crypto.WireGuardCrypto.generateKeyPair()
        val serverKeys = com.example.crypto.WireGuardCrypto.generateKeyPair()
        val psk = com.example.crypto.WireGuardCrypto.generatePresharedKey()

        val iface = com.wireguard.config.Interface.Builder()
            .parsePrivateKey(clientKeys.privateKey)
            .addAddress(com.wireguard.config.InetNetwork.parse("10.66.66.2/32"))
            .addDnsServer(java.net.InetAddress.getByName("1.1.1.1"))
            .setMtu(1420)
            .excludeApplications(listOf("com.example.other"))
            .build()

        val peer = com.wireguard.config.Peer.Builder()
            .parsePublicKey(serverKeys.publicKey)
            .parsePreSharedKey(psk)
            .setEndpoint(com.wireguard.config.InetEndpoint.parse("192.0.2.1:51820"))
            .addAllowedIp(com.wireguard.config.InetNetwork.parse("0.0.0.0/0"))
            .addAllowedIp(com.wireguard.config.InetNetwork.parse("::/0"))
            .setPersistentKeepalive(25)
            .build()

        val config = com.wireguard.config.Config.Builder()
            .setInterface(iface)
            .addPeer(peer)
            .build()

        assertNotNull(config)
        assertEquals(1420, config.`interface`.mtu.orElse(0))
        assertEquals(1, config.peers.size)
    }

    @Test
    fun testEndpointResolverIpDetection() {
        // Raw IPv4
        assertTrue(com.example.vpn.EndpointResolver.isIpAddress("192.0.2.1"))
        assertTrue(com.example.vpn.EndpointResolver.isIpAddress("10.0.0.1"))
        assertTrue(com.example.vpn.EndpointResolver.isIpAddress("127.0.0.1"))

        // Raw IPv6
        assertTrue(com.example.vpn.EndpointResolver.isIpAddress("2001:db8::1"))
        assertTrue(com.example.vpn.EndpointResolver.isIpAddress("[2001:db8::1]"))
        assertTrue(com.example.vpn.EndpointResolver.isIpAddress("::1"))

        // Hostnames
        org.junit.Assert.assertFalse(com.example.vpn.EndpointResolver.isIpAddress("vpn.example.com"))
        org.junit.Assert.assertFalse(com.example.vpn.EndpointResolver.isIpAddress("de-fra.wireshield-relay.net"))
        org.junit.Assert.assertFalse(com.example.vpn.EndpointResolver.isIpAddress(""))
    }

    @Test
    fun testEndpointResolverCaching() {
        com.example.vpn.EndpointResolver.clearCache()
        assertNull(com.example.vpn.EndpointResolver.getCachedIp("node.example.org"))

        com.example.vpn.EndpointResolver.putInCache("node.example.org", "198.51.100.42")
        assertEquals("198.51.100.42", com.example.vpn.EndpointResolver.getCachedIp("node.example.org"))

        com.example.vpn.EndpointResolver.clearCache()
        assertNull(com.example.vpn.EndpointResolver.getCachedIp("node.example.org"))
    }

    @Test
    fun testEndpointResolverCacheTtlAndEviction() {
        com.example.vpn.EndpointResolver.clearCache()
        val host = "server.vpn.internal"
        val ip = "203.0.113.19"

        // Fresh cache entry
        com.example.vpn.EndpointResolver.putInCache(host, ip, System.currentTimeMillis())
        assertEquals(ip, com.example.vpn.EndpointResolver.getCachedIp(host))

        // Eviction
        com.example.vpn.EndpointResolver.evict(host)
        assertNull(com.example.vpn.EndpointResolver.getCachedIp(host))

        // Expired cache entry (older than 10 minutes)
        val expiredTime = System.currentTimeMillis() - (11 * 60 * 1000L)
        com.example.vpn.EndpointResolver.putInCache(host, ip, expiredTime)
        assertNull(com.example.vpn.EndpointResolver.getCachedIp(host))
    }

    @Test
    fun testWireGuardUapiHandshakeParser() {
        val dummyUapi = """
            public_key=d18cfd7950c46b14d2417772591694f4a387515f2a1b926ff9be866b1859bf56
            endpoint=198.51.100.1:51820
            last_handshake_time_sec=1700000000
            last_handshake_time_nsec=0
            rx_bytes=1048576
            tx_bytes=2097152
        """.trimIndent()

        val epoch = com.example.vpn.WireGuardStatsHelper.parseHandshakeFromUapi(
            dummyUapi,
            "d18cfd7950c46b14d2417772591694f4a387515f2a1b926ff9be866b1859bf56"
        )
        assertEquals(1700000000000L, epoch)

        val nonExistentEpoch = com.example.vpn.WireGuardStatsHelper.parseHandshakeFromUapi(
            dummyUapi,
            "0000000000000000000000000000000000000000000000000000000000000000"
        )
        assertEquals(0L, nonExistentEpoch)
    }
}







