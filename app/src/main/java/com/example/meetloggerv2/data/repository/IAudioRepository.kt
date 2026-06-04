package com.example.meetloggerv2.data.repository

import android.net.Uri
import com.example.meetloggerv2.core.network.NetworkResult
import okhttp3.ResponseBody
import java.io.File

interface IAudioRepository {
    fun uploadAudioToStorage(userId: String, fileName: String, uri: Uri, onComplete: (String?, Exception?) -> Unit)
    fun deleteAudioFromStorage(userId: String, fileName: String, onComplete: (Boolean, Exception?) -> Unit)
    suspend fun uploadAudioToBackend(file: File, userId: String, fileName: String, speakersJson: String, followUpFileName: String): NetworkResult<ResponseBody>
    fun listAudioFiles(userId: String, onComplete: (List<String>?, Exception?) -> Unit)
    fun downloadAudioBytes(userId: String, fileName: String, onComplete: (ByteArray?, Exception?) -> Unit)
    fun downloadAudioToFile(userId: String, fileName: String, destination: File, onComplete: (Boolean, Exception?) -> Unit)
    fun getAudioDownloadUrl(userId: String, fileName: String, onComplete: (String?, Exception?) -> Unit)
    fun uploadAudioBytes(userId: String, fileName: String, bytes: ByteArray, onComplete: (Boolean, Exception?) -> Unit)
}
