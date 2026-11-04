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

enum class VpnProfileMode(val title: String, val subtitle: String, val iconEmoji: String) {
    BALANCED("Balanced Shield", "Optimized WireGuard for daily browsing & privacy", "🛡️"),
    STEALTH("Stealth Mode", "Anti-DPI packet obfuscation for restricted networks", "🕵️"),
    GAMING("Ultra Gaming", "Minimized ping buffer, jitter dampening & lowest latency", "🎮"),
    STREAMING("Media Streaming", "Maximum MTU throughput for 4K video playback", "🎬"),
    TORRENT_P2P("P2P High-Speed", "High concurrent sockets with port forwarding simulation", "⚡"),
    BATTERY_SAVER("Battery Saver", "Smart idle sleep & reduced persistent keepalive", "🔋")
}

enum class DnsShieldMode(val title: String, val primaryDns: String, val secondaryDns: String, val description: String) {
    ADBLOCK("Ad & Tracker Shield", "94.140.14.14", "94.140.15.15", "Blocks intrusive ads, mobile analytics & tracking scripts"),
    MALWARE("Malware Protection", "9.9.9.9", "149.112.112.112", "Blocks phishing, ransomware, botnets & malicious domains"),
    FAMILY_SAFE("Family Protection", "185.228.168.168", "185.228.169.168", "Blocks adult content & enforces Google/Bing SafeSearch"),
    CLOUDFLARE_FAST("Ultra Fast (1.1.1.1)", "1.1.1.1", "1.0.0.1", "Fastest worldwide response times with zero DNS logging"),
    CUSTOM("Custom DNS", "", "", "User-specified custom upstream DNS resolver")
}

data class SpeedTestResult(
    val isTesting: Boolean = false,
    val pingMs: Int = -1,
    val jitterMs: Int = -1,
    val downloadSpeedBps: Long = 0,
    val uploadSpeedBps: Long = 0,
    val packetLossPercent: Int = 0,
    val testTimestamp: Long = 0L
) {
    val formattedDownload: String get() = VpnStats.formatSpeed(downloadSpeedBps)
    val formattedUpload: String get() = VpnStats.formatSpeed(uploadSpeedBps)
}

data class LeakTestResult(
    val isTesting: Boolean = false,
    val detectedDns: String? = null,
    val detectedIp: String? = null,
    val isDnsLeaking: Boolean = false,
    val isWebRtcLeaking: Boolean = false,
    val isProtected: Boolean = true
)

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
    val clientPrivateKey: String? = null,
    val clientIp: String = "10.66.66.2/32",
    val dnsServers: String = "1.1.1.1, 1.0.0.1",
    val pingMs: Int = -1,
    val loadPercent: Int = 0,
    val isCustom: Boolean = false,
    val isOnline: Boolean = true,
    val isMultiHopEligible: Boolean = true
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
    val currentPublicIp: String? = null,
    val adsBlockedCount: Long = 0
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
    val selectedServerId: String = "",
    // V2 Advanced Features
    val profileMode: VpnProfileMode = VpnProfileMode.BALANCED,
    val dnsShieldMode: DnsShieldMode = DnsShieldMode.ADBLOCK,
    val obfuscationEnabled: Boolean = false,
    val bypassLocalNetwork: Boolean = true,
    val multiHopEnabled: Boolean = false,
    val multiHopExitServerId: String = "",
    val splitTunnelEnabled: Boolean = false,
    val splitTunnelExcludedPackages: Set<String> = emptySet()
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
                val addresses = mutableListOf<String>()
                val dnsList = mutableListOf<String>()
                var mtu = 1420
                var peerPublicKey = ""
                var peerEndpoint = ""
                val allowedIpsList = mutableListOf<String>()
                var presharedKey: String? = null
                var keepalive = 25

                val lines = raw.lines()
                var inInterface = false
                var inPeer = false

                for (rawLine in lines) {
                    // Strip comments starting with # or ;
                    val line = rawLine.substringBefore('#').substringBefore(';').trim()
                    if (line.isEmpty()) continue

                    if (line.equals("[Interface]", ignoreCase = true)) {
                        inInterface = true
                        inPeer = false
                        continue
                    }
                    if (line.equals("[Peer]", ignoreCase = true)) {
                        inInterface = false
                        inPeer = true
                        continue
                    }

                    val parts = line.split("=", limit = 2)
                    if (parts.size != 2) continue
                    val key = parts[0].trim().lowercase(Locale.ROOT)
                    val value = parts[1].trim()

                    if (inInterface) {
                        when (key) {
                            "privatekey" -> privateKey = value
                            "address" -> {
                                value.split(",").forEach { addr ->
                                    val cleaned = addr.trim()
                                    if (cleaned.isNotBlank()) addresses.add(cleaned)
                                }
                            }
                            "dns" -> {
                                value.split(",").forEach { dns ->
                                    val cleaned = dns.trim()
                                    if (cleaned.isNotBlank()) dnsList.add(cleaned)
                                }
                            }
                            "mtu" -> mtu = value.toIntOrNull() ?: 1420
                        }
                    } else if (inPeer) {
                        when (key) {
                            "publickey" -> peerPublicKey = value
                            "presharedkey" -> presharedKey = value
                            "endpoint" -> peerEndpoint = value
                            "allowedips" -> {
                                value.split(",").forEach { ip ->
                                    val cleaned = ip.trim()
                                    if (cleaned.isNotBlank()) allowedIpsList.add(cleaned)
                                }
                            }
                            "persistentkeepalive" -> keepalive = value.toIntOrNull() ?: 25
                        }
                    }
                }

                if (peerPublicKey.isNotBlank() && peerEndpoint.isNotBlank()) {
                    val finalAddress = if (addresses.isNotEmpty()) addresses.joinToString(", ") else "10.66.66.2/32"
                    val finalDns = if (dnsList.isNotEmpty()) dnsList.joinToString(", ") else "1.1.1.1, 1.0.0.1"
                    val finalAllowedIps = if (allowedIpsList.isNotEmpty()) allowedIpsList.joinToString(", ") else "0.0.0.0/0, ::/0"

                    return WireGuardConfig(
                        privateKey = privateKey,
                        address = finalAddress,
                        dns = finalDns,
                        mtu = mtu,
                        peerPublicKey = peerPublicKey,
                        peerEndpoint = peerEndpoint,
                        peerAllowedIps = finalAllowedIps,
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
