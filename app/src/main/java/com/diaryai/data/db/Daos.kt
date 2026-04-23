package com.diaryai.data.db

import androidx.room.*
import com.diaryai.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DiarySessionDao {
    @Query("SELECT * FROM diary_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<DiarySession>>

    @Query("SELECT * FROM diary_sessions WHERE id = :id")
    suspend fun getById(id: String): DiarySession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: DiarySession)

    @Update
    suspend fun update(session: DiarySession)

    @Delete
    suspend fun delete(session: DiarySession)

    @Query("SELECT * FROM diary_sessions WHERE status = :status")
    fun getByStatus(status: SessionStatus): Flow<List<DiarySession>>
}

@Dao
interface ScanPageDao {
    @Query("SELECT * FROM scan_pages WHERE sessionId = :sessionId ORDER BY pageIndex ASC")
    fun getPagesForSession(sessionId: String): Flow<List<ScanPage>>

    @Query("SELECT * FROM scan_pages WHERE sessionId = :sessionId ORDER BY pageIndex ASC")
    suspend fun getPagesForSessionSync(sessionId: String): List<ScanPage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: ScanPage)

    @Update
    suspend fun update(page: ScanPage)

    @Query("DELETE FROM scan_pages WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("SELECT * FROM scan_pages WHERE id = :id")
    suspend fun getById(id: String): ScanPage?
}

@Dao
interface TaskItemDao {
    @Query("SELECT * FROM task_items ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<TaskItem>>

    @Query("SELECT * FROM task_items WHERE sessionId = :sessionId")
    fun getTasksForSession(sessionId: String): Flow<List<TaskItem>>

    @Query("SELECT * FROM task_items WHERE notionSyncStatus = :status")
    suspend fun getByNotionSyncStatus(status: SyncStatus): List<TaskItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskItem>)

    @Update
    suspend fun update(task: TaskItem)

    @Delete
    suspend fun delete(task: TaskItem)

    @Query("SELECT * FROM task_items WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<TaskItem>

    @Query("SELECT * FROM task_items WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: TaskStatus): Flow<List<TaskItem>>
}

@Dao
interface KnowledgeEntryDao {
    @Query("SELECT * FROM knowledge_entries ORDER BY updatedAt DESC")
    fun getAllEntries(): Flow<List<KnowledgeEntry>>

    @Query("SELECT * FROM knowledge_entries WHERE sessionId = :sessionId")
    fun getEntriesForSession(sessionId: String): Flow<List<KnowledgeEntry>>

    @Query("SELECT * FROM knowledge_entries WHERE notionSyncStatus = :status")
    suspend fun getByNotionSyncStatus(status: SyncStatus): List<KnowledgeEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: KnowledgeEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<KnowledgeEntry>)

    @Update
    suspend fun update(entry: KnowledgeEntry)

    @Delete
    suspend fun delete(entry: KnowledgeEntry)

    @Query("SELECT * FROM knowledge_entries WHERE title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<KnowledgeEntry>

    @Query("SELECT * FROM knowledge_entries WHERE tags LIKE '%' || :tag || '%'")
    fun getByTag(tag: String): Flow<List<KnowledgeEntry>>
}

@Dao
interface NotionSyncDao {
    @Query("SELECT * FROM notion_sync_records WHERE status IN ('PENDING','FAILED') ORDER BY retryCount ASC")
    suspend fun getPendingRecords(): List<NotionSyncRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: NotionSyncRecord)

    @Update
    suspend fun update(record: NotionSyncRecord)

    @Query("SELECT * FROM notion_sync_records WHERE localEntityId = :id")
    suspend fun getByEntityId(id: String): NotionSyncRecord?

    @Query("SELECT * FROM notion_sync_records ORDER BY syncedAt DESC")
    fun getAll(): Flow<List<NotionSyncRecord>>
}

@Dao
interface BackupRecordDao {
    @Query("SELECT * FROM backup_records ORDER BY createdAt DESC")
    fun getAllBackups(): Flow<List<BackupRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BackupRecord)

    @Update
    suspend fun update(record: BackupRecord)

    @Query("SELECT * FROM backup_records WHERE status = 'UPLOADED' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getLatestUploaded(limit: Int = 5): List<BackupRecord>

    @Query("SELECT * FROM backup_records WHERE id = :id")
    suspend fun getById(id: String): BackupRecord?
}
