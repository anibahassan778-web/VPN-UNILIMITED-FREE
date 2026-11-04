package com.example.vpn

import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Statistics
import com.wireguard.crypto.Key

/**
 * Peer statistics model wrapping wireguard-android stats and handshake telemetry.
 */
data class PeerStatistics(
    val key: Key?,
    val rxBytes: Long,
    val txBytes: Long,
    val latestHandshakeEpochMillis: Long
)

/**
 * Helper to inspect the wireguard-go UAPI state directly from GoBackend to extract
 * real handshake timestamps (`last_handshake_time_sec`).
 */
object WireGuardStatsHelper {

    /**
     * Reads wireguard-go UAPI configuration text via GoBackend's internal wgGetConfig native handle.
     */
    fun getUapiConfig(backend: GoBackend): String? {
        return try {
            val handleField = GoBackend::class.java.getDeclaredField("currentTunnelHandle")
            handleField.isAccessible = true
            val handle = handleField.getInt(backend)
            if (handle == -1) return null
            val method = GoBackend::class.java.getDeclaredMethod("wgGetConfig", Int::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(null, handle) as? String
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts last_handshake_time_sec from wireguard-go UAPI for a given public key.
     */
    fun getLatestHandshakeEpochMillis(backend: GoBackend, targetKeyHexOrBase64: String): Long {
        val uapi = getUapiConfig(backend) ?: return 0L
        return parseHandshakeFromUapi(uapi, targetKeyHexOrBase64)
    }

    fun parseHandshakeFromUapi(uapi: String, targetKeyHexOrBase64: String): Long {
        return try {
            val targetKeyHex = try {
                if (targetKeyHexOrBase64.length == 44 && targetKeyHexOrBase64.endsWith("=")) {
                    Key.fromBase64(targetKeyHexOrBase64).toHex().lowercase()
                } else {
                    targetKeyHexOrBase64.lowercase()
                }
            } catch (_: Exception) {
                targetKeyHexOrBase64.lowercase()
            }

            var currentPeerMatches = false
            var handshakeEpochMillis = 0L

            for (line in uapi.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("public_key=")) {
                    val keyHex = trimmed.substring(11).trim().lowercase()
                    currentPeerMatches = (keyHex == targetKeyHex)
                } else if (currentPeerMatches && trimmed.startsWith("last_handshake_time_sec=")) {
                    val secStr = trimmed.substring(24).trim()
                    val sec = secStr.toLongOrNull() ?: 0L
                    if (sec > 0L) {
                        handshakeEpochMillis = sec * 1000L
                    }
                }
            }
            handshakeEpochMillis
        } catch (_: Exception) {
            0L
        }
    }
}

/**
 * Extension method on Statistics to look up a peer by Key or Base64 string and obtain
 * rx/tx and handshake timestamp.
 */
fun Statistics.peer(key: Key, backend: GoBackend? = null): PeerStatistics {
    val rx = peerRx(key)
    val tx = peerTx(key)
    val handshake = if (backend != null) {
        WireGuardStatsHelper.getLatestHandshakeEpochMillis(backend, key.toHex())
    } else {
        if (rx > 0L) System.currentTimeMillis() else 0L
    }
    return PeerStatistics(key, rx, tx, handshake)
}

fun Statistics.peer(serverPublicKeyBase64: String, backend: GoBackend? = null): PeerStatistics {
    val key = try { Key.fromBase64(serverPublicKeyBase64) } catch (_: Exception) { null }
    return if (key != null) {
        peer(key, backend)
    } else {
        val handshake = if (backend != null) {
            WireGuardStatsHelper.getLatestHandshakeEpochMillis(backend, serverPublicKeyBase64)
        } else {
            if (totalRx() > 0L) System.currentTimeMillis() else 0L
        }
        PeerStatistics(null, totalRx(), totalTx(), handshake)
    }
}
