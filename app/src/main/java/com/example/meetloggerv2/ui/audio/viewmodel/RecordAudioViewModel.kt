package com.example.meetloggerv2.ui.audio.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.meetloggerv2.data.repository.AudioRepository
import com.example.meetloggerv2.data.repository.FileRepository
import com.google.firebase.firestore.FieldValue
import androidx.lifecycle.viewModelScope
import com.example.meetloggerv2.core.network.NetworkResult
import kotlinx.coroutines.launch
import java.io.File

class RecordAudioViewModel(
    private val audioRepository: AudioRepository = AudioRepository(),
    private val fileRepository: FileRepository = FileRepository()
) : ViewModel() {

    private val _uiState = MutableLiveData<UiState>(UiState.Idle)
    val uiState: LiveData<UiState> = _uiState

    private val _userFiles = MutableLiveData<List<String>>()
    val userFiles: LiveData<List<String>> = _userFiles

    fun fetchUserFiles(userId: String) {
        fileRepository.getUserFiles(userId, { dataList ->
            val names = dataList.mapNotNull { it["fileName"] as? String }
            _userFiles.postValue(names)
        }, {
            _uiState.postValue(UiState.Error(it.message ?: "Failed to fetch files"))
        })
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
        audioRepository.deleteAudioFromStorage(userId, fileName) { success, exception ->
        }
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
