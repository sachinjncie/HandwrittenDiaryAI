package com.diaryai.backup

import android.content.Context
import com.diaryai.data.db.BackupRecordDao
import com.diaryai.data.db.DiaryDatabase
import com.diaryai.data.model.BackupRecord
import com.diaryai.data.model.BackupStatus
import com.diaryai.util.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class BackupResult(
    val success: Boolean,
    val recordId: String?,
    val sizeBytes: Long,
    val error: String? = null
)

data class RestoreResult(
    val success: Boolean,
    val error: String? = null
)

@Singleton
class DriveBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupDao: BackupRecordDao,
    private val settingsManager: SettingsManager
) {
    private val backupDir get() = File(context.filesDir, "backups").also { it.mkdirs() }

    // ── Backup ───────────────────────────────────────────────────────────────

    suspend fun createBackup(): BackupResult = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val archiveFile = File(backupDir, "backup_$id.zip")

        try {
            // 1. Close DB connections temporarily
            val db = DiaryDatabase.getInstance(context)

            // 2. Create zip archive
            createArchive(archiveFile)

            // 3. Compute checksum
            val checksum = sha256(archiveFile)

            // 4. Record in local DB
            val record = BackupRecord(
                id = id,
                driveFileId = null,
                archivePath = archiveFile.absolutePath,
                archiveChecksum = checksum,
                sizeBytes = archiveFile.length(),
                status = BackupStatus.PENDING
            )
            backupDao.insert(record)

            // 5. Upload to Drive (requires Google Sign-In token from settingsManager)
            val driveFileId = uploadToDrive(archiveFile, id)

            // 6. Update record
            val updated = record.copy(
                driveFileId = driveFileId,
                status = if (driveFileId != null) BackupStatus.UPLOADED else BackupStatus.PENDING
            )
            backupDao.update(updated)

            BackupResult(true, id, archiveFile.length())
        } catch (e: Exception) {
            BackupResult(false, null, 0, e.message)
        }
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    suspend fun listBackups(): List<BackupRecord> = withContext(Dispatchers.IO) {
        backupDao.getLatestUploaded(10)
    }

    suspend fun restoreBackup(record: BackupRecord): RestoreResult = withContext(Dispatchers.IO) {
        try {
            // Download if not available locally
            val archiveFile = if (File(record.archivePath).exists()) {
                File(record.archivePath)
            } else if (record.driveFileId != null) {
                downloadFromDrive(record.driveFileId, record.id)
            } else {
                return@withContext RestoreResult(false, "Archive not found locally or on Drive")
            }

            // Verify checksum
            val checksum = sha256(archiveFile)
            if (checksum != record.archiveChecksum) {
                return@withContext RestoreResult(false, "Checksum mismatch — archive may be corrupted")
            }

            // Extract
            extractArchive(archiveFile, context.filesDir)

            backupDao.update(record.copy(status = BackupStatus.RESTORED))
            RestoreResult(true)
        } catch (e: Exception) {
            RestoreResult(false, e.message)
        }
    }

    // ── Archive helpers ───────────────────────────────────────────────────────

    private fun createArchive(output: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
            // Include app files directory (scans, exports, etc.)
            addDirToZip(zip, context.filesDir, "files/")
            // Include database file
            val dbFile = context.getDatabasePath("diary_ai.db")
            if (dbFile.exists()) addFileToZip(zip, dbFile, "db/diary_ai.db")
            val dbWal = File(dbFile.parent, "diary_ai.db-wal")
            val dbShm = File(dbFile.parent, "diary_ai.db-shm")
            if (dbWal.exists()) addFileToZip(zip, dbWal, "db/diary_ai.db-wal")
            if (dbShm.exists()) addFileToZip(zip, dbShm, "db/diary_ai.db-shm")
        }
    }

    private fun addDirToZip(zip: ZipOutputStream, dir: File, prefix: String) {
        dir.listFiles()?.forEach { file ->
            if (file.name == "backups") return@forEach  // skip backup dir itself
            if (file.isDirectory) addDirToZip(zip, file, "$prefix${file.name}/")
            else addFileToZip(zip, file, "$prefix${file.name}")
        }
    }

    private fun addFileToZip(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun extractArchive(archive: File, destDir: File) {
        java.util.zip.ZipFile(archive).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val dest = File(destDir, entry.name)
                if (entry.isDirectory) { dest.mkdirs(); return@forEach }
                dest.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // ── Google Drive integration ──────────────────────────────────────────────
    // Requires user to be signed in with Google (handled by MainActivity / SettingsScreen)

    private fun uploadToDrive(file: File, backupId: String): String? {
        // TODO: implement with google-api-client-android
        // val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
        // credential.selectedAccount = settingsManager.googleAccount
        // val drive = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential).build()
        // val metadata = com.google.api.services.drive.model.File().apply {
        //     name = "diary_backup_$backupId.zip"
        //     parents = listOf("appDataFolder")
        // }
        // val content = FileContent("application/zip", file)
        // val result = drive.files().create(metadata, content).execute()
        // return result.id
        return "drive_stub_$backupId"  // stub until real OAuth token is available
    }

    private fun downloadFromDrive(driveFileId: String, backupId: String): File {
        // TODO: implement Drive download
        throw Exception("Drive download not yet implemented. Configure Google Sign-In in Settings first.")
    }
}
