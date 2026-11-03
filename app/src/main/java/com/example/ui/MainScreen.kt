package com.example.ui

import android.app.Activity
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ServerRepository
import com.example.model.NetworkMonitorState
import com.example.model.VpnServer
import com.example.model.VpnSettings
import com.example.model.VpnStats
import com.example.model.VpnState
import com.example.ui.components.LogsDialog
import com.example.ui.components.ServerSelectionSheet
import com.example.ui.theme.AmberConnecting
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldConnected
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Speed
import com.example.model.LeakTestResult
import com.example.model.SpeedTestResult
import com.example.model.VpnProfileMode
import com.example.ui.theme.RedDisconnected
import com.example.vpn.VpnManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vpnState: VpnState,
    currentServer: VpnServer?,
    stats: VpnStats,
    settings: VpnSettings,
    allServers: List<VpnServer>,
    logs: List<String>,
    monitorState: NetworkMonitorState = NetworkMonitorState(),
    speedTestResult: SpeedTestResult = SpeedTestResult(),
    leakTestResult: LeakTestResult = LeakTestResult(),
    onToggleConnect: () -> Unit,
    onUnblockTraffic: () -> Unit = {},
    onSelectServer: (VpnServer) -> Unit,
    onAddCustomServer: (VpnServer) -> Unit,
    onDeleteCustomServer: (String) -> Unit,
    onSelectProfile: (VpnProfileMode) -> Unit = {},
    onRunSpeedTest: () -> Unit = {},
    onRunLeakTest: () -> Unit = {},
    onPanicPurge: () -> Unit = {},
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val serverRepo = remember { ServerRepository(context) }

    var showServerSheet by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showPanicConfirmDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentServer?.let { VpnManager.connect(context, it) }
        } else {
            Toast.makeText(context, "VPN permission is required to create tunnel", Toast.LENGTH_SHORT).show()
        }
    }

    val handleConnectClick = {
        if (vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING) {
            onToggleConnect()
        } else {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                onToggleConnect()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "WireShield",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    val badgeColor = when (vpnState) {
                        VpnState.CONNECTED -> EmeraldConnected
                        VpnState.BLOCKED -> RedDisconnected
                        VpnState.CONNECTING, VpnState.RECONNECTING -> AmberConnecting
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    val badgeText = when (vpnState) {
                        VpnState.CONNECTED -> "PROTECTED"
                        VpnState.BLOCKED -> "INTERNET BLOCKED"
                        VpnState.CONNECTING, VpnState.RECONNECTING -> "CONNECTING"
                        else -> "UNPROTECTED"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }

                    IconButton(
                        onClick = { showLogsDialog = true },
                        modifier = Modifier.testTag("btn_open_logs")
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = "View logs")
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("btn_open_settings")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Emergency Lockdown Banner (Shown when VPN connection dropped and internet is blocked)
            if (vpnState == VpnState.BLOCKED || monitorState.isTrafficBlocked) {
                EmergencyBlockBanner(
                    dropReason = monitorState.dropReason ?: "Connection lost unexpectedly",
                    onReconnect = handleConnectClick,
                    onUnblock = onUnblockTraffic
                )
            }

            // Hero Connection Centerpiece
            HeroConnectionButton(
                vpnState = vpnState,
                onClick = handleConnectClick
            )

            // Connection Duration & Egress IP
            ConnectionStatusCard(
                vpnState = vpnState,
                stats = stats,
                onCopyIp = { ip ->
                    clipboardManager.setText(AnnotatedString(ip))
                    Toast.makeText(context, "IP copied: $ip", Toast.LENGTH_SHORT).show()
                }
            )

            // Profile Mode Selector Row
            VpnProfileSelectorRow(
                selectedProfile = settings.profileMode,
                onSelectProfile = onSelectProfile
            )

            // Current Server Selection Card
            CurrentServerCard(
                server = currentServer,
                onClick = { showServerSheet = true }
            )

            // Traffic & Bandwidth Statistics Card
            TrafficStatsCard(
                stats = stats,
                isConnected = vpnState == VpnState.CONNECTED,
                monitorState = monitorState
            )

            // Security Indicators Bar
            SecurityIndicatorsRow(
                settings = settings,
                vpnState = vpnState,
                monitorState = monitorState
            )

            // Diagnostics & Speed / Leak Testing Card
            DiagnosticsTestingCard(
                speedTestResult = speedTestResult,
                leakTestResult = leakTestResult,
                isConnected = vpnState == VpnState.CONNECTED,
                onRunSpeedTest = onRunSpeedTest,
                onRunLeakTest = onRunLeakTest
            )

            // Emergency Panic Wipe Button
            PanicPurgeCard(
                onClick = { showPanicConfirmDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showServerSheet) {
        ServerSelectionSheet(
            sheetState = sheetState,
            servers = allServers,
            selectedServer = currentServer,
            onDismiss = { showServerSheet = false },
            onServerSelected = { server ->
                onSelectServer(server)
                if (vpnState == VpnState.CONNECTED || vpnState == VpnState.BLOCKED) {
                    VpnManager.connect(context, server)
                }
            },
            onAddCustomServer = onAddCustomServer,
            onDeleteCustomServer = onDeleteCustomServer,
            onPingAll = {
                scope.launch {
                    serverRepo.pingAllServers()
                }
            }
        )
    }

    if (showLogsDialog) {
        LogsDialog(
            logs = logs,
            onDismiss = { showLogsDialog = false }
        )
    }

    if (showPanicConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPanicConfirmDialog = false },
            title = { Text("Emergency Panic Purge", fontWeight = FontWeight.Bold) },
            text = { Text("Instantly terminates VPN tunnel, deletes all custom servers, purges WireGuard cryptographic keys, and resets application configuration to factory defaults.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showPanicConfirmDialog = false
                        onPanicPurge()
                        Toast.makeText(context, "All VPN configurations purged", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Purge All Data", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showPanicConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EmergencyBlockBanner(
    dropReason: String,
    onReconnect: () -> Unit,
    onUnblock: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("emergency_block_banner")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Internet Blocked (Kill Switch)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Text(
                text = "VPN connection dropped: $dropReason. All device internet traffic has been strictly blocked to prevent data privacy leaks to your ISP.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onReconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_reconnect_emergency")
                ) {
                    Text("Reconnect VPN", color = Color.White)
                }

                OutlinedButton(
                    onClick = onUnblock,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_unblock_emergency")
                ) {
                    Text("Unblock Internet")
                }
            }
        }
    }
}

@Composable
fun HeroConnectionButton(
    vpnState: VpnState,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (vpnState == VpnState.CONNECTING || vpnState == VpnState.RECONNECTING || vpnState == VpnState.BLOCKED) 1.08f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val activeGlowColor by animateColorAsState(
        targetValue = when (vpnState) {
            VpnState.CONNECTED -> EmeraldConnected
            VpnState.BLOCKED -> RedDisconnected
            VpnState.CONNECTING, VpnState.RECONNECTING -> AmberConnecting
            VpnState.DISCONNECTING -> AmberConnecting
            VpnState.ERROR -> RedDisconnected
            VpnState.DISCONNECTED -> CyanPrimary
        },
        label = "glow_color"
    )

    val statusText = when (vpnState) {
        VpnState.CONNECTED -> "CONNECTED"
        VpnState.BLOCKED -> "TRAFFIC BLOCKED"
        VpnState.CONNECTING -> "CONNECTING..."
        VpnState.RECONNECTING -> "RECONNECTING"
        VpnState.DISCONNECTING -> "DISCONNECTING..."
        VpnState.ERROR -> "CONNECTION FAILED"
        VpnState.DISCONNECTED -> "DISCONNECTED"
    }

    val actionHint = when (vpnState) {
        VpnState.CONNECTED -> "Tap to Disconnect"
        VpnState.BLOCKED -> "Kill Switch Active • Tap to Reconnect"
        VpnState.CONNECTING, VpnState.RECONNECTING -> "Please wait..."
        VpnState.DISCONNECTING -> "Disconnecting..."
        VpnState.ERROR -> "Tap to Retry"
        VpnState.DISCONNECTED -> "Tap to Connect"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .scale(if (vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING || vpnState == VpnState.BLOCKED) pulseScale else 1f)
        ) {
            // Outer glow ring
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                activeGlowColor.copy(alpha = if (vpnState == VpnState.CONNECTED || vpnState == VpnState.BLOCKED) 0.35f else 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Middle border ring
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .border(
                        width = 3.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(activeGlowColor, activeGlowColor.copy(alpha = 0.4f))
                        ),
                        shape = CircleShape
                    )
            )

            // Inner clickable core button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                    .testTag("vpn_toggle_button")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (vpnState == VpnState.CONNECTED || vpnState == VpnState.BLOCKED) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "VPN toggle",
                        tint = activeGlowColor,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = statusText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = activeGlowColor,
            letterSpacing = 1.2.sp
        )

        Text(
            text = actionHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "WireGuard® Tunnel • ChaCha20-Poly1305",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun ConnectionStatusCard(
    vpnState: VpnState,
    stats: VpnStats,
    onCopyIp: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Duration counter
            Column {
                Text(
                    text = "Connection Time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (vpnState == VpnState.CONNECTED) stats.formattedDuration else "00:00:00",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (vpnState == VpnState.CONNECTED) EmeraldConnected else MaterialTheme.colorScheme.onSurface
                )
            }

            // Public IP Address
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (vpnState == VpnState.CONNECTED) "Secured Public IP" else if (vpnState == VpnState.BLOCKED) "IP (Traffic Cut)" else "Current IP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        stats.currentPublicIp?.let { onCopyIp(it) }
                    }
                ) {
                    Text(
                        text = if (vpnState == VpnState.BLOCKED) "BLOCKED" else (stats.currentPublicIp ?: "Checking..."),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = if (vpnState == VpnState.BLOCKED) RedDisconnected else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy IP",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CurrentServerCard(
    server: VpnServer?,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("card_current_server")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(text = server?.flagEmoji ?: "🌐", fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server?.name ?: "Select a Server",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (server != null) "${server.city}, ${server.country}" else "Tap to choose location",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (server != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { server.loadPercent / 100f },
                            modifier = Modifier
                                .width(50.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (server.loadPercent < 60) EmeraldConnected else Color(0xFFFFB300),
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${server.loadPercent}% load",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Latency & Change action
            Column(horizontalAlignment = Alignment.End) {
                if (server != null && server.pingMs > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldConnected.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${server.pingMs} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldConnected,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = "Change ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TrafficStatsCard(
    stats: VpnStats,
    isConnected: Boolean,
    monitorState: NetworkMonitorState = NetworkMonitorState()
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Download column
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Download",
                            tint = EmeraldConnected,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "DOWNLOAD",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isConnected) stats.formattedDownloadSpeed else "0 B/s",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isConnected) "Total: ${stats.formattedTotalDownload}" else "Total: 0 B",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(50.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                )

                // Upload column
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Upload",
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "UPLOAD",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isConnected) stats.formattedUploadSpeed else "0 B/s",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isConnected) "Total: ${stats.formattedTotalUpload}" else "Total: 0 B",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (monitorState.totalDropsBlocked > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🛡️ Watchdog: ${monitorState.totalDropsBlocked} drop event(s) blocked safely.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun SecurityIndicatorsRow(
    settings: VpnSettings,
    vpnState: VpnState = VpnState.DISCONNECTED,
    monitorState: NetworkMonitorState = NetworkMonitorState()
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val killSwitchText = when {
            vpnState == VpnState.BLOCKED -> "LOCKDOWN"
            settings.killSwitchEnabled -> "ON"
            else -> "OFF"
        }

        val watchdogText = when {
            monitorState.isTrafficBlocked -> "BLOCKED"
            monitorState.isMonitoring -> "ACTIVE"
            settings.strictKillSwitchWatchdog -> "STANDBY"
            else -> "OFF"
        }

        SecurityIndicatorBadge(
            label = "Kill Switch",
            isActive = settings.killSwitchEnabled || vpnState == VpnState.BLOCKED,
            activeText = killSwitchText,
            inactiveText = "OFF",
            modifier = Modifier.weight(1f)
        )
        SecurityIndicatorBadge(
            label = "Watchdog",
            isActive = settings.strictKillSwitchWatchdog,
            activeText = watchdogText,
            inactiveText = "OFF",
            modifier = Modifier.weight(1f)
        )
        SecurityIndicatorBadge(
            label = "DNS Leak",
            isActive = settings.dnsLeakProtection,
            activeText = "GUARDED",
            inactiveText = "OFF",
            modifier = Modifier.weight(1f)
        )
        SecurityIndicatorBadge(
            label = "IPv6 Leak",
            isActive = settings.ipv6LeakProtection,
            activeText = "BLOCKED",
            inactiveText = "OFF",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SecurityIndicatorBadge(
    label: String,
    isActive: Boolean,
    activeText: String,
    inactiveText: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isActive) activeText else inactiveText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun VpnProfileSelectorRow(
    selectedProfile: VpnProfileMode,
    onSelectProfile: (VpnProfileMode) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("card_vpn_profiles")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Specialized Profiles",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = selectedProfile.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VpnProfileMode.values().forEach { profile ->
                    val isSelected = profile == selectedProfile
                    val chipBg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    val borderModifier = if (isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(chipBg)
                            .then(borderModifier)
                            .clickable { onSelectProfile(profile) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("profile_chip_${profile.name.lowercase()}")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = profile.iconEmoji, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = profile.title.split(" ").first(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticsTestingCard(
    speedTestResult: SpeedTestResult,
    leakTestResult: LeakTestResult,
    isConnected: Boolean,
    onRunSpeedTest: () -> Unit,
    onRunLeakTest: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("card_diagnostics")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Diagnostics & Leak Testing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (leakTestResult.detectedDns != null) {
                    val isSafe = leakTestResult.isProtected
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSafe) EmeraldConnected.copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isSafe) "LEAK FREE" else "POTENTIAL LEAK",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSafe) EmeraldConnected else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Speed test results summary
            if (speedTestResult.pingMs > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Latency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${speedTestResult.pingMs} ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Jitter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${speedTestResult.jitterMs} ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Speed Down", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(speedTestResult.formattedDownload, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = EmeraldConnected)
                    }
                }
            }

            if (leakTestResult.detectedDns != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "DNS Resolver: ${leakTestResult.detectedDns}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRunSpeedTest,
                    enabled = !speedTestResult.isTesting,
                    modifier = Modifier.weight(1f).testTag("btn_run_speed_test")
                ) {
                    Text(if (speedTestResult.isTesting) "Testing..." else "Speed Test")
                }

                OutlinedButton(
                    onClick = onRunLeakTest,
                    enabled = !leakTestResult.isTesting,
                    modifier = Modifier.weight(1f).testTag("btn_run_leak_test")
                ) {
                    Text(if (leakTestResult.isTesting) "Verifying..." else "Leak Check")
                }
            }
        }
    }
}

@Composable
fun PanicPurgeCard(
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("card_panic_purge")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🚨", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Panic Lockdown & Purge",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Instantly wipe cryptographic keys and sever connections",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "Purge ›",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
