package com.example.meetloggerv2.ui.report.viewmodel

import androidx.lifecycle.*
import com.example.meetloggerv2.data.model.ProcessedFile
import com.example.meetloggerv2.data.repository.IFileRepository
import com.example.meetloggerv2.core.util.Event
import com.example.meetloggerv2.core.session.AuthSession
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val fileRepository: IFileRepository,
    private val authSession: AuthSession
) : ViewModel() {

    sealed class ReportEvent {
        data class FetchDetailsSuccess(val content: String) : ReportEvent()
        data class FetchDetailsError(val errorMsg: String) : ReportEvent()
    }

    private val _rawFiles = MutableStateFlow<List<Triple<String, Timestamp, String>>>(emptyList())
    private val _query = MutableStateFlow<String>("")

    val filteredFiles: StateFlow<List<Triple<String, Timestamp, String>>> = combine(_rawFiles, _query) { files, q ->
        val displayFiles = files.filter { it.third == "processed" }
        if (q.isEmpty()) {
            displayFiles.map { Triple(it.first.substringBeforeLast("."), it.second, it.third) }
        } else {
            val low = q.lowercase(Locale.getDefault())
            displayFiles.filter { it.first.lowercase(Locale.getDefault()).contains(low) }
                .map { Triple(it.first.substringBeforeLast("."), it.second, it.third) }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _uiState = MutableStateFlow<ReportUiState>(ReportUiState.Idle)
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private val _reportEvent = MutableSharedFlow<Event<ReportEvent>>()
    val reportEvent: SharedFlow<Event<ReportEvent>> = _reportEvent.asSharedFlow()

    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    fun setQuery(q: String) {
        _query.value = q
    }

    fun fetchFiles() {
        val userId = authSession.currentUserId() ?: return
        listenerRegistration?.remove()
        listenerRegistration = fileRepository.getUserFiles(userId, { dataList ->
            val list = dataList.mapNotNull { data ->
                val file = ProcessedFile.fromMap(data) ?: return@mapNotNull null
                Triple(file.fileName, file.timestamp, file.status)
            }
            _rawFiles.value = list
        }, {
            _uiState.value = ReportUiState.Error(it.message ?: "Failed to fetch files")
        })
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }

    fun deleteFiles(fileNames: List<String>) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = ReportUiState.Loading("Deleting...")
        var count = 0
        fileNames.forEach { name ->
            fileRepository.deleteFile(userId, name, {
                count++
                if (count == fileNames.size) _uiState.value = ReportUiState.Idle
            }, {
                _uiState.value = ReportUiState.Error(it.message ?: "Failed to delete $name")
            })
        }
    }

    fun renameFile(oldName: String, newName: String) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = ReportUiState.Loading("Renaming...")
        fileRepository.renameFile(userId, oldName, newName, {
            _uiState.value = ReportUiState.Idle
        }, {
            _uiState.value = ReportUiState.Error(it.message ?: "Rename failed")
        })
    }

    fun copyFile(oldName: String, newName: String) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = ReportUiState.Loading("Copying...")
        fileRepository.copyFile(userId, oldName, newName, {
            fileRepository.updateFileContent(userId, newName, mapOf("isCopy" to true), {
                _uiState.value = ReportUiState.Idle
            }, {
                _uiState.value = ReportUiState.Idle
            })
        }, {
            _uiState.value = ReportUiState.Error(it.message ?: "Copy failed")
        })
    }

    fun fetchFileDetails(fileName: String) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = ReportUiState.Loading("Fetching details...")
        fileRepository.getFileDetails(userId, fileName, { data ->
            val resp = data?.get("Response") as? String ?: "No response"
            viewModelScope.launch {
                _reportEvent.emit(Event(ReportEvent.FetchDetailsSuccess(resp.replace("*", "").trim())))
            }
            _uiState.value = ReportUiState.Idle
        }, {
            viewModelScope.launch {
                _reportEvent.emit(Event(ReportEvent.FetchDetailsError(it.message ?: "Failed to fetch details")))
            }
            _uiState.value = ReportUiState.Idle
        })
    }

    fun getFullFileName(shortName: String): String? {
        return _rawFiles.value.find { it.first.substringBeforeLast(".") == shortName }?.first
    }

    sealed class ReportUiState {
        object Idle : ReportUiState()
        data class Loading(val message: String) : ReportUiState()
        data class Error(val message: String) : ReportUiState()
    }
}
