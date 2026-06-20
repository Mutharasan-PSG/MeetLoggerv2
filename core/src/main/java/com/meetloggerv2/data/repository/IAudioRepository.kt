package com.meetloggerv2.data.repository

import com.meetloggerv2.core.network.NetworkResult
import okhttp3.ResponseBody
import java.io.File

interface IAudioRepository {
    suspend fun uploadAudioToBackend(
        file: File, 
        userId: String, 
        fileName: String, 
        speakersJson: String, 
        followUpFileName: String,
        autoSendEmail: Boolean,
        userEmail: String,
        userName: String
    ): NetworkResult<ResponseBody>
    fun downloadAudioBytes(userId: String, fileName: String, onComplete: (ByteArray?, Exception?) -> Unit)
    fun downloadAudioToFile(userId: String, fileName: String, destination: File, onComplete: (Boolean, Exception?) -> Unit)
    fun getAudioDownloadUrl(userId: String, fileName: String, onComplete: (String?, Exception?) -> Unit)

    // Industry Standard Signed URL Flow
    suspend fun getUploadUrl(userId: String, fileName: String): NetworkResult<String>
    suspend fun getPlaybackUrl(userId: String, fileName: String): NetworkResult<String>
    suspend fun uploadToSignedUrl(url: String, file: File): NetworkResult<Unit>
    suspend fun listRawFilesFromBackend(userId: String): NetworkResult<List<String>>
}
