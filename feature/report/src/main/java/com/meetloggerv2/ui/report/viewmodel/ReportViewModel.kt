package com.meetloggerv2.ui.report.viewmodel

import androidx.lifecycle.*
import com.meetloggerv2.data.model.ProcessedFile
import com.meetloggerv2.data.repository.IFileRepository
import com.meetloggerv2.core.util.Event
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.core.network.NetworkResult
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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

    private val _uiState = MutableStateFlow<ReportUiState>(ReportUiState.Loading("Loading reports..."))
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private val _reportEvent = MutableSharedFlow<Event<ReportEvent>>()
    val reportEvent: SharedFlow<Event<ReportEvent>> = _reportEvent.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // True only until the first list fetch resolves. Drives the initial list
    // shimmer and never flips back, so action loading (delete/rename/copy/
    // fetch-details) on the shared uiState can't re-trigger the shimmer.
    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    fun setQuery(q: String) {
        _query.value = q
    }

    init {
        observeFiles()
    }

    private fun observeFiles() {
        val userId = authSession.currentUserId() ?: return
        viewModelScope.launch {
            fileRepository.getFilesFlow(userId).collect { dataList ->
                val list = dataList.mapNotNull { data ->
                    val file = ProcessedFile.fromMap(data) ?: return@mapNotNull null
                    Triple(file.fileName, file.timestamp, file.status)
                }
                // Structural equality check to avoid unnecessary UI refreshes
                if (_rawFiles.value != list) {
                    _rawFiles.value = list
                }
                // The local cache flow has produced data: leave the initial
                // shimmer now so we go straight to the list, never flashing the
                // empty placeholder in between.
                if (list.isNotEmpty()) {
                    _isInitialLoading.value = false
                }
            }
        }
    }

    fun fetchFiles(showLoading: Boolean = false) {
        val userId = authSession.currentUserId() ?: return
        
        // Refresh from server API
        viewModelScope.launch {
            if (showLoading) _uiState.value = ReportUiState.Loading("Updating reports...")
            _isRefreshing.value = true
            val result = fileRepository.listFilesFromBackend(userId)
            if (result is NetworkResult.Error) {
                _uiState.value = ReportUiState.Error(result.message ?: "Failed to fetch files")
            } else {
                _uiState.value = ReportUiState.Idle
            }
            _isRefreshing.value = false
            _isInitialLoading.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun deleteFiles(fileNames: List<String>) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = ReportUiState.Loading("Deleting...")
        
        viewModelScope.launch {
            val deleted = mutableListOf<String>()
            fileNames.forEach { name ->
                val result = fileRepository.deleteFileOnBackend(userId, name, "file")
                if (result is NetworkResult.Success) {
                    deleted.add(name)
                }
            }
            // Reflect the removal in the in-memory list BEFORE leaving the loading
            // state, so the blocking loader stays up until the change is already on
            // screen (closes the gap before the Room cache flow re-emits).
            if (deleted.isNotEmpty()) {
                _rawFiles.value = _rawFiles.value.filterNot { it.first in deleted }
            }
            if (deleted.size == fileNames.size) {
                _uiState.value = ReportUiState.Idle
            } else {
                _uiState.value = ReportUiState.Error("Some files failed to delete")
            }
        }
    }

    fun renameFile(oldName: String, newName: String) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = ReportUiState.Loading("Renaming...")
        
        viewModelScope.launch {
            val result = fileRepository.renameFileOnBackend(userId, oldName, newName, "file")
            when (result) {
                is NetworkResult.Success -> {
                    // Rename in the in-memory list BEFORE leaving the loading state so
                    // the loader stays up until the renamed item is already on screen.
                    _rawFiles.value = _rawFiles.value.map { triple ->
                        if (triple.first == oldName) Triple(newName, triple.second, triple.third) else triple
                    }
                    _uiState.value = ReportUiState.Idle
                }
                is NetworkResult.Error -> _uiState.value = ReportUiState.Error(result.message ?: "Rename failed")
                else -> {}
            }
        }
    }

    fun copyFile(oldName: String, newName: String) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = ReportUiState.Loading("Copying...")
        
        viewModelScope.launch {
            val result = fileRepository.copyFileOnBackend(userId, oldName, newName)
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.value = ReportUiState.Idle
                }
                is NetworkResult.Error -> _uiState.value = ReportUiState.Error(result.message ?: "Copy failed")
                else -> {}
            }
        }
    }


    fun fetchFileDetails(fileName: String) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = ReportUiState.Loading("Fetching details...")
        
        viewModelScope.launch {
            val result = fileRepository.getFileDetailsFromBackend(userId, fileName)
            when (result) {
                is NetworkResult.Success -> {
                    val data = result.data
                    val resp = data?.get("Response") as? String ?: "No response"
                    _reportEvent.emit(Event(ReportEvent.FetchDetailsSuccess(resp.trim())))
                    _uiState.value = ReportUiState.Idle
                }
                is NetworkResult.Error -> {
                    _reportEvent.emit(Event(ReportEvent.FetchDetailsError(result.message ?: "Failed to fetch details")))
                    _uiState.value = ReportUiState.Idle
                }
                else -> {}
            }
        }
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
