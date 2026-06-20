package com.meetloggerv2.data.repository

import com.meetloggerv2.core.network.NetworkResult

interface IFileRepository {
    // All file/user metadata operations go through the backend REST API.
    suspend fun listFilesFromBackend(userId: String): NetworkResult<List<Map<String, Any>>>
    fun getFilesFlow(userId: String): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>>

    // Home activity history — a separate, retained track independent of file ops.
    suspend fun listHistoryFromBackend(userId: String): NetworkResult<List<Map<String, Any>>>
    fun getHistoryFlow(userId: String): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>>
    // Optimistic local history so Home reflects an upload the instant it starts.
    suspend fun insertLocalHistory(userId: String, fileName: String, status: String)
    suspend fun removeLocalHistory(userId: String, fileName: String)
    suspend fun getCachedUserFiles(userId: String): List<Map<String, Any>>
    suspend fun getFileDetailsFromBackend(userId: String, fileName: String): NetworkResult<Map<String, Any>>
    suspend fun deleteFileOnBackend(userId: String, fileName: String, target: String? = null): NetworkResult<Unit>
    suspend fun renameFileOnBackend(userId: String, oldName: String, newName: String, target: String? = null): NetworkResult<Unit>
    suspend fun copyFileOnBackend(userId: String, oldName: String, newName: String): NetworkResult<String>
    suspend fun saveAsNewCopyOnBackend(userId: String, fileName: String, data: Map<String, Any>): NetworkResult<String>
    suspend fun updateFileContentOnBackend(userId: String, fileName: String, updates: Map<String, Any>): NetworkResult<Unit>
    suspend fun getUserProfileFromBackend(userId: String): NetworkResult<Map<String, Any>>
    suspend fun updateUserProfileOnBackend(userId: String, updates: Map<String, Any>): NetworkResult<Unit>
}
