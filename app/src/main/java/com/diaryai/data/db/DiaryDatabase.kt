package com.diaryai.data.db

import android.content.Context
import androidx.room.*
import com.diaryai.data.model.*

@Database(
    entities = [
        DiarySession::class,
        ScanPage::class,
        TaskItem::class,
        KnowledgeEntry::class,
        NotionSyncRecord::class,
        BackupRecord::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun sessionDao(): DiarySessionDao
    abstract fun scanPageDao(): ScanPageDao
    abstract fun taskDao(): TaskItemDao
    abstract fun knowledgeDao(): KnowledgeEntryDao
    abstract fun notionSyncDao(): NotionSyncDao
    abstract fun backupDao(): BackupRecordDao

    companion object {
        @Volatile private var INSTANCE: DiaryDatabase? = null

        fun getInstance(context: Context): DiaryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DiaryDatabase::class.java,
                    "diary_ai.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}

class Converters {
    @TypeConverter fun fromSessionStatus(v: SessionStatus) = v.name
    @TypeConverter fun toSessionStatus(v: String) = SessionStatus.valueOf(v)
    @TypeConverter fun fromSyncStatus(v: SyncStatus) = v.name
    @TypeConverter fun toSyncStatus(v: String) = SyncStatus.valueOf(v)
    @TypeConverter fun fromTaskPriority(v: TaskPriority) = v.name
    @TypeConverter fun toTaskPriority(v: String) = TaskPriority.valueOf(v)
    @TypeConverter fun fromTaskStatus(v: TaskStatus) = v.name
    @TypeConverter fun toTaskStatus(v: String) = TaskStatus.valueOf(v)
    @TypeConverter fun fromBackupStatus(v: BackupStatus) = v.name
    @TypeConverter fun toBackupStatus(v: String) = BackupStatus.valueOf(v)
}
