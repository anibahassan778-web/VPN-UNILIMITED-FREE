package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.crypto.WireGuardCrypto
import com.example.model.VpnServer
import com.example.model.WireGuardConfig
import java.util.UUID

@Composable
fun AddCustomServerDialog(
    onDismiss: () -> Unit,
    onServerAdded: (VpnServer) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Form, 1: Raw Config
    var rawConfigText by remember { mutableStateOf("") }

    var serverName by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("Custom Server") }
    var city by remember { mutableStateOf("Self-Hosted") }
    var endpointHost by remember { mutableStateOf("") }
    var endpointPort by remember { mutableStateOf("51820") }
    var publicKey by remember { mutableStateOf("") }
    var presharedKey by remember { mutableStateOf("") }
    var clientIp by remember { mutableStateOf("10.66.66.2/32") }
    var dnsServers by remember { mutableStateOf("1.1.1.1, 1.0.0.1") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add WireGuard Server") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Manual") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Import .conf") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = serverName,
                        onValueChange = { serverName = it },
                        label = { Text("Server Name *") },
                        placeholder = { Text("e.g. My Home VPS") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_server_name"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = endpointHost,
                            onValueChange = { endpointHost = it },
                            label = { Text("Host / IP *") },
                            placeholder = { Text("198.51.100.1") },
                            modifier = Modifier
                                .weight(2f)
                                .testTag("input_endpoint_host"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endpointPort,
                            onValueChange = { endpointPort = it },
                            label = { Text("Port *") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_endpoint_port"),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = publicKey,
                        onValueChange = { publicKey = it },
                        label = { Text("Server Public Key *") },
                        placeholder = { Text("Base64 32-byte key") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_public_key"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = country,
                            onValueChange = { country = it },
                            label = { Text("Country") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text("City") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = clientIp,
                        onValueChange = { clientIp = it },
                        label = { Text("Client Assigned IP") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = presharedKey,
                        onValueChange = { presharedKey = it },
                        label = { Text("Pre-shared Key (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = dnsServers,
                        onValueChange = { dnsServers = it },
                        label = { Text("DNS Servers") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else {
                    OutlinedTextField(
                        value = rawConfigText,
                        onValueChange = { rawConfigText = it },
                        label = { Text("Paste WireGuard .conf content") },
                        placeholder = {
                            Text(
                                "[Interface]\nPrivateKey = ...\nAddress = 10.66.66.2/32\n\n[Peer]\nPublicKey = ...\nEndpoint = 1.2.3.4:51820\nAllowedIPs = 0.0.0.0/0"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .testTag("input_raw_config")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Parses [Interface] Address and [Peer] Endpoint & PublicKey automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        if (serverName.isBlank()) {
                            errorMessage = "Server name is required"
                            return@Button
                        }
                        if (endpointHost.isBlank()) {
                            errorMessage = "Host/IP is required"
                            return@Button
                        }
                        val portNum = endpointPort.toIntOrNull()
                        if (portNum == null || portNum !in 1..65535) {
                            errorMessage = "Invalid port number"
                            return@Button
                        }
                        if (!WireGuardCrypto.isValidKey(publicKey)) {
                            errorMessage = "Invalid 32-byte Base64 Public Key"
                            return@Button
                        }

                        val newServer = VpnServer(
                            id = "custom-" + UUID.randomUUID().toString().take(8),
                            name = serverName.trim(),
                            country = country.trim(),
                            countryCode = "UN",
                            city = city.trim(),
                            endpointHost = endpointHost.trim(),
                            endpointPort = portNum,
                            publicKey = publicKey.trim(),
                            presharedKey = presharedKey.takeIf { it.isNotBlank() }?.trim(),
                            clientIp = clientIp.trim(),
                            dnsServers = dnsServers.trim(),
                            isCustom = true
                        )
                        onServerAdded(newServer)
                    } else {
                        val parsed = WireGuardConfig.parse(rawConfigText)
                        if (parsed == null) {
                            errorMessage = "Failed to parse WireGuard config. Check [Peer] and Endpoint syntax."
                            return@Button
                        }
                        val endpointParts = parsed.peerEndpoint.split(":")
                        val host = endpointParts.getOrElse(0) { "" }
                        val port = endpointParts.getOrNull(1)?.toIntOrNull() ?: 51820

                        val newServer = VpnServer(
                            id = "custom-" + UUID.randomUUID().toString().take(8),
                            name = if (serverName.isNotBlank()) serverName else "Custom Node ($host)",
                            country = "Custom",
                            countryCode = "UN",
                            city = "Self-Hosted",
                            endpointHost = host,
                            endpointPort = port,
                            publicKey = parsed.peerPublicKey,
                            presharedKey = parsed.presharedKey,
                            clientIp = parsed.address,
                            dnsServers = parsed.dns,
                            isCustom = true
                        )
                        onServerAdded(newServer)
                    }
                },
                modifier = Modifier.testTag("btn_save_server")
            ) {
                Text("Add Server")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
