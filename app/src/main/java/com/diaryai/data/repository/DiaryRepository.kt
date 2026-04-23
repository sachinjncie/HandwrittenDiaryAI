package com.diaryai.data.repository

import android.content.Context
import com.diaryai.data.db.*
import com.diaryai.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionDao: DiarySessionDao,
    private val pageDao: ScanPageDao,
    private val taskDao: TaskItemDao,
    private val knowledgeDao: KnowledgeEntryDao
) {
    // ── Sessions ────────────────────────────────────────────────────────────
    fun getAllSessions(): Flow<List<DiarySession>> = sessionDao.getAllSessions()

    suspend fun getSession(id: String): DiarySession? = sessionDao.getById(id)

    suspend fun createSession(title: String): DiarySession {
        val session = DiarySession(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "Diary Entry ${java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}" }
        )
        sessionDao.insert(session)
        return session
    }

    suspend fun updateSession(session: DiarySession) = sessionDao.update(session.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteSession(session: DiarySession) {
        // Clean up image files
        pageDao.getPagesForSessionSync(session.id).forEach { page ->
            File(page.imagePath).delete()
            page.thumbnailPath?.let { File(it).delete() }
        }
        sessionDao.delete(session)
    }

    // ── Pages ────────────────────────────────────────────────────────────────
    fun getPagesForSession(sessionId: String): Flow<List<ScanPage>> = pageDao.getPagesForSession(sessionId)

    suspend fun addPage(sessionId: String, imagePath: String, thumbnailPath: String?, pageIndex: Int): ScanPage {
        val page = ScanPage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            pageIndex = pageIndex,
            imagePath = imagePath,
            thumbnailPath = thumbnailPath
        )
        pageDao.insert(page)
        return page
    }

    suspend fun updatePage(page: ScanPage) = pageDao.update(page)

    suspend fun getScansDirectory(): File {
        val dir = File(context.filesDir, "scans")
        dir.mkdirs()
        return dir
    }

    suspend fun getExportsDirectory(): File {
        val dir = File(context.filesDir, "exports")
        dir.mkdirs()
        return dir
    }

    // ── Tasks ────────────────────────────────────────────────────────────────
    fun getAllTasks(): Flow<List<TaskItem>> = taskDao.getAllTasks()
    fun getTasksForSession(sessionId: String) = taskDao.getTasksForSession(sessionId)

    suspend fun saveTask(task: TaskItem) = taskDao.insert(task)
    suspend fun saveTasks(tasks: List<TaskItem>) = taskDao.insertAll(tasks)
    suspend fun updateTask(task: TaskItem) = taskDao.update(task.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteTask(task: TaskItem) = taskDao.delete(task)
    suspend fun searchTasks(query: String) = taskDao.search(query)

    // ── Knowledge ────────────────────────────────────────────────────────────
    fun getAllKnowledge(): Flow<List<KnowledgeEntry>> = knowledgeDao.getAllEntries()
    fun getKnowledgeForSession(sessionId: String) = knowledgeDao.getEntriesForSession(sessionId)

    suspend fun saveKnowledge(entry: KnowledgeEntry) = knowledgeDao.insert(entry)
    suspend fun saveKnowledgeList(entries: List<KnowledgeEntry>) = knowledgeDao.insertAll(entries)
    suspend fun updateKnowledge(entry: KnowledgeEntry) = knowledgeDao.update(entry.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteKnowledge(entry: KnowledgeEntry) = knowledgeDao.delete(entry)
    suspend fun searchKnowledge(query: String) = knowledgeDao.search(query)

    // ── Combined search ──────────────────────────────────────────────────────
    suspend fun search(query: String): List<SearchResult> {
        val tasks = taskDao.search(query).map {
            SearchResult(it.id, "task", it.title, it.description.take(100), it.createdAt)
        }
        val knowledge = knowledgeDao.search(query).map {
            SearchResult(it.id, "knowledge", it.title, it.summary.take(100), it.createdAt)
        }
        return (tasks + knowledge).sortedByDescending { it.createdAt }
    }

    // ── Export ───────────────────────────────────────────────────────────────
    suspend fun exportSessionAsMarkdown(sessionId: String): File {
        val session = sessionDao.getById(sessionId) ?: error("Session not found")
        val tasks = taskDao.getTasksForSession(sessionId)
        val knowledge = knowledgeDao.getEntriesForSession(sessionId)
        val pages = pageDao.getPagesForSessionSync(sessionId)

        val sb = StringBuilder()
        sb.appendLine("# ${session.title}")
        sb.appendLine("Created: ${java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(session.createdAt))}")
        sb.appendLine()
        sb.appendLine("## Diary Text")
        pages.forEach { page ->
            sb.appendLine("### Page ${page.pageIndex + 1}")
            sb.appendLine(page.correctedText.ifBlank { page.rawOcrText })
            sb.appendLine()
        }

        val exportDir = getExportsDirectory()
        val file = File(exportDir, "${session.id}_export.md")
        file.writeText(sb.toString())
        return file
    }
}
