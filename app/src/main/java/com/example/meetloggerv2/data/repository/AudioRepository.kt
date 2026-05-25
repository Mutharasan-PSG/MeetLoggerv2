package com.example.meetloggerv2.data.repository

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storageMetadata
import com.example.meetloggerv2.data.remote.ApiService
import com.example.meetloggerv2.data.remote.RetrofitClient
import com.example.meetloggerv2.util.NetworkResult
import com.example.meetloggerv2.util.SafeApiCall
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import java.io.File

class AudioRepository(
    private val apiService: ApiService = RetrofitClient.apiService
) : SafeApiCall {

    private val storage: com.google.firebase.storage.FirebaseStorage by lazy { com.google.firebase.storage.FirebaseStorage.getInstance() }

    fun uploadAudioToStorage(userId: String, fileName: String, uri: Uri, onComplete: (String?, Exception?) -> Unit) {
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

    fun deleteAudioFromStorage(userId: String, fileName: String, onComplete: (Boolean, Exception?) -> Unit) {
        val storageRef = storage.reference.child("AudioFiles/$userId/$fileName")
        storageRef.delete()
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { onComplete(false, it) }
    }

    suspend fun uploadAudioToBackend(
        file: File,
        userId: String,
        fileName: String,
        speakersJson: String,
        followUpFileName: String
    ): NetworkResult<ResponseBody> {
        val fileRequestBody = file.asRequestBody("audio/mpeg".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", file.name, fileRequestBody)
        
        val userIdBody = userId.toRequestBody("text/plain".toMediaTypeOrNull())
        val fileNameBody = fileName.toRequestBody("text/plain".toMediaTypeOrNull())
        val speakersBody = speakersJson.toRequestBody("text/plain".toMediaTypeOrNull())
        val followUpFileNameBody = followUpFileName.toRequestBody("text/plain".toMediaTypeOrNull())

        return safeApiCall {
            apiService.uploadAudio(
                filePart,
                userIdBody,
                fileNameBody,
                speakersBody,
                followUpFileNameBody
            )
        }
    }

    fun listAudioFiles(userId: String, onComplete: (List<String>?, Exception?) -> Unit) {
        val storageRef = storage.reference.child("AudioFiles/$userId/")
        storageRef.listAll()
            .addOnSuccessListener { listResult ->
                val names = listResult.items.map { it.name }
                onComplete(names, null)
            }
            .addOnFailureListener { onComplete(null, it) }
    }

    fun downloadAudioBytes(userId: String, fileName: String, onComplete: (ByteArray?, Exception?) -> Unit) {
        val storageRef = storage.reference.child("AudioFiles/$userId/$fileName")
        storageRef.getBytes(Long.MAX_VALUE)
            .addOnSuccessListener { onComplete(it, null) }
            .addOnFailureListener { onComplete(null, it) }
    }

    fun uploadAudioBytes(userId: String, fileName: String, bytes: ByteArray, onComplete: (Boolean, Exception?) -> Unit) {
        val storageRef = storage.reference.child("AudioFiles/$userId/$fileName")
        storageRef.putBytes(bytes)
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { onComplete(false, it) }
    }
}
