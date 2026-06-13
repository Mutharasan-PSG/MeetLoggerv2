package com.example.meetloggerv2.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: LocalFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<LocalFileEntity>)

    @Query("SELECT * FROM local_files WHERE userId = :userId ORDER BY timestampMillis DESC")
    suspend fun getUserFiles(userId: String): List<LocalFileEntity>

    @Query("SELECT * FROM local_files WHERE userId = :userId ORDER BY timestampMillis DESC")
    fun getUserFilesFlow(userId: String): Flow<List<LocalFileEntity>>

    @Query("SELECT * FROM local_files WHERE userId = :userId AND fileName = :fileName LIMIT 1")
    suspend fun getFileDetails(userId: String, fileName: String): LocalFileEntity?

    @Query("SELECT * FROM local_files WHERE userId = :userId AND fileName = :fileName LIMIT 1")
    fun getFileDetailsFlow(userId: String, fileName: String): Flow<LocalFileEntity?>

    @Query("DELETE FROM local_files WHERE userId = :userId AND fileName = :fileName")
    suspend fun deleteFile(userId: String, fileName: String)

    @Query("DELETE FROM local_files WHERE userId = :userId")
    suspend fun clearUserFiles(userId: String)

    @Transaction
    suspend fun syncUserFiles(userId: String, files: List<LocalFileEntity>) {
        clearUserFiles(userId)
        insertFiles(files)
    }
}
