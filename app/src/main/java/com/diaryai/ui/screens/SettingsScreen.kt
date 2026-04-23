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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.diaryai.service.ModelDownloadState
import com.diaryai.ui.viewmodel.MainViewModel
import com.diaryai.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNotionSetup: () -> Unit,
    onBack: () -> Unit
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val context = LocalContext.current
    val settings = viewModel.settingsManager
    val googleAuthState by settingsViewModel.googleAuthState.collectAsState()
    val modelStatus by settingsViewModel.modelDownloadStatus.collectAsState()

    var autoSync by remember { mutableStateOf(settings.autoSyncEnabled) }
    var autoBackup by remember { mutableStateOf(settings.autoBackupEnabled) }
    var showModelUrlDialog by remember { mutableStateOf(false) }
    var customModelUrl by remember { mutableStateOf("") }
    var showDriveGuide by remember { mutableStateOf(googleAuthState.needsSetup) }

    // Update guide visibility when auth state changes
    LaunchedEffect(googleAuthState.needsSetup) {
        if (googleAuthState.needsSetup) showDriveGuide = true
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        settingsViewModel.handleGoogleSignInResult(result.data)
    }

    // Poll download progress while downloading
    LaunchedEffect(modelStatus.state) {
        while (modelStatus.state == ModelDownloadState.DOWNLOADING) {
            kotlinx.coroutines.delay(1500)
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

            // ── Notion Sync ───────────────────────────────────────────────
            item { SectionHeader("Notion Sync") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val configured = settings.isNotionConfigured
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(
                                if (configured) Icons.Default.CheckCircle else Icons.Default.Cloud,
                                null,
                                tint = if (configured) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (configured) "Notion connected" else "Not set up yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (configured) "Tasks and knowledge sync to your workspace"
                                    else "Tap below for a step-by-step setup guide",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Button(
                            onClick = onNotionSetup,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(if (configured) Icons.Default.Edit else Icons.Default.OpenInBrowser, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (configured) "Edit Notion Settings" else "Set Up Notion (Step-by-step Guide)")
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
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.AccountCircle, null,
                                    tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(32.dp))
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
                            Text("Encrypted Drive backup active.",
                                style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(
                                onClick = { settingsViewModel.googleSignOut() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ExitToApp, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Sign Out")
                            }

                        } else {
                            Text("Tap below to sign in with Google. Backups are stored in a private app folder — not visible in your Drive.",
                                style = MaterialTheme.typography.bodySmall)

                            Button(
                                onClick = {
                                    val intent = settingsViewModel.getGoogleSignInIntent(withDriveScope = false)
                                    googleSignInLauncher.launch(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AccountCircle, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Sign in with Google")
                            }

                            // Error message
                            googleAuthState.error?.let { err ->
                                Text(err, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error)
                            }

                            // One-time SHA-1 setup guide
                            if (googleAuthState.needsSetup || showDriveGuide) {
                                DriveSetupGuide(
                                    context = context,
                                    onDismiss = { showDriveGuide = false }
                                )
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
                                Text("On-Device Gemma AI", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("OCR correction · Task extraction · Knowledge generation",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }

                        when (modelStatus.state) {
                            ModelDownloadState.IDLE, ModelDownloadState.FAILED -> {
                                if (modelStatus.state == ModelDownloadState.FAILED) {
                                    Text("Download failed: ${modelStatus.error}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text("Model not downloaded. ~537 MB — download on Wi-Fi.",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Button(
                                    onClick = { settingsViewModel.downloadGemmaModel() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.CloudDownload, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (modelStatus.state == ModelDownloadState.FAILED) "Retry Download" else "Download Gemma AI Model (~537 MB)")
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
                                Text(
                                    if (modelStatus.totalMb > 0)
                                        "Downloading… ${modelStatus.downloadedMb.toInt()} / ${modelStatus.totalMb.toInt()} MB (${modelStatus.progressPercent}%)"
                                    else "Downloading…",
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
                                OutlinedButton(
                                    onClick = { settingsViewModel.cancelModelDownload() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Cancel, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Cancel Download")
                                }
                            }

                            ModelDownloadState.COMPLETED, ModelDownloadState.ALREADY_EXISTS -> {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CheckCircle, null,
                                        tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                                    Text("AI model ready — all features active",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.tertiary)
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
                        Text("Handwritten Diary AI v1.2.0", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Local-first · Notion Free · Google Drive backup", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Text("Kotlin · Jetpack Compose · Room · ML Kit · WorkManager",
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
                    Text("Enter a direct download URL for a MediaPipe LLM Inference .task model (Gemma 3/4 INT4 or INT8).",
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
                    if (customModelUrl.isNotBlank()) settingsViewModel.downloadGemmaModel(customModelUrl)
                    showModelUrlDialog = false
                }) { Text("Start Download") }
            },
            dismissButton = { TextButton(onClick = { showModelUrlDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun DriveSetupGuide(context: android.content.Context, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("One-time Drive Setup", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                }
            }
            Text("Google Sign-In requires your app's SHA-1 registered in Google Cloud Console. Do this once:",
                style = MaterialTheme.typography.bodySmall)

            listOf(
                "1. Go to console.cloud.google.com → create/select a project",
                "2. Enable the Google Drive API",
                "3. Go to APIs & Services → Credentials → Create OAuth 2.0 Client ID",
                "4. Choose Android app type",
                "5. Package name: com.diaryai.handwrittendiaryai",
                "6. SHA-1: run the command below on your PC and paste the result",
                "7. Download google-services.json and place it in app/ folder, then rebuild"
            ).forEach { step ->
                Text(step, style = MaterialTheme.typography.bodySmall)
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Text(
                    "keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Button(
                onClick = { context.openUrl("https://console.cloud.google.com/apis/credentials") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInBrowser, null)
                Spacer(Modifier.width(8.dp))
                Text("Open Google Cloud Console")
            }
        }
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
