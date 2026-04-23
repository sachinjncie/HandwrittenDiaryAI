package com.diaryai.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.diaryai.service.ModelDownloadState
import com.diaryai.ui.viewmodel.MainViewModel
import com.diaryai.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings = viewModel.settingsManager
    val googleAuthState by settingsViewModel.googleAuthState.collectAsState()
    val modelStatus by settingsViewModel.modelDownloadStatus.collectAsState()

    var notionToken by remember { mutableStateOf(settings.notionToken) }
    var notionTasksDb by remember { mutableStateOf(settings.notionTasksDbId) }
    var notionKbDb by remember { mutableStateOf(settings.notionKnowledgeDbId) }
    var autoSync by remember { mutableStateOf(settings.autoSyncEnabled) }
    var autoBackup by remember { mutableStateOf(settings.autoBackupEnabled) }
    var showNotionToken by remember { mutableStateOf(false) }
    var showModelUrlDialog by remember { mutableStateOf(false) }
    var customModelUrl by remember { mutableStateOf("") }

    // ── Google Sign-In launcher ──────────────────────────────────────────
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        settingsViewModel.handleGoogleSignInResult(result.data)
    }

    // Poll download progress while downloading
    LaunchedEffect(modelStatus.state) {
        while (modelStatus.state == ModelDownloadState.DOWNLOADING) {
            kotlinx.coroutines.delay(1000)
            settingsViewModel.pollModelDownload()
        }
    }

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

            // ── Notion ────────────────────────────────────────────────────
            item { SectionHeader("Notion Free Sync") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = notionToken,
                            onValueChange = { notionToken = it },
                            label = { Text("Notion Integration Token") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showNotionToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showNotionToken = !showNotionToken }) {
                                    Icon(if (showNotionToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                                }
                            },
                            supportingText = { Text("From notion.so → Settings → Integrations → New integration") }
                        )
                        OutlinedTextField(
                            value = notionTasksDb,
                            onValueChange = { notionTasksDb = it },
                            label = { Text("Tasks Database ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = { Text("32-char ID from your Tasks database URL") }
                        )
                        OutlinedTextField(
                            value = notionKbDb,
                            onValueChange = { notionKbDb = it },
                            label = { Text("Knowledge Database ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = { Text("32-char ID from your Knowledge database URL") }
                        )
                        Button(
                            onClick = {
                                settings.notionToken = notionToken
                                settings.notionTasksDbId = notionTasksDb
                                settings.notionKnowledgeDbId = notionKbDb
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save Notion Settings")
                        }

                        // Notion configured indicator
                        val configured = notionToken.isNotBlank() && notionTasksDb.isNotBlank() && notionKbDb.isNotBlank()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                if (configured) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                null,
                                tint = if (configured) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                if (configured) "Notion configured" else "Not configured",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (configured) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // ── Google Drive ───────────────────────────────────────────────
            item { SectionHeader("Google Drive Backup") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                        if (googleAuthState.isSignedIn) {
                            // Signed-in state
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.AccountCircle, null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(32.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(googleAuthState.accountName ?: "Google Account",
                                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(googleAuthState.accountEmail ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                Icon(Icons.Default.CheckCircle, null,
                                    tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                            }
                            Text("Drive backup is active — encrypted archives stored in private appDataFolder",
                                style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(
                                onClick = { settingsViewModel.googleSignOut() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Logout, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Sign Out")
                            }
                        } else {
                            // Signed-out state
                            Text("Sign in to enable encrypted Google Drive backup.",
                                style = MaterialTheme.typography.bodySmall)
                            Text("Backups are stored in a private app folder — not visible in your Drive.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

                            Button(
                                onClick = {
                                    val signInIntent = settingsViewModel.getGoogleSignInIntent()
                                    googleSignInLauncher.launch(signInIntent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AccountCircle, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Sign in with Google")
                            }

                            googleAuthState.error?.let { err ->
                                Text(err, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // ── Gemma AI Model ─────────────────────────────────────────────
            item { SectionHeader("Gemma AI Model") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("On-Device Gemma 4", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("OCR correction · Task extraction · Knowledge generation",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }

                        when (modelStatus.state) {
                            ModelDownloadState.IDLE -> {
                                Text("Model not downloaded. The model is ~537 MB — download on Wi-Fi.",
                                    style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = { settingsViewModel.downloadGemmaModel() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.CloudDownload, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Download Gemma 3 1B (~537 MB)")
                                }
                                OutlinedButton(
                                    onClick = { showModelUrlDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Link, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Use Custom Model URL")
                                }
                            }

                            ModelDownloadState.DOWNLOADING -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Downloading… ${modelStatus.downloadedMb.toInt()} / ${modelStatus.totalMb.toInt()} MB",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (modelStatus.totalMb > 0) {
                                        LinearProgressIndicator(
                                            progress = { modelStatus.progressPercent / 100f },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                    Text("${modelStatus.progressPercent}%", style = MaterialTheme.typography.labelSmall)
                                    OutlinedButton(
                                        onClick = { settingsViewModel.cancelModelDownload() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Cancel, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Cancel Download")
                                    }
                                }
                            }

                            ModelDownloadState.COMPLETED, ModelDownloadState.ALREADY_EXISTS -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CheckCircle, null,
                                        tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                                    Text("Model ready — AI features active",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.tertiary)
                                }
                                modelStatus.modelPath?.let { path ->
                                    Text(path.takeLast(50), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                }
                                OutlinedButton(
                                    onClick = { settingsViewModel.deleteGemmaModel() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Delete, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Delete Model (free up space)")
                                }
                            }

                            ModelDownloadState.FAILED -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Error, null,
                                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    Text("Download failed", style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error)
                                }
                                modelStatus.error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                Button(
                                    onClick = { settingsViewModel.downloadGemmaModel() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Retry Download")
                                }
                                OutlinedButton(
                                    onClick = { showModelUrlDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Link, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Use Custom Model URL")
                                }
                            }
                        }
                    }
                }
            }

            // ── Automation ─────────────────────────────────────────────────
            item { SectionHeader("Automation") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("Auto-Sync to Notion", style = MaterialTheme.typography.bodyMedium)
                                Text("Every 6h on Wi-Fi", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = autoSync, onCheckedChange = { autoSync = it; settings.autoSyncEnabled = it })
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("Auto-Backup to Drive", style = MaterialTheme.typography.bodyMedium)
                                Text("Daily on Wi-Fi + charging", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = autoBackup, onCheckedChange = { autoBackup = it; settings.autoBackupEnabled = it })
                        }
                    }
                }
            }

            // ── About ──────────────────────────────────────────────────────
            item { SectionHeader("About") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Handwritten Diary AI", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall)
                        Text("Local-first · Notion Free · Google Drive backup", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Text("Kotlin · Jetpack Compose · Room · ML Kit · Gemma 4 · WorkManager",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    // ── Custom model URL dialog ────────────────────────────────────────────
    if (showModelUrlDialog) {
        AlertDialog(
            onDismissRequest = { showModelUrlDialog = false },
            title = { Text("Custom Model URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the direct download URL for a MediaPipe LLM Inference .task model file (Gemma 3/4 INT4 or INT8).",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = customModelUrl,
                        onValueChange = { customModelUrl = it },
                        label = { Text("Model URL") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://…/gemma_model.task") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (customModelUrl.isNotBlank()) {
                        settingsViewModel.downloadGemmaModel(customModelUrl)
                    }
                    showModelUrlDialog = false
                }) { Text("Start Download") }
            },
            dismissButton = { TextButton(onClick = { showModelUrlDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}
