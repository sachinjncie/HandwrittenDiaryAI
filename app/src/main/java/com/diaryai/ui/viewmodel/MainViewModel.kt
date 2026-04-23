package com.diaryai.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diaryai.data.model.*
import com.diaryai.data.repository.DiaryRepository
import com.diaryai.service.GemmaService
import com.diaryai.service.OcrService
import com.diaryai.sync.NotionSyncService
import com.diaryai.sync.SyncReport
import com.diaryai.backup.DriveBackupService
import com.diaryai.backup.BackupResult
import com.diaryai.util.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class UiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: DiaryRepository,
    private val ocrService: OcrService,
    private val gemmaService: GemmaService,
    private val notionSync: NotionSyncService,
    private val driveBackup: DriveBackupService,
    val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val sessions = repo.getAllSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allTasks = repo.getAllTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allKnowledge = repo.getAllKnowledge().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    // backupList loaded via loadBackups()

    // ── Current session state ────────────────────────────────────────────────
    private val _currentSession = MutableStateFlow<DiarySession?>(null)
    val currentSession: StateFlow<DiarySession?> = _currentSession.asStateFlow()

    private val _currentPages = MutableStateFlow<List<ScanPage>>(emptyList())
    val currentPages: StateFlow<List<ScanPage>> = _currentPages.asStateFlow()

    private val _extractedTasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val extractedTasks: StateFlow<List<TaskItem>> = _extractedTasks.asStateFlow()

    private val _extractedKnowledge = MutableStateFlow<List<KnowledgeEntry>>(emptyList())
    val extractedKnowledge: StateFlow<List<KnowledgeEntry>> = _extractedKnowledge.asStateFlow()

    private val _syncReport = MutableStateFlow<SyncReport?>(null)
    val syncReport: StateFlow<SyncReport?> = _syncReport.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _backupList = MutableStateFlow<List<BackupRecord>>(emptyList())
    val backupList: StateFlow<List<BackupRecord>> = _backupList.asStateFlow()

    // ── Session management ───────────────────────────────────────────────────

    fun createNewSession(title: String = "") = viewModelScope.launch {
        val session = repo.createSession(title)
        _currentSession.value = session
        _currentPages.value = emptyList()
    }

    fun loadSession(sessionId: String) = viewModelScope.launch {
        _currentSession.value = repo.getSession(sessionId)
        repo.getPagesForSession(sessionId).collect { pages ->
            _currentPages.value = pages
        }
    }

    fun deleteSession(session: DiarySession) = viewModelScope.launch {
        repo.deleteSession(session)
    }

    // ── OCR ──────────────────────────────────────────────────────────────────

    fun runOcrOnPage(page: ScanPage) = viewModelScope.launch {
        _uiState.value = UiState(isLoading = true)
        try {
            val result = ocrService.recognizeText(page.imagePath)
            val updated = page.copy(rawOcrText = result.fullText, ocrConfidence = result.confidence)
            repo.updatePage(updated)
            _currentPages.value = _currentPages.value.map { if (it.id == page.id) updated else it }
            _uiState.value = UiState(message = "OCR complete — ${result.confidence.times(100).toInt()}% confidence")
        } catch (e: Exception) {
            _uiState.value = UiState(error = "OCR failed: ${e.message}")
        }
    }

    fun updatePageText(pageId: String, correctedText: String) = viewModelScope.launch {
        val page = _currentPages.value.find { it.id == pageId } ?: return@launch
        val updated = page.copy(correctedText = correctedText)
        repo.updatePage(updated)
        _currentPages.value = _currentPages.value.map { if (it.id == pageId) updated else it }
    }

    // ── AI ───────────────────────────────────────────────────────────────────

    fun loadGemmaModel() = viewModelScope.launch {
        _uiState.value = UiState(isLoading = true)
        try {
            gemmaService.loadModel()
            _uiState.value = UiState(message = "Gemma model ready")
        } catch (e: Exception) {
            _uiState.value = UiState(error = "Model load failed: ${e.message}")
        }
    }

    fun runAiOnCurrentSession() = viewModelScope.launch {
        val session = _currentSession.value ?: return@launch
        val pages = _currentPages.value
        if (pages.isEmpty()) return@launch

        _uiState.value = UiState(isLoading = true)
        try {
            val fullText = pages.joinToString("\n\n") { it.correctedText.ifBlank { it.rawOcrText } }

            // Correct OCR
            val corrected = gemmaService.correctOcrText(fullText)

            // Extract tasks
            val tasks = gemmaService.extractTasks(corrected, session.id)
            _extractedTasks.value = tasks

            // Extract knowledge
            val knowledge = gemmaService.extractKnowledge(corrected, session.id)
            _extractedKnowledge.value = knowledge

            _uiState.value = UiState(message = "AI extracted ${tasks.size} tasks, ${knowledge.size} knowledge entries")
        } catch (e: Exception) {
            _uiState.value = UiState(error = "AI processing failed: ${e.message}")
        }
    }

    fun approveTask(task: TaskItem) = viewModelScope.launch {
        repo.saveTask(task)
        _extractedTasks.value = _extractedTasks.value.filter { it.id != task.id }
    }

    fun approveAllTasks() = viewModelScope.launch {
        repo.saveTasks(_extractedTasks.value)
        _extractedTasks.value = emptyList()
    }

    fun approveKnowledge(entry: KnowledgeEntry) = viewModelScope.launch {
        repo.saveKnowledge(entry)
        _extractedKnowledge.value = _extractedKnowledge.value.filter { it.id != entry.id }
    }

    fun approveAllKnowledge() = viewModelScope.launch {
        repo.saveKnowledgeList(_extractedKnowledge.value)
        _extractedKnowledge.value = emptyList()
    }

    // ── Search ───────────────────────────────────────────────────────────────

    fun search(query: String) = viewModelScope.launch {
        if (query.isBlank()) { _searchResults.value = emptyList(); return@launch }
        _searchResults.value = repo.search(query)
    }

    // ── Sync ─────────────────────────────────────────────────────────────────

    fun syncNow() = viewModelScope.launch {
        _uiState.value = UiState(isLoading = true)
        try {
            val report = notionSync.syncAll()
            _syncReport.value = report
            _uiState.value = UiState(message = "Synced: ${report.syncedTasks} tasks, ${report.syncedKnowledge} KB entries")
        } catch (e: Exception) {
            _uiState.value = UiState(error = "Sync failed: ${e.message}")
        }
    }

    // ── Backup ───────────────────────────────────────────────────────────────

    fun backupNow() = viewModelScope.launch {
        _uiState.value = UiState(isLoading = true)
        try {
            val result = driveBackup.createBackup()
            if (result.success) {
                _uiState.value = UiState(message = "Backup created (${result.sizeBytes / 1024}KB)")
                loadBackups()
            } else {
                _uiState.value = UiState(error = "Backup failed: ${result.error}")
            }
        } catch (e: Exception) {
            _uiState.value = UiState(error = "Backup error: ${e.message}")
        }
    }

    fun loadBackups() = viewModelScope.launch {
        _backupList.value = driveBackup.listBackups()
    }

    fun restoreBackup(record: BackupRecord) = viewModelScope.launch {
        _uiState.value = UiState(isLoading = true)
        try {
            val result = driveBackup.restoreBackup(record)
            if (result.success) _uiState.value = UiState(message = "Restore complete — restart app")
            else _uiState.value = UiState(error = "Restore failed: ${result.error}")
        } catch (e: Exception) {
            _uiState.value = UiState(error = e.message)
        }
    }

    // ── Export ───────────────────────────────────────────────────────────────

    fun exportCurrentSession() = viewModelScope.launch {
        val session = _currentSession.value ?: return@launch
        try {
            val file = repo.exportSessionAsMarkdown(session.id)
            _uiState.value = UiState(message = "Exported to ${file.name}")
        } catch (e: Exception) {
            _uiState.value = UiState(error = "Export failed: ${e.message}")
        }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null, error = null) }
}
