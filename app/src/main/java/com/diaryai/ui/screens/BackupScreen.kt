package com.diaryai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diaryai.data.model.BackupRecord
import com.diaryai.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val backups by viewModel.backupList.collectAsState()
    var confirmRestore by remember { mutableStateOf<BackupRecord?>(null) }

    LaunchedEffect(Unit) { viewModel.loadBackups() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudUpload, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Google Drive Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text("Encrypted archive stored in app-private Drive folder.\nIncludes scans, database, exports, and sync state.",
                            style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { viewModel.backupNow() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else Icon(Icons.Default.Backup, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Backup Now")
                        }
                    }
                }
            }

            item {
                Text("Backup History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp))
            }

            if (backups.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No backups yet", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            } else {
                items(backups) { backup ->
                    BackupRecordCard(backup = backup, onRestore = { confirmRestore = backup })
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Backup Contents", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        listOf("Room database", "All scan images", "Processed assets",
                            "Exports (Markdown/JSON)", "Sync metadata", "App settings").forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    confirmRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore Backup") },
            text = { Text("Restore backup from ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(backup.createdAt))}?\nThe app will need to restart after restore.") },
            confirmButton = {
                Button(onClick = { viewModel.restoreBackup(backup); confirmRestore = null }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } }
        )
    }

    uiState.message?.let {
        LaunchedEffect(it) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessage()
        }
    }
}

@Composable
fun BackupRecordCard(backup: BackupRecord, onRestore: () -> Unit) {
    val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(fmt.format(Date(backup.createdAt)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("${backup.sizeBytes / 1024}KB · v${backup.version}", style = MaterialTheme.typography.bodySmall)
                Text(backup.status.name.lowercase(), style = MaterialTheme.typography.labelSmall,
                    color = when (backup.status.name) {
                        "UPLOADED" -> MaterialTheme.colorScheme.tertiary
                        "FAILED" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    })
            }
            OutlinedButton(onClick = onRestore, contentPadding = PaddingValues(12.dp, 4.dp)) {
                Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Restore")
            }
        }
    }
}
