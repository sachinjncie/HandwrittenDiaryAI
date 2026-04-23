package com.diaryai.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.diaryai.util.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ModelDownloadState {
    IDLE, DOWNLOADING, COMPLETED, FAILED, ALREADY_EXISTS
}

data class ModelDownloadStatus(
    val state: ModelDownloadState = ModelDownloadState.IDLE,
    val progressPercent: Int = 0,
    val downloadedMb: Float = 0f,
    val totalMb: Float = 0f,
    val error: String? = null,
    val modelPath: String? = null
)

/**
 * Manages the download of the Gemma 4 on-device model file.
 *
 * The model is a MediaPipe LLM Inference .task file.
 * Official model variants (from Google AI Edge / Kaggle Models):
 *   - gemma-3-1b-it-cpu-int8.task   (~537 MB)  — fastest, recommended for phones
 *   - gemma-3-2b-it-cpu-int4.task   (~1.1 GB)  — better quality, needs 4GB RAM
 *
 * Users must accept the Gemma license at https://ai.google.dev/gemma/terms
 * before the model can be downloaded.
 *
 * In production: download via HuggingFace API token or Kaggle credentials.
 * For this build: uses a direct public URL pattern; swap in your signed URL.
 */
@Singleton
class GemmaModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) {
    companion object {
        // MediaPipe-compatible Gemma 3 1B INT8 model (smallest usable variant)
        // Replace with actual signed URL from ai.google.dev or Kaggle
        const val MODEL_URL_1B = "https://storage.googleapis.com/mediapipe-models/llm_inference/gemma-2b-it-gpu-int4/float16/1/model.bin"
        const val MODEL_FILENAME = "gemma_model.task"
        const val MODEL_DISPLAY_NAME = "Gemma 3 1B (INT8, ~537 MB)"
    }

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val modelDir: File get() = File(context.filesDir, "models").also { it.mkdirs() }
    val modelFile: File get() = File(modelDir, MODEL_FILENAME)

    val isModelReady: Boolean get() = modelFile.exists() && modelFile.length() > 10_000_000L

    private val _status = MutableStateFlow(
        ModelDownloadStatus(
            state = if (isModelReady) ModelDownloadState.ALREADY_EXISTS else ModelDownloadState.IDLE,
            modelPath = if (isModelReady) modelFile.absolutePath else null
        )
    )
    val status: StateFlow<ModelDownloadStatus> = _status.asStateFlow()

    private var activeDownloadId: Long = -1L

    fun startDownload(modelUrl: String = MODEL_URL_1B) {
        if (isModelReady) {
            _status.value = ModelDownloadStatus(
                state = ModelDownloadState.ALREADY_EXISTS,
                modelPath = modelFile.absolutePath
            )
            settingsManager.gemmaModelPath = modelFile.absolutePath
            return
        }

        if (_status.value.state == ModelDownloadState.DOWNLOADING) return

        try {
            val request = DownloadManager.Request(Uri.parse(modelUrl)).apply {
                setTitle("Gemma AI Model")
                setDescription("Downloading on-device AI model ($MODEL_DISPLAY_NAME)…")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(File(modelDir, "$MODEL_FILENAME.download")))
                setAllowedOverMetered(false)
                setAllowedOverRoaming(false)
                addRequestHeader("User-Agent", "HandwrittenDiaryAI/1.0")
            }

            activeDownloadId = downloadManager.enqueue(request)
            _status.value = ModelDownloadStatus(state = ModelDownloadState.DOWNLOADING)

        } catch (e: Exception) {
            _status.value = ModelDownloadStatus(state = ModelDownloadState.FAILED, error = e.message)
        }
    }

    fun cancelDownload() {
        if (activeDownloadId != -1L) {
            downloadManager.remove(activeDownloadId)
            activeDownloadId = -1L
            _status.value = ModelDownloadStatus(state = ModelDownloadState.IDLE)
        }
    }

    fun pollDownloadProgress() {
        if (activeDownloadId == -1L) return

        val query = DownloadManager.Query().setFilterById(activeDownloadId)
        val cursor = downloadManager.query(query)

        if (cursor.moveToFirst()) {
            val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val bytesDownloaded = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val bytesTotal = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

            val dlStatus = cursor.getInt(statusCol)
            val downloaded = cursor.getLong(bytesDownloaded)
            val total = cursor.getLong(bytesTotal)
            val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0

            when (dlStatus) {
                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                    _status.value = ModelDownloadStatus(
                        state = ModelDownloadState.DOWNLOADING,
                        progressPercent = percent,
                        downloadedMb = downloaded / 1_000_000f,
                        totalMb = total / 1_000_000f
                    )
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    // Rename .download → final file
                    val tempFile = File(modelDir, "$MODEL_FILENAME.download")
                    if (tempFile.exists()) tempFile.renameTo(modelFile)

                    settingsManager.gemmaModelPath = modelFile.absolutePath
                    activeDownloadId = -1L
                    _status.value = ModelDownloadStatus(
                        state = ModelDownloadState.COMPLETED,
                        progressPercent = 100,
                        modelPath = modelFile.absolutePath
                    )
                }
                DownloadManager.STATUS_FAILED -> {
                    val reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = cursor.getInt(reasonCol)
                    activeDownloadId = -1L
                    _status.value = ModelDownloadStatus(
                        state = ModelDownloadState.FAILED,
                        error = "Download failed (code $reason). Check your connection and try again."
                    )
                }
                DownloadManager.STATUS_PAUSED -> {
                    _status.value = _status.value.copy(
                        state = ModelDownloadState.DOWNLOADING,
                        progressPercent = percent,
                        downloadedMb = downloaded / 1_000_000f,
                        totalMb = total / 1_000_000f
                    )
                }
            }
        }
        cursor.close()
    }

    fun useCustomModelPath(path: String) {
        val file = File(path)
        if (file.exists() && file.length() > 0) {
            settingsManager.gemmaModelPath = path
            _status.value = ModelDownloadStatus(
                state = ModelDownloadState.ALREADY_EXISTS,
                modelPath = path
            )
        }
    }

    fun deleteModel() {
        if (modelFile.exists()) modelFile.delete()
        settingsManager.gemmaModelPath = ""
        _status.value = ModelDownloadStatus(state = ModelDownloadState.IDLE)
    }
}
