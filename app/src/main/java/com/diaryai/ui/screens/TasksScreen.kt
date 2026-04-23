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
import com.diaryai.data.model.TaskItem
import com.diaryai.data.model.TaskStatus
import com.diaryai.ui.viewmodel.MainViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val allTasks by viewModel.allTasks.collectAsState()
    var statusFilter by remember { mutableStateOf<TaskStatus?>(null) }

    val filtered = if (statusFilter == null) allTasks else allTasks.filter { it.status == statusFilter }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Filter chips
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text("All") })
                FilterChip(selected = statusFilter == TaskStatus.OPEN, onClick = { statusFilter = TaskStatus.OPEN }, label = { Text("Open") })
                FilterChip(selected = statusFilter == TaskStatus.DONE, onClick = { statusFilter = TaskStatus.DONE }, label = { Text("Done") })
                FilterChip(selected = statusFilter == TaskStatus.IN_PROGRESS, onClick = { statusFilter = TaskStatus.IN_PROGRESS }, label = { Text("Active") })
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tasks yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered) { task ->
                        FullTaskCard(task = task, onToggleDone = {
                            val updated = task.copy(status = if (task.status == TaskStatus.DONE) TaskStatus.OPEN else TaskStatus.DONE)
                            viewModel.viewModelScope.launch { /* status update handled via repo in a real impl */ }
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun FullTaskCard(task: TaskItem, onToggleDone: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = task.status == TaskStatus.DONE,
                onCheckedChange = { onToggleDone() }
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (task.description.isNotBlank()) Text(task.description, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(task.priority.name.lowercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    task.dueDateHint?.let { Text("Due: $it", style = MaterialTheme.typography.labelSmall) }
                    val syncIcon = when (task.notionSyncStatus.name) {
                        "SYNCED" -> "☁"
                        "FAILED" -> "✗"
                        else -> "○"
                    }
                    Text("$syncIcon Notion", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        }
    }
}


