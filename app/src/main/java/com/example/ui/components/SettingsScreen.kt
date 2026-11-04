package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    onPanicPurge: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var backendUrlInput by remember { mutableStateOf(settings.backendApiUrl) }
    var customDnsInput by remember { mutableStateOf(settings.customDns) }
    var showSplitTunnelDialog by remember { mutableStateOf(false) }
    var showPanicConfirmDialog by remember { mutableStateOf(false) }

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

                    // Android System-Level Kill Switch Guidance
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Android OS-Level Kill Switch",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "For absolute hardware-level leak protection, turn on 'Always-on VPN' and 'Block connections without VPN' in Android system settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS)
                                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Go to Android Settings > Network > VPN", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("btn_open_system_vpn_settings"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Open Android VPN Settings")
                            }
                        }
                    }

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
                        description = "Enforces private encrypted DNS routing to prevent ISP DNS eavesdropping.",
                        checked = settings.dnsLeakProtection,
                        testTag = "switch_dns_leak",
                        onCheckedChange = { checked ->
                            onSettingsChanged { it.copy(dnsLeakProtection = checked) }
                        }
                    )

                    // DNS Shield Mode Picker
                    Text(
                        text = "DNS Shield Protection Profile",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.example.model.DnsShieldMode.values().forEach { mode ->
                            val isSelected = settings.dnsShieldMode == mode
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSettingsChanged { it.copy(dnsShieldMode = mode) }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.RadioButton(
                                        selected = isSelected,
                                        onClick = { onSettingsChanged { it.copy(dnsShieldMode = mode) } }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mode.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = mode.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (settings.dnsShieldMode == com.example.model.DnsShieldMode.CUSTOM) {
                        OutlinedTextField(
                            value = customDnsInput,
                            onValueChange = {
                                customDnsInput = it
                                onSettingsChanged { s -> s.copy(customDns = it.trim()) }
                            },
                            label = { Text("Custom DNS IP") },
                            placeholder = { Text("e.g. 1.1.1.1, 9.9.9.9 or 94.140.14.14") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_custom_dns"),
                            singleLine = true
                        )
                    }

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

            // Advanced Routing & Split Tunneling
            SectionHeader(title = "Advanced Routing & Split Tunneling", icon = Icons.Default.NetworkCheck)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Split Tunneling toggle
                    SettingToggleRow(
                        title = "Split Tunneling",
                        description = "Permits selected apps (e.g. banking, food delivery) to access the internet directly without routing through VPN.",
                        checked = settings.splitTunnelEnabled,
                        testTag = "switch_split_tunnel",
                        onCheckedChange = { checked ->
                            onSettingsChanged { it.copy(splitTunnelEnabled = checked) }
                        }
                    )

                    if (settings.splitTunnelEnabled) {
                        Button(
                            onClick = { showSplitTunnelDialog = true },
                            modifier = Modifier.fillMaxWidth().testTag("btn_select_split_apps")
                        ) {
                            Text("Select Bypass Apps (${settings.splitTunnelExcludedPackages.size} selected)")
                        }
                    }

                    // Obfuscation / Stealth Mode
                    SettingToggleRow(
                        title = "Stealth Mode (Anti-DPI Obfuscation)",
                        description = "Masks WireGuard handshake packet headers with random padding to bypass Deep Packet Inspection (DPI) censorship firewalls.",
                        checked = settings.obfuscationEnabled,
                        testTag = "switch_obfuscation",
                        onCheckedChange = { checked ->
                            onSettingsChanged { it.copy(obfuscationEnabled = checked) }
                        }
                    )

                    // Bypass Local Network (LAN)
                    SettingToggleRow(
                        title = "Bypass Local Network (LAN)",
                        description = "Maintains local network visibility for printers, Chromecast, smart home devices, and local file shares.",
                        checked = settings.bypassLocalNetwork,
                        testTag = "switch_bypass_lan",
                        onCheckedChange = { checked ->
                            onSettingsChanged { it.copy(bypassLocalNetwork = checked) }
                        }
                    )

                    // Multi-Hop Double VPN
                    SettingToggleRow(
                        title = "Multi-Hop Double VPN",
                        description = "Chains connection through two separate privacy nodes in series for double encryption defense.",
                        checked = settings.multiHopEnabled,
                        testTag = "switch_multi_hop",
                        onCheckedChange = { checked ->
                            onSettingsChanged { it.copy(multiHopEnabled = checked) }
                        }
                    )
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

            // Emergency Panic Wipe
            SectionHeader(title = "Emergency Panic Purge", icon = Icons.Default.Lock)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Panic Lockdown & Purge",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Instantly disconnects tunnel, removes all saved servers, regenerates keys, wipes traffic history, and restores default factory state.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(
                        onClick = { showPanicConfirmDialog = true },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("btn_panic_wipe")
                    ) {
                        Text("Purge All Data & Reset Keys", color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSplitTunnelDialog) {
        SplitTunnelDialog(
            selectedPackages = settings.splitTunnelExcludedPackages,
            onSave = { updated ->
                onSettingsChanged { it.copy(splitTunnelExcludedPackages = updated) }
            },
            onDismiss = { showSplitTunnelDialog = false }
        )
    }

    if (showPanicConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPanicConfirmDialog = false },
            title = { Text("Confirm Panic Purge", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure? This will permanently erase all custom servers, purge cached network configurations, and reset WireGuard cryptographic keys.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPanicConfirmDialog = false
                        onPanicPurge()
                        Toast.makeText(context, "All VPN configurations successfully purged", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Purge Everything", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPanicConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
