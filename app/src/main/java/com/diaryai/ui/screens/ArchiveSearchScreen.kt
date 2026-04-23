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
import com.diaryai.data.model.SearchResult
import com.diaryai.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveSearchScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.searchResults.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()
    val allKnowledge by viewModel.allKnowledge.collectAsState()
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(query) {
        kotlinx.coroutines.delay(300)
        viewModel.search(query)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search diary, tasks, knowledge…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = if (query.isNotBlank()) {
                            { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, null) } }
                        } else null
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("All (${results.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Tasks (${allTasks.size})") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Knowledge (${allKnowledge.size})") })
            }

            when (tab) {
                0 -> SearchResultsList(results)
                1 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allTasks.filter { query.isBlank() || it.title.contains(query, true) }) { task ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("${task.priority.name} · ${task.status.name}", style = MaterialTheme.typography.bodySmall)
                                task.dueDateHint?.let { Text("Due: $it", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
                2 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allKnowledge.filter { query.isBlank() || it.title.contains(query, true) || it.tags.contains(query, true) }) { entry ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(entry.summary, style = MaterialTheme.typography.bodySmall)
                                Text(entry.tags, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultsList(results: List<SearchResult>) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Spacer(Modifier.height(8.dp))
                Text("No results found", style = MaterialTheme.typography.bodyLarge)
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { result ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (result.type == "task") Icons.Default.CheckCircle else Icons.Default.Book,
                            null, modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(result.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (result.snippet.isNotBlank()) Text(result.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}
