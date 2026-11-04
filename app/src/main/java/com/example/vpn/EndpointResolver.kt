package com.example.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves VPN server endpoints to IP addresses BEFORE establishing the tunnel.
 *
 * Physical Network Resolution:
 * Instead of cm.activeNetwork (which returns the VPN interface during reconnects and
 * causes routing loops), this resolver iterates cm.allNetworks to select underlying
 * physical interfaces (Wi-Fi, Cellular, Ethernet) that have internet capability.
 *
 * Resilience & Cache:
 * - 10-minute cache TTL prevents repeated DNS requests while keeping records fresh.
 * - evict(host) / clearCache() allow purging cached entries immediately after a handshake failure.
 */
object EndpointResolver {

    const val CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes TTL

    data class CacheEntry(
        val ip: String,
        val timestampMillis: Long
    )

    private val resolutionCache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Checks if a host string is a numeric IPv4 or IPv6 literal.
     */
    fun isIpAddress(host: String): Boolean {
        val trimmed = host.trim().removeSurrounding("[", "]")
        if (trimmed.isEmpty()) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.net.InetAddresses.isNumericAddress(trimmed)
            } else {
                trimmed.matches(Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")) || trimmed.contains(":")
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Resolves a server endpoint hostname to an IP address BEFORE establishing the tunnel.
     * Iterates cm.allNetworks to find physical networks without TRANSPORT_VPN.
     */
    suspend fun resolveEndpoint(context: Context, host: String): String? = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().removeSurrounding("[", "]")
        if (cleanHost.isEmpty()) return@withContext null

        // If host is already an IP, cache and return immediately
        if (isIpAddress(cleanHost)) {
            resolutionCache[cleanHost] = CacheEntry(cleanHost, System.currentTimeMillis())
            return@withContext cleanHost
        }

        // Check if we have a valid, unexpired cached IP
        val cached = resolutionCache[cleanHost]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.timestampMillis) < CACHE_TTL_MS) {
            VpnManager.log("[DNS] Using cached endpoint IP ${cached.ip} for '$cleanHost' (age: ${(now - cached.timestampMillis) / 1000}s)")
            return@withContext cached.ip
        }

        // Resolve via underlying physical networks (Wi-Fi, Cellular, Ethernet)
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val physicalNetworks = cm.allNetworks.filter { net ->
                    val caps = cm.getNetworkCapabilities(net) ?: return@filter false
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                }

                for (network in physicalNetworks) {
                    try {
                        val addresses = network.getAllByName(cleanHost)
                        val resolved = addresses.firstOrNull()?.hostAddress
                        if (!resolved.isNullOrBlank()) {
                            resolutionCache[cleanHost] = CacheEntry(resolved, now)
                            VpnManager.log("[DNS] Physical network resolved '$cleanHost' -> $resolved")
                            return@withContext resolved
                        }
                    } catch (_: Exception) {
                        // Continue to next available physical network
                    }
                }
            }

            // Fallback resolution if cm.allNetworks yielded no result
            val fallbackAddresses = InetAddress.getAllByName(cleanHost)
            val fallbackResolved = fallbackAddresses.firstOrNull()?.hostAddress
            if (!fallbackResolved.isNullOrBlank()) {
                resolutionCache[cleanHost] = CacheEntry(fallbackResolved, now)
                VpnManager.log("[DNS] Fallback resolved '$cleanHost' -> $fallbackResolved")
                return@withContext fallbackResolved
            }
        } catch (e: Exception) {
            VpnManager.log("[DNS] Resolution failed for host '$cleanHost': ${e.message}")
        }

        // If DNS failed but we have a stale cached IP, use as last resort
        if (cached != null) {
            VpnManager.log("[DNS] Warning: Using expired cached IP ${cached.ip} for '$cleanHost'")
            return@withContext cached.ip
        }

        null
    }

    fun getCachedIp(host: String): String? {
        val cleanHost = host.trim().removeSurrounding("[", "]")
        val cached = resolutionCache[cleanHost] ?: return null
        val now = System.currentTimeMillis()
        return if ((now - cached.timestampMillis) < CACHE_TTL_MS) cached.ip else null
    }

    fun putInCache(host: String, ip: String, timestampMillis: Long = System.currentTimeMillis()) {
        val cleanHost = host.trim().removeSurrounding("[", "]")
        val cleanIp = ip.trim().removeSurrounding("[", "]")
        resolutionCache[cleanHost] = CacheEntry(cleanIp, timestampMillis)
    }

    fun evict(host: String) {
        val cleanHost = host.trim().removeSurrounding("[", "]")
        resolutionCache.remove(cleanHost)
        VpnManager.log("[DNS] Evicted '$cleanHost' from endpoint resolution cache")
    }

    fun clearCache() {
        resolutionCache.clear()
        VpnManager.log("[DNS] Cleared endpoint resolution cache")
    }
}
