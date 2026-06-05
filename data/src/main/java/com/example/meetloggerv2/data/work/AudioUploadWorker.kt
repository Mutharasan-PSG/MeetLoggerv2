package com.example.meetloggerv2.data.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.meetloggerv2.core.network.NetworkResult
import com.example.meetloggerv2.data.repository.IAudioRepository
import com.example.meetloggerv2.data.repository.IFileRepository
import com.google.firebase.firestore.FieldValue
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

        val localFile = File(filePath)
        if (!localFile.exists()) {
            return@withContext Result.failure(workDataOf("error" to "Local file does not exist"))
        }
        val fileUri = Uri.fromFile(localFile)

        try {
            // Stage 1: Uploading to Firebase Storage
            setProgress(workDataOf(PROGRESS_STAGE to "Uploading to storage..."))
            val downloadUrl = uploadToStorage(audioRepository, userId, fileName, fileUri)

            // Stage 2: Saving metadata to Firestore
            setProgress(workDataOf(PROGRESS_STAGE to "Saving metadata..."))
            val status = if (action == ACTION_PROCESS) "processing" else "saved"
            val fileData = hashMapOf(
                "fileName" to fileName,
                "audioUrl" to downloadUrl,
                "status" to status,
                "OriginalLanguage" to "en",
                "timestamp_clientUpload" to FieldValue.serverTimestamp()
            )
            if (action == ACTION_PROCESS) {
                fileData["followUpFileName"] = followUpFileName
            }
            saveMetadata(fileRepository, userId, fileName, fileData)

            // Stage 3: Send to Backend if Processing
            if (action == ACTION_PROCESS) {
                setProgress(workDataOf(PROGRESS_STAGE to "Backend processing..."))
                val speakersJson = com.google.gson.Gson().toJson(speakerNames)
                val result = audioRepository.uploadAudioToBackend(localFile, userId, fileName, speakersJson, followUpFileName)
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

            Result.success(workDataOf(KEY_ACTION to action, KEY_FILE_NAME to fileName))
        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
            }
        }
    }

    private suspend fun uploadToStorage(
        audioRepository: IAudioRepository,
        userId: String,
        fileName: String,
        uri: Uri
    ): String = suspendCancellableCoroutine { continuation ->
        audioRepository.uploadAudioToStorage(userId, fileName, uri) { downloadUrl, exception ->
            if (downloadUrl != null) {
                continuation.resume(downloadUrl)
            } else {
                continuation.resumeWithException(exception ?: Exception("Firebase storage upload failed"))
            }
        }
    }

    private suspend fun saveMetadata(
        fileRepository: IFileRepository,
        userId: String,
        fileName: String,
        data: Map<String, Any>
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        fileRepository.saveFileMetadata(userId, fileName, data, {
            continuation.resume(Unit)
        }, {
            continuation.resumeWithException(it)
        })
    }
}
