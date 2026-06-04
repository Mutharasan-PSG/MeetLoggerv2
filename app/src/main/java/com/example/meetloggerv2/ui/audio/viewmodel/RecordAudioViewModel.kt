package com.example.meetloggerv2.ui.audio.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.meetloggerv2.core.media.AudioRecorderManager
import com.example.meetloggerv2.core.network.NetworkResult
import com.example.meetloggerv2.data.repository.AudioRepository
import com.example.meetloggerv2.data.repository.IAudioRepository
import com.example.meetloggerv2.data.repository.FileRepository
import com.example.meetloggerv2.data.repository.IFileRepository
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class RecordAudioViewModel @JvmOverloads constructor(
    application: Application,
    private val audioRepository: IAudioRepository = AudioRepository(),
    private val fileRepository: IFileRepository = FileRepository()
) : AndroidViewModel(application) {

    enum class RecordState {
        IDLE, RECORDING, PAUSED, STOPPED
    }

    private val _uiState = MutableLiveData<UiState>(UiState.Idle)
    val uiState: LiveData<UiState> = _uiState

    private val _recordState = MutableLiveData<RecordState>(RecordState.IDLE)
    val recordState: LiveData<RecordState> = _recordState

    private val _elapsedTime = MutableLiveData<Int>(0)
    val elapsedTime: LiveData<Int> = _elapsedTime

    private val _userFiles = MutableLiveData<List<String>>()
    val userFiles: LiveData<List<String>> = _userFiles

    private val audioRecorder = AudioRecorderManager()
    private var recordTimerJob: Job? = null
    private var recordingStartTime = 0L
    private var elapsedTimeBeforePause = 0L

    fun fetchUserFiles(userId: String) {
        fileRepository.getUserFiles(userId, { dataList ->
            val names = dataList.mapNotNull { it["fileName"] as? String }
            _userFiles.postValue(names)
        }, {
            _uiState.postValue(UiState.Error(it.message ?: "Failed to fetch files"))
        })
    }

    fun startRecording(outputFile: File) {
        try {
            audioRecorder.start(outputFile)
            _recordState.value = RecordState.RECORDING
            recordingStartTime = System.currentTimeMillis()
            elapsedTimeBeforePause = 0L
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
            elapsedTimeBeforePause += System.currentTimeMillis() - recordingStartTime
            stopTimer()
        }
    }

    fun resumeRecording() {
        if (_recordState.value == RecordState.PAUSED) {
            audioRecorder.resume()
            _recordState.value = RecordState.RECORDING
            recordingStartTime = System.currentTimeMillis()
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
                val elapsed = System.currentTimeMillis() - recordingStartTime + elapsedTimeBeforePause
                _elapsedTime.postValue(elapsed.toInt())
                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        recordTimerJob?.cancel()
        recordTimerJob = null
    }

    fun saveAudio(userId: String, audioFile: File, uri: Uri) {
        _uiState.value = UiState.Saving
        audioRepository.uploadAudioToStorage(userId, audioFile.name, uri) { downloadUrl, exception ->
            if (downloadUrl != null) {
                val fileData = hashMapOf(
                    "fileName" to audioFile.name,
                    "audioUrl" to downloadUrl,
                    "status" to "saved",
                    "OriginalLanguage" to "en",
                    "timestamp_clientUpload" to FieldValue.serverTimestamp()
                )
                fileRepository.saveFileMetadata(userId, audioFile.name, fileData, {
                    _uiState.postValue(UiState.Saved(audioFile.name))
                }, {
                    _uiState.postValue(UiState.Error(it.message ?: "Failed to save metadata"))
                })
            } else {
                _uiState.postValue(UiState.Error(exception?.message ?: "Failed to upload audio"))
            }
        }
    }

    fun processAudio(userId: String, audioFile: File, uri: Uri, speakerNames: List<String>, followUpFileName: String) {
        _uiState.value = UiState.Processing("Uploading...")
        
        audioRepository.uploadAudioToStorage(userId, audioFile.name, uri) { downloadUrl, exception ->
            if (downloadUrl != null) {
                _uiState.postValue(UiState.Processing("Metadata..."))
                val fileData = hashMapOf(
                    "fileName" to audioFile.name,
                    "audioUrl" to downloadUrl,
                    "status" to "processing",
                    "timestamp_clientUpload" to FieldValue.serverTimestamp(),
                    "followUpFileName" to followUpFileName
                )
                fileRepository.saveFileMetadata(userId, audioFile.name, fileData, {
                    _uiState.postValue(UiState.Processing("Backend..."))
                    val speakersJson = com.google.gson.Gson().toJson(speakerNames)
                    viewModelScope.launch {
                        val result = audioRepository.uploadAudioToBackend(audioFile, userId, audioFile.name, speakersJson, followUpFileName)
                        when (result) {
                            is NetworkResult.Success -> _uiState.postValue(UiState.Processed)
                            is NetworkResult.Error -> _uiState.postValue(UiState.Error(result.message ?: "Backend processing failed"))
                            else -> {}
                        }
                    }
                }, {
                    _uiState.postValue(UiState.Error(it.message ?: "Failed to save metadata"))
                })
            } else {
                _uiState.postValue(UiState.Error(exception?.message ?: "Failed to upload audio"))
            }
        }
    }

    fun deleteAudio(userId: String, fileName: String) {
        audioRepository.deleteAudioFromStorage(userId, fileName) { _, _ -> }
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
