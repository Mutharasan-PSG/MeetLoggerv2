package com.example.meetloggerv2.ui.audio.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.example.meetloggerv2.data.repository.IAudioRepository
import com.example.meetloggerv2.data.repository.IFileRepository
import com.example.meetloggerv2.data.work.AudioUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UploadAudioViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val audioRepository: IAudioRepository,
    private val fileRepository: IFileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    private val _userFiles = MutableStateFlow<List<String>>(emptyList())
    // Keep userFiles LiveData exposed for dialog compatibility
    val userFiles: LiveData<List<String>> = _userFiles.asLiveData()
    val userFilesState: StateFlow<List<String>> = _userFiles.asStateFlow()

    fun fetchUserFiles(userId: String) {
        fileRepository.getUserFiles(userId, { dataList ->
            val names = dataList.mapNotNull { it["fileName"] as? String }
            _userFiles.value = names
        }, {
            _uiState.value = UploadUiState.Error(it.message ?: "Failed to fetch files")
        })
    }

    fun processAudio(userId: String, file: File, uri: Uri, speakerNames: List<String>, followUpFileName: String) {
        _uiState.value = UploadUiState.Processing("Uploading...")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val data = workDataOf(
            AudioUploadWorker.KEY_USER_ID to userId,
            AudioUploadWorker.KEY_FILE_PATH to file.absolutePath,
            AudioUploadWorker.KEY_FILE_NAME to file.name,
            AudioUploadWorker.KEY_SPEAKER_NAMES to speakerNames.toTypedArray(),
            AudioUploadWorker.KEY_FOLLOW_UP_FILE_NAME to followUpFileName,
            AudioUploadWorker.KEY_ACTION to AudioUploadWorker.ACTION_PROCESS
        )

        val workRequest = OneTimeWorkRequestBuilder<AudioUploadWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork("upload_${file.name}", ExistingWorkPolicy.REPLACE, workRequest)

        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfoByIdFlow(workRequest.id)
                .collect { workInfo ->
                    if (workInfo != null) {
                        when (workInfo.state) {
                            WorkInfo.State.RUNNING -> {
                                val stage = workInfo.progress.getString(AudioUploadWorker.PROGRESS_STAGE) ?: "Uploading..."
                                _uiState.value = UploadUiState.Processing(stage)
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                _uiState.value = UploadUiState.Processed
                            }
                            WorkInfo.State.FAILED -> {
                                val errorMsg = workInfo.outputData.getString("error") ?: "Upload failed"
                                _uiState.value = UploadUiState.Error(errorMsg)
                            }
                            else -> {}
                        }
                    }
                }
        }
    }

    sealed class UploadUiState {
        object Idle : UploadUiState()
        data class Processing(val stage: String) : UploadUiState()
        object Processed : UploadUiState()
        data class Error(val message: String) : UploadUiState()
    }
}
