package com.meetloggerv2.data.repository

import com.meetloggerv2.data.local.db.LocalFileDao
import com.meetloggerv2.data.local.db.LocalFileEntity
import com.meetloggerv2.data.local.db.HistoryDao
import com.meetloggerv2.data.local.db.HistoryEntity
import kotlinx.coroutines.flow.map
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.core.network.SafeApiCall
import com.meetloggerv2.data.remote.ApiService
import com.meetloggerv2.data.remote.FileUpdateRequest
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject

class FileRepository @Inject constructor(
    private val localFileDao: LocalFileDao,
    private val historyDao: HistoryDao,
    private val apiService: ApiService
) : IFileRepository, SafeApiCall {

    // Auth is retained client-side only to mint the Firebase ID token that
    // authorizes every backend REST call. No direct Firestore/Storage access.
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private suspend fun getFirebaseIdToken(): String = suspendCancellableCoroutine { continuation ->
        val user = auth.currentUser
        if (user == null) {
            continuation.resumeWithException(IllegalStateException("User is not authenticated"))
            return@suspendCancellableCoroutine
        }

        user.getIdToken(false)
            .addOnSuccessListener { result ->
                val token = result.token
                if (token.isNullOrBlank()) {
                    continuation.resumeWithException(IllegalStateException("Firebase token is empty"))
                } else {
                    continuation.resume(token)
                }
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }

    override suspend fun listFilesFromBackend(userId: String): NetworkResult<List<Map<String, Any>>> {
        return safeApiCall { 
            val firebaseToken = getFirebaseIdToken()
            val response = apiService.listFiles("Bearer $firebaseToken", userId)
            if (response.isSuccessful && response.body() != null) {
                val files = response.body()!!
                // Sync with local database using a transaction to avoid UI flickering
                try {
                    val entities = files.map { LocalFileEntity.fromMap(userId, it) }
                    localFileDao.syncUserFiles(userId, entities)
                } catch (e: Exception) {
                    // Log error but return success as we have the data
                }
            }
            response
        }
    }

    override fun getFilesFlow(userId: String): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> {
        return localFileDao.getUserFilesFlow(userId).map { list ->
            list.map { it.toMap() }
        }
    }

    override suspend fun listHistoryFromBackend(userId: String): NetworkResult<List<Map<String, Any>>> {
        return safeApiCall {
            val firebaseToken = getFirebaseIdToken()
            val response = apiService.listHistory("Bearer $firebaseToken", userId)
            if (response.isSuccessful && response.body() != null) {
                try {
                    val entities = response.body()!!.map { HistoryEntity.fromMap(userId, it) }
                    historyDao.syncHistory(userId, entities)
                } catch (e: Exception) {
                    // Keep the cached history if the local sync fails; data is still returned.
                }
            }
            response
        }
    }

    override fun getHistoryFlow(userId: String): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> {
        return historyDao.getHistoryFlow(userId).map { list ->
            list.map { it.toMap() }
        }
    }

    override suspend fun insertLocalHistory(userId: String, fileName: String, status: String) {
        try {
            historyDao.upsert(
                HistoryEntity(
                    fileName = fileName,
                    userId = userId,
                    status = status,
                    timestampMillis = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {
            // Optimistic only; ignore local write failures.
        }
    }

    override suspend fun removeLocalHistory(userId: String, fileName: String) {
        try {
            historyDao.deleteHistory(userId, fileName)
        } catch (_: Exception) {
        }
    }

    override suspend fun getCachedUserFiles(userId: String): List<Map<String, Any>> {
        return try {
            localFileDao.getUserFiles(userId).map { it.toMap() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getFileDetailsFromBackend(userId: String, fileName: String): NetworkResult<Map<String, Any>> {
        return safeApiCall { 
            val firebaseToken = getFirebaseIdToken()
            val response = apiService.getFileDetails("Bearer $firebaseToken", userId, fileName)
            if (response.isSuccessful && response.body() != null) {
                try {
                    val data = response.body()!!.toMutableMap()
                    data["fileName"] = fileName
                    val entity = LocalFileEntity.fromMap(userId, data)
                    localFileDao.insertFile(entity)
                } catch (e: Exception) {
                    // Log error
                }
            }
            response
        }
    }
    override suspend fun deleteFileOnBackend(userId: String, fileName: String, target: String?): NetworkResult<Unit> {
        return safeApiCall {
            val firebaseToken = getFirebaseIdToken()
            val response = apiService.deleteFile("Bearer $firebaseToken", userId, fileName, target)
            if (response.isSuccessful) {
                // Optimistically update the local cache so the list Flow reflects
                // the change instantly, without waiting for a follow-up list fetch.
                // Do not delete local cached record if we only deleted raw audio!
                if (target != "audio") {
                    try { localFileDao.deleteFile(userId, fileName) } catch (_: Exception) {}
                }
                retrofit2.Response.success(Unit)
            }
            else retrofit2.Response.error(response.code(), response.errorBody()!!)
        }
    }

    override suspend fun renameFileOnBackend(userId: String, oldName: String, newName: String, target: String?): NetworkResult<Unit> {
        return safeApiCall {
            val firebaseToken = getFirebaseIdToken()
            val response = apiService.renameFile("Bearer $firebaseToken", userId, oldName, mapOf("newName" to newName), target)
            if (response.isSuccessful) {
                // Optimistically rename in the local cache for an instant UI update.
                // Do not rename local cached record if we only renamed raw audio!
                if (target != "audio") {
                    try {
                        val existing = localFileDao.getFileDetails(userId, oldName)
                        if (existing != null) {
                            localFileDao.deleteFile(userId, oldName)
                            localFileDao.insertFile(existing.copy(fileName = newName))
                        }
                    } catch (_: Exception) {}
                }
                retrofit2.Response.success(Unit)
            }
            else retrofit2.Response.error(response.code(), response.errorBody()!!)
        }
    }

    override suspend fun copyFileOnBackend(userId: String, oldName: String, newName: String): NetworkResult<String> {
        return safeApiCall {
            val firebaseToken = getFirebaseIdToken()
            val response = apiService.copyFile("Bearer $firebaseToken", userId, oldName, mapOf("newName" to newName))
            if (response.isSuccessful && response.body() != null) {
                val serverName = response.body()!!["newName"] ?: newName
                // Optimistically add the copy to the local cache for an instant UI update.
                try {
                    val existing = localFileDao.getFileDetails(userId, oldName)
                    if (existing != null) {
                        localFileDao.insertFile(existing.copy(fileName = serverName, audioUrl = null, isCopy = true))
                    }
                } catch (_: Exception) {}
                retrofit2.Response.success(serverName)
            } else {
                retrofit2.Response.error(response.code(), response.errorBody()!!)
            }
        }
    }

    override suspend fun saveAsNewCopyOnBackend(userId: String, fileName: String, data: Map<String, Any>): NetworkResult<String> {
        return safeApiCall {
            val firebaseToken = getFirebaseIdToken()
            val response = apiService.saveAsNewCopy("Bearer $firebaseToken", userId, fileName, com.meetloggerv2.data.remote.SaveAsNewRequest(data))
            if (response.isSuccessful && response.body() != null) {
                val serverName = response.body()!!["newName"] ?: fileName
                retrofit2.Response.success(serverName)
            } else {
                retrofit2.Response.error(response.code(), response.errorBody()!!)
            }
        }
    }

    override suspend fun updateFileContentOnBackend(userId: String, fileName: String, updates: Map<String, Any>): NetworkResult<Unit> {
        return safeApiCall {
            val firebaseToken = getFirebaseIdToken()
            val response = apiService.updateFileContent("Bearer $firebaseToken", userId, fileName, FileUpdateRequest(updates))
            if (response.isSuccessful) retrofit2.Response.success(Unit)
            else retrofit2.Response.error(response.code(), response.errorBody()!!)
        }
    }

    override suspend fun getUserProfileFromBackend(userId: String): NetworkResult<Map<String, Any>> {
        return safeApiCall {
            val firebaseToken = getFirebaseIdToken()
            apiService.getUserProfile("Bearer $firebaseToken", userId)
        }
    }

    override suspend fun updateUserProfileOnBackend(userId: String, updates: Map<String, Any>): NetworkResult<Unit> {
        return safeApiCall {
            val firebaseToken = getFirebaseIdToken()
            val response = apiService.updateUserProfile("Bearer $firebaseToken", userId, com.meetloggerv2.data.remote.ProfileUpdateRequest(updates))
            if (response.isSuccessful) retrofit2.Response.success(Unit)
            else retrofit2.Response.error(response.code(), response.errorBody()!!)
        }
    }

    override suspend fun deleteUserAccountFromBackend(userId: String): NetworkResult<Unit> {
        return safeApiCall {
            val firebaseToken = getFirebaseIdToken()
            val response = apiService.deleteUserAccount("Bearer $firebaseToken", userId)
            if (response.isSuccessful) retrofit2.Response.success(Unit)
            else retrofit2.Response.error(response.code(), response.errorBody()!!)
        }
    }
}
