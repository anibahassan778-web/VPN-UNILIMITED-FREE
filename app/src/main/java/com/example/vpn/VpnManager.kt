package com.example.vpn

import android.content.Context
import com.example.data.IpLookupService
import com.example.data.ServerRepository
import com.example.data.SettingsRepository
import com.example.model.NetworkMonitorState
import com.example.model.VpnServer
import com.example.model.VpnSettings
import com.example.model.VpnStats
import com.example.model.VpnState
import com.example.model.WireGuardKeyPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VpnManager {

    private val managerScope = CoroutineScope(Dispatchers.Main + Job())

    private val _vpnState = MutableStateFlow(VpnState.DISCONNECTED)
    val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

    private val _currentServer = MutableStateFlow<VpnServer?>(null)
    val currentServer: StateFlow<VpnServer?> = _currentServer.asStateFlow()

    private val _stats = MutableStateFlow(VpnStats())
    val stats: StateFlow<VpnStats> = _stats.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(listOf("[SYSTEM] WireShield VPN Engine initialized"))
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    val monitorState: StateFlow<NetworkMonitorState> = NetworkMonitor.monitorState

    private var durationJob: Job? = null
    private var initialIp: String? = null

    fun init(context: Context) {
        val serverRepo = ServerRepository(context)
        val settingsRepo = SettingsRepository(context)
        val savedServerId = settingsRepo.settings.value.selectedServerId
        val initialServer = serverRepo.getServerById(savedServerId) ?: serverRepo.servers.value.firstOrNull()
        _currentServer.value = initialServer

        NetworkMonitor.init(context)

        managerScope.launch(Dispatchers.IO) {
            initialIp = IpLookupService.fetchCurrentIp()
            if (initialIp != null) {
                _stats.value = _stats.value.copy(currentPublicIp = initialIp)
            }
        }
    }

    fun selectServer(server: VpnServer, context: Context) {
        _currentServer.value = server
        val settingsRepo = SettingsRepository(context)
        settingsRepo.updateSettings { it.copy(selectedServerId = server.id) }
        log("Selected server changed to: ${server.name} (${server.country})")
    }

    fun connect(context: Context, server: VpnServer? = _currentServer.value) {
        val target = server ?: _currentServer.value
        if (target == null) {
            log("Cannot connect: No server selected")
            return
        }

        _currentServer.value = target
        _vpnState.value = VpnState.CONNECTING
        _stats.value = VpnStats(currentPublicIp = initialIp)
        startDurationTimer()

        WireShieldVpnService.start(context, target.id)
    }

    fun disconnect(context: Context) {
        _vpnState.value = VpnState.DISCONNECTING
        stopDurationTimer()
        NetworkMonitor.stopMonitoring()
        WireShieldVpnService.stop(context)
    }

    fun unblockTraffic(context: Context) {
        log("[KILL SWITCH] User requested emergency traffic unblock.")
        stopDurationTimer()
        NetworkMonitor.unblockTraffic(context)
    }

    fun toggleConnection(context: Context) {
        when (_vpnState.value) {
            VpnState.CONNECTED -> disconnect(context)
            VpnState.CONNECTING, VpnState.RECONNECTING -> disconnect(context)
            VpnState.DISCONNECTED, VpnState.ERROR -> connect(context)
            VpnState.BLOCKED -> connect(context) // Reconnect when blocked
            VpnState.DISCONNECTING -> {}
        }
    }

    fun onStateChanged(state: VpnState, server: VpnServer?) {
        _vpnState.value = state
        if (server != null) {
            _currentServer.value = server
        }

        if (state == VpnState.CONNECTED) {
            startDurationTimer()
            managerScope.launch(Dispatchers.IO) {
                delay(1500)
                val egressIp = IpLookupService.fetchCurrentIp()
                if (egressIp != null) {
                    _stats.value = _stats.value.copy(currentPublicIp = egressIp)
                    log("Verified secure egress IP: $egressIp")
                }
            }
        } else if (state == VpnState.DISCONNECTED || state == VpnState.ERROR) {
            stopDurationTimer()
            if (initialIp != null) {
                _stats.value = _stats.value.copy(currentPublicIp = initialIp)
            }
        } else if (state == VpnState.BLOCKED) {
            stopDurationTimer()
        }
    }

    fun onVpnError(errorMessage: String) {
        _vpnState.value = VpnState.ERROR
        stopDurationTimer()
        log("[ERROR] $errorMessage")
    }

    fun updateTraffic(txBytes: Long, rxBytes: Long, txSpeed: Long, rxSpeed: Long) {
        val currentDuration = _stats.value.durationSeconds
        val currentIp = _stats.value.currentPublicIp
        _stats.value = VpnStats(
            durationSeconds = currentDuration,
            txBytes = txBytes,
            rxBytes = rxBytes,
            txSpeedBps = txSpeed,
            rxSpeedBps = rxSpeed,
            currentPublicIp = currentIp
        )
    }

    fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val entry = "[$timestamp] $message"
        val updated = (_logs.value + entry).takeLast(200)
        _logs.value = updated
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        val startTime = System.currentTimeMillis()
        durationJob = managerScope.launch {
            while (isActive) {
                delay(1000)
                if (_vpnState.value == VpnState.CONNECTED) {
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                    _stats.value = _stats.value.copy(durationSeconds = elapsedSeconds)
                }
            }
        }
    }

    private fun stopDurationTimer() {
        durationJob?.cancel()
        durationJob = null
    }

    fun getServerById(context: Context, serverId: String?): VpnServer? {
        val repo = ServerRepository(context)
        return if (serverId != null) repo.getServerById(serverId) else repo.servers.value.firstOrNull()
    }

    fun getSettings(context: Context): VpnSettings {
        return SettingsRepository(context).settings.value
    }

    fun getClientKeyPair(context: Context): WireGuardKeyPair {
        return SettingsRepository(context).getOrCreateClientKeyPair()
    }
}
