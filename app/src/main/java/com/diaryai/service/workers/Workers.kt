package com.diaryai.service.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.diaryai.backup.DriveBackupService
import com.diaryai.sync.NotionSyncService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class NotionSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notionSyncService: NotionSyncService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val report = notionSyncService.syncAll()
            if (report.failedItems == 0) Result.success()
            else Result.retry()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

@HiltWorker
class DriveBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val driveBackupService: DriveBackupService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val result = driveBackupService.createBackup()
            if (result.success) Result.success() else Result.retry()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}

object ScheduleWorkers {
    fun scheduleAll(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // Notion sync – every 6 hours when on Wi-Fi
        val syncConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val syncRequest = PeriodicWorkRequestBuilder<NotionSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(syncConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            "notion_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )

        // Drive backup – daily on Wi-Fi + charging
        val backupConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .build()
        val backupRequest = PeriodicWorkRequestBuilder<DriveBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(backupConstraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            "drive_backup",
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
    }

    fun triggerSyncNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<NotionSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }

    fun triggerBackupNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<DriveBackupWorker>().build()
        WorkManager.getInstance(context).enqueue(req)
    }
}
