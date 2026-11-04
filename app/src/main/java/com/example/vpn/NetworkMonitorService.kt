package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.NetworkMonitorState
import com.example.model.VpnServer
import com.example.model.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class NetworkMonitorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NetworkMonitor.init(this.applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_MONITOR
        when (action) {
            ACTION_START_MONITOR -> {
                val serverId = intent?.getStringExtra(EXTRA_SERVER_ID)
                val server = VpnManager.getServerById(this, serverId)
                if (server != null) {
                    NetworkMonitor.startMonitoring(this, server)
                }
            }
            ACTION_STOP_MONITOR -> {
                NetworkMonitor.stopMonitoring()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        NetworkMonitor.stopMonitoring()
    }

    companion object {
        const val ACTION_START_MONITOR = "com.example.wireshield.START_MONITOR"
        const val ACTION_STOP_MONITOR = "com.example.wireshield.STOP_MONITOR"
        const val EXTRA_SERVER_ID = "extra_server_id"

        fun start(context: Context, serverId: String) {
            val intent = Intent(context, NetworkMonitorService::class.java).apply {
                action = ACTION_START_MONITOR
                putExtra(EXTRA_SERVER_ID, serverId)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            val intent = Intent(context, NetworkMonitorService::class.java).apply {
                action = ACTION_STOP_MONITOR
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}

object NetworkMonitor {

    private val monitorScope = CoroutineScope(Dispatchers.IO + Job())
    private var watchdogJob: Job? = null
    private var recoveryJob: Job? = null

    private val _monitorState = MutableStateFlow(NetworkMonitorState())
    val monitorState: StateFlow<NetworkMonitorState> = _monitorState.asStateFlow()

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var targetServer: VpnServer? = null
    private var appContext: Context? = null

    private val lastPacketTimestamp = AtomicLong(System.currentTimeMillis())
    private val isMonitoringActive = AtomicBoolean(false)
    private val isRecoveryUnderway = AtomicBoolean(false)

    fun init(context: Context) {
        appContext = context.applicationContext
        setupSystemNetworkCallback(context.applicationContext)
    }

    private fun setupSystemNetworkCallback(context: Context) {
        if (connectivityManager != null) return
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                VpnManager.log("[MONITOR] Physical network available.")
                _monitorState.value = _monitorState.value.copy(isServerReachable = true)

                // If currently blocked, attempt auto-recovery
                if (_monitorState.value.isTrafficBlocked && !isRecoveryUnderway.get()) {
                    triggerAutoRecovery(context)
                }
            }

            override fun onLost(network: Network) {
                VpnManager.log("[MONITOR] Underlying physical network connection lost!")
                _monitorState.value = _monitorState.value.copy(isServerReachable = false)

                val settings = VpnManager.getSettings(context)
                if (VpnManager.vpnState.value == VpnState.CONNECTED && (settings.killSwitchEnabled || settings.strictKillSwitchWatchdog)) {
                    triggerEmergencyBlock(context, "Physical network connection lost unexpectedly")
                }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                _monitorState.value = _monitorState.value.copy(isServerReachable = hasInternet)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            VpnManager.log("[MONITOR] NetworkCallback registration warning: ${e.message}")
        }
    }

    fun startMonitoring(context: Context, server: VpnServer) {
        appContext = context.applicationContext
        targetServer = server
        isMonitoringActive.set(true)
        lastPacketTimestamp.set(System.currentTimeMillis())

        _monitorState.value = _monitorState.value.copy(
            isMonitoring = true,
            isDropDetected = false,
            dropReason = null,
            isServerReachable = true
        )

        VpnManager.log("[MONITOR] Connection Watchdog activated for ${server.name} (${server.endpointDisplay})")

        watchdogJob?.cancel()
        watchdogJob = monitorScope.launch {
            runWatchdogLoop(context.applicationContext, server)
        }
    }

    fun stopMonitoring() {
        isMonitoringActive.set(false)
        watchdogJob?.cancel()
        watchdogJob = null
        recoveryJob?.cancel()
        recoveryJob = null
        isRecoveryUnderway.set(false)

        _monitorState.value = _monitorState.value.copy(
            isMonitoring = false,
            isDropDetected = false
        )
        VpnManager.log("[MONITOR] Watchdog monitoring deactivated.")
    }

    fun reportPacketActivity(txBytes: Long, rxBytes: Long) {
        // Only incoming data (rxBytes > 0) proves traffic is flowing back through the tunnel;
        // tx alone will increment even when sending into a dead network or blackhole.
        if (rxBytes > 0) {
            lastPacketTimestamp.set(System.currentTimeMillis())
        }
    }

    fun reportTunnelHeartbeat(isAlive: Boolean, latencyMs: Int = -1) {
        if (isAlive) {
            lastPacketTimestamp.set(System.currentTimeMillis())
            _monitorState.value = _monitorState.value.copy(latencyMs = latencyMs, isServerReachable = true)
        }
    }

    private suspend fun runWatchdogLoop(context: Context, server: VpnServer) {
        var consecutiveFailures = 0

        while (isMonitoringActive.get() && monitorScope.isActive) {
            delay(3000)

            if (VpnManager.vpnState.value != VpnState.CONNECTED) {
                continue
            }

            val now = System.currentTimeMillis()
            val timeSinceLastPacket = now - lastPacketTimestamp.get()

            // 1. Silent Tunnel Timeout check:
            // If tunnel has been connected for > 10s and no packets passed in > 30s, probe server endpoint
            val isEndpointAlive = probeServerReachability(server.endpointHost, server.endpointPort)
            if (!isEndpointAlive) {
                consecutiveFailures++
                VpnManager.log("[MONITOR] Endpoint probe failed ($consecutiveFailures/2) for ${server.endpointDisplay}")
            } else {
                consecutiveFailures = 0
            }

            // 2. Drop Condition Evaluation
            if (consecutiveFailures >= 2 || (timeSinceLastPacket > 45000L && consecutiveFailures > 0)) {
                val reason = if (!isEndpointAlive) {
                    "WireGuard endpoint ${server.endpointDisplay} unreachable"
                } else {
                    "Tunnel packet flow timed out"
                }

                val settings = VpnManager.getSettings(context)
                if (settings.killSwitchEnabled || settings.strictKillSwitchWatchdog) {
                    triggerEmergencyBlock(context, reason)
                    break
                } else {
                    VpnManager.log("[MONITOR] VPN drop detected: $reason (Kill Switch is OFF)")
                    VpnManager.onVpnError(reason)
                }
            }
        }
    }

    private fun probeServerReachability(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2500)
                true
            }
        } catch (_: Exception) {
            // WireGuard uses UDP, so an ICMP port unreachable or connection refused means host is up;
            // let's verify if DNS resolves or ping succeeds
            try {
                val address = java.net.InetAddress.getByName(host)
                address.isReachable(2000) || (address.hostAddress?.isNotBlank() == true)
            } catch (_: Exception) {
                false
            }
        }
    }

    fun triggerEmergencyBlock(context: Context, reason: String) {
        val now = System.currentTimeMillis()
        val totalDrops = _monitorState.value.totalDropsBlocked + 1

        _monitorState.value = _monitorState.value.copy(
            isDropDetected = true,
            isTrafficBlocked = true,
            dropReason = reason,
            lastDropTimestamp = now,
            totalDropsBlocked = totalDrops
        )

        VpnManager.log("=====================================================")
        VpnManager.log("[KILL SWITCH] ⚠️ VPN DROP DETECTED: $reason")
        VpnManager.log("[KILL SWITCH] 🛡️ EMERGENCY LOCKDOWN ACTIVE: All Internet Traffic Blocked")
        VpnManager.log("=====================================================")

        // Notify WireShieldVpnService to swap TUN into Blackhole Interface
        WireShieldVpnService.blockTraffic(context, reason)
        VpnManager.onStateChanged(VpnState.BLOCKED, targetServer)

        // Trigger background recovery watchdog
        triggerAutoRecovery(context)
    }

    fun unblockTraffic(context: Context) {
        _monitorState.value = _monitorState.value.copy(
            isTrafficBlocked = false,
            isDropDetected = false,
            dropReason = null
        )
        isRecoveryUnderway.set(false)
        recoveryJob?.cancel()

        VpnManager.log("[KILL SWITCH] Emergency Traffic Block lifted by user.")
        WireShieldVpnService.unblockTraffic(context)
        VpnManager.onStateChanged(VpnState.DISCONNECTED, null)
    }

    fun triggerAutoRecovery(context: Context) {
        val server = targetServer ?: return
        val settings = VpnManager.getSettings(context)
        if (!settings.autoReconnectOnNetworkSwitch && !settings.strictKillSwitchWatchdog) {
            return
        }

        if (isRecoveryUnderway.get()) return
        isRecoveryUnderway.set(true)

        recoveryJob?.cancel()
        recoveryJob = monitorScope.launch {
            VpnManager.log("[MONITOR] Auto-Recovery Watchdog started: waiting for network recovery...")
            var attempts = 0

            while (isRecoveryUnderway.get() && isActive) {
                delay(3000)
                attempts++
                val isReachable = probeServerReachability(server.endpointHost, server.endpointPort)

                if (isReachable) {
                    VpnManager.log("[MONITOR] Server ${server.endpointDisplay} reached on attempt #$attempts. Restoring VPN tunnel...")
                    _monitorState.value = _monitorState.value.copy(isTrafficBlocked = false, isDropDetected = false)
                    isRecoveryUnderway.set(false)

                    // Re-initiate clean connection to server
                    VpnManager.connect(context, server)
                    break
                } else {
                    if (attempts % 5 == 0) {
                        VpnManager.log("[MONITOR] Still waiting for network path to ${server.endpointDisplay} (Attempt #$attempts)...")
                    }
                }
            }
        }
    }
}
