package com.meetloggerv2.data.local

import android.content.Context
import android.os.Environment
import com.meetloggerv2.core.session.ISessionCleanup
import com.meetloggerv2.core.util.AppLogger
import com.meetloggerv2.data.local.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionCleanup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val settingsDataStore: SettingsDataStore
) : ISessionCleanup {
    override suspend fun clearAllLocalData() = withContext(Dispatchers.IO) {
        try {
            database.clearAllTables()
        } catch (e: Exception) {
            AppLogger.e("SessionCleanup", "Error clearing Room database tables", e)
        }
        try {
            ProfileDataStore(context).clear()
        } catch (e: Exception) {
            AppLogger.e("SessionCleanup", "Error clearing Profile DataStore", e)
        }
        try {
            settingsDataStore.clear()
        } catch (e: Exception) {
            AppLogger.e("SessionCleanup", "Error clearing Settings DataStore", e)
        }

        // Clear local files and cache directories
        try {
            deleteDirectoryContents(context.cacheDir)
        } catch (e: Exception) {
            AppLogger.e("SessionCleanup", "Error deleting cache directory contents", e)
        }
        try {
            deleteDirectoryContents(context.externalCacheDir)
        } catch (e: Exception) {
            AppLogger.e("SessionCleanup", "Error deleting external cache directory contents", e)
        }
        try {
            deleteDirectoryContents(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC))
        } catch (e: Exception) {
            AppLogger.e("SessionCleanup", "Error deleting music directory contents", e)
        }
    }

    private fun deleteDirectoryContents(dir: File?) {
        if (dir == null || !dir.exists()) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                deleteDirectoryContents(file)
            }
            file.delete()
        }
    }
}
