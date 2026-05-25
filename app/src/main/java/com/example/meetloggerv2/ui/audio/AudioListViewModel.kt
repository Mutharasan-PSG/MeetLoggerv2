package com.example.meetloggerv2.ui.audio

import androidx.lifecycle.*
import com.example.meetloggerv2.data.repository.AudioRepository
import com.example.meetloggerv2.data.repository.FileRepository
import com.google.firebase.firestore.FieldValue
import com.example.meetloggerv2.util.NetworkResult
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class AudioListViewModel(
    private val audioRepository: AudioRepository = AudioRepository(),
    private val fileRepository: FileRepository = FileRepository()
) : ViewModel() {

    private val _rawAudioFiles = MutableLiveData<List<String>>()
    private val _query = MutableLiveData<String>("")

    val filteredAudioFiles: LiveData<List<String>> = _query.switchMap { q ->
        _rawAudioFiles.map { files ->
            if (q.isNullOrEmpty()) files
            else files.filter { it.lowercase(Locale.getDefault()).contains(q.lowercase(Locale.getDefault())) }
        }
    }

    private val _uiState = MutableLiveData<AudioUiState>(AudioUiState.Idle)
    val uiState: LiveData<AudioUiState> = _uiState

    private val _userFiles = MutableLiveData<List<String>>()
    val userFiles: LiveData<List<String>> = _userFiles

    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    fun setQuery(q: String) {
        _query.value = q
    }

    fun fetchAudioFiles(userId: String) {
        listenerRegistration?.remove()
        audioRepository.listAudioFiles(userId) { names, exception ->
            if (names != null) {
                _rawAudioFiles.postValue(names.map { it.substringBeforeLast(".") }.sorted())
            } else {
                _uiState.postValue(AudioUiState.Error(exception?.message ?: "Failed to list audio files"))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }

    fun fetchUserFiles(userId: String) {
        _uiState.value = AudioUiState.Loading("Fetching files...")
        fileRepository.getUserFiles(userId, { dataList ->
            val names = dataList.mapNotNull { it["fileName"] as? String }
            _userFiles.postValue(names)
            _uiState.postValue(AudioUiState.Idle)
        }, {
            _uiState.postValue(AudioUiState.Error(it.message ?: "Failed to fetch files"))
        })
    }

    fun deleteAudioFiles(userId: String, names: List<String>) {
        _uiState.value = AudioUiState.Loading("Deleting...")
        var count = 0
        names.forEach { name ->
            audioRepository.deleteAudioFromStorage(userId, "$name.mp3") { _, _ ->
                count++
                if (count == names.size) {
                    fetchAudioFiles(userId)
                    _uiState.postValue(AudioUiState.Idle)
                }
            }
        }
    }

    fun renameAudioFile(userId: String, oldName: String, newName: String) {
        _uiState.value = AudioUiState.Loading("Renaming...")
        val oldFullName = "$oldName.mp3"
        val newFullName = "$newName.mp3"

        audioRepository.downloadAudioBytes(userId, oldFullName) { bytes, exception ->
            if (bytes != null) {
                audioRepository.uploadAudioBytes(userId, newFullName, bytes) { success, uploadException ->
                    if (success) {
                        audioRepository.deleteAudioFromStorage(userId, oldFullName) { _, _ ->
                            fileRepository.renameFile(userId, oldFullName, newFullName, {
                                fetchAudioFiles(userId)
                                _uiState.postValue(AudioUiState.Idle)
                            }, {
                                _uiState.postValue(AudioUiState.Error(it.message ?: "Firestore update failed"))
                            })
                        }
                    } else {
                        _uiState.postValue(AudioUiState.Error(uploadException?.message ?: "Upload failed"))
                    }
                }
            } else {
                _uiState.postValue(AudioUiState.Error(exception?.message ?: "Download failed"))
            }
        }
    }

    fun processAudio(userId: String, audioFile: File, speakerNames: List<String>, followUpFileName: String, finalFileName: String, audioUrl: String) {
        _uiState.value = AudioUiState.Loading("Processing...")
        val fileData = hashMapOf(
            "fileName" to finalFileName,
            "audioUrl" to audioUrl,
            "status" to "processing",
            "timestamp_clientUpload" to FieldValue.serverTimestamp(),
            "followUpFileName" to followUpFileName
        )

        fileRepository.saveFileMetadata(userId, finalFileName, fileData, {
            val speakersJson = com.google.gson.Gson().toJson(speakerNames)
            viewModelScope.launch {
                val result = audioRepository.uploadAudioToBackend(audioFile, userId, finalFileName, speakersJson, followUpFileName)
                when (result) {
                    is NetworkResult.Success -> _uiState.postValue(AudioUiState.Processed)
                    is NetworkResult.Error -> _uiState.postValue(AudioUiState.Error(result.message ?: "Backend processing failed"))
                    else -> {}
                }
            }
        }, {
            _uiState.postValue(AudioUiState.Error(it.message ?: "Failed to save metadata"))
        })
    }

    sealed class AudioUiState {
        object Idle : AudioUiState()
        data class Loading(val message: String) : AudioUiState()
        object Processed : AudioUiState()
        data class Error(val message: String) : AudioUiState()
    }
}
