package com.meetloggerv2.ui.audio.viewmodel

import androidx.lifecycle.*
import com.meetloggerv2.data.repository.IAudioRepository
import com.meetloggerv2.data.repository.IFileRepository
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.core.util.Event
import com.meetloggerv2.core.session.AuthSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import javax.inject.Inject

import com.meetloggerv2.data.local.SettingsDataStore

@HiltViewModel
class AudioListViewModel @Inject constructor(
    private val audioRepository: IAudioRepository,
    private val fileRepository: IFileRepository,
    private val authSession: AuthSession,
    private val settingsDataStore: SettingsDataStore
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

    private val _uiState = MutableStateFlow<AudioUiState>(AudioUiState.Loading("Loading..."))
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()

    private val _userFiles = MutableStateFlow<List<String>>(emptyList())
    val userFilesState: StateFlow<List<String>> = _userFiles.asStateFlow()
    // Keep userFiles LiveData exposed for dialog compatibility
    val userFiles: LiveData<List<String>> = _userFiles.asLiveData()

    private val _audioEvent = MutableSharedFlow<Event<AudioEvent>>()
    val audioEvent: SharedFlow<Event<AudioEvent>> = _audioEvent.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // True only until the first audio-list fetch resolves. Drives the initial
    // list shimmer and never flips back, so action loading (download/delete/
    // rename/process) on the shared uiState can't re-trigger the shimmer.
    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    fun setQuery(q: String) {
        _query.value = q
    }

    fun fetchAudioFiles(showLoading: Boolean = false) {
        val userId = authSession.currentUserId() ?: return
        if (showLoading) _uiState.value = AudioUiState.Loading("Loading...")
        _isRefreshing.value = true
        viewModelScope.launch {
            val result = audioRepository.listRawFilesFromBackend(userId)
            if (result is NetworkResult.Success) {
                // Set the list before leaving the shimmer so we never flash the
                // empty placeholder between shimmer and list.
                _rawAudioFiles.value = result.data?.map { it.substringBeforeLast(".") }?.sorted() ?: emptyList()
                _uiState.value = AudioUiState.Idle
            } else if (result is NetworkResult.Error) {
                _uiState.value = AudioUiState.Error(result.message ?: "Failed to list audio files")
            }
            _isRefreshing.value = false
            _isInitialLoading.value = false
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
        viewModelScope.launch {
            val result = audioRepository.getPlaybackUrl(userId, fileName)
            if (result is NetworkResult.Success) {
                _audioEvent.emit(Event(AudioEvent.DownloadUrlSuccess(fileName, result.data ?: "")))
            } else if (result is NetworkResult.Error) {
                _audioEvent.emit(Event(AudioEvent.DownloadUrlError(fileName, result.message ?: "Failed to get playback URL")))
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
    }

    fun fetchUserFiles() {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = AudioUiState.Loading("Fetching files...")
        
        viewModelScope.launch {
            fileRepository.getFilesFlow(userId).collect { dataList ->
                val names = dataList.mapNotNull { it["fileName"] as? String }
                _userFiles.value = names
            }
        }

        viewModelScope.launch {
            val result = audioRepository.listRawFilesFromBackend(userId)
            if (result is NetworkResult.Success) {
                _rawAudioFiles.value = result.data?.map { it.substringBeforeLast(".") }?.sorted() ?: emptyList()
                _uiState.value = AudioUiState.Idle
            } else if (result is NetworkResult.Error) {
                _uiState.value = AudioUiState.Error(result.message ?: "Failed to fetch files")
            }
            _isInitialLoading.value = false
        }
    }

    fun deleteAudioFiles(names: List<String>) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = AudioUiState.Loading("Deleting...")
        
        viewModelScope.launch {
            var successCount = 0
            names.forEach { name ->
                val result = fileRepository.deleteFileOnBackend(userId, name)
                if (result is NetworkResult.Success) {
                    successCount++
                }
            }
            if (successCount == names.size) {
                // Update the in-memory list immediately so the change shows
                // instantly; the fetch below just reconciles with the server.
                _rawAudioFiles.value = _rawAudioFiles.value.filterNot { it in names }
                _uiState.value = AudioUiState.Idle
                fetchAudioFiles()
            } else {
                _uiState.value = AudioUiState.Error("Some files failed to delete")
            }
        }
    }

    fun renameAudioFile(oldName: String, newName: String) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = AudioUiState.Loading("Renaming...")
        
        viewModelScope.launch {
            val result = fileRepository.renameFileOnBackend(userId, oldName, newName)
            when (result) {
                is NetworkResult.Success -> {
                    // Rename in the in-memory list immediately for an instant UI
                    // update; the fetch below just reconciles with the server.
                    _rawAudioFiles.value = _rawAudioFiles.value
                        .map { if (it == oldName) newName else it }
                        .sorted()
                    _uiState.value = AudioUiState.Idle
                    fetchAudioFiles()
                }
                is NetworkResult.Error -> _uiState.value = AudioUiState.Error(result.message ?: "Rename failed")
                else -> {}
            }
        }
    }

    fun processAudio(audioFile: File, speakerNames: List<String>, followUpFileName: String, finalFileName: String, audioUrl: String) {
        val userId = authSession.currentUserId() ?: return
        val userEmail = authSession.currentUserEmail() ?: ""
        val userName = authSession.currentUserName() ?: "User"
        _uiState.value = AudioUiState.Loading("Processing...")
        
        // Use a standard ISO timestamp instead of FieldValue.serverTimestamp() for REST
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = sdf.format(Date())

        val fileData = hashMapOf(
            "fileName" to finalFileName,
            "audioUrl" to audioUrl,
            "status" to "processing",
            "timestamp_clientUpload" to timestamp,
            "followUpFileName" to followUpFileName
        )

        viewModelScope.launch {
            val metaResult = fileRepository.saveAsNewCopyOnBackend(userId, finalFileName, fileData)
            if (metaResult is NetworkResult.Success) {
                val serverName = metaResult.data ?: finalFileName
                val speakersJson = com.google.gson.Gson().toJson(speakerNames)
                val autoSend = settingsDataStore.autoSendEmail.first()
                val result = audioRepository.uploadAudioToBackend(
                    audioFile, 
                    userId, 
                    serverName, 
                    speakersJson, 
                    followUpFileName,
                    autoSend,
                    userEmail,
                    userName
                )
                when (result) {
                    is NetworkResult.Success -> _uiState.value = AudioUiState.Processed
                    is NetworkResult.Error -> _uiState.value = AudioUiState.Error(result.message ?: "Backend processing failed")
                    else -> {}
                }
            } else if (metaResult is NetworkResult.Error) {
                _uiState.value = AudioUiState.Error(metaResult.message ?: "Failed to save metadata")
            }
        }
    }

    sealed class AudioUiState {
        object Idle : AudioUiState()
        data class Loading(val message: String) : AudioUiState()
        object Processed : AudioUiState()
        data class Error(val message: String) : AudioUiState()
    }
}
