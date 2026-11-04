package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VpnServer
import com.example.model.WireGuardConfig
import com.example.ui.theme.EmeraldConnected
import java.util.UUID

@Composable
fun AddCustomServerDialog(
    onDismiss: () -> Unit,
    onServerAdded: (VpnServer) -> Unit
) {
    val context = LocalContext.current
    var rawConfigText by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf("") }
    var countryName by remember { mutableStateOf("WireGuard Node") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var parsedConfig by remember { mutableStateOf<WireGuardConfig?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!content.isNullOrBlank()) {
                    rawConfigText = content
                    val filename = getFileNameFromUri(context, uri)
                    if (!filename.isNullOrBlank() && profileName.isBlank()) {
                        profileName = filename
                    }
                    val config = WireGuardConfig.parse(content)
                    if (config != null) {
                        parsedConfig = config
                        errorMessage = null
                        Toast.makeText(context, "Valid WireGuard configuration loaded", Toast.LENGTH_SHORT).show()
                    } else {
                        parsedConfig = null
                        errorMessage = "Invalid WireGuard format: missing [Interface] or [Peer] section"
                    }
                } else {
                    errorMessage = "The selected file was empty"
                }
            } catch (e: Exception) {
                errorMessage = "Failed to read file: ${e.message}"
            }
        }
    }

    val updateParsedState: (String) -> Unit = { text ->
        rawConfigText = text
        if (text.isNotBlank()) {
            val config = WireGuardConfig.parse(text)
            if (config != null) {
                parsedConfig = config
                errorMessage = null
                if (profileName.isBlank()) {
                    val host = config.peerEndpoint.split(":").firstOrNull() ?: "wireguard"
                    profileName = "WG-$host"
                }
            } else {
                parsedConfig = null
            }
        } else {
            parsedConfig = null
            errorMessage = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import WireGuard Profile", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "WireShield operates exclusively with genuine WireGuard .conf files. Select a .conf file from your device or paste its content below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // File Picker Button
                OutlinedButton(
                    onClick = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_select_conf_file"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select .conf File From Storage", fontWeight = FontWeight.SemiBold)
                }

                // Profile Name Field
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile Name (Optional)") },
                    placeholder = { Text("e.g. Home Server, Mullvad Amsterdam") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_conf_profile_name"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Paste Configuration Area
                OutlinedTextField(
                    value = rawConfigText,
                    onValueChange = updateParsedState,
                    label = { Text("WireGuard .conf Content") },
                    placeholder = {
                        Text(
                            "[Interface]\nPrivateKey = ...\nAddress = 10.66.66.2/32\nDNS = 1.1.1.1\n\n[Peer]\nPublicKey = ...\nEndpoint = 198.51.100.1:51820\nAllowedIPs = 0.0.0.0/0, ::/0"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("input_raw_config"),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                // Parsed Config Inspection Preview Card
                if (parsedConfig != null) {
                    val cfg = parsedConfig!!
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldConnected,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Valid WireGuard Configuration",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldConnected
                                )
                            }
                            Text(
                                text = "• Endpoint: ${cfg.peerEndpoint}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "• Client IP: ${cfg.address}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "• DNS: ${cfg.dns}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "• Peer Key: ${cfg.peerPublicKey.take(16)}...",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            if (cfg.privateKey.isNotBlank()) {
                                Text(
                                    text = "• Client Private Key: Verified Present",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldConnected,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                if (errorMessage != null) {
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
                    val config = parsedConfig ?: WireGuardConfig.parse(rawConfigText)
                    if (config == null) {
                        errorMessage = "Cannot save: Please provide a valid WireGuard configuration with [Interface] and [Peer]."
                        return@Button
                    }

                    val endpointParts = config.peerEndpoint.split(":")
                    val host = endpointParts.getOrElse(0) { "" }
                    val port = endpointParts.getOrNull(1)?.toIntOrNull() ?: 51820

                    val finalName = if (profileName.isNotBlank()) profileName.trim() else "WG-$host"

                    val newServer = VpnServer(
                        id = "imported-" + UUID.randomUUID().toString().take(8),
                        name = finalName,
                        country = countryName,
                        countryCode = "UN",
                        city = host,
                        endpointHost = host,
                        endpointPort = port,
                        publicKey = config.peerPublicKey,
                        presharedKey = config.presharedKey,
                        clientPrivateKey = config.privateKey.takeIf { it.isNotBlank() },
                        clientIp = config.address,
                        dnsServers = config.dns,
                        pingMs = -1,
                        loadPercent = 0,
                        isCustom = true,
                        isOnline = true
                    )
                    onServerAdded(newServer)
                },
                modifier = Modifier.testTag("btn_save_server"),
                enabled = rawConfigText.isNotBlank()
            ) {
                Text("Import Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (_: Exception) {
            // fallback
        }
    }
    if (result == null) {
        result = uri.path?.let { path ->
            val cut = path.lastIndexOf('/')
            if (cut != -1) path.substring(cut + 1) else path
        }
    }
    return result?.removeSuffix(".conf")?.removeSuffix(".txt")
}
