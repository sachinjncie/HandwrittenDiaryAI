package com.diaryai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diaryai.data.model.KnowledgeEntry
import com.diaryai.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val allKnowledge by viewModel.allKnowledge.collectAsState()
    var selectedTag by remember { mutableStateOf<String?>(null) }

    val allTags = allKnowledge.flatMap { it.tags.split(",").filter { t -> t.isNotBlank() } }.distinct().sorted()
    val filtered = if (selectedTag == null) allKnowledge
    else allKnowledge.filter { it.tags.contains(selectedTag!!) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Knowledge Base") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Tag filter strip
            if (allTags.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(selected = selectedTag == null, onClick = { selectedTag = null }, label = { Text("All") })
                    }
                    items(allTags) { tag ->
                        FilterChip(selected = selectedTag == tag, onClick = { selectedTag = if (selectedTag == tag) null else tag }, label = { Text(tag.trim()) })
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LibraryBooks, null, modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        Spacer(Modifier.height(8.dp))
                        Text("No knowledge entries yet", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered) { entry ->
                        KnowledgeEntryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
fun KnowledgeEntryCard(entry: KnowledgeEntry) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(entry.summary, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) Int.MAX_VALUE else 2)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, modifier = Modifier.size(20.dp)
                )
            }

            if (expanded && entry.body.isNotBlank()) {
                HorizontalDivider()
                Text(entry.body, style = MaterialTheme.typography.bodySmall)
            }

            val tags = entry.tags.split(",").filter { it.isNotBlank() }
            if (tags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(tags) { tag ->
                        SuggestionChip(onClick = {}, label = { Text(tag.trim(), style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }
        }
    }
}
