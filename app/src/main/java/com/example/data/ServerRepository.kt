package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.VpnServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Repository for managing WireGuard servers.
 *
 * Single Source of Truth:
 * - Reads bundled fallback servers from assets/servers.json (with clear YOUR_SERVER_IP and YOUR_SERVER_PUBLIC_KEY placeholders).
 * - Persists and merges custom user-defined nodes from SharedPreferences.
 * - Syncs with self-hosted management backend REST API.
 * - Measures real latency with zero invented/random numbers; unreachable or placeholder servers are marked OFFLINE.
 */
class ServerRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("wireshield_servers", Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // Fallback default servers using explicit placeholders
    private val defaultServers: List<VpnServer> by lazy {
        loadServersFromAsset()
    }

    private val _servers = MutableStateFlow<List<VpnServer>>(loadAllServers())
    val servers: StateFlow<List<VpnServer>> = _servers.asStateFlow()

    private fun loadServersFromAsset(): List<VpnServer> {
        return try {
            val jsonString = context.assets.open("servers.json").bufferedReader().use { it.readText() }
            parseServerListJson(jsonString, isCustom = false)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseServerListJson(jsonStr: String, isCustom: Boolean): List<VpnServer> {
        val list = mutableListOf<VpnServer>()
        val jsonArray = JSONArray(jsonStr)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val host = obj.getString("endpointHost")
            val pubKey = obj.getString("publicKey")
            val isConfigured = !host.contains("YOUR_SERVER") && !pubKey.contains("YOUR_SERVER") && host.isNotBlank() && pubKey.isNotBlank()
            list.add(
                VpnServer(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    country = obj.getString("country"),
                    countryCode = obj.optString("countryCode", "UN"),
                    city = obj.getString("city"),
                    endpointHost = host,
                    endpointPort = obj.getInt("endpointPort"),
                    publicKey = pubKey,
                    presharedKey = obj.optString("presharedKey").takeIf { it.isNotBlank() },
                    clientPrivateKey = obj.optString("clientPrivateKey").takeIf { it.isNotBlank() },
                    clientIp = obj.optString("clientIp", "10.66.66.2/32"),
                    dnsServers = obj.optString("dnsServers", "1.1.1.1, 1.0.0.1"),
                    pingMs = obj.optInt("pingMs", -1),
                    loadPercent = obj.optInt("loadPercent", 0),
                    isCustom = isCustom,
                    isOnline = obj.optBoolean("isOnline", isConfigured) && isConfigured
                )
            )
        }
        return list
    }

    private fun loadAllServers(): List<VpnServer> {
        val customServers = loadCustomServers()
        return defaultServers + customServers
    }

    private fun loadCustomServers(): List<VpnServer> {
        val jsonStr = prefs.getString(KEY_CUSTOM_SERVERS, null) ?: return emptyList()
        return try {
            parseServerListJson(jsonStr, isCustom = true)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCustomServers(customs: List<VpnServer>) {
        val jsonArray = JSONArray()
        for (server in customs) {
            val obj = JSONObject().apply {
                put("id", server.id)
                put("name", server.name)
                put("country", server.country)
                put("countryCode", server.countryCode)
                put("city", server.city)
                put("endpointHost", server.endpointHost)
                put("endpointPort", server.endpointPort)
                put("publicKey", server.publicKey)
                put("presharedKey", server.presharedKey ?: "")
                put("clientPrivateKey", server.clientPrivateKey ?: "")
                put("clientIp", server.clientIp)
                put("dnsServers", server.dnsServers)
                put("pingMs", server.pingMs)
                put("loadPercent", server.loadPercent)
                put("isOnline", server.isOnline)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_SERVERS, jsonArray.toString()).apply()
    }

    fun getServerById(id: String): VpnServer? {
        return _servers.value.find { it.id == id }
    }

    fun addCustomServer(server: VpnServer) {
        val currentCustoms = loadCustomServers().toMutableList()
        currentCustoms.removeAll { it.id == server.id }
        currentCustoms.add(0, server.copy(isCustom = true))
        saveCustomServers(currentCustoms)
        _servers.value = defaultServers + currentCustoms
    }

    fun removeCustomServer(id: String) {
        val currentCustoms = loadCustomServers().filterNot { it.id == id }
        saveCustomServers(currentCustoms)
        _servers.value = defaultServers + currentCustoms
    }

    fun resetToDefaults() {
        prefs.edit().remove(KEY_CUSTOM_SERVERS).apply()
        _servers.value = defaultServers
    }

    /**
     * Probes real server latency.
     * Returns real ping in ms, or -1 (OFFLINE) on failure.
     * Never invents random or fake numbers.
     */
    suspend fun pingServer(server: VpnServer): Int = withContext(Dispatchers.IO) {
        // Placeholder check: servers with placeholder IP are instantly marked OFFLINE
        if (server.endpointHost.contains("YOUR_SERVER") || server.publicKey.contains("YOUR_SERVER")) {
            updateServerStatus(server.id, pingMs = -1, isOnline = false)
            return@withContext -1
        }

        val startTime = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(server.endpointHost, server.endpointPort), 2000)
            }
            val elapsed = (System.currentTimeMillis() - startTime).toInt()
            updateServerStatus(server.id, pingMs = elapsed, isOnline = true)
            elapsed
        } catch (_: Exception) {
            // TCP connect failed; try ICMP isReachable probe
            try {
                val address = java.net.InetAddress.getByName(server.endpointHost)
                if (address.isReachable(2000)) {
                    val elapsed = (System.currentTimeMillis() - startTime).toInt()
                    updateServerStatus(server.id, pingMs = elapsed, isOnline = true)
                    elapsed
                } else {
                    updateServerStatus(server.id, pingMs = -1, isOnline = false)
                    -1
                }
            } catch (_: Exception) {
                // Host resolution or connection failed completely: Server is OFFLINE
                updateServerStatus(server.id, pingMs = -1, isOnline = false)
                -1
            }
        }
    }

    private fun updateServerStatus(id: String, pingMs: Int, isOnline: Boolean) {
        _servers.value = _servers.value.map {
            if (it.id == id) it.copy(pingMs = pingMs, isOnline = isOnline) else it
        }
    }

    suspend fun pingAllServers() = withContext(Dispatchers.IO) {
        _servers.value.forEach { server ->
            pingServer(server)
        }
    }

    suspend fun syncWithBackend(backendUrl: String): Result<List<VpnServer>> = withContext(Dispatchers.IO) {
        try {
            val url = "${backendUrl.trimEnd('/')}/api/servers"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
            val fetchedServers = parseServerListJson(body, isCustom = false)

            val customs = loadCustomServers()
            _servers.value = fetchedServers + customs
            Result.success(_servers.value)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val KEY_CUSTOM_SERVERS = "custom_wireguard_servers"
    }
}
