package com.diaryai.sync

import android.content.Context
import com.diaryai.data.db.NotionSyncDao
import com.diaryai.data.db.TaskItemDao
import com.diaryai.data.db.KnowledgeEntryDao
import com.diaryai.data.model.*
import com.diaryai.util.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SyncReport(
    val syncedTasks: Int,
    val syncedKnowledge: Int,
    val skippedAttachments: Int,
    val failedItems: Int,
    val errors: List<String>
)

@Singleton
class NotionSyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notionSyncDao: NotionSyncDao,
    private val taskDao: TaskItemDao,
    private val knowledgeDao: KnowledgeEntryDao,
    private val settingsManager: SettingsManager
) {
    private val client = OkHttpClient()
    private val NOTION_VERSION = "2022-06-28"
    private val MAX_ATTACHMENT_BYTES = 5L * 1024 * 1024 // 5 MB

    private val notionToken get() = settingsManager.notionToken
    private val tasksDatabaseId get() = settingsManager.notionTasksDbId
    private val knowledgeDatabaseId get() = settingsManager.notionKnowledgeDbId

    suspend fun syncAll(): SyncReport = withContext(Dispatchers.IO) {
        if (notionToken.isBlank()) return@withContext SyncReport(0, 0, 0, 0, listOf("Notion token not configured"))

        val errors = mutableListOf<String>()
        var syncedTasks = 0
        var syncedKnowledge = 0
        var skippedAttachments = 0
        var failedItems = 0

        // Sync pending tasks
        val pendingTasks = taskDao.getByNotionSyncStatus(SyncStatus.PENDING)
        pendingTasks.forEach { task ->
            try {
                syncTask(task)
                syncedTasks++
            } catch (e: Exception) {
                failedItems++
                errors.add("Task '${task.title}': ${e.message}")
            }
        }

        // Sync pending knowledge
        val pendingKnowledge = knowledgeDao.getByNotionSyncStatus(SyncStatus.PENDING)
        pendingKnowledge.forEach { entry ->
            try {
                val attachmentSkipped = syncKnowledge(entry)
                syncedKnowledge++
                if (attachmentSkipped) skippedAttachments++
            } catch (e: Exception) {
                failedItems++
                errors.add("Knowledge '${entry.title}': ${e.message}")
            }
        }

        SyncReport(syncedTasks, syncedKnowledge, skippedAttachments, failedItems, errors)
    }

    private suspend fun syncTask(task: TaskItem) {
        val existingRecord = notionSyncDao.getByEntityId(task.id)

        val properties = JSONObject().apply {
            put("Name", JSONObject().put("title", JSONArray().put(JSONObject().put("text", JSONObject().put("content", task.title)))))
            put("Description", JSONObject().put("rich_text", JSONArray().put(JSONObject().put("text", JSONObject().put("content", task.description)))))
            put("Priority", JSONObject().put("select", JSONObject().put("name", task.priority.name)))
            put("Status", JSONObject().put("select", JSONObject().put("name", task.status.name)))
            put("LocalId", JSONObject().put("rich_text", JSONArray().put(JSONObject().put("text", JSONObject().put("content", task.id)))))
            task.dueDateHint?.let { put("DueHint", JSONObject().put("rich_text", JSONArray().put(JSONObject().put("text", JSONObject().put("content", it))))) }
            task.tags.let { if (it.isNotBlank()) put("Tags", JSONObject().put("multi_select", JSONArray(it.split(",").map { t -> JSONObject().put("name", t.trim()) }))) }
        }

        val body = JSONObject().apply {
            put("parent", JSONObject().put("database_id", tasksDatabaseId))
            put("properties", properties)
        }

        if (existingRecord?.notionPageId != null) {
            patchNotion("pages/${existingRecord.notionPageId}", body)
        } else {
            val response = postNotion("pages", body)
            val pageId = response.getString("id")
            val record = NotionSyncRecord(
                id = UUID.randomUUID().toString(),
                localEntityId = task.id,
                entityType = "task",
                notionPageId = pageId,
                notionDatabaseId = tasksDatabaseId,
                syncedAt = System.currentTimeMillis(),
                status = SyncStatus.SYNCED
            )
            notionSyncDao.insert(record)
        }
        taskDao.update(task.copy(notionSyncStatus = SyncStatus.SYNCED))
    }

    private suspend fun syncKnowledge(entry: KnowledgeEntry): Boolean {
        val existingRecord = notionSyncDao.getByEntityId(entry.id)
        var attachmentSkipped = false

        val properties = JSONObject().apply {
            put("Name", JSONObject().put("title", JSONArray().put(JSONObject().put("text", JSONObject().put("content", entry.title)))))
            put("Summary", JSONObject().put("rich_text", JSONArray().put(JSONObject().put("text", JSONObject().put("content", entry.summary.take(2000))))))
            put("Tags", JSONObject().put("multi_select", JSONArray(entry.tags.split(",").filter { it.isNotBlank() }.map { t -> JSONObject().put("name", t.trim()) })))
            put("Category", JSONObject().put("select", JSONObject().put("name", entry.category)))
            put("LocalId", JSONObject().put("rich_text", JSONArray().put(JSONObject().put("text", JSONObject().put("content", entry.id)))))
        }

        // Check attachment eligibility
        entry.localArchiveRef?.let { ref ->
            val file = File(ref)
            if (file.exists() && file.length() > MAX_ATTACHMENT_BYTES) {
                attachmentSkipped = true
                properties.put("AttachmentStatus", JSONObject().put("rich_text", JSONArray().put(JSONObject().put("text", JSONObject().put("content", "local_only - exceeds 5MB Notion Free limit")))))
            }
        }

        val bodyContent = JSONArray().put(
            JSONObject().put("object", "block").put("type", "paragraph").put("paragraph",
                JSONObject().put("rich_text", JSONArray().put(JSONObject().put("text", JSONObject().put("content", entry.body.take(2000))))))
        )

        val body = JSONObject().apply {
            put("parent", JSONObject().put("database_id", knowledgeDatabaseId))
            put("properties", properties)
            put("children", bodyContent)
        }

        if (existingRecord?.notionPageId != null) {
            patchNotion("pages/${existingRecord.notionPageId}", body)
        } else {
            val response = postNotion("pages", body)
            val pageId = response.getString("id")
            val record = NotionSyncRecord(
                id = UUID.randomUUID().toString(),
                localEntityId = entry.id,
                entityType = "knowledge",
                notionPageId = pageId,
                notionDatabaseId = knowledgeDatabaseId,
                syncedAt = System.currentTimeMillis(),
                attachmentSkippedReason = if (attachmentSkipped) "file_too_large" else null,
                status = SyncStatus.SYNCED
            )
            notionSyncDao.insert(record)
        }
        knowledgeDao.update(entry.copy(notionSyncStatus = SyncStatus.SYNCED))
        return attachmentSkipped
    }

    private fun postNotion(endpoint: String, body: JSONObject): JSONObject {
        val req = Request.Builder()
            .url("https://api.notion.com/v1/$endpoint")
            .addHeader("Authorization", "Bearer $notionToken")
            .addHeader("Notion-Version", NOTION_VERSION)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val res = client.newCall(req).execute()
        val responseBody = res.body?.string() ?: throw Exception("Empty response")
        if (!res.isSuccessful) throw Exception("Notion API error ${res.code}: $responseBody")
        return JSONObject(responseBody)
    }

    private fun patchNotion(endpoint: String, body: JSONObject): JSONObject {
        val req = Request.Builder()
            .url("https://api.notion.com/v1/$endpoint")
            .addHeader("Authorization", "Bearer $notionToken")
            .addHeader("Notion-Version", NOTION_VERSION)
            .addHeader("Content-Type", "application/json")
            .patch(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val res = client.newCall(req).execute()
        val responseBody = res.body?.string() ?: throw Exception("Empty response")
        if (!res.isSuccessful) throw Exception("Notion API error ${res.code}: $responseBody")
        return JSONObject(responseBody)
    }
}
