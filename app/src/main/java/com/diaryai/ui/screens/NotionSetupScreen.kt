package com.diaryai.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.diaryai.ui.viewmodel.MainViewModel

/**
 * Friendly step-by-step Notion setup wizard.
 * Replaces the raw token + database ID fields with guided instructions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotionSetupScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings = viewModel.settingsManager

    var currentStep by remember { mutableIntStateOf(1) }
    var token by remember { mutableStateOf(settings.notionToken) }
    var tasksDbId by remember { mutableStateOf(settings.notionTasksDbId) }
    var knowledgeDbId by remember { mutableStateOf(settings.notionKnowledgeDbId) }
    var showToken by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(
        settings.notionToken.isNotBlank() &&
        settings.notionTasksDbId.isNotBlank() &&
        settings.notionKnowledgeDbId.isNotBlank()
    )}

    val totalSteps = 5

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notion Setup") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Configured banner ─────────────────────────────────────────
            if (saved) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.CheckCircle, null,
                                tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(28.dp))
                            Column {
                                Text("Notion is configured!", fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge)
                                Text("Sync will push tasks and knowledge to your workspace.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // ── Step progress ─────────────────────────────────────────────
            item {
                StepProgressBar(currentStep = currentStep, totalSteps = totalSteps)
            }

            // ── Steps ─────────────────────────────────────────────────────
            item {
                when (currentStep) {
                    1 -> StepCard(
                        step = 1, title = "Create a free Notion account",
                        onNext = { currentStep = 2 }, onBack = null
                    ) {
                        Text("If you don't have a Notion account yet, sign up for free at notion.so — the free plan is all you need.",
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { context.openUrl("https://www.notion.so/signup") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInBrowser, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open notion.so/signup")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Already have an account? Tap Next.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }

                    2 -> StepCard(
                        step = 2, title = "Create an Integration",
                        onNext = { currentStep = 3 }, onBack = { currentStep = 1 }
                    ) {
                        InstructionStep(1, "Go to notion.so/my-integrations")
                        InstructionStep(2, "Click \"+ New integration\"")
                        InstructionStep(3, "Name it \"Diary AI\" and select your workspace")
                        InstructionStep(4, "Under Capabilities, enable: Read, Update, Insert content")
                        InstructionStep(5, "Click Submit — then copy the Internal Integration Token")

                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { context.openUrl("https://www.notion.so/my-integrations") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInBrowser, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open Notion Integrations page")
                        }
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("Paste your Integration Token here") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showToken) VisualTransformation.None
                                                   else PasswordVisualTransformation(),
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = { showToken = !showToken }) {
                                        Icon(if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                                    }
                                    IconButton(onClick = {
                                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        token = clip.primaryClip?.getItemAt(0)?.text?.toString() ?: token
                                    }) {
                                        Icon(Icons.Default.ContentPaste, null)
                                    }
                                }
                            },
                            placeholder = { Text("secret_xxxxxxxxxxxx") },
                            supportingText = { Text("Starts with \"secret_\"") }
                        )
                    }

                    3 -> StepCard(
                        step = 3, title = "Create your Tasks database",
                        onNext = { if (tasksDbId.isNotBlank()) currentStep = 4 },
                        nextEnabled = tasksDbId.isNotBlank(),
                        onBack = { currentStep = 2 }
                    ) {
                        InstructionStep(1, "In Notion, create a new page — name it \"Diary Tasks\"")
                        InstructionStep(2, "Click the \"+\" to add a database block → choose \"Table\"")
                        InstructionStep(3, "Add these columns: Name, Description, Priority, Status, DueHint, Tags, LocalId")
                        InstructionStep(4, "Click the ··· menu → Connections → add your \"Diary AI\" integration")
                        InstructionStep(5, "Copy the Database ID from the URL:")

                        DatabaseIdHint()

                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tasksDbId,
                            onValueChange = { tasksDbId = it.trim().replace("-", "") },
                            label = { Text("Tasks Database ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    tasksDbId = clip.primaryClip?.getItemAt(0)?.text?.toString()
                                        ?.trim()?.replace("-", "") ?: tasksDbId
                                }) { Icon(Icons.Default.ContentPaste, null) }
                            },
                            placeholder = { Text("32-character ID") },
                            isError = tasksDbId.isNotBlank() && tasksDbId.length != 32,
                            supportingText = {
                                if (tasksDbId.isNotBlank() && tasksDbId.length != 32)
                                    Text("Should be exactly 32 characters — remove hyphens",
                                        color = MaterialTheme.colorScheme.error)
                                else Text("${tasksDbId.length}/32 characters")
                            }
                        )
                    }

                    4 -> StepCard(
                        step = 4, title = "Create your Knowledge database",
                        onNext = { if (knowledgeDbId.isNotBlank()) currentStep = 5 },
                        nextEnabled = knowledgeDbId.isNotBlank(),
                        onBack = { currentStep = 3 }
                    ) {
                        InstructionStep(1, "Create another new page — name it \"Diary Knowledge\"")
                        InstructionStep(2, "Add a Table database block")
                        InstructionStep(3, "Columns: Name, Summary, Tags, Category, LocalId")
                        InstructionStep(4, "Connect your \"Diary AI\" integration via ··· → Connections")
                        InstructionStep(5, "Copy the Database ID from the URL (same as step 3)")

                        DatabaseIdHint()

                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = knowledgeDbId,
                            onValueChange = { knowledgeDbId = it.trim().replace("-", "") },
                            label = { Text("Knowledge Database ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    knowledgeDbId = clip.primaryClip?.getItemAt(0)?.text?.toString()
                                        ?.trim()?.replace("-", "") ?: knowledgeDbId
                                }) { Icon(Icons.Default.ContentPaste, null) }
                            },
                            placeholder = { Text("32-character ID") },
                            isError = knowledgeDbId.isNotBlank() && knowledgeDbId.length != 32,
                            supportingText = {
                                if (knowledgeDbId.isNotBlank() && knowledgeDbId.length != 32)
                                    Text("Should be exactly 32 characters — remove hyphens",
                                        color = MaterialTheme.colorScheme.error)
                                else Text("${knowledgeDbId.length}/32 characters")
                            }
                        )
                    }

                    5 -> StepCard(
                        step = 5, title = "Save & test connection",
                        nextLabel = "Finish",
                        onNext = {
                            settings.notionToken = token
                            settings.notionTasksDbId = tasksDbId
                            settings.notionKnowledgeDbId = knowledgeDbId
                            saved = true
                            onBack()
                        },
                        nextEnabled = token.isNotBlank() && tasksDbId.length == 32 && knowledgeDbId.length == 32,
                        onBack = { currentStep = 4 }
                    ) {
                        // Summary of what was entered
                        SummaryRow("Token", if (token.isNotBlank()) "✓ Set (${token.take(12)}…)" else "✗ Missing")
                        SummaryRow("Tasks DB ID", if (tasksDbId.length == 32) "✓ $tasksDbId" else "✗ Missing or invalid")
                        SummaryRow("Knowledge DB ID", if (knowledgeDbId.length == 32) "✓ $knowledgeDbId" else "✗ Missing or invalid")

                        Spacer(Modifier.height(12.dp))
                        Text("What gets synced to Notion:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        listOf(
                            "✓ Task titles, descriptions, priority, due hints, tags",
                            "✓ Knowledge entries: title, summary, tags, body",
                            "✗ Original scans (too large — stay local + Drive backup)",
                            "✗ Attachments >5 MB (Notion Free limit)"
                        ).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

// ── Sub-components ────────────────────────────────────────────────────────────

@Composable
fun StepProgressBar(currentStep: Int, totalSteps: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (1..totalSteps).forEach { step ->
            val active = step == currentStep
            val done = step < currentStep
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            done   -> MaterialTheme.colorScheme.tertiary
                            active -> MaterialTheme.colorScheme.primary
                            else   -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                    )
            )
        }
        Text("$currentStep / $totalSteps", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
fun StepCard(
    step: Int,
    title: String,
    onNext: () -> Unit,
    onBack: (() -> Unit)?,
    nextLabel: String = "Next",
    nextEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$step", color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            content()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onBack != null) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text("Back")
                    }
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(if (onBack != null) 1f else 1f),
                    enabled = nextEnabled
                ) { Text(nextLabel) }
            }
        }
    }
}

@Composable
fun InstructionStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 2.dp)) {
        Box(
            modifier = Modifier.size(20.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
fun DatabaseIdHint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Finding the Database ID:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text("Open the database page in a browser. The URL looks like:", style = MaterialTheme.typography.bodySmall)
            Text(
                "notion.so/your-name/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx?v=...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text("The 32 hex characters after the last / and before ? is the ID.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.6f))
    }
}

fun Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
