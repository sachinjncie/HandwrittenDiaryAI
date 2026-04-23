package com.diaryai.di

import android.content.Context
import com.diaryai.data.db.*
import com.diaryai.data.repository.DiaryRepository
import com.diaryai.backup.DriveBackupService
import com.diaryai.service.GemmaService
import com.diaryai.service.OcrService
import com.diaryai.sync.NotionSyncService
import com.diaryai.util.SettingsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): DiaryDatabase =
        DiaryDatabase.getInstance(ctx)

    @Provides @Singleton fun provideSessionDao(db: DiaryDatabase) = db.sessionDao()
    @Provides @Singleton fun provideScanPageDao(db: DiaryDatabase) = db.scanPageDao()
    @Provides @Singleton fun provideTaskDao(db: DiaryDatabase) = db.taskDao()
    @Provides @Singleton fun provideKnowledgeDao(db: DiaryDatabase) = db.knowledgeDao()
    @Provides @Singleton fun provideNotionSyncDao(db: DiaryDatabase) = db.notionSyncDao()
    @Provides @Singleton fun provideBackupDao(db: DiaryDatabase) = db.backupDao()
}
