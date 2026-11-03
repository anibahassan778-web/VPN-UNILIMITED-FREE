package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.data.ServerRepository
import com.example.data.SettingsRepository
import com.example.model.ThemeMode
import com.example.ui.MainScreen
import com.example.ui.components.SettingsScreen
import com.example.ui.theme.WireShieldTheme
import com.example.vpn.VpnManager

enum class AppScreen {
    MAIN,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepo = SettingsRepository(this)
        val serverRepo = ServerRepository(this)

        setContent {
            val settings by settingsRepo.settings.collectAsState()
            val servers by serverRepo.servers.collectAsState()
            val vpnState by VpnManager.vpnState.collectAsState()
            val currentServer by VpnManager.currentServer.collectAsState()
            val stats by VpnManager.stats.collectAsState()
            val logs by VpnManager.logs.collectAsState()
            val monitorState by VpnManager.monitorState.collectAsState()
            val speedTestResult by VpnManager.speedTestResult.collectAsState()
            val leakTestResult by VpnManager.leakTestResult.collectAsState()

            var currentScreen by remember { mutableStateOf(AppScreen.MAIN) }
            val clientKeyPair = remember(settings) { settingsRepo.getOrCreateClientKeyPair() }

            // Permission for Android 13+ notifications
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* Granted or denied */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            val isDarkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            WireShieldTheme(darkTheme = isDarkTheme) {
                when (currentScreen) {
                    AppScreen.MAIN -> {
                        MainScreen(
                            vpnState = vpnState,
                            currentServer = currentServer,
                            stats = stats,
                            settings = settings,
                            allServers = servers,
                            logs = logs,
                            monitorState = monitorState,
                            speedTestResult = speedTestResult,
                            leakTestResult = leakTestResult,
                            onToggleConnect = { VpnManager.toggleConnection(this@MainActivity) },
                            onUnblockTraffic = { VpnManager.unblockTraffic(this@MainActivity) },
                            onSelectServer = { server -> VpnManager.selectServer(server, this@MainActivity) },
                            onAddCustomServer = { newServer -> serverRepo.addCustomServer(newServer) },
                            onDeleteCustomServer = { id -> serverRepo.removeCustomServer(id) },
                            onSelectProfile = { profile ->
                                settingsRepo.updateSettings { it.copy(profileMode = profile) }
                            },
                            onRunSpeedTest = { VpnManager.runSpeedTest() },
                            onRunLeakTest = { VpnManager.runLeakTest(this@MainActivity) },
                            onPanicPurge = { VpnManager.purgeAllData(this@MainActivity) },
                            onOpenSettings = { currentScreen = AppScreen.SETTINGS }
                        )
                    }
                    AppScreen.SETTINGS -> {
                        BackHandler {
                            currentScreen = AppScreen.MAIN
                        }
                        SettingsScreen(
                            settings = settings,
                            clientKeyPair = clientKeyPair,
                            onSettingsChanged = { update -> settingsRepo.updateSettings(update) },
                            onRegenerateKeys = { settingsRepo.regenerateClientKeyPair() },
                            onPanicPurge = { VpnManager.purgeAllData(this@MainActivity) },
                            onBack = { currentScreen = AppScreen.MAIN }
                        )
                    }
                }
            }
        }
    }
}
