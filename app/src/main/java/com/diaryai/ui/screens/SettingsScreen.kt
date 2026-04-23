package com.diaryai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.diaryai.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val settings = viewModel.settingsManager
    var notionToken by remember { mutableStateOf(settings.notionToken) }
    var notionTasksDb by remember { mutableStateOf(settings.notionTasksDbId) }
    var notionKbDb by remember { mutableStateOf(settings.notionKnowledgeDbId) }
    var autoSync by remember { mutableStateOf(settings.autoSyncEnabled) }
    var autoBackup by remember { mutableStateOf(settings.autoBackupEnabled) }
    var showNotionToken by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Notion ────────────────────────────────────────────────────────
            item {
                Text("Notion Free", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = notionToken,
                            onValueChange = { notionToken = it },
                            label = { Text("Notion Integration Token") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (showNotionToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showNotionToken = !showNotionToken }) {
                                    Icon(if (showNotionToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                                }
                            },
                            supportingText = { Text("From notion.so → Settings → Integrations → Internal") }
                        )
                        OutlinedTextField(
                            value = notionTasksDb,
                            onValueChange = { notionTasksDb = it },
                            label = { Text("Tasks Database ID") },
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("32-char ID from your Tasks database URL") }
                        )
                        OutlinedTextField(
                            value = notionKbDb,
                            onValueChange = { notionKbDb = it },
                            label = { Text("Knowledge Database ID") },
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("32-char ID from your Knowledge database URL") }
                        )
                        Button(
                            onClick = {
                                settings.notionToken = notionToken
                                settings.notionTasksDbId = notionTasksDb
                                settings.notionKnowledgeDbId = notionKbDb
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save Notion Settings") }
                    }
                }
            }

            // ── Google Drive ───────────────────────────────────────────────────
            item { Text("Google Drive Backup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Sign in with Google to enable encrypted Drive backup.\nBackups are stored in a private appDataFolder — invisible to other apps.",
                            style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { /* TODO: trigger Google Sign-In */ }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.AccountCircle, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sign in with Google")
                        }
                    }
                }
            }

            // ── Automation ─────────────────────────────────────────────────────
            item { Text("Automation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("Auto-Sync", style = MaterialTheme.typography.bodyMedium)
                                Text("Sync every 6h on Wi-Fi", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = autoSync, onCheckedChange = { autoSync = it; settings.autoSyncEnabled = it })
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("Auto-Backup", style = MaterialTheme.typography.bodyMedium)
                                Text("Daily on Wi-Fi + charging", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = autoBackup, onCheckedChange = { autoBackup = it; settings.autoBackupEnabled = it })
                        }
                    }
                }
            }

            // ── AI ─────────────────────────────────────────────────────────────
            item { Text("AI Model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Gemma 4 on-device model", style = MaterialTheme.typography.bodyMedium)
                        Text("Place the .task model file in assets/ or use the download path. The model path is configured automatically after first run.",
                            style = MaterialTheme.typography.bodySmall)
                        val modelStatus = if (settings.gemmaModelPath.isNotBlank()) "Model path set" else "No model loaded — AI features use rule-based fallback"
                        Text(modelStatus, style = MaterialTheme.typography.labelMedium,
                            color = if (settings.gemmaModelPath.isNotBlank()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ── About ──────────────────────────────────────────────────────────
            item { Text("About", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Handwritten Diary AI", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall)
                        Text("Local-first · Notion Free · Google Drive backup", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Text("Stack: Kotlin · Jetpack Compose · Room · ML Kit · WorkManager", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
