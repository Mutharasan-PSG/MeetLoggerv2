package com.meetloggerv2.data.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.data.repository.IAudioRepository
import com.meetloggerv2.data.repository.IFileRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AudioUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun audioRepository(): IAudioRepository
        fun fileRepository(): IFileRepository
    }

    companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_SPEAKER_NAMES = "speaker_names"
        const val KEY_FOLLOW_UP_FILE_NAME = "follow_up_file_name"
        const val KEY_AUTO_SEND_EMAIL = "auto_send_email"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_USER_NAME = "user_name"
        const val KEY_ACTION = "action"

        const val ACTION_SAVE = "SAVE"
        const val ACTION_PROCESS = "PROCESS"

        const val PROGRESS_STAGE = "progress_stage"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        val audioRepository = entryPoint.audioRepository()
        val fileRepository = entryPoint.fileRepository()

        val userId = inputData.getString(KEY_USER_ID) ?: return@withContext Result.failure()
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return@withContext Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return@withContext Result.failure()
        val action = inputData.getString(KEY_ACTION) ?: ACTION_SAVE
        val speakerNames = inputData.getStringArray(KEY_SPEAKER_NAMES) ?: emptyArray()
        val followUpFileName = inputData.getString(KEY_FOLLOW_UP_FILE_NAME) ?: ""
        val autoSendEmail = inputData.getBoolean(KEY_AUTO_SEND_EMAIL, false)
        val userEmail = inputData.getString(KEY_USER_EMAIL) ?: ""
        val userName = inputData.getString(KEY_USER_NAME) ?: "User"

        val localFile = File(filePath)
        if (!localFile.exists()) {
            return@withContext Result.failure(workDataOf("error" to "Local file does not exist"))
        }

        // Optimistically record this file in the Home history so it appears as
        // "processing"/"saved" the instant the upload starts — before the blob
        // upload and metadata round-trips complete.
        val optimisticStatus = if (action == ACTION_PROCESS) "processing" else "saved"
        fileRepository.insertLocalHistory(userId, fileName, optimisticStatus)
        var metadataSaved = false

        try {
            // Stage 1: Uploading to Firebase Storage via Signed URL
            setProgress(workDataOf(PROGRESS_STAGE to "Requesting upload permission..."))
            val urlResult = audioRepository.getUploadUrl(userId, fileName)
            if (urlResult is NetworkResult.Error) {
                throw Exception(urlResult.message ?: "Failed to get upload URL")
            }
            val uploadUrl = (urlResult as NetworkResult.Success).data ?: throw Exception("Empty upload URL")

            setProgress(workDataOf(PROGRESS_STAGE to "Uploading audio..."))
            val uploadResult = audioRepository.uploadToSignedUrl(uploadUrl, localFile)
            if (uploadResult is NetworkResult.Error) {
                throw Exception(uploadResult.message ?: "Failed to upload to storage")
            }

            // Stage 2: Saving metadata to Firestore
            setProgress(workDataOf(PROGRESS_STAGE to "Saving metadata..."))
            val status = if (action == ACTION_PROCESS) "processing" else "saved"
            
            // Use ISO timestamp for REST API
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val timestamp = sdf.format(java.util.Date())

            val fileData = hashMapOf(
                "fileName" to fileName,
                "audioUrl" to "AudioFiles/$userId/$fileName", // Store the path instead of public URL
                "status" to status,
                "OriginalLanguage" to "en",
                "timestamp_clientUpload" to timestamp
            )
            if (action == ACTION_PROCESS) {
                fileData["followUpFileName"] = followUpFileName
            }
            
            val metaResult = fileRepository.saveAsNewCopyOnBackend(userId, fileName, fileData)
            if (metaResult is NetworkResult.Error) {
                throw Exception(metaResult.message ?: "Failed to save metadata via backend")
            }

            val serverFileName = if (metaResult is NetworkResult.Success) (metaResult.data ?: fileName) else fileName

            metadataSaved = true
            // The backend now holds the authoritative history entry. If it
            // de-duplicated the name, drop the optimistic row; then sync so Home
            // shows the real entry. Upsert sync won't disturb other entries.
            if (serverFileName != fileName) {
                fileRepository.removeLocalHistory(userId, fileName)
            }
            fileRepository.listHistoryFromBackend(userId)

            // Stage 3: Send to Backend if Processing
            if (action == ACTION_PROCESS) {
                setProgress(workDataOf(PROGRESS_STAGE to "Backend processing..."))
                val speakersJson = com.google.gson.Gson().toJson(speakerNames)
                val result = audioRepository.uploadAudioToBackend(
                    localFile, 
                    userId, 
                    serverFileName, 
                    speakersJson, 
                    followUpFileName,
                    autoSendEmail,
                    userEmail,
                    userName
                )
                when (result) {
                    is NetworkResult.Success -> {
                        // Success
                    }
                    is NetworkResult.Error -> {
                        return@withContext Result.retry()
                    }
                    else -> {}
                }
            }

            Result.success(workDataOf(KEY_ACTION to action, KEY_FILE_NAME to serverFileName))
        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                // Upload ultimately failed before the backend recorded it: drop the
                // optimistic Home entry so it doesn't linger forever as "processing".
                if (!metadataSaved) {
                    try { fileRepository.removeLocalHistory(userId, fileName) } catch (_: Exception) {}
                }
                Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
            }
        }
    }
}
