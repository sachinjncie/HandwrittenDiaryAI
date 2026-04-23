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
import com.diaryai.data.model.KnowledgeEntry
import com.diaryai.data.model.TaskItem
import com.diaryai.data.model.TaskPriority
import com.diaryai.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiResultsScreen(
    viewModel: MainViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val tasks by viewModel.extractedTasks.collectAsState()
    val knowledge by viewModel.extractedKnowledge.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.runAiOnCurrentSession()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Suggestions") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text("Gemma is analysing your diary…")
                    Text("Extracting tasks and knowledge", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Tasks (${tasks.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Knowledge (${knowledge.size})") })
            }

            when (selectedTab) {
                0 -> TasksTab(tasks = tasks, viewModel = viewModel, onDone = onDone)
                1 -> KnowledgeTab(knowledge = knowledge, viewModel = viewModel, onDone = onDone)
            }
        }
    }
}

@Composable
fun TasksTab(tasks: List<TaskItem>, viewModel: MainViewModel, onDone: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tasks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No tasks extracted — all approved or none found", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${tasks.size} tasks found", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { viewModel.approveAllTasks() }) { Text("Approve All") }
                }
            }
            items(tasks) { task ->
                TaskSuggestionCard(task = task, onApprove = { viewModel.approveTask(task) })
            }
        }
        item {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Done — Save to Archive")
            }
        }
    }
}

@Composable
fun TaskSuggestionCard(task: TaskItem, onApprove: () -> Unit) {
    val priorityColor = when (task.priority) {
        TaskPriority.CRITICAL -> MaterialTheme.colorScheme.error
        TaskPriority.HIGH -> MaterialTheme.colorScheme.tertiary
        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primary
        TaskPriority.LOW -> MaterialTheme.colorScheme.secondary
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (task.description.isNotBlank()) Text(task.description, style = MaterialTheme.typography.bodySmall)
                }
                AssistChip(onClick = {}, label = { Text(task.priority.name) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = priorityColor.copy(alpha = 0.15f)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                task.dueDateHint?.let { Text("Due: $it", style = MaterialTheme.typography.labelSmall) }
                Spacer(Modifier.weight(1f))
                Text("${(task.confidence * 100).toInt()}% confidence", style = MaterialTheme.typography.labelSmall)
                FilledTonalButton(onClick = onApprove, contentPadding = PaddingValues(12.dp, 4.dp)) {
                    Text("Approve")
                }
            }
        }
    }
}

@Composable
fun KnowledgeTab(knowledge: List<KnowledgeEntry>, viewModel: MainViewModel, onDone: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (knowledge.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No knowledge entries — all approved or none found", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${knowledge.size} knowledge entries", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { viewModel.approveAllKnowledge() }) { Text("Approve All") }
                }
            }
            items(knowledge) { entry ->
                KnowledgeSuggestionCard(entry = entry, onApprove = { viewModel.approveKnowledge(entry) })
            }
        }
        item { Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") } }
    }
}

@Composable
fun KnowledgeSuggestionCard(entry: KnowledgeEntry, onApprove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(entry.summary, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                entry.tags.split(",").filter { it.isNotBlank() }.take(3).forEach { tag ->
                    SuggestionChip(onClick = {}, label = { Text(tag.trim(), style = MaterialTheme.typography.labelSmall) })
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = onApprove, contentPadding = PaddingValues(12.dp, 4.dp)) { Text("Approve") }
            }
        }
    }
}
