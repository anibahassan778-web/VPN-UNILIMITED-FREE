package com.example.ui.components

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfoItem(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)

@Composable
fun SplitTunnelDialog(
    selectedPackages: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var appsList by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var currentSelection by remember { mutableStateOf(selectedPackages.toMutableSet()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = try {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            } catch (_: Exception) {
                emptyList()
            }

            val items = installed.map { app ->
                val label = try {
                    pm.getApplicationLabel(app).toString()
                } catch (_: Exception) {
                    app.packageName
                }
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                AppInfoItem(app.packageName, label, isSystem)
            }.sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))

            appsList = items
            isLoading = false
        }
    }

    val filteredApps = remember(appsList, searchQuery) {
        if (searchQuery.isBlank()) appsList
        else appsList.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Split Tunneling", fontWeight = FontWeight.Bold)
                Text(
                    "Selected apps will bypass VPN tunnel directly",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...") },
                    modifier = Modifier.fillMaxWidth().testTag("input_search_split_tunnel"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Scanning installed apps...", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isChecked = currentSelection.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) {
                                            currentSelection = (currentSelection - app.packageName).toMutableSet()
                                        } else {
                                            currentSelection = (currentSelection + app.packageName).toMutableSet()
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            currentSelection = (currentSelection + app.packageName).toMutableSet()
                                        } else {
                                            currentSelection = (currentSelection - app.packageName).toMutableSet()
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = app.packageName,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(currentSelection)
                    onDismiss()
                },
                modifier = Modifier.testTag("btn_confirm_split_tunnel")
            ) {
                Text("Save (${currentSelection.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
