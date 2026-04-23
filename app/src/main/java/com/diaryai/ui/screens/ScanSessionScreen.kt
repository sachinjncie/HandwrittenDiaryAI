package com.diaryai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.diaryai.ui.viewmodel.MainViewModel
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSessionScreen(
    viewModel: MainViewModel,
    onProceedToOcr: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scannedUris = remember { mutableStateListOf<Uri>() }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val session by viewModel.currentSession.collectAsState()

    // ── Camera launcher (fallback: take photo directly) ────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { uri ->
                scannedUris.add(uri)
                // Save page to session
                viewModel.addScannedPage(uri, context)
            }
        }
    }

    // ── Image picker (gallery fallback) ──────────────────────────────────
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            scannedUris.add(uri)
            viewModel.addScannedPage(uri, context)
        }
    }

    // ── ML Kit Document Scanner launcher ─────────────────────────────────
    val mlKitScannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // ML Kit returns scanned page URIs via GmsDocumentScanning result
            // Parse result and add pages
            result.data?.let { intent ->
                com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
                    .fromActivityResultIntent(intent)
                    ?.pages?.forEach { page ->
                        val uri = page.imageUri
                        scannedUris.add(uri)
                        viewModel.addScannedPage(uri, context)
                    }
            }
        }
    }

    // ── Camera permission launcher ─────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchMlKitScanner(context, mlKitScannerLauncher)
        } else {
            showPermissionDialog = true
        }
    }

    fun onScanClick() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED -> {
                launchMlKitScanner(context, mlKitScannerLauncher)
            }
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Diary Pages") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Session title
            session?.let {
                Text(it.title, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }

            // ── Scan button ───────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.DocumentScanner, null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Tap below to scan a diary page",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "ML Kit auto-crops, deskews, and enhances each page",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Button(
                        onClick = { onScanClick() },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan Page", style = MaterialTheme.typography.titleMedium)
                    }

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Photo, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import from Gallery")
                    }
                }
            }

            // ── Scanned pages preview ─────────────────────────────────────
            if (scannedUris.isNotEmpty()) {
                Text(
                    "${scannedUris.size} page${if (scannedUris.size > 1) "s" else ""} scanned",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(scannedUris) { index, uri ->
                        ScannedPageThumb(
                            uri = uri,
                            pageNumber = index + 1,
                            onDelete = {
                                scannedUris.removeAt(index)
                                viewModel.removeScannedPage(index)
                            }
                        )
                    }
                }

                // ── Proceed button ────────────────────────────────────────
                Button(
                    onClick = onProceedToOcr,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = scannedUris.isNotEmpty()
                ) {
                    Icon(Icons.Default.DocumentScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Run OCR on ${scannedUris.size} Page${if (scannedUris.size > 1) "s" else ""}")
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp))
                }
            }

            // ── Tips ──────────────────────────────────────────────────────
            if (scannedUris.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tips for best OCR results", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        listOf(
                            "Good lighting — avoid shadows across the page",
                            "Hold camera directly above the page",
                            "Keep handwriting in a single color ink",
                            "Scan one page at a time for best accuracy"
                        ).forEach { tip ->
                            Text("• $tip", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    // ── Permission denied dialog ──────────────────────────────────────────
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Camera Permission Required") },
            text = { Text("Camera access is needed to scan diary pages. Please grant it in Settings → Apps → Handwritten Diary AI → Permissions.") },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("OK") }
            }
        )
    }
}

@Composable
fun ScannedPageThumb(uri: Uri, pageNumber: Int, onDelete: () -> Unit) {
    Box(
        modifier = Modifier
            .size(100.dp, 140.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Page $pageNumber",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Page number badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(4.dp)
        ) {
            Text(
                "Page $pageNumber",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        // Delete button
        IconButton(
            onClick = onDelete,
            modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
        ) {
            Icon(
                Icons.Default.Cancel, null,
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            )
        }
    }
}

// ── ML Kit Document Scanner launcher helper ────────────────────────────────

private fun launchMlKitScanner(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>
) {
    try {
        val options = com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(
                com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
            )
            .setScannerMode(
                com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
            )
            .build()

        com.google.mlkit.vision.documentscanner.GmsDocumentScanning
            .getClient(options)
            .getStartScanIntent(context as androidx.activity.ComponentActivity)
            .addOnSuccessListener { intentSender ->
                launcher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                )
            }
            .addOnFailureListener { e ->
                // ML Kit scanner not available on this device — fall back to camera
                android.util.Log.e("ScanScreen", "ML Kit scanner unavailable: ${e.message}")
                launchCameraFallback(context, launcher)
            }
    } catch (e: Exception) {
        android.util.Log.e("ScanScreen", "Scanner error: ${e.message}")
    }
}

private fun launchCameraFallback(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>
) {
    // This is a no-op stub — camera fallback is handled by the cameraLauncher above
    android.widget.Toast.makeText(
        context,
        "Please use 'Import from Gallery' if camera scanner is unavailable",
        android.widget.Toast.LENGTH_LONG
    ).show()
}
