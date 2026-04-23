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
import androidx.compose.ui.unit.dp
import com.diaryai.sync.SyncReport
import com.diaryai.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncCenterScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val syncReport by viewModel.syncReport.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()
    val allKnowledge by viewModel.allKnowledge.collectAsState()
    val settings = viewModel.settingsManager

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync Center") },
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
                            Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Notion Free Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }

                        val configured = settings.isNotionConfigured
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (configured) Icons.Default.CheckCircle else Icons.Default.Warning,
                                null,
                                tint = if (configured) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (configured) "Connected" else "Not configured — set token in Settings",
                                style = MaterialTheme.typography.bodySmall)
                        }

                        val pendingTasks = allTasks.count { it.notionSyncStatus.name == "PENDING" }
                        val pendingKb = allKnowledge.count { it.notionSyncStatus.name == "PENDING" }
                        Text("Pending: $pendingTasks tasks, $pendingKb knowledge entries", style = MaterialTheme.typography.bodySmall)

                        Button(
                            onClick = { viewModel.syncNow() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading && configured
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else Icon(Icons.Default.Sync, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sync Now")
                        }
                    }
                }
            }

            syncReport?.let { report ->
                item {
                    SyncReportCard(report)
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sync Policy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("• Text-first sync — original scans stay local", style = MaterialTheme.typography.bodySmall)
                        Text("• Attachments >5 MB are skipped (Notion Free limit)", style = MaterialTheme.typography.bodySmall)
                        Text("• One-way: local → Notion", style = MaterialTheme.typography.bodySmall)
                        Text("• Duplicate prevention via external local IDs", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    uiState.error?.let { err ->
        LaunchedEffect(err) {
            kotlinx.coroutines.delay(4000)
            viewModel.clearMessage()
        }
        Snackbar(modifier = Modifier.padding(16.dp)) { Text(err) }
    }
}

@Composable
fun SyncReportCard(report: SyncReport) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Last Sync Report", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("✓ ${report.syncedTasks} tasks synced", style = MaterialTheme.typography.bodySmall)
            Text("✓ ${report.syncedKnowledge} knowledge entries synced", style = MaterialTheme.typography.bodySmall)
            if (report.skippedAttachments > 0)
                Text("⚠ ${report.skippedAttachments} attachments skipped (>5 MB)", style = MaterialTheme.typography.bodySmall)
            if (report.failedItems > 0)
                Text("✗ ${report.failedItems} items failed", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
        }
    }
}
