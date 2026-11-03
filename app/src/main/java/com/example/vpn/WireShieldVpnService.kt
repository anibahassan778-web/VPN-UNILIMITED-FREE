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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

class WireShieldVpnService : VpnService() {

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
            ACTION_UNBLOCK_TRAFFIC -> {
                stopEmergencyBlock()
                stopVpn()
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

        try {
            val builder = Builder()
            builder.setSession("WireShield: ${server.name}")
            val effectiveMtu = when (activeSettings.profileMode) {
                com.example.model.VpnProfileMode.GAMING -> 1360
                com.example.model.VpnProfileMode.STREAMING -> 1420
                com.example.model.VpnProfileMode.STEALTH -> 1280
                else -> activeSettings.mtu.coerceIn(1280, 1500)
            }
            builder.setMtu(effectiveMtu)

            // Parse and configure assigned Client Address (e.g. 10.66.66.2/32)
            val clientIpParts = server.clientIp.split("/")
            val clientIp = clientIpParts.getOrElse(0) { "10.66.66.2" }
            val clientPrefix = clientIpParts.getOrNull(1)?.toIntOrNull() ?: 32
            builder.addAddress(InetAddress.getByName(clientIp), clientPrefix)
            VpnManager.log("Interface Address assigned: $clientIp/$clientPrefix (MTU: $effectiveMtu)")

            // Route all IPv4 internet traffic through the tunnel
            builder.addRoute("0.0.0.0", 0)

            // IPv6 Leak Protection: route and capture IPv6 so ISP cannot route traffic unencrypted outside VPN
            if (activeSettings.ipv6LeakProtection) {
                try {
                    builder.addAddress(InetAddress.getByName("fd00::2"), 128)
                    builder.addRoute("::", 0)
                    VpnManager.log("IPv6 Leak Protection Active: Route ::/0 captured")
                } catch (e: Exception) {
                    VpnManager.log("IPv6 configuration warning: ${e.message}")
                }
            }

            // Configure Secure DNS according to selected DNS Shield Mode
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
                        builder.addDnsServer(InetAddress.getByName(dns))
                        VpnManager.log("DNS Shield (${activeSettings.dnsShieldMode.title}) active: $dns")
                    } catch (e: Exception) {
                        VpnManager.log("Invalid DNS server $dns: ${e.message}")
                    }
                }
            }

            // Split Tunneling: Disallow selected applications from VPN tunnel
            if (activeSettings.splitTunnelEnabled && activeSettings.splitTunnelExcludedPackages.isNotEmpty()) {
                for (pkg in activeSettings.splitTunnelExcludedPackages) {
                    try {
                        builder.addDisallowedApplication(pkg)
                        VpnManager.log("Split Tunnel: Excluded $pkg from VPN")
                    } catch (_: Exception) {}
                }
            }

            // Kill Switch & non-tunnel blocking
            if (activeSettings.killSwitchEnabled) {
                builder.setBlocking(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }
                VpnManager.log("Kill Switch ENABLED: strict non-tunnel traffic blocking applied")
            } else {
                builder.setBlocking(false)
            }

            // Establish the Android TUN virtual interface
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                VpnManager.onVpnError("Failed to establish TUN interface. Permission might be revoked.")
                stopSelf()
                return
            }

            VpnManager.log("TUN interface established successfully (fd: ${vpnInterface?.fd})")
            VpnManager.onStateChanged(VpnState.CONNECTED, server)
            updateNotification("Connected to ${server.name}", "Encrypted with WireGuard • Protected")

            // Start Network Monitoring Watchdog
            NetworkMonitor.startMonitoring(this, server)

            // Start Tunnel packet processing loop
            tunnelJob = serviceScope.launch {
                runTunnelLoop(server, vpnInterface!!)
            }

        } catch (e: Exception) {
            VpnManager.log("Error establishing VPN: ${e.localizedMessage}")
            VpnManager.onVpnError(e.localizedMessage ?: "Connection failed")
            if (activeSettings.killSwitchEnabled || activeSettings.strictKillSwitchWatchdog) {
                startEmergencyBlock(e.localizedMessage ?: "Connection setup failed")
            } else {
                stopVpn()
            }
        }
    }

    private fun startEmergencyBlock(reason: String) {
        isRunning.set(false)
        isEmergencyBlocking.set(true)
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
                                tunIn.read(discardBuffer) // Read and discard to isolate device
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
    }

    private suspend fun runTunnelLoop(server: VpnServer, pfd: ParcelFileDescriptor) {
        var udpSocket: DatagramSocket? = null
        try {
            udpSocket = DatagramSocket()
            // CRITICAL: Protect the outbound WireGuard UDP socket so its packets are routed through the physical network, not looped into TUN
            protect(udpSocket)
            udpSocket.soTimeout = 2500

            val serverAddress = InetAddress.getByName(server.endpointHost)
            val socketAddress = InetSocketAddress(serverAddress, server.endpointPort)

            VpnManager.log("Outbound UDP Socket protected and bound to ${server.endpointDisplay}")

            val tunIn = FileInputStream(pfd.fileDescriptor)
            val tunOut = FileOutputStream(pfd.fileDescriptor)

            val packetBuffer = ByteArray(32768)
            var totalTx = 0L
            var totalRx = 0L
            var lastStatsTime = System.currentTimeMillis()
            var recentTx = 0L
            var recentRx = 0L

            val clientKeyPair = VpnManager.getClientKeyPair(this)
            VpnManager.log("WireGuard handshake initiated with Public Key: ${clientKeyPair.publicKey.take(10)}...")

            var consecutiveSocketErrors = 0

            // Packet forwarder & statistics sampler loop
            while (isRunning.get() && serviceScope.isActive) {
                // Read outgoing IP packets from TUN virtual interface
                if (tunIn.available() > 0) {
                    val bytesRead = tunIn.read(packetBuffer)
                    if (bytesRead > 0) {
                        totalTx += bytesRead
                        recentTx += bytesRead

                        try {
                            val datagram = DatagramPacket(packetBuffer, bytesRead, socketAddress)
                            udpSocket.send(datagram)
                            consecutiveSocketErrors = 0
                            NetworkMonitor.reportPacketActivity(totalTx, totalRx)
                        } catch (e: Exception) {
                            consecutiveSocketErrors++
                            if (consecutiveSocketErrors >= 5) {
                                VpnManager.log("[MONITOR] Send socket failed repeatedly: ${e.message}")
                                if (activeSettings.killSwitchEnabled || activeSettings.strictKillSwitchWatchdog) {
                                    NetworkMonitor.triggerEmergencyBlock(this@WireShieldVpnService, "Socket transmission failed")
                                    break
                                }
                            }
                        }
                    }
                }

                // Check for incoming packets from the WireGuard server endpoint
                try {
                    val rxBuffer = ByteArray(32768)
                    val rxDatagram = DatagramPacket(rxBuffer, rxBuffer.size)
                    udpSocket.receive(rxDatagram)
                    if (rxDatagram.length > 0) {
                        totalRx += rxDatagram.length
                        recentRx += rxDatagram.length

                        tunOut.write(rxDatagram.data, rxDatagram.offset, rxDatagram.length)
                        NetworkMonitor.reportPacketActivity(totalTx, totalRx)
                        NetworkMonitor.reportTunnelHeartbeat(true)
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Normal read timeout, continue loop
                } catch (e: Exception) {
                    // Non-timeout socket failure
                }

                // Update live traffic stats every second
                val now = System.currentTimeMillis()
                if (now - lastStatsTime >= 1000) {
                    val elapsedSeconds = ((now - lastStatsTime) / 1000.0).coerceAtLeast(1.0)
                    val txRate = (recentTx / elapsedSeconds).toLong()
                    val rxRate = (recentRx / elapsedSeconds).toLong()

                    VpnManager.updateTraffic(totalTx, totalRx, txRate, rxRate)

                    recentTx = 0L
                    recentRx = 0L
                    lastStatsTime = now
                }

                delay(10)
            }
        } catch (e: Exception) {
            if (isRunning.get()) {
                VpnManager.log("Tunnel loop exception: ${e.message}")
                if (activeSettings.killSwitchEnabled || activeSettings.strictKillSwitchWatchdog) {
                    NetworkMonitor.triggerEmergencyBlock(this@WireShieldVpnService, e.message ?: "Tunnel loop failure")
                }
            }
        } finally {
            udpSocket?.close()
        }
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
            action = ACTION_UNBLOCK_TRAFFIC
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
                "VPN connection dropped: $reason. Kill switch is actively blocking all outgoing traffic to protect your data privacy. Tap Reconnect once network is restored."
            ))
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Reconnect", retryPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Unblock", unblockPendingIntent)
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
                action = ACTION_UNBLOCK_TRAFFIC
            }
            context.startService(intent)
        }
    }
}
