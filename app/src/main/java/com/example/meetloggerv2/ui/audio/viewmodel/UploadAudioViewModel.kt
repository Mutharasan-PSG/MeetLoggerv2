package com.example.meetloggerv2.ui.audio.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.meetloggerv2.data.repository.AudioRepository
import com.example.meetloggerv2.data.repository.IAudioRepository
import com.example.meetloggerv2.data.repository.FileRepository
import com.example.meetloggerv2.data.repository.IFileRepository
import com.google.firebase.firestore.FieldValue
import androidx.lifecycle.viewModelScope
import com.example.meetloggerv2.core.network.NetworkResult
import kotlinx.coroutines.launch
import java.io.File

class UploadAudioViewModel @JvmOverloads constructor(
    private val audioRepository: IAudioRepository = AudioRepository(),
    private val fileRepository: IFileRepository = FileRepository()
) : ViewModel() {

    private val _uiState = MutableLiveData<UploadUiState>(UploadUiState.Idle)
    val uiState: LiveData<UploadUiState> = _uiState

    private val _userFiles = MutableLiveData<List<String>>()
    val userFiles: LiveData<List<String>> = _userFiles

    fun fetchUserFiles(userId: String) {
        fileRepository.getUserFiles(userId, { dataList ->
            val names = dataList.mapNotNull { it["fileName"] as? String }
            _userFiles.postValue(names)
        }, {
            _uiState.postValue(UploadUiState.Error(it.message ?: "Failed to fetch files"))
        })
    }

    fun processAudio(userId: String, file: File, uri: Uri, speakerNames: List<String>, followUpFileName: String) {
        _uiState.value = UploadUiState.Processing("Uploading...")
        
        var firebaseDone = false
        var backendDone = false

        // Firebase upload
        audioRepository.uploadAudioToStorage(userId, file.name, uri) { downloadUrl, exception ->
            if (downloadUrl != null) {
                val fileData = hashMapOf(
                    "fileName" to file.name,
                    "audioUrl" to downloadUrl,
                    "status" to "processing",
                    "OriginalLanguage" to "en",
                    "timestamp_clientUpload" to FieldValue.serverTimestamp(),
                    "followUpFileName" to followUpFileName
                )
                fileRepository.saveFileMetadata(userId, file.name, fileData, {
                    firebaseDone = true
                    checkCompletion(firebaseDone, backendDone)
                }, {
                    _uiState.postValue(UploadUiState.Error(it.message ?: "Failed to save metadata"))
                })
            } else {
                _uiState.postValue(UploadUiState.Error(exception?.message ?: "Failed to upload to Firebase"))
            }
        }

        // Backend upload
        val speakersJson = com.google.gson.Gson().toJson(speakerNames)
        viewModelScope.launch {
            val result = audioRepository.uploadAudioToBackend(file, userId, file.name, speakersJson, followUpFileName)
            when (result) {
                is NetworkResult.Success -> {
                    backendDone = true
                    checkCompletion(firebaseDone, backendDone)
                }
                is NetworkResult.Error -> _uiState.postValue(UploadUiState.Error(result.message ?: "Backend upload failed"))
                else -> {}
            }
        }
    }

    private fun checkCompletion(firebaseDone: Boolean, backendDone: Boolean) {
        if (firebaseDone && backendDone) {
            _uiState.postValue(UploadUiState.Processed)
        }
    }

    sealed class UploadUiState {
        object Idle : UploadUiState()
        data class Processing(val stage: String) : UploadUiState()
        object Processed : UploadUiState()
        data class Error(val message: String) : UploadUiState()
    }
}
