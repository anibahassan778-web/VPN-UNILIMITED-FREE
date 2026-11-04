package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.VpnServer
import com.example.model.VpnSettings
import com.example.model.VpnState
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

class WireShieldVpnService : VpnService() {

    private val backend by lazy { GoBackend(applicationContext) }
    private val wireGuardTunnel = object : Tunnel {
        override fun getName(): String = "wireshield"
        override fun onStateChange(newState: Tunnel.State) {
            VpnManager.log("WireGuard tunnel state: $newState")
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelJob: Job? = null
    private var blackholeSinkJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val isRunning = AtomicBoolean(false)
    private val isEmergencyBlocking = AtomicBoolean(false)
    private var activeServer: VpnServer? = null
    private var activeSettings: VpnSettings = VpnSettings()

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupNetworkCallback()
        NetworkMonitor.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_CONNECT
        when (action) {
            ACTION_CONNECT -> {
                val serverId = intent?.getStringExtra(EXTRA_SERVER_ID)
                serviceScope.launch {
                    val server = VpnManager.getServerById(this@WireShieldVpnService, serverId)
                    if (server != null) {
                        startVpn(server)
                    } else {
                        VpnManager.onVpnError("Server not found")
                        stopSelf()
                    }
                }
            }
            ACTION_DISCONNECT -> {
                stopVpn()
            }
            ACTION_BLOCK_TRAFFIC -> {
                val reason = intent?.getStringExtra(EXTRA_DROP_REASON) ?: "VPN Connection dropped"
                startEmergencyBlock(reason)
            }
            ACTION_UNBLOCK_TRAFFIC, ACTION_UNBLOCK_AND_DISCONNECT -> {
                unblockAndDisconnect()
            }
            ACTION_RETRY_CONNECT -> {
                val serverId = intent?.getStringExtra(EXTRA_SERVER_ID) ?: activeServer?.id
                serviceScope.launch {
                    val server = VpnManager.getServerById(this@WireShieldVpnService, serverId)
                    if (server != null) {
                        stopEmergencyBlock()
                        startVpn(server)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpn(server: VpnServer) {
        // Ensure blackhole is torn down cleanly before establishing WireGuard tunnel
        stopEmergencyBlock()
        if (isRunning.get()) {
            stopTunnel()
        }

        activeServer = server
        activeSettings = VpnManager.getSettings(this)
        isRunning.set(true)
        isEmergencyBlocking.set(false)
        VpnManager.onStateChanged(VpnState.CONNECTING, server)
        VpnManager.log("Initiating WireGuard tunnel for ${server.name} (${server.endpointDisplay})")

        val notification = createNotification("Connecting to ${server.name}...", "Establishing secure WireGuard tunnel")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            try {
                // STEP 2: Pre-resolve endpoint hostname using physical networks BEFORE tunnel creation
                val resolvedIp = EndpointResolver.resolveEndpoint(this@WireShieldVpnService, server.endpointHost)
                if (resolvedIp.isNullOrBlank()) {
                    VpnManager.log("[DNS] Fatal: Cannot resolve endpoint '${server.endpointHost}' before tunnel creation")
                    EndpointResolver.evict(server.endpointHost)
                    isRunning.set(false)
                    VpnManager.onVpnError("Unable to resolve server address: ${server.endpointHost}")
                    if (activeSettings.killSwitchEnabled || activeSettings.strictKillSwitchWatchdog) {
                        startEmergencyBlock("DNS resolution failed for server ${server.endpointHost}")
                    } else {
                        stopVpn()
                    }
                    return@launch
                }

                VpnManager.log("Endpoint resolved to $resolvedIp:${server.endpointPort} (cached and pre-verified)")

                // STEP 1: Build official WireGuard Config
                val interfaceBuilder = Interface.Builder()
                val clientKeyPair = VpnManager.getClientKeyPair(this@WireShieldVpnService)
                val privateKeyToUse = if (!server.clientPrivateKey.isNullOrBlank()) {
                    server.clientPrivateKey
                } else {
                    clientKeyPair.privateKey
                }
                // Cryptographic private key is injected directly without printing or logging
                interfaceBuilder.parsePrivateKey(privateKeyToUse)

                val clientIpParts = server.clientIp.split("/")
                val clientIp = clientIpParts.getOrElse(0) { "10.66.66.2" }
                val clientPrefix = clientIpParts.getOrNull(1)?.toIntOrNull() ?: 32
                interfaceBuilder.addAddress(InetNetwork.parse("$clientIp/$clientPrefix"))

                // IPv6 Leak Protection
                if (activeSettings.ipv6LeakProtection) {
                    try {
                        interfaceBuilder.addAddress(InetNetwork.parse("fd00::2/128"))
                        VpnManager.log("IPv6 Leak Protection Active: fd00::2/128 assigned")
                    } catch (e: Exception) {
                        VpnManager.log("IPv6 configuration warning: ${e.message}")
                    }
                }

                val effectiveMtu = when (activeSettings.profileMode) {
                    com.example.model.VpnProfileMode.GAMING -> 1360
                    com.example.model.VpnProfileMode.STREAMING -> 1420
                    com.example.model.VpnProfileMode.STEALTH -> 1280
                    else -> activeSettings.mtu.coerceIn(1280, 1500)
                }
                interfaceBuilder.setMtu(effectiveMtu)

                // Configure Secure DNS
                val dnsServers = when (activeSettings.dnsShieldMode) {
                    com.example.model.DnsShieldMode.ADBLOCK -> listOf("94.140.14.14", "94.140.15.15")
                    com.example.model.DnsShieldMode.MALWARE -> listOf("9.9.9.9", "149.112.112.112")
                    com.example.model.DnsShieldMode.FAMILY_SAFE -> listOf("185.228.168.168", "185.228.169.168")
                    com.example.model.DnsShieldMode.CLOUDFLARE_FAST -> listOf("1.1.1.1", "1.0.0.1")
                    com.example.model.DnsShieldMode.CUSTOM -> {
                        if (activeSettings.customDns.isNotBlank()) activeSettings.customDns.split(",").map { it.trim() }
                        else server.dnsServers.split(",").map { it.trim() }
                    }
                }

                for (dns in dnsServers) {
                    if (dns.isNotBlank()) {
                        try {
                            interfaceBuilder.addDnsServer(InetAddress.getByName(dns))
                            VpnManager.log("DNS Shield (${activeSettings.dnsShieldMode.title}) configured: $dns")
                        } catch (e: Exception) {
                            VpnManager.log("Invalid DNS server $dns: ${e.message}")
                        }
                    }
                }

                // Split Tunneling
                if (activeSettings.splitTunnelEnabled && activeSettings.splitTunnelExcludedPackages.isNotEmpty()) {
                    try {
                        interfaceBuilder.excludeApplications(activeSettings.splitTunnelExcludedPackages)
                        VpnManager.log("Split Tunnel: Excluded ${activeSettings.splitTunnelExcludedPackages.size} packages")
                    } catch (e: Exception) {
                        VpnManager.log("Split Tunnel warning: ${e.message}")
                    }
                }

                val wgInterface = interfaceBuilder.build()

                // Peer configuration
                val peerBuilder = Peer.Builder()
                peerBuilder.parsePublicKey(server.publicKey)

                if (!server.presharedKey.isNullOrBlank()) {
                    try {
                        peerBuilder.parsePreSharedKey(server.presharedKey)
                        VpnManager.log("WireGuard Post-Quantum Pre-Shared Key (PSK) configured")
                    } catch (e: Exception) {
                        VpnManager.log("PSK parsing warning: ${e.message}")
                    }
                }

                val endpointString = if (resolvedIp.contains(":")) {
                    "[$resolvedIp]:${server.endpointPort}"
                } else {
                    "$resolvedIp:${server.endpointPort}"
                }
                peerBuilder.setEndpoint(InetEndpoint.parse(endpointString))

                peerBuilder.addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
                peerBuilder.addAllowedIp(InetNetwork.parse("::/0"))
                peerBuilder.setPersistentKeepalive(25)

                val peer = peerBuilder.build()

                // Complete Config
                val config = Config.Builder()
                    .setInterface(wgInterface)
                    .addPeer(peer)
                    .build()

                // Bring tunnel UP via GoBackend
                backend.setState(wireGuardTunnel, Tunnel.State.UP, config)
                VpnManager.log("Tunnel interface brought UP. Polling for WireGuard handshake (up to 15s)...")
                updateNotification("Connecting to ${server.name}...", "Awaiting cryptographic handshake...")

                // CRITICAL HANDSHAKE VERIFICATION:
                // Do NOT report CONNECTED immediately after setState(UP).
                // Poll backend.getStatistics(tunnel).peer(serverPublicKey).latestHandshakeEpochMillis()
                val handshakeDeadline = System.currentTimeMillis() + 15_000L
                var handshakeConfirmed = false
                var latestHandshake = 0L

                while (System.currentTimeMillis() < handshakeDeadline && isRunning.get() && isActive) {
                    delay(500)
                    try {
                        val stats = backend.getStatistics(wireGuardTunnel)
                        val peerStats = stats.peer(server.publicKey, backend)
                        latestHandshake = peerStats.latestHandshakeEpochMillis
                        val rx = peerStats.rxBytes

                        // Handshake is confirmed if epochMillis is non-zero or real incoming bytes arrived
                        if (latestHandshake > 0L || rx > 0L) {
                            handshakeConfirmed = true
                            break
                        }
                    } catch (_: Exception) {}
                }

                if (!handshakeConfirmed) {
                    VpnManager.log("[HANDSHAKE] Handshake timed out after 15s with ${server.endpointDisplay}. Server not responding.")
                    // Clear the cached endpoint host so next retry forces fresh DNS lookup
                    EndpointResolver.evict(server.endpointHost)

                    // Tear down tunnel
                    stopTunnel()
                    isRunning.set(false)
                    VpnManager.onVpnError("Server not responding")

                    if (activeSettings.killSwitchEnabled || activeSettings.strictKillSwitchWatchdog) {
                        startEmergencyBlock("Server not responding to WireGuard handshake")
                    } else {
                        stopVpn()
                    }
                    return@launch
                }

                VpnManager.log("WireGuard cryptographic handshake confirmed (handshake epoch: $latestHandshake).")
                VpnManager.onStateChanged(VpnState.CONNECTED, server)
                updateNotification("Connected to ${server.name}", "Encrypted with WireGuard • Protected")

                // Start Network Monitoring Watchdog
                NetworkMonitor.startMonitoring(this@WireShieldVpnService, server)

                // Start live WireGuard statistics sampler
                startStatsSampler()

            } catch (e: Exception) {
                VpnManager.log("Error establishing WireGuard tunnel: ${e.localizedMessage}")
                EndpointResolver.evict(server.endpointHost)
                VpnManager.onVpnError(e.localizedMessage ?: "Connection failed")
                if (activeSettings.killSwitchEnabled || activeSettings.strictKillSwitchWatchdog) {
                    startEmergencyBlock(e.localizedMessage ?: "Connection setup failed")
                } else {
                    stopVpn()
                }
            }
        }
    }

    private fun startStatsSampler() {
        tunnelJob?.cancel()
        tunnelJob = serviceScope.launch {
            var lastRx = 0L
            var lastTx = 0L
            var lastStatsTime = System.currentTimeMillis()
            var recentRx = 0L
            var recentTx = 0L

            while (isRunning.get() && isActive) {
                delay(1000)
                try {
                    val stats = backend.getStatistics(wireGuardTunnel)
                    val srvKey = activeServer?.publicKey ?: ""
                    val peerStats = stats.peer(srvKey, backend)

                    val totalRx = stats.totalRx()
                    val totalTx = stats.totalTx()
                    val now = System.currentTimeMillis()

                    val rxDelta = (totalRx - lastRx).coerceAtLeast(0)
                    val txDelta = (totalTx - lastTx).coerceAtLeast(0)
                    recentRx += rxDelta
                    recentTx += txDelta

                    val elapsedSeconds = ((now - lastStatsTime) / 1000.0).coerceAtLeast(1.0)
                    val rxRate = (recentRx / elapsedSeconds).toLong()
                    val txRate = (recentTx / elapsedSeconds).toLong()

                    VpnManager.updateTraffic(totalTx, totalRx, txRate, rxRate)

                    // Report packet activity to NetworkMonitor:
                    // Only incoming bytes (rxDelta > 0) confirm active link; tx alone does not
                    NetworkMonitor.reportPacketActivity(totalTx, totalRx)

                    // Heartbeat depends on recent handshake (< 3 minutes) OR rx > 0, NOT on tx
                    val handshakeEpoch = peerStats.latestHandshakeEpochMillis
                    val hasRecentHandshake = (handshakeEpoch > 0L && (now - handshakeEpoch) < 180_000L)
                    val isAlive = hasRecentHandshake || (rxDelta > 0 || totalRx > 0)
                    NetworkMonitor.reportTunnelHeartbeat(isAlive)

                    lastRx = totalRx
                    lastTx = totalTx
                    recentRx = 0L
                    recentTx = 0L
                    lastStatsTime = now
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Emergency Blackhole Architecture:
     * Android strictly permits only ONE active VpnService TUN interface at a time.
     * To prevent mutual revocation between GoBackend and this emergency blackhole:
     * 1. GoBackend is first completely torn down via `backend.setState(DOWN)`.
     * 2. Once the OS VPN interface slot is completely released, WireShieldVpnService
     *    calls `Builder().establish()` to establish an isolated sink interface.
     */
    private fun startEmergencyBlock(reason: String) {
        isRunning.set(false)
        isEmergencyBlocking.set(true)

        // Step 1: Ensure GoBackend is completely DOWN and released before establishing blackhole
        stopTunnel()

        VpnManager.onStateChanged(VpnState.BLOCKED, activeServer)
        VpnManager.log("[KILL SWITCH] Establishing emergency blackhole TUN to block all internet traffic ($reason)...")

        try {
            val builder = Builder()
            builder.setSession("WireShield: KILL SWITCH LOCKDOWN")
            builder.setMtu(1280)
            builder.addAddress(InetAddress.getByName("10.255.255.2"), 32)
            builder.addRoute("0.0.0.0", 0)

            try {
                builder.addAddress(InetAddress.getByName("fd00:dead:beef::2"), 128)
                builder.addRoute("::", 0)
            } catch (_: Exception) {}

            // Blackhole DNS server to guarantee no plain-text DNS leak
            builder.addDnsServer(InetAddress.getByName("10.255.255.1"))
            builder.setBlocking(true)

            vpnInterface = builder.establish()

            val blockedNotification = createBlockedNotification(reason)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, blockedNotification)

            // Start blackhole discard loop
            blackholeSinkJob?.cancel()
            blackholeSinkJob = serviceScope.launch {
                vpnInterface?.let { pfd ->
                    try {
                        val tunIn = FileInputStream(pfd.fileDescriptor)
                        val discardBuffer = ByteArray(16384)
                        while (isEmergencyBlocking.get() && isActive) {
                            if (tunIn.available() > 0) {
                                tunIn.read(discardBuffer) // Discard all traffic to isolate device
                            }
                            delay(25)
                        }
                    } catch (_: Exception) {}
                }
            }
            VpnManager.log("[KILL SWITCH] Emergency blackhole active: All outgoing internet traffic is blocked.")
        } catch (e: Exception) {
            VpnManager.log("[KILL SWITCH] Warning establishing blackhole: ${e.message}")
        }
    }

    private fun stopEmergencyBlock() {
        isEmergencyBlocking.set(false)
        blackholeSinkJob?.cancel()
        blackholeSinkJob = null
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
    }

    /**
     * Fail-safe disconnect and unblock action that ALWAYS restores normal network connectivity.
     */
    fun unblockAndDisconnect() {
        isRunning.set(false)
        stopEmergencyBlock()
        stopTunnel()
        NetworkMonitor.stopMonitoring()
        VpnManager.onStateChanged(VpnState.DISCONNECTED, null)
        VpnManager.log("[KILL SWITCH] Emergency unblock executed: all blackholes removed, normal network restored.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setupNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (isRunning.get() && activeSettings.autoReconnectOnNetworkSwitch) {
                    VpnManager.log("Network switch detected (Network available). Refreshing WireGuard tunnel...")
                    activeServer?.let { srv ->
                        serviceScope.launch {
                            delay(500)
                            if (isRunning.get()) {
                                startVpn(srv)
                            }
                        }
                    }
                }
            }

            override fun onLost(network: Network) {
                if (isRunning.get()) {
                    VpnManager.log("[MONITOR] Physical network disconnected while VPN active.")
                    if (activeSettings.killSwitchEnabled || activeSettings.strictKillSwitchWatchdog) {
                        NetworkMonitor.triggerEmergencyBlock(this@WireShieldVpnService, "Physical network connection lost")
                    }
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (_: Exception) {}
    }

    private fun stopTunnel() {
        tunnelJob?.cancel()
        tunnelJob = null
        try {
            backend.setState(wireGuardTunnel, Tunnel.State.DOWN, null)
        } catch (e: Exception) {
            VpnManager.log("Notice: WireGuard tunnel teardown: ${e.message}")
        }
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
    }

    private fun stopVpn() {
        isRunning.set(false)
        stopEmergencyBlock()
        stopTunnel()
        NetworkMonitor.stopMonitoring()
        VpnManager.onStateChanged(VpnState.DISCONNECTED, null)
        VpnManager.log("WireGuard tunnel safely closed and disconnected.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.set(false)
        stopEmergencyBlock()
        stopTunnel()
        NetworkMonitor.stopMonitoring()
        serviceScope.cancel()
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WireShield VPN Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live status of your encrypted WireGuard tunnel"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, WireShieldVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createBlockedNotification(reason: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val retryIntent = Intent(this, WireShieldVpnService::class.java).apply {
            action = ACTION_RETRY_CONNECT
            putExtra(EXTRA_SERVER_ID, activeServer?.id)
        }
        val retryPendingIntent = PendingIntent.getService(
            this,
            2,
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val unblockIntent = Intent(this, WireShieldVpnService::class.java).apply {
            action = ACTION_UNBLOCK_AND_DISCONNECT
        }
        val unblockPendingIntent = PendingIntent.getService(
            this,
            3,
            unblockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠️ Internet Blocked (Kill Switch)")
            .setContentText("VPN dropped: $reason. Traffic blocked to protect privacy.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "VPN connection dropped: $reason. Kill switch is actively blocking all outgoing traffic to protect your data privacy. Tap Reconnect once network is restored, or tap Disconnect & Unblock to lift the block."
            ))
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Reconnect", retryPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect & Unblock", unblockPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(title, content))
    }

    companion object {
        const val ACTION_CONNECT = "com.example.wireshield.CONNECT"
        const val ACTION_DISCONNECT = "com.example.wireshield.DISCONNECT"
        const val ACTION_BLOCK_TRAFFIC = "com.example.wireshield.BLOCK_TRAFFIC"
        const val ACTION_UNBLOCK_TRAFFIC = "com.example.wireshield.UNBLOCK_TRAFFIC"
        const val ACTION_UNBLOCK_AND_DISCONNECT = "com.example.wireshield.UNBLOCK_AND_DISCONNECT"
        const val ACTION_RETRY_CONNECT = "com.example.wireshield.RETRY_CONNECT"
        const val EXTRA_SERVER_ID = "extra_server_id"
        const val EXTRA_DROP_REASON = "extra_drop_reason"
        private const val CHANNEL_ID = "wireshield_vpn_status_channel"
        private const val NOTIFICATION_ID = 4001

        fun start(context: Context, serverId: String) {
            val intent = Intent(context, WireShieldVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_SERVER_ID, serverId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WireShieldVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }

        fun blockTraffic(context: Context, reason: String) {
            val intent = Intent(context, WireShieldVpnService::class.java).apply {
                action = ACTION_BLOCK_TRAFFIC
                putExtra(EXTRA_DROP_REASON, reason)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun unblockTraffic(context: Context) {
            val intent = Intent(context, WireShieldVpnService::class.java).apply {
                action = ACTION_UNBLOCK_AND_DISCONNECT
            }
            context.startService(intent)
        }
    }
}
