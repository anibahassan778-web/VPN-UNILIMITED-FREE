package com.example.model

import java.util.Locale

enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    RECONNECTING,
    ERROR,
    BLOCKED // Emergency Internet Block Active (Kill Switch engaged due to drop)
}

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

data class NetworkMonitorState(
    val isMonitoring: Boolean = false,
    val isDropDetected: Boolean = false,
    val isTrafficBlocked: Boolean = false,
    val dropReason: String? = null,
    val lastDropTimestamp: Long = 0L,
    val totalDropsBlocked: Int = 0,
    val latencyMs: Int = -1,
    val isServerReachable: Boolean = true
)

data class VpnServer(
    val id: String,
    val name: String,
    val country: String,
    val countryCode: String,
    val city: String,
    val endpointHost: String,
    val endpointPort: Int,
    val publicKey: String,
    val presharedKey: String? = null,
    val clientIp: String = "10.66.66.2/32",
    val dnsServers: String = "1.1.1.1, 1.0.0.1",
    val pingMs: Int = -1,
    val loadPercent: Int = 20,
    val isCustom: Boolean = false,
    val isOnline: Boolean = true
) {
    val flagEmoji: String
        get() {
            if (countryCode.length != 2) return "🌐"
            val firstChar = Character.codePointAt(countryCode.uppercase(Locale.ROOT), 0) - 0x41 + 0x1F1E6
            val secondChar = Character.codePointAt(countryCode.uppercase(Locale.ROOT), 1) - 0x41 + 0x1F1E6
            return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
        }

    val endpointDisplay: String
        get() = "$endpointHost:$endpointPort"
}

data class VpnStats(
    val durationSeconds: Long = 0,
    val txBytes: Long = 0,
    val rxBytes: Long = 0,
    val txSpeedBps: Long = 0,
    val rxSpeedBps: Long = 0,
    val currentPublicIp: String? = null
) {
    val formattedDuration: String
        get() {
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        }

    val formattedDownloadSpeed: String
        get() = formatSpeed(rxSpeedBps)

    val formattedUploadSpeed: String
        get() = formatSpeed(txSpeedBps)

    val formattedTotalDownload: String
        get() = formatBytes(rxBytes)

    val formattedTotalUpload: String
        get() = formatBytes(txBytes)

    companion object {
        fun formatSpeed(bytesPerSec: Long): String {
            return when {
                bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
                bytesPerSec >= 1024 -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1024.0)
                else -> "$bytesPerSec B/s"
            }
        }

        fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
                bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
                bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
                else -> "$bytes B"
            }
        }
    }
}

data class VpnSettings(
    val killSwitchEnabled: Boolean = false,
    val strictKillSwitchWatchdog: Boolean = true,
    val dnsLeakProtection: Boolean = true,
    val ipv6LeakProtection: Boolean = true,
    val autoConnectOnLaunch: Boolean = false,
    val autoReconnectOnNetworkSwitch: Boolean = true,
    val customDns: String = "",
    val mtu: Int = 1420,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val backendApiUrl: String = "http://10.0.2.2:3000",
    val selectedServerId: String = "srv-de-fra"
)

data class WireGuardKeyPair(
    val privateKey: String,
    val publicKey: String
)

data class WireGuardConfig(
    val privateKey: String,
    val address: String,
    val dns: String,
    val mtu: Int = 1420,
    val peerPublicKey: String,
    val peerEndpoint: String,
    val peerAllowedIps: String = "0.0.0.0/0, ::/0",
    val presharedKey: String? = null,
    val persistentKeepalive: Int = 25
) {
    fun toConfigFile(): String {
        val sb = StringBuilder()
        sb.append("[Interface]\n")
        sb.append("PrivateKey = ").append(privateKey).append("\n")
        sb.append("Address = ").append(address).append("\n")
        sb.append("DNS = ").append(dns).append("\n")
        sb.append("MTU = ").append(mtu).append("\n\n")

        sb.append("[Peer]\n")
        sb.append("PublicKey = ").append(peerPublicKey).append("\n")
        if (!presharedKey.isNullOrBlank()) {
            sb.append("PresharedKey = ").append(presharedKey).append("\n")
        }
        sb.append("Endpoint = ").append(peerEndpoint).append("\n")
        sb.append("AllowedIPs = ").append(peerAllowedIps).append("\n")
        sb.append("PersistentKeepalive = ").append(persistentKeepalive).append("\n")
        return sb.toString()
    }

    companion object {
        fun parse(raw: String): WireGuardConfig? {
            try {
                var privateKey = ""
                var address = "10.66.66.2/32"
                var dns = "1.1.1.1, 1.0.0.1"
                var mtu = 1420
                var peerPublicKey = ""
                var peerEndpoint = ""
                var peerAllowedIps = "0.0.0.0/0, ::/0"
                var presharedKey: String? = null
                var keepalive = 25

                val lines = raw.lines()
                var inInterface = false
                var inPeer = false

                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#") || trimmed.isEmpty()) continue
                    if (trimmed.equals("[Interface]", ignoreCase = true)) {
                        inInterface = true
                        inPeer = false
                        continue
                    }
                    if (trimmed.equals("[Peer]", ignoreCase = true)) {
                        inInterface = false
                        inPeer = true
                        continue
                    }

                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size != 2) continue
                    val key = parts[0].trim()
                    val value = parts[1].trim()

                    if (inInterface) {
                        when (key.lowercase(Locale.ROOT)) {
                            "privatekey" -> privateKey = value
                            "address" -> address = value
                            "dns" -> dns = value
                            "mtu" -> mtu = value.toIntOrNull() ?: 1420
                        }
                    } else if (inPeer) {
                        when (key.lowercase(Locale.ROOT)) {
                            "publickey" -> peerPublicKey = value
                            "presharedkey" -> presharedKey = value
                            "endpoint" -> peerEndpoint = value
                            "allowedips" -> peerAllowedIps = value
                            "persistentkeepalive" -> keepalive = value.toIntOrNull() ?: 25
                        }
                    }
                }

                if (peerPublicKey.isNotBlank() && peerEndpoint.isNotBlank()) {
                    return WireGuardConfig(
                        privateKey = privateKey,
                        address = address,
                        dns = dns,
                        mtu = mtu,
                        peerPublicKey = peerPublicKey,
                        peerEndpoint = peerEndpoint,
                        peerAllowedIps = peerAllowedIps,
                        presharedKey = presharedKey,
                        persistentKeepalive = keepalive
                    )
                }
            } catch (_: Exception) {
                return null
            }
            return null
        }
    }
}
