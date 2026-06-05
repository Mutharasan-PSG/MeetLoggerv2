package com.example.meetloggerv2.ui.audio.viewmodel

import androidx.lifecycle.*
import com.example.meetloggerv2.data.repository.IAudioRepository
import com.example.meetloggerv2.data.repository.IFileRepository
import com.google.firebase.firestore.FieldValue
import com.example.meetloggerv2.core.network.NetworkResult
import com.example.meetloggerv2.core.util.Event
import com.example.meetloggerv2.core.session.AuthSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import javax.inject.Inject

@HiltViewModel
class AudioListViewModel @Inject constructor(
    private val audioRepository: IAudioRepository,
    private val fileRepository: IFileRepository,
    private val authSession: AuthSession
) : ViewModel() {

    sealed class AudioEvent {
        data class DownloadFileSuccess(val fileName: String, val localFile: File) : AudioEvent()
        data class DownloadFileError(val fileName: String, val errorMsg: String) : AudioEvent()

        data class DownloadUrlSuccess(val fileName: String, val url: String) : AudioEvent()
        data class DownloadUrlError(val fileName: String, val errorMsg: String) : AudioEvent()

        data class DownloadBytesSuccess(val fileName: String, val bytes: ByteArray) : AudioEvent()
        data class DownloadBytesError(val fileName: String, val errorMsg: String) : AudioEvent()
    }

    private val _rawAudioFiles = MutableStateFlow<List<String>>(emptyList())
    private val _query = MutableStateFlow<String>("")

    val filteredAudioFiles: StateFlow<List<String>> = combine(_rawAudioFiles, _query) { files, q ->
        if (q.isEmpty()) files
        else files.filter { it.lowercase(Locale.getDefault()).contains(q.lowercase(Locale.getDefault())) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _uiState = MutableStateFlow<AudioUiState>(AudioUiState.Idle)
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()

    private val _userFiles = MutableStateFlow<List<String>>(emptyList())
    val userFilesState: StateFlow<List<String>> = _userFiles.asStateFlow()
    // Keep userFiles LiveData exposed for dialog compatibility
    val userFiles: LiveData<List<String>> = _userFiles.asLiveData()

    private val _audioEvent = MutableSharedFlow<Event<AudioEvent>>()
    val audioEvent: SharedFlow<Event<AudioEvent>> = _audioEvent.asSharedFlow()

    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    fun setQuery(q: String) {
        _query.value = q
    }

    fun fetchAudioFiles() {
        val userId = authSession.currentUserId() ?: return
        listenerRegistration?.remove()
        _uiState.value = AudioUiState.Loading("Loading...")
        audioRepository.listAudioFiles(userId) { names, exception ->
            if (names != null) {
                _rawAudioFiles.value = names.map { it.substringBeforeLast(".") }.sorted()
                _uiState.value = AudioUiState.Idle
            } else {
                _uiState.value = AudioUiState.Error(exception?.message ?: "Failed to list audio files")
            }
        }
    }

    fun downloadAudioFile(fileName: String, destination: File) {
        val userId = authSession.currentUserId() ?: return
        audioRepository.downloadAudioToFile(userId, fileName, destination) { success, exception ->
            viewModelScope.launch {
                if (success) {
                    _audioEvent.emit(Event(AudioEvent.DownloadFileSuccess(fileName, destination)))
                } else {
                    _audioEvent.emit(Event(AudioEvent.DownloadFileError(fileName, exception?.message ?: "Download failed")))
                }
            }
        }
    }

    fun getAudioDownloadUrl(fileName: String) {
        val userId = authSession.currentUserId() ?: return
        audioRepository.getAudioDownloadUrl(userId, fileName) { url, exception ->
            viewModelScope.launch {
                if (url != null) {
                    _audioEvent.emit(Event(AudioEvent.DownloadUrlSuccess(fileName, url)))
                } else {
                    _audioEvent.emit(Event(AudioEvent.DownloadUrlError(fileName, exception?.message ?: "Failed to get download URL")))
                }
            }
        }
    }

    fun downloadAudioBytes(fileName: String) {
        val userId = authSession.currentUserId() ?: return
        audioRepository.downloadAudioBytes(userId, fileName) { bytes, exception ->
            viewModelScope.launch {
                if (bytes != null) {
                    _audioEvent.emit(Event(AudioEvent.DownloadBytesSuccess(fileName, bytes)))
                } else {
                    _audioEvent.emit(Event(AudioEvent.DownloadBytesError(fileName, exception?.message ?: "Download failed")))
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }

    fun fetchUserFiles() {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = AudioUiState.Loading("Fetching files...")
        fileRepository.getUserFiles(userId, { dataList ->
            val names = dataList.mapNotNull { it["fileName"] as? String }
            _userFiles.value = names
            _uiState.value = AudioUiState.Idle
        }, {
            _uiState.value = AudioUiState.Error(it.message ?: "Failed to fetch files")
        })
    }

    fun deleteAudioFiles(names: List<String>) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = AudioUiState.Loading("Deleting...")
        var count = 0
        names.forEach { name ->
            audioRepository.deleteAudioFromStorage(userId, "$name.mp3") { _, _ ->
                count++
                if (count == names.size) {
                    fetchAudioFiles()
                    _uiState.value = AudioUiState.Idle
                }
            }
        }
    }

    fun renameAudioFile(oldName: String, newName: String) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = AudioUiState.Loading("Renaming...")
        val oldFullName = "$oldName.mp3"
        val newFullName = "$newName.mp3"

        audioRepository.downloadAudioBytes(userId, oldFullName) { bytes, exception ->
            if (bytes != null) {
                audioRepository.uploadAudioBytes(userId, newFullName, bytes) { success, uploadException ->
                    if (success) {
                        audioRepository.deleteAudioFromStorage(userId, oldFullName) { _, _ ->
                            fileRepository.renameFile(userId, oldFullName, newFullName, {
                                fetchAudioFiles()
                                _uiState.value = AudioUiState.Idle
                            }, {
                                _uiState.value = AudioUiState.Error(it.message ?: "Firestore update failed")
                            })
                        }
                    } else {
                        _uiState.value = AudioUiState.Error(uploadException?.message ?: "Upload failed")
                    }
                }
            } else {
                _uiState.value = AudioUiState.Error(exception?.message ?: "Download failed")
            }
        }
    }

    fun processAudio(audioFile: File, speakerNames: List<String>, followUpFileName: String, finalFileName: String, audioUrl: String) {
        val userId = authSession.currentUserId() ?: return
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
                    is NetworkResult.Success -> _uiState.value = AudioUiState.Processed
                    is NetworkResult.Error -> _uiState.value = AudioUiState.Error(result.message ?: "Backend processing failed")
                    else -> {}
                }
            }
        }, {
            _uiState.value = AudioUiState.Error(it.message ?: "Failed to save metadata")
        })
    }

    sealed class AudioUiState {
        object Idle : AudioUiState()
        data class Loading(val message: String) : AudioUiState()
        object Processed : AudioUiState()
        data class Error(val message: String) : AudioUiState()
    }
}
