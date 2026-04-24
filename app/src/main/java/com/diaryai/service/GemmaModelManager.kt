package com.diaryai.service

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.diaryai.util.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val modelPath: String? = null,
    val statusDetail: String? = null   // human-readable DownloadManager status
)

/**
 * Manages the download of the Gemma on-device model (.task file).
 *
 * ## How to get the model
 * Gemma .task files are gated on HuggingFace — you need a free account and a token:
 *
 *   1. Go to https://huggingface.co/litert-community/Gemma3-1B-IT
 *   2. Accept the Gemma license (one-time, takes <1 minute)
 *   3. Go to https://huggingface.co/settings/tokens and create a Read token
 *   4. Copy the direct file URL:
 *      https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-cpu-int4.task
 *   5. Enter the URL + token in the app Settings → Gemma AI Model section
 *
 * The download request includes an Authorization header with the token so
 * DownloadManager can fetch the gated file.
 */
@Singleton
class GemmaModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) {
    companion object {
        // Correct HuggingFace URLs for MediaPipe-ready Gemma 3 1B .task files
        // These require license acceptance + HF token (free account)
        const val MODEL_URL_CPU_INT4 =
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-cpu-int4.task"
        const val MODEL_URL_CPU_INT8 =
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-cpu-int8.task"

        // HuggingFace model page — user must accept license here
        const val HF_MODEL_PAGE = "https://huggingface.co/litert-community/Gemma3-1B-IT"
        const val HF_TOKENS_PAGE = "https://huggingface.co/settings/tokens"

        const val MODEL_FILENAME    = "gemma_model.task"
        const val MODEL_DISPLAY_NAME = "Gemma 3 1B INT4 (~537 MB)"
    }

    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val modelDir: File
        get() {
            // DownloadManager cannot write to internal storage (filesDir).
            // Use getExternalFilesDir which is app-private but writable by DM.
            val dir = context.getExternalFilesDir("models")
                ?: File(context.filesDir, "models")
            dir.mkdirs()
            return dir
        }

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

    /**
     * Start downloading the model.
     *
     * @param modelUrl  Direct URL to the .task file (HuggingFace resolve URL)
     * @param hfToken   HuggingFace read token — required for gated Gemma models.
     *                  Get one free at https://huggingface.co/settings/tokens
     * @param wifiOnly  If true, pause download when only mobile data is available
     */
    fun startDownload(
        modelUrl: String = MODEL_URL_CPU_INT4,
        hfToken: String = "",
        wifiOnly: Boolean = false
    ) {
        if (isModelReady) {
            _status.value = ModelDownloadStatus(
                state = ModelDownloadState.ALREADY_EXISTS,
                modelPath = modelFile.absolutePath
            )
            settingsManager.gemmaModelPath = modelFile.absolutePath
            return
        }

        if (_status.value.state == ModelDownloadState.DOWNLOADING) return

        if (hfToken.isBlank()) {
            _status.value = ModelDownloadStatus(
                state = ModelDownloadState.FAILED,
                error = "HuggingFace token required — tap 'Get Model' to set it up"
            )
            return
        }

        try {
            val request = DownloadManager.Request(Uri.parse(modelUrl)).apply {
                setTitle("Gemma AI Model")
                setDescription("Downloading on-device AI model ($MODEL_DISPLAY_NAME)…")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                val destFile = File(modelDir, "$MODEL_FILENAME.download")
                setDestinationUri(Uri.fromFile(destFile))
                setAllowedOverMetered(!wifiOnly)
                setAllowedOverRoaming(!wifiOnly)
                // HuggingFace requires Authorization header for gated model files
                addRequestHeader("Authorization", "Bearer $hfToken")
                addRequestHeader("User-Agent", "HandwrittenDiaryAI/1.4")
            }

            activeDownloadId = downloadManager.enqueue(request)
            _status.value = ModelDownloadStatus(state = ModelDownloadState.DOWNLOADING)

        } catch (e: Exception) {
            _status.value = ModelDownloadStatus(
                state = ModelDownloadState.FAILED,
                error = e.message
            )
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
            val statusCol     = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val bytesDownCol  = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val bytesTotalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

            val dlStatus   = cursor.getInt(statusCol)
            val downloaded = cursor.getLong(bytesDownCol)
            val total      = cursor.getLong(bytesTotalCol)
            val percent    = if (total > 0) ((downloaded * 100) / total).toInt() else 0

            when (dlStatus) {
                DownloadManager.STATUS_RUNNING -> {
                    _status.value = ModelDownloadStatus(
                        state = ModelDownloadState.DOWNLOADING,
                        progressPercent = percent,
                        downloadedMb = downloaded / 1_000_000f,
                        totalMb = total / 1_000_000f,
                        statusDetail = "Downloading…"
                    )
                }
                DownloadManager.STATUS_PENDING -> {
                    _status.value = ModelDownloadStatus(
                        state = ModelDownloadState.DOWNLOADING,
                        progressPercent = 0,
                        statusDetail = "Queued — waiting to start…"
                    )
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val tempFile = File(modelDir, "$MODEL_FILENAME.download")
                    if (tempFile.exists()) {
                        val success = tempFile.renameTo(modelFile)
                        if (!success) {
                            tempFile.copyTo(modelFile, overwrite = true)
                            tempFile.delete()
                        }
                    }
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
                    val reason    = cursor.getInt(reasonCol)
                    val reasonMsg = when (reason) {
                        DownloadManager.ERROR_CANNOT_RESUME       -> "Cannot resume — file may have changed"
                        DownloadManager.ERROR_DEVICE_NOT_FOUND    -> "Storage not found"
                        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
                        DownloadManager.ERROR_FILE_ERROR          -> "File write error — check storage space"
                        DownloadManager.ERROR_HTTP_DATA_ERROR     -> "HTTP data error — try again"
                        DownloadManager.ERROR_INSUFFICIENT_SPACE  -> "Not enough storage space"
                        DownloadManager.ERROR_TOO_MANY_REDIRECTS  -> "Too many redirects — check URL"
                        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Server error (403?) — check token & license"
                        DownloadManager.ERROR_UNKNOWN             -> "Unknown error — check your connection"
                        else -> "Failed (code $reason)"
                    }
                    activeDownloadId = -1L
                    _status.value = ModelDownloadStatus(
                        state = ModelDownloadState.FAILED,
                        error = reasonMsg
                    )
                }
                DownloadManager.STATUS_PAUSED -> {
                    val reasonCol2  = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val pauseReason = cursor.getInt(reasonCol2)
                    val pauseMsg    = when (pauseReason) {
                        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for network…"
                        DownloadManager.PAUSED_WAITING_TO_RETRY    -> "Waiting to retry…"
                        DownloadManager.PAUSED_QUEUED_FOR_WIFI     ->
                            "Waiting for Wi-Fi — tap to switch to mobile data"
                        else -> "Paused (reason $pauseReason)"
                    }
                    _status.value = _status.value.copy(
                        state = ModelDownloadState.DOWNLOADING,
                        progressPercent = percent,
                        downloadedMb = downloaded / 1_000_000f,
                        totalMb = total / 1_000_000f,
                        statusDetail = pauseMsg
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
