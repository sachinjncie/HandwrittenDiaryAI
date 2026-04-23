package com.diaryai.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "diary_ai_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to regular prefs if encryption fails
        context.getSharedPreferences("diary_ai_prefs", Context.MODE_PRIVATE)
    }

    var notionToken: String
        get() = prefs.getString("notion_token", "") ?: ""
        set(value) = prefs.edit().putString("notion_token", value).apply()

    var notionTasksDbId: String
        get() = prefs.getString("notion_tasks_db_id", "") ?: ""
        set(value) = prefs.edit().putString("notion_tasks_db_id", value).apply()

    var notionKnowledgeDbId: String
        get() = prefs.getString("notion_knowledge_db_id", "") ?: ""
        set(value) = prefs.edit().putString("notion_knowledge_db_id", value).apply()

    var googleAccountEmail: String
        get() = prefs.getString("google_account_email", "") ?: ""
        set(value) = prefs.edit().putString("google_account_email", value).apply()

    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean("auto_backup", false)
        set(value) = prefs.edit().putBoolean("auto_backup", value).apply()

    var autoSyncEnabled: Boolean
        get() = prefs.getBoolean("auto_sync", false)
        set(value) = prefs.edit().putBoolean("auto_sync", value).apply()

    var darkModeEnabled: Boolean
        get() = prefs.getBoolean("dark_mode", false)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()

    var gemmaModelPath: String
        get() = prefs.getString("gemma_model_path", "") ?: ""
        set(value) = prefs.edit().putString("gemma_model_path", value).apply()

    val isNotionConfigured: Boolean
        get() = notionToken.isNotBlank() && notionTasksDbId.isNotBlank() && knowledgeDatabaseId.isNotBlank()

    val knowledgeDatabaseId: String
        get() = notionKnowledgeDbId
}
