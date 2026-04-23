package com.diaryai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.diaryai.data.model.ScanPage
import com.diaryai.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrReviewScreen(
    viewModel: MainViewModel,
    onProceedToAI: () -> Unit,
    onBack: () -> Unit
) {
    val pages by viewModel.currentPages.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review OCR Text") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = onProceedToAI) {
                        Text("AI Analysis →")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text("Running OCR on device…")
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp, padding.calculateTopPadding(), 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (pages.isEmpty()) {
                    item { Text("No pages scanned yet.", style = MaterialTheme.typography.bodyLarge) }
                } else {
                    itemsIndexed(pages) { index, page ->
                        PageReviewCard(
                            page = page,
                            pageNumber = index + 1,
                            onRunOcr = { viewModel.runOcrOnPage(page) },
                            onTextChanged = { text -> viewModel.updatePageText(page.id, text) }
                        )
                    }
                }

                item {
                    Button(
                        onClick = onProceedToAI,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = pages.any { it.rawOcrText.isNotBlank() }
                    ) {
                        Icon(Icons.Default.Psychology, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Proceed to AI Analysis")
                    }
                }
            }
        }
    }

    uiState.message?.let {
        LaunchedEffect(it) {
            kotlinx.coroutines.delay(1500)
            viewModel.clearMessage()
        }
    }
}

@Composable
fun PageReviewCard(
    page: ScanPage,
    pageNumber: Int,
    onRunOcr: () -> Unit,
    onTextChanged: (String) -> Unit
) {
    var editedText by remember(page.id) { mutableStateOf(page.correctedText.ifBlank { page.rawOcrText }) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Page $pageNumber", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (page.ocrConfidence > 0f) {
                    val pct = (page.ocrConfidence * 100).toInt()
                    val color = when {
                        pct >= 80 -> MaterialTheme.colorScheme.tertiary
                        pct >= 60 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    }
                    AssistChip(onClick = {}, label = { Text("$pct% confidence") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = color.copy(alpha = 0.15f)))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onRunOcr, contentPadding = PaddingValues(8.dp, 4.dp)) {
                    Icon(Icons.Default.DocumentScanner, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("OCR", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (page.rawOcrText.isBlank()) {
                Text("Tap OCR to extract text from this page", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            } else {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it; onTextChanged(it) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 300.dp),
                    label = { Text("Corrected text") },
                    minLines = 3,
                    maxLines = 15
                )
            }
        }
    }
}
