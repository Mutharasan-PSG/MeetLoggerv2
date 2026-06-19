package com.meetloggerv2.data.repository

import android.net.Uri
import com.meetloggerv2.data.remote.ApiService
import com.meetloggerv2.data.remote.RetrofitClient
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.core.network.SafeApiCall
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class AudioRepository(
    private val apiService: ApiService
) : IAudioRepository, SafeApiCall {

    private val storage: com.google.firebase.storage.FirebaseStorage by lazy { com.google.firebase.storage.FirebaseStorage.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun uploadAudioToStorage(userId: String, fileName: String, uri: Uri, onComplete: (String?, Exception?) -> Unit) {
        val storageRef = storage.reference.child("AudioFiles/$userId/$fileName")
        val metadata = com.google.firebase.storage.storageMetadata { contentType = "audio/mpeg" }

        storageRef.putFile(uri, metadata)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    onComplete(downloadUri.toString(), null)
                }.addOnFailureListener { onComplete(null, it) }
            }
            .addOnFailureListener { onComplete(null, it) }
    }

    override fun deleteAudioFromStorage(userId: String, fileName: String, onComplete: (Boolean, Exception?) -> Unit) {
        val storageRef = storage.reference.child("AudioFiles/$userId/$fileName")
        storageRef.delete()
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { onComplete(false, it) }
    }

    override suspend fun uploadAudioToBackend(
        file: File,
        userId: String,
        fileName: String,
        speakersJson: String,
        followUpFileName: String,
        autoSendEmail: Boolean,
        userEmail: String,
        userName: String
    ): NetworkResult<ResponseBody> {
        val fileRequestBody = file.asRequestBody("audio/mpeg".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", file.name, fileRequestBody)
        
        val userIdBody = userId.toRequestBody("text/plain".toMediaTypeOrNull())
        val fileNameBody = fileName.toRequestBody("text/plain".toMediaTypeOrNull())
        val speakersBody = speakersJson.toRequestBody("text/plain".toMediaTypeOrNull())
        val followUpFileNameBody = followUpFileName.toRequestBody("text/plain".toMediaTypeOrNull())
        val autoSendEmailBody = autoSendEmail.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val userEmailBody = userEmail.toRequestBody("text/plain".toMediaTypeOrNull())
        val userNameBody = userName.toRequestBody("text/plain".toMediaTypeOrNull())

        return safeApiCall {
            val firebaseToken = getFirebaseIdToken()
            apiService.uploadAudio(
                "Bearer $firebaseToken",
                filePart,
                userIdBody,
                fileNameBody,
                speakersBody,
                followUpFileNameBody,
                autoSendEmailBody,
                userEmailBody,
                userNameBody
            )
        }
    }

    override fun listAudioFiles(userId: String, onComplete: (List<String>?, Exception?) -> Unit) {
        val storageRef = storage.reference.child("AudioFiles/$userId/")
        storageRef.listAll()
            .addOnSuccessListener { listResult ->
                val names = listResult.items.map { it.name }
                onComplete(names, null)
            }
            .addOnFailureListener { onComplete(null, it) }
    }

    override fun downloadAudioBytes(userId: String, fileName: String, onComplete: (ByteArray?, Exception?) -> Unit) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val firebaseToken = getFirebaseIdToken()
                val response = apiService.downloadAudioFile("Bearer $firebaseToken", userId, fileName)
                if (response.isSuccessful && response.body() != null) {
                    val bytes = response.body()!!.bytes()
                    onComplete(bytes, null)
                } else {
                    onComplete(null, Exception("Download failed: ${response.code()} - ${response.message()}"))
                }
            } catch (e: Exception) {
                onComplete(null, e)
            }
        }
    }

    override fun downloadAudioToFile(userId: String, fileName: String, destination: File, onComplete: (Boolean, Exception?) -> Unit) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val firebaseToken = getFirebaseIdToken()
                val response = apiService.downloadAudioFile("Bearer $firebaseToken", userId, fileName)
                if (response.isSuccessful && response.body() != null) {
                    response.body()!!.byteStream().use { inputStream ->
                        destination.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    onComplete(true, null)
                } else {
                    onComplete(false, Exception("Download failed: ${response.code()} - ${response.message()}"))
                }
            } catch (e: Exception) {
                onComplete(false, e)
            }
        }
    }

    override fun getAudioDownloadUrl(userId: String, fileName: String, onComplete: (String?, Exception?) -> Unit) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = getPlaybackUrl(userId, fileName)
            if (result is NetworkResult.Success) {
                onComplete(result.data, null)
            } else if (result is NetworkResult.Error) {
                onComplete(null, Exception(result.message ?: "Failed to get playback URL"))
            }
        }
    }

    override fun uploadAudioBytes(userId: String, fileName: String, bytes: ByteArray, onComplete: (Boolean, Exception?) -> Unit) {
        val storageRef = storage.reference.child("AudioFiles/$userId/$fileName")
        storageRef.putBytes(bytes)
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { onComplete(false, it) }
    }

    override suspend fun getUploadUrl(userId: String, fileName: String): NetworkResult<String> {
        return safeApiCall {
            val firebaseToken = getFirebaseIdToken()
            val response = apiService.getUploadUrl("Bearer $firebaseToken", mapOf("userId" to userId, "fileName" to fileName))
            if (response.isSuccessful && response.body() != null) {
                val url = response.body()!!["uploadUrl"] ?: ""
                retrofit2.Response.success(url)
            } else {
                retrofit2.Response.error(response.code(), response.errorBody()!!)
            }
        }
    }

    override suspend fun getPlaybackUrl(userId: String, fileName: String): NetworkResult<String> {
        return safeApiCall {
            val firebaseToken = getFirebaseIdToken()
            val response = apiService.getPlaybackUrl("Bearer $firebaseToken", userId, fileName)
            if (response.isSuccessful && response.body() != null) {
                val url = response.body()!!["playbackUrl"] ?: ""
                retrofit2.Response.success(url)
            } else {
                retrofit2.Response.error(response.code(), response.errorBody()!!)
            }
        }
    }

    override suspend fun uploadToSignedUrl(url: String, file: File): NetworkResult<Unit> {
        val requestBody = file.asRequestBody("audio/mpeg".toMediaTypeOrNull())
        return safeApiCall {
            val response = apiService.uploadToSignedUrl(url, requestBody, "audio/mpeg")
            if (response.isSuccessful) {
                retrofit2.Response.success(Unit)
            } else {
                retrofit2.Response.error(response.code(), response.errorBody()!!)
            }
        }
    }

    override suspend fun listRawFilesFromBackend(userId: String): NetworkResult<List<String>> {
        return safeApiCall {
            val firebaseToken = getFirebaseIdToken()
            apiService.listRawFiles("Bearer $firebaseToken", userId)
        }
    }

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
}
