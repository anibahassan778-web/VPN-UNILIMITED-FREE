package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ServerRepository
import com.example.data.SettingsRepository
import com.example.model.ThemeMode
import com.example.model.VpnSettings
import com.example.model.WireGuardKeyPair
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: VpnSettings,
    clientKeyPair: WireGuardKeyPair,
    onSettingsChanged: ((VpnSettings) -> VpnSettings) -> Unit,
    onRegenerateKeys: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var backendUrlInput by remember { mutableStateOf(settings.backendApiUrl) }
    var customDnsInput by remember { mutableStateOf(settings.customDns) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Privacy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_back_settings")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Security & Tunnel Section
            SectionHeader(title = "Security & Protection", icon = Icons.Default.Security)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Kill Switch
                    SettingToggleRow(
                        title = "Kill Switch",
                        description = "Blocks all unencrypted internet traffic if the VPN connection drops unexpectedly.",
                        checked = settings.killSwitchEnabled,
                        testTag = "switch_kill_switch",
                        onCheckedChange = { checked ->
                            onSettingsChanged { it.copy(killSwitchEnabled = checked) }
                        }
                    )

                    // Strict Network Watchdog
                    SettingToggleRow(
                        title = "Strict Network Watchdog",
                        description = "Active background service that detects silent connection drops, packet timeouts, and network changes, instantly severing internet traffic.",
                        checked = settings.strictKillSwitchWatchdog,
                        testTag = "switch_strict_watchdog",
                        onCheckedChange = { checked ->
                            onSettingsChanged { it.copy(strictKillSwitchWatchdog = checked) }
                        }
                    )

                    // DNS Leak Protection
                    SettingToggleRow(
                        title = "DNS Leak Protection",
                        description = "Enforces private encrypted DNS routing (Cloudflare/Quad9) to prevent ISP DNS eavesdropping.",
                        checked = settings.dnsLeakProtection,
                        testTag = "switch_dns_leak",
                        onCheckedChange = { checked ->
                            onSettingsChanged { it.copy(dnsLeakProtection = checked) }
                        }
                    )

                    // IPv6 Leak Protection
                    SettingToggleRow(
                        title = "IPv6 Leak Protection",
                        description = "Routes/blocks IPv6 to prevent ISP bypassing the VPN tunnel over unencrypted IPv6.",
                        checked = settings.ipv6LeakProtection,
                        testTag = "switch_ipv6_leak",
                        onCheckedChange = { checked ->
                            onSettingsChanged { it.copy(ipv6LeakProtection = checked) }
                        }
                    )

                    // Custom DNS Field
                    OutlinedTextField(
                        value = customDnsInput,
                        onValueChange = {
                            customDnsInput = it
                            onSettingsChanged { s -> s.copy(customDns = it.trim()) }
                        },
                        label = { Text("Custom DNS (Optional)") },
                        placeholder = { Text("e.g. 1.1.1.1, 9.9.9.9 or 94.140.14.14") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_custom_dns"),
                        singleLine = true
                    )
                }
            }

            // Connection Automation
            SectionHeader(title = "Connection Behavior", icon = Icons.Default.NetworkCheck)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingToggleRow(
                        title = "Auto-Connect on Launch",
                        description = "Automatically connects to your favorite server when the app opens or device boots.",
                        checked = settings.autoConnectOnLaunch,
                        testTag = "switch_auto_connect",
                        onCheckedChange = { checked ->
                            onSettingsChanged { it.copy(autoConnectOnLaunch = checked) }
                        }
                    )

                    SettingToggleRow(
                        title = "Auto-Reconnect on Network Switch",
                        description = "Seamlessly preserves your WireGuard session when switching between Wi-Fi and 4G/5G.",
                        checked = settings.autoReconnectOnNetworkSwitch,
                        testTag = "switch_auto_reconnect",
                        onCheckedChange = { checked ->
                            onSettingsChanged { it.copy(autoReconnectOnNetworkSwitch = checked) }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tunnel MTU", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("1420 (Standard) or 1280 (Strict Mobile)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onSettingsChanged { it.copy(mtu = 1420) } },
                                modifier = Modifier.testTag("btn_mtu_1420")
                            ) {
                                Text(if (settings.mtu == 1420) "✓ 1420" else "1420")
                            }
                            OutlinedButton(
                                onClick = { onSettingsChanged { it.copy(mtu = 1280) } },
                                modifier = Modifier.testTag("btn_mtu_1280")
                            ) {
                                Text(if (settings.mtu == 1280) "✓ 1280" else "1280")
                            }
                        }
                    }
                }
            }

            // WireGuard Client Identity (Cryptography)
            SectionHeader(title = "WireGuard Cryptography", icon = Icons.Default.Lock)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Your WireGuard Public Key",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = clientKeyPair.publicKey,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(clientKeyPair.publicKey))
                                    Toast.makeText(context, "Public key copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy key", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Private key is kept safe and never leaves this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onRegenerateKeys,
                            modifier = Modifier.testTag("btn_regen_keys")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Regenerate")
                        }
                    }
                }
            }

            // Self-Hosted Backend Server Sync
            SectionHeader(title = "Self-Hosted Backend Sync", icon = Icons.Default.Sync)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Connect to your self-hosted management API to auto-fetch node lists and client configs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = backendUrlInput,
                        onValueChange = {
                            backendUrlInput = it
                            onSettingsChanged { s -> s.copy(backendApiUrl = it.trim()) }
                        },
                        label = { Text("Backend Server URL") },
                        placeholder = { Text("https://vpn.yourdomain.com") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_backend_url"),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            isSyncing = true
                            scope.launch {
                                val repo = ServerRepository(context)
                                val result = repo.syncWithBackend(backendUrlInput)
                                isSyncing = false
                                if (result.isSuccess) {
                                    Toast.makeText(context, "Synced ${result.getOrNull()?.size ?: 0} servers!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Sync failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isSyncing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_sync_backend")
                    ) {
                        Text(if (isSyncing) "Syncing..." else "Sync Server List Now")
                    }
                }
            }

            // Open Source & Privacy Policy
            SectionHeader(title = "Open Source & Privacy", icon = Icons.Default.Info)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Privacy First • No Data Logging",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• Zero activity logging: We do NOT track or record websites visited, DNS queries, downloads, or IP traffic.\n" +
                            "• Completely open source: No proprietary closed blobs, no analytics tracking SDKs, no ads, no artificial bandwidth throttle.\n" +
                            "• Self-hosted freedom: Unlimited bandwidth depends solely on the network throughput of servers you or your community operate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
