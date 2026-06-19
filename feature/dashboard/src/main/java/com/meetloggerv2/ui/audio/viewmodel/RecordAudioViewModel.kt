package com.meetloggerv2.ui.audio.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.meetloggerv2.core.media.AudioRecorderManager
import com.meetloggerv2.data.repository.IAudioRepository
import com.meetloggerv2.data.repository.IFileRepository
import com.meetloggerv2.data.work.AudioUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.data.local.SettingsDataStore
import kotlinx.coroutines.flow.first

@HiltViewModel
class RecordAudioViewModel @Inject constructor(
    application: Application,
    private val audioRepository: IAudioRepository,
    private val fileRepository: IFileRepository,
    private val authSession: AuthSession,
    private val settingsDataStore: SettingsDataStore
) : AndroidViewModel(application) {

    enum class RecordState {
        IDLE, RECORDING, PAUSED, STOPPED
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _recordState = MutableStateFlow<RecordState>(RecordState.IDLE)
    val recordState: StateFlow<RecordState> = _recordState.asStateFlow()

    private val _elapsedTime = MutableStateFlow<Int>(0)
    val elapsedTime: StateFlow<Int> = _elapsedTime.asStateFlow()

    private val _userFiles = MutableStateFlow<List<String>>(emptyList())
    // Keep userFiles LiveData exposed for dialog compatibility
    val userFiles: LiveData<List<String>> = _userFiles.asLiveData()
    val userFilesState: StateFlow<List<String>> = _userFiles.asStateFlow()

    private val audioRecorder = AudioRecorderManager()
    private var recordTimerJob: Job? = null

    fun fetchUserFiles(userId: String) {
        fileRepository.getUserFiles(userId, { dataList ->
            val names = dataList.mapNotNull { it["fileName"] as? String }
            _userFiles.value = names
        }, {
            _uiState.value = UiState.Error(it.message ?: "Failed to fetch files")
        })
    }

    fun startRecording(outputFile: File) {
        try {
            audioRecorder.start(outputFile)
            _recordState.value = RecordState.RECORDING
            _elapsedTime.value = 0
            startTimer()
        } catch (e: Exception) {
            _uiState.value = UiState.Error(e.message ?: "Failed to start recording")
        }
    }

    fun pauseRecording() {
        if (_recordState.value == RecordState.RECORDING) {
            audioRecorder.pause()
            _recordState.value = RecordState.PAUSED
            stopTimer()
        }
    }

    fun resumeRecording() {
        if (_recordState.value == RecordState.PAUSED) {
            audioRecorder.resume()
            _recordState.value = RecordState.RECORDING
            startTimer()
        }
    }

    fun stopRecording() {
        if (_recordState.value == RecordState.RECORDING || _recordState.value == RecordState.PAUSED) {
            audioRecorder.stop()
            _recordState.value = RecordState.STOPPED
            stopTimer()
        }
    }

    fun releaseRecorder() {
        audioRecorder.release()
        _recordState.value = RecordState.IDLE
        stopTimer()
    }

    fun getMaxAmplitude(): Int = audioRecorder.getMaxAmplitude()

    private fun startTimer() {
        recordTimerJob?.cancel()
        recordTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _elapsedTime.value += 1
                if (authSession.currentUserSubscription() == "free" && _elapsedTime.value >= 1800) {
                    stopRecording()
                    _uiState.value = UiState.Error("Free plan limit reached: Recording stopped at 30 minutes")
                    break
                }
            }
        }
    }

    private fun stopTimer() {
        recordTimerJob?.cancel()
        recordTimerJob = null
    }

    fun saveAudio(userId: String, audioFile: File, uri: Uri) {
        _uiState.value = UiState.Saving

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val data = workDataOf(
            AudioUploadWorker.KEY_USER_ID to userId,
            AudioUploadWorker.KEY_FILE_PATH to audioFile.absolutePath,
            AudioUploadWorker.KEY_FILE_NAME to audioFile.name,
            AudioUploadWorker.KEY_ACTION to AudioUploadWorker.ACTION_SAVE
        )

        val workRequest = OneTimeWorkRequestBuilder<AudioUploadWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .build()

        WorkManager.getInstance(getApplication())
            .enqueueUniqueWork("upload_${audioFile.name}", ExistingWorkPolicy.REPLACE, workRequest)

        observeWorkProgress(workRequest.id, audioFile.name)
    }

    fun processAudio(userId: String, audioFile: File, uri: Uri, speakerNames: List<String>, followUpFileName: String) {
        _uiState.value = UiState.Processing("Uploading...")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        viewModelScope.launch {
            val autoSend = settingsDataStore.autoSendEmail.first()
            val userEmail = authSession.currentUserEmail() ?: ""
            val userName = authSession.currentUserName() ?: "User"

            val data = workDataOf(
                AudioUploadWorker.KEY_USER_ID to userId,
                AudioUploadWorker.KEY_FILE_PATH to audioFile.absolutePath,
                AudioUploadWorker.KEY_FILE_NAME to audioFile.name,
                AudioUploadWorker.KEY_SPEAKER_NAMES to speakerNames.toTypedArray(),
                AudioUploadWorker.KEY_FOLLOW_UP_FILE_NAME to followUpFileName,
                AudioUploadWorker.KEY_AUTO_SEND_EMAIL to autoSend,
                AudioUploadWorker.KEY_USER_EMAIL to userEmail,
                AudioUploadWorker.KEY_USER_NAME to userName,
                AudioUploadWorker.KEY_ACTION to AudioUploadWorker.ACTION_PROCESS
            )

            val workRequest = OneTimeWorkRequestBuilder<AudioUploadWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .build()

            WorkManager.getInstance(getApplication())
                .enqueueUniqueWork("upload_${audioFile.name}", ExistingWorkPolicy.REPLACE, workRequest)

            observeWorkProgress(workRequest.id, audioFile.name)
        }
    }

    private fun observeWorkProgress(workId: java.util.UUID, fallbackFileName: String) {
        viewModelScope.launch {
            WorkManager.getInstance(getApplication())
                .getWorkInfoByIdFlow(workId)
                .collect { workInfo ->
                    if (workInfo != null) {
                        when (workInfo.state) {
                            WorkInfo.State.RUNNING -> {
                                val stage = workInfo.progress.getString(AudioUploadWorker.PROGRESS_STAGE) ?: "Uploading..."
                                _uiState.value = UiState.Processing(stage)
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                val action = workInfo.outputData.getString(AudioUploadWorker.KEY_ACTION)
                                val completedName = workInfo.outputData.getString(AudioUploadWorker.KEY_FILE_NAME) ?: fallbackFileName
                                if (action == AudioUploadWorker.ACTION_SAVE) {
                                    _uiState.value = UiState.Saved(completedName)
                                } else {
                                    _uiState.value = UiState.Processed
                                }
                            }
                            WorkInfo.State.FAILED -> {
                                val errorMsg = workInfo.outputData.getString("error") ?: "Upload failed"
                                _uiState.value = UiState.Error(errorMsg)
                            }
                            else -> {}
                        }
                    }
                }
        }
    }

    fun deleteAudio(userId: String, fileName: String) {
        viewModelScope.launch {
            fileRepository.deleteFileOnBackend(userId, fileName)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
        stopTimer()
    }

    sealed class UiState {
        object Idle : UiState()
        object Saving : UiState()
        data class Saved(val fileName: String) : UiState()
        data class Processing(val stage: String) : UiState()
        object Processed : UiState()
        data class Error(val message: String) : UiState()
    }
}
