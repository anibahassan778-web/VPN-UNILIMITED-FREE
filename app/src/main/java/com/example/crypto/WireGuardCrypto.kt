package com.example.crypto

import android.util.Base64
import com.example.model.WireGuardKeyPair
import java.security.SecureRandom
import java.util.Arrays

object WireGuardCrypto {

    private val secureRandom = SecureRandom()

    /**
     * Generates a new cryptographically secure Curve25519 keypair for WireGuard.
     */
    fun generateKeyPair(): WireGuardKeyPair {
        val privateKeyBytes = ByteArray(32)
        secureRandom.nextBytes(privateKeyBytes)
        clampPrivateKey(privateKeyBytes)

        val publicKeyBytes = curve25519Base(privateKeyBytes)

        val privateKeyB64 = encodeBase64(privateKeyBytes)
        val publicKeyB64 = encodeBase64(publicKeyBytes)

        return WireGuardKeyPair(
            privateKey = privateKeyB64,
            publicKey = publicKeyB64
        )
    }

    /**
     * Generates an optional 32-byte pre-shared key (PSK) for post-quantum resistance.
     */
    fun generatePresharedKey(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return encodeBase64(bytes)
    }

    /**
     * Generates a public key from an existing Base64-encoded private key.
     */
    fun generatePublicKeyFromPrivate(privateKeyBase64: String): String? {
        return try {
            val privateKeyBytes = decodeBase64(privateKeyBase64)
            if (privateKeyBytes.size != 32) return null
            clampPrivateKey(privateKeyBytes)
            val publicKeyBytes = curve25519Base(privateKeyBytes)
            encodeBase64(publicKeyBytes)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Validates whether a Base64 string is a valid 32-byte WireGuard key.
     */
    fun isValidKey(keyBase64: String?): Boolean {
        if (keyBase64.isNullOrBlank()) return false
        return try {
            val decoded = decodeBase64(keyBase64.trim())
            decoded.size == 32
        } catch (_: Exception) {
            false
        }
    }

    private fun clampPrivateKey(key: ByteArray) {
        key[0] = (key[0].toInt() and 248).toByte()
        key[31] = (key[31].toInt() and 127).toByte()
        key[31] = (key[31].toInt() or 64).toByte()
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Throwable) {
            // Fallback for JVM unit tests without android.util.Base64 mock
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            Base64.decode(str, Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(str)
        }
    }

    // -------------------------------------------------------------
    // RFC 7748 Curve25519 Montgomery Ladder implementation
    // -------------------------------------------------------------
    private fun curve25519Base(scalar: ByteArray): ByteArray {
        val basePoint = ByteArray(32).apply { this[0] = 9 }
        return curve25519(scalar, basePoint)
    }

    fun curve25519(scalar: ByteArray, point: ByteArray): ByteArray {
        val clamped = Arrays.copyOf(scalar, 32)
        clampPrivateKey(clamped)

        val x1 = decodeUCoordinate(point)
        val x2 = LongArray(10).apply { this[0] = 1 }
        val z2 = LongArray(10)
        val x3 = Arrays.copyOf(x1, 10)
        val z3 = LongArray(10).apply { this[0] = 1 }

        var swap = 0
        for (pos in 254 downTo 0) {
            val bit = (clamped[pos / 8].toInt() ushr (pos % 8)) and 1
            swap = swap xor bit
            cswap(swap, x2, x3)
            cswap(swap, z2, z3)
            swap = bit

            val a = add(x2, z2)
            val aa = sqr(a)
            val b = sub(x2, z2)
            val bb = sqr(b)
            val e = sub(aa, bb)
            val c = add(x3, z3)
            val d = sub(x3, z3)
            val da = mul(d, a)
            val cb = mul(c, b)

            val newX3 = sqr(add(da, cb))
            val newZ3 = mul(x1, sqr(sub(da, cb)))
            val newX2 = mul(aa, bb)
            val newZ2 = mul(e, add(aa, mulScalar(e, 121665L)))

            System.arraycopy(newX2, 0, x2, 0, 10)
            System.arraycopy(newZ2, 0, z2, 0, 10)
            System.arraycopy(newX3, 0, x3, 0, 10)
            System.arraycopy(newZ3, 0, z3, 0, 10)
        }
        cswap(swap, x2, x3)
        cswap(swap, z2, z3)

        val result = mul(x2, inv(z2))
        return encodeUCoordinate(result)
    }

    private fun decodeUCoordinate(bytes: ByteArray): LongArray {
        val out = LongArray(10)
        for (i in 0 until 10) {
            val b0 = (bytes[(i * 26) / 8].toInt() and 0xFF).toLong()
            val b1 = (bytes.getOrElse((i * 26) / 8 + 1) { 0 }.toInt() and 0xFF).toLong()
            val b2 = (bytes.getOrElse((i * 26) / 8 + 2) { 0 }.toInt() and 0xFF).toLong()
            val b3 = (bytes.getOrElse((i * 26) / 8 + 3) { 0 }.toInt() and 0xFF).toLong()
            val shift = (i * 26) % 8
            val raw = (b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)) ushr shift
            out[i] = if (i % 2 == 0) raw and 0x3FFFFFFL else raw and 0x1FFFFFFL
        }
        return carry(out)
    }

    private fun encodeUCoordinate(elem: LongArray): ByteArray {
        val fullyReduced = carry(elem)
        val out = ByteArray(32)
        var bitBuf = 0L
        var bits = 0
        var byteIdx = 0
        for (i in 0 until 10) {
            bitBuf = bitBuf or (fullyReduced[i] shl bits)
            bits += if (i % 2 == 0) 26 else 25
            while (bits >= 8 && byteIdx < 32) {
                out[byteIdx++] = (bitBuf and 0xFF).toByte()
                bitBuf = bitBuf ushr 8
                bits -= 8
            }
        }
        return out
    }

    private fun cswap(swap: Int, a: LongArray, b: LongArray) {
        val mask = -swap.toLong()
        for (i in 0 until 10) {
            val t = mask and (a[i] xor b[i])
            a[i] = a[i] xor t
            b[i] = b[i] xor t
        }
    }

    private fun add(a: LongArray, b: LongArray): LongArray = LongArray(10) { a[it] + b[it] }

    private fun sub(a: LongArray, b: LongArray): LongArray = LongArray(10) { a[it] - b[it] + 0x7FFFFFFL }

    private fun mulScalar(a: LongArray, scalar: Long): LongArray = LongArray(10) { a[it] * scalar }

    private fun mul(a: LongArray, b: LongArray): LongArray {
        val res = LongArray(19)
        for (i in 0 until 10) {
            for (j in 0 until 10) {
                res[i + j] += a[i] * b[j]
            }
        }
        // Reduce mod 2^255 - 19
        for (i in 0 until 9) {
            res[i] += res[i + 10] * 19L
        }
        val out = LongArray(10) { res[it] }
        return carry(out)
    }

    private fun sqr(a: LongArray): LongArray = mul(a, a)

    private fun inv(z: LongArray): LongArray {
        // Fermat's Little Theorem: z^(p-2) mod p
        var t0 = sqr(z)
        var t1 = sqr(sqr(t0))
        t1 = mul(t1, z)
        t0 = mul(t0, t1)
        var t2 = sqr(t0)
        t1 = mul(t1, t2)
        t2 = sqr(t1)
        for (i in 1 until 5) t2 = sqr(t2)
        t1 = mul(t2, t1)
        t2 = sqr(t1)
        for (i in 1 until 10) t2 = sqr(t2)
        t2 = mul(t2, t1)
        var t3 = sqr(t2)
        for (i in 1 until 20) t3 = sqr(t3)
        t2 = mul(t3, t2)
        t2 = sqr(t2)
        for (i in 1 until 10) t2 = sqr(t2)
        t1 = mul(t2, t1)
        t2 = sqr(t1)
        for (i in 1 until 50) t2 = sqr(t2)
        t2 = mul(t2, t1)
        t3 = sqr(t2)
        for (i in 1 until 100) t3 = sqr(t3)
        t2 = mul(t3, t2)
        t2 = sqr(t2)
        for (i in 1 until 50) t2 = sqr(t2)
        t1 = mul(t2, t1)
        t1 = sqr(t1)
        for (i in 1 until 5) t1 = sqr(t1)
        return mul(t1, t0)
    }

    private fun carry(a: LongArray): LongArray {
        val out = Arrays.copyOf(a, 10)
        for (round in 0 until 2) {
            for (i in 0 until 9) {
                val maxBits = if (i % 2 == 0) 26 else 25
                val c = out[i] shr maxBits
                out[i] -= c shl maxBits
                out[i + 1] += c
            }
            val c = out[9] shr 25
            out[9] -= c shl 25
            out[0] += c * 19L
        }
        return out
    }
}
