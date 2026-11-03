package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.crypto.WireGuardCrypto
import com.example.model.ThemeMode
import com.example.model.VpnSettings
import com.example.model.WireGuardKeyPair
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("wireshield_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<VpnSettings> = _settings.asStateFlow()

    private fun loadSettings(): VpnSettings {
        return VpnSettings(
            killSwitchEnabled = prefs.getBoolean(KEY_KILL_SWITCH, false),
            strictKillSwitchWatchdog = prefs.getBoolean(KEY_STRICT_WATCHDOG, true),
            dnsLeakProtection = prefs.getBoolean(KEY_DNS_LEAK, true),
            ipv6LeakProtection = prefs.getBoolean(KEY_IPV6_LEAK, true),
            autoConnectOnLaunch = prefs.getBoolean(KEY_AUTO_CONNECT, false),
            autoReconnectOnNetworkSwitch = prefs.getBoolean(KEY_AUTO_RECONNECT, true),
            customDns = prefs.getString(KEY_CUSTOM_DNS, "") ?: "",
            mtu = prefs.getInt(KEY_MTU, 1420),
            themeMode = ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name),
            backendApiUrl = prefs.getString(KEY_BACKEND_URL, "http://10.0.2.2:3000") ?: "http://10.0.2.2:3000",
            selectedServerId = prefs.getString(KEY_SELECTED_SERVER, "srv-de-fra") ?: "srv-de-fra"
        )
    }

    fun updateSettings(update: (VpnSettings) -> VpnSettings) {
        val newSettings = update(_settings.value)
        prefs.edit()
            .putBoolean(KEY_KILL_SWITCH, newSettings.killSwitchEnabled)
            .putBoolean(KEY_STRICT_WATCHDOG, newSettings.strictKillSwitchWatchdog)
            .putBoolean(KEY_DNS_LEAK, newSettings.dnsLeakProtection)
            .putBoolean(KEY_IPV6_LEAK, newSettings.ipv6LeakProtection)
            .putBoolean(KEY_AUTO_CONNECT, newSettings.autoConnectOnLaunch)
            .putBoolean(KEY_AUTO_RECONNECT, newSettings.autoReconnectOnNetworkSwitch)
            .putString(KEY_CUSTOM_DNS, newSettings.customDns)
            .putInt(KEY_MTU, newSettings.mtu)
            .putString(KEY_THEME_MODE, newSettings.themeMode.name)
            .putString(KEY_BACKEND_URL, newSettings.backendApiUrl)
            .putString(KEY_SELECTED_SERVER, newSettings.selectedServerId)
            .apply()
        _settings.value = newSettings
    }

    fun getOrCreateClientKeyPair(): WireGuardKeyPair {
        val priv = prefs.getString(KEY_CLIENT_PRIV_KEY, null)
        val pub = prefs.getString(KEY_CLIENT_PUB_KEY, null)
        if (priv != null && pub != null && WireGuardCrypto.isValidKey(priv)) {
            return WireGuardKeyPair(priv, pub)
        }
        val newKeyPair = WireGuardCrypto.generateKeyPair()
        prefs.edit()
            .putString(KEY_CLIENT_PRIV_KEY, newKeyPair.privateKey)
            .putString(KEY_CLIENT_PUB_KEY, newKeyPair.publicKey)
            .apply()
        return newKeyPair
    }

    fun regenerateClientKeyPair(): WireGuardKeyPair {
        val newKeyPair = WireGuardCrypto.generateKeyPair()
        prefs.edit()
            .putString(KEY_CLIENT_PRIV_KEY, newKeyPair.privateKey)
            .putString(KEY_CLIENT_PUB_KEY, newKeyPair.publicKey)
            .apply()
        return newKeyPair
    }

    companion object {
        private const val KEY_KILL_SWITCH = "kill_switch"
        private const val KEY_STRICT_WATCHDOG = "strict_watchdog"
        private const val KEY_DNS_LEAK = "dns_leak"
        private const val KEY_IPV6_LEAK = "ipv6_leak"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_CUSTOM_DNS = "custom_dns"
        private const val KEY_MTU = "mtu"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_SELECTED_SERVER = "selected_server"
        private const val KEY_CLIENT_PRIV_KEY = "client_priv_key"
        private const val KEY_CLIENT_PUB_KEY = "client_pub_key"
    }
}
