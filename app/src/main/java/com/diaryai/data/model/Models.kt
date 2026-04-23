package com.diaryai.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ─── Diary Session ───────────────────────────────────────────────────────────

@Entity(tableName = "diary_sessions")
data class DiarySession(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pageCount: Int = 0,
    val status: SessionStatus = SessionStatus.DRAFT,
    val notionSyncStatus: SyncStatus = SyncStatus.PENDING,
    val driveSyncStatus: SyncStatus = SyncStatus.PENDING
)

enum class SessionStatus { DRAFT, OCR_DONE, AI_PROCESSED, APPROVED, ARCHIVED }
enum class SyncStatus { PENDING, SYNCING, SYNCED, FAILED, SKIPPED }

// ─── Scan Page ────────────────────────────────────────────────────────────────

@Entity(
    tableName = "scan_pages",
    foreignKeys = [ForeignKey(
        entity = DiarySession::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class ScanPage(
    @PrimaryKey val id: String,
    val sessionId: String,
    val pageIndex: Int,
    val imagePath: String,
    val thumbnailPath: String?,
    val rawOcrText: String = "",
    val correctedText: String = "",
    val ocrConfidence: Float = 0f,
    val attachableToNotion: Boolean = false,
    val estimatedUploadSizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

// ─── Task Item ────────────────────────────────────────────────────────────────

@Entity(
    tableName = "task_items",
    foreignKeys = [ForeignKey(
        entity = DiarySession::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class TaskItem(
    @PrimaryKey val id: String,
    val sessionId: String,
    val title: String,
    val description: String = "",
    val dueDateHint: String? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val confidence: Float = 0.8f,
    val status: TaskStatus = TaskStatus.OPEN,
    val sourceImageLocalOnly: Boolean = true,
    val notionPageId: String? = null,
    val notionSyncStatus: SyncStatus = SyncStatus.PENDING,
    val tags: String = "",          // comma-separated
    val projectHint: String? = null,
    val isCarryForward: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class TaskPriority { LOW, MEDIUM, HIGH, CRITICAL }
enum class TaskStatus { OPEN, IN_PROGRESS, DONE, DEFERRED, CANCELLED }

// ─── Knowledge Entry ──────────────────────────────────────────────────────────

@Entity(
    tableName = "knowledge_entries",
    foreignKeys = [ForeignKey(
        entity = DiarySession::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class KnowledgeEntry(
    @PrimaryKey val id: String,
    val sessionId: String,
    val title: String,
    val summary: String,
    val body: String,
    val tags: String = "",          // comma-separated
    val category: String = "general",
    val localArchiveRef: String? = null,
    val notionPageId: String? = null,
    val notionSyncStatus: SyncStatus = SyncStatus.PENDING,
    val relatedEntryIds: String = "",  // comma-separated
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ─── Notion Sync Record ───────────────────────────────────────────────────────

@Entity(tableName = "notion_sync_records")
data class NotionSyncRecord(
    @PrimaryKey val id: String,
    val localEntityId: String,
    val entityType: String,             // "task" | "knowledge"
    val notionPageId: String?,
    val notionDatabaseId: String,
    val syncedAt: Long?,
    val attachmentSkippedReason: String? = null,
    val status: SyncStatus = SyncStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null
)

// ─── Backup Record ────────────────────────────────────────────────────────────

@Entity(tableName = "backup_records")
data class BackupRecord(
    @PrimaryKey val id: String,
    val driveFileId: String?,
    val archivePath: String,
    val archiveChecksum: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val status: BackupStatus = BackupStatus.PENDING,
    val version: Int = 1
)

enum class BackupStatus { PENDING, UPLOADING, UPLOADED, FAILED, RESTORED }

// ─── Search / FTS helper ──────────────────────────────────────────────────────

data class SearchResult(
    val id: String,
    val type: String,       // "task" | "knowledge" | "session"
    val title: String,
    val snippet: String,
    val createdAt: Long
)
