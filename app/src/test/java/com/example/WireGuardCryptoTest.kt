package com.example

import com.example.crypto.WireGuardCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardCryptoTest {

    @Test
    fun testKeyPairGeneration() {
        val keyPair = WireGuardCrypto.generateKeyPair()
        assertNotNull(keyPair.privateKey)
        assertNotNull(keyPair.publicKey)
        assertTrue(keyPair.privateKey.isNotEmpty())
        assertTrue(keyPair.publicKey.isNotEmpty())

        // WireGuard Base64 32-byte key is 44 characters ending with '=' or valid base64
        assertEquals(44, keyPair.privateKey.length)
        assertEquals(44, keyPair.publicKey.length)

        assertTrue(WireGuardCrypto.isValidKey(keyPair.privateKey))
        assertTrue(WireGuardCrypto.isValidKey(keyPair.publicKey))
    }

    @Test
    fun testPresharedKeyGeneration() {
        val psk = WireGuardCrypto.generatePresharedKey()
        assertNotNull(psk)
        assertEquals(44, psk.length)
        assertTrue(WireGuardCrypto.isValidKey(psk))
    }

    @Test
    fun testPublicKeyDerivationFromPrivate() {
        val keyPair = WireGuardCrypto.generateKeyPair()
        val derivedPublic = WireGuardCrypto.generatePublicKeyFromPrivate(keyPair.privateKey)
        assertNotNull(derivedPublic)
        assertEquals(keyPair.publicKey, derivedPublic)
    }

    @Test
    fun testInvalidKeyValidation() {
        assertFalse(WireGuardCrypto.isValidKey(null))
        assertFalse(WireGuardCrypto.isValidKey(""))
        assertFalse(WireGuardCrypto.isValidKey("invalid-key"))
        assertFalse(WireGuardCrypto.isValidKey("SGVsbG8=")) // Too short (not 32 bytes)
    }
}
