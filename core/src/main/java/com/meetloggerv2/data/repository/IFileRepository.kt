package com.meetloggerv2.data.repository

import com.meetloggerv2.data.model.User
import com.google.firebase.firestore.ListenerRegistration

import com.meetloggerv2.core.network.NetworkResult

interface IFileRepository {
    fun getUserFiles(userId: String, onUpdate: (List<Map<String, Any>>) -> Unit, onError: (Exception) -> Unit): ListenerRegistration
    
    // Backend API versions
    suspend fun listFilesFromBackend(userId: String): NetworkResult<List<Map<String, Any>>>
    fun getFilesFlow(userId: String): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>>
    suspend fun getCachedUserFiles(userId: String): List<Map<String, Any>>
    suspend fun getFileDetailsFromBackend(userId: String, fileName: String): NetworkResult<Map<String, Any>>
    suspend fun deleteFileOnBackend(userId: String, fileName: String): NetworkResult<Unit>
    suspend fun renameFileOnBackend(userId: String, oldName: String, newName: String): NetworkResult<Unit>
    suspend fun copyFileOnBackend(userId: String, oldName: String, newName: String): NetworkResult<String>
    suspend fun saveAsNewCopyOnBackend(userId: String, fileName: String, data: Map<String, Any>): NetworkResult<String>
    suspend fun updateFileContentOnBackend(userId: String, fileName: String, updates: Map<String, Any>): NetworkResult<Unit>
    suspend fun getUserProfileFromBackend(userId: String): NetworkResult<Map<String, Any>>
    suspend fun updateUserProfileOnBackend(userId: String, updates: Map<String, Any>): NetworkResult<Unit>

    fun getFileDetails(userId: String, fileName: String, onSuccess: (Map<String, Any>?) -> Unit, onError: (Exception) -> Unit)
    fun updateFileContent(userId: String, fileName: String, updates: Map<String, Any>, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun deleteFile(userId: String, fileName: String, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun renameFile(userId: String, oldFullName: String, newFullName: String, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun copyFile(userId: String, oldFullName: String, newFullName: String, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun saveFileMetadata(userId: String, fileName: String, data: Map<String, Any>, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun saveUser(user: User, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun getUser(userId: String, onSuccess: (Map<String, Any>?) -> Unit, onError: (Exception) -> Unit)
    fun checkUserExists(userId: String, onResult: (Boolean) -> Unit, onError: (Exception) -> Unit)
}
