package com.diaryai.ui.screens

import androidx.compose.foundation.clickable
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
import com.diaryai.data.model.DiarySession
import com.diaryai.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNewScan: () -> Unit,
    onSessionClick: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val sessions by viewModel.sessions.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<DiarySession?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Handwritten Diary AI", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { onNavigate("search") }) { Icon(Icons.Default.Search, "Search") }
                    IconButton(onClick = { onNavigate("settings") }) { Icon(Icons.Default.Settings, "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigate("tasks") },
                    icon = { Icon(Icons.Default.CheckCircle, null) },
                    label = { Text("Tasks") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigate("knowledge") },
                    icon = { Icon(Icons.Default.Book, null) },
                    label = { Text("Knowledge") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigate("sync") },
                    icon = { Icon(Icons.Default.Sync, null) },
                    label = { Text("Sync") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigate("backup") },
                    icon = { Icon(Icons.Default.CloudUpload, null) },
                    label = { Text("Backup") }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewScan,
                icon = { Icon(Icons.Default.CameraAlt, null) },
                text = { Text("Scan Page") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Dashboard stats
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allTasks by viewModel.allTasks.collectAsState()
                val allKnowledge by viewModel.allKnowledge.collectAsState()
                StatCard("Sessions", sessions.size.toString(), Modifier.weight(1f))
                StatCard("Tasks", allTasks.size.toString(), Modifier.weight(1f))
                StatCard("Knowledge", allKnowledge.size.toString(), Modifier.weight(1f))
            }

            if (sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EditNote, null, modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text("No diary entries yet", style = MaterialTheme.typography.titleMedium)
                        Text("Tap Scan Page to get started", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(sessions) { session ->
                        SessionCard(
                            session = session,
                            onClick = { onSessionClick(session.id) },
                            onDelete = { showDeleteDialog = session }
                        )
                    }
                }
            }
        }
    }

    uiState.message?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearMessage()
        }
    }

    showDeleteDialog?.let { session ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Entry") },
            text = { Text("Delete '${session.title}'? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSession(session); showDeleteDialog = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SessionCard(session: DiarySession, onClick: () -> Unit, onDelete: () -> Unit) {
    val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(session.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(fmt.format(Date(session.updatedAt)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text("${session.pageCount} pages · ${session.status.name.lowercase()}", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)) }
        }
    }
}
