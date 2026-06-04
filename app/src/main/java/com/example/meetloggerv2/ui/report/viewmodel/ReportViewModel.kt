package com.example.meetloggerv2.ui.report.viewmodel

import androidx.lifecycle.*
import com.example.meetloggerv2.data.model.ProcessedFile
import com.example.meetloggerv2.data.repository.FileRepository
import com.example.meetloggerv2.data.repository.IFileRepository
import com.example.meetloggerv2.core.util.Event
import com.example.meetloggerv2.core.session.AuthSession
import com.google.firebase.Timestamp
import java.util.*

class ReportViewModel @JvmOverloads constructor(
    private val fileRepository: IFileRepository = FileRepository(),
    private val authSession: AuthSession = AuthSession()
) : ViewModel() {

    sealed class ReportEvent {
        data class FetchDetailsSuccess(val content: String) : ReportEvent()
        data class FetchDetailsError(val errorMsg: String) : ReportEvent()
    }

    private val _rawFiles = MutableLiveData<List<Triple<String, Timestamp, String>>>()
    private val _query = MutableLiveData<String>("")

    val filteredFiles: LiveData<List<Triple<String, Timestamp, String>>> = _query.switchMap { q ->
        _rawFiles.map { files ->
            val displayFiles = files.filter { it.third == "processed" }
            if (q.isNullOrEmpty()) {
                displayFiles.map { Triple(it.first.substringBeforeLast("."), it.second, it.third) }
            } else {
                val low = q.lowercase(Locale.getDefault())
                displayFiles.filter { it.first.lowercase(Locale.getDefault()).contains(low) }
                    .map { Triple(it.first.substringBeforeLast("."), it.second, it.third) }
            }
        }
    }

    private val _uiState = MutableLiveData<ReportUiState>(ReportUiState.Idle)
    val uiState: LiveData<ReportUiState> = _uiState

    private val _reportEvent = MutableLiveData<Event<ReportEvent>>()
    val reportEvent: LiveData<Event<ReportEvent>> = _reportEvent

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
            _rawFiles.postValue(list)
        }, {
            _uiState.postValue(ReportUiState.Error(it.message ?: "Failed to fetch files"))
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
                if (count == fileNames.size) _uiState.postValue(ReportUiState.Idle)
            }, {
                _uiState.postValue(ReportUiState.Error(it.message ?: "Failed to delete $name"))
            })
        }
    }

    fun renameFile(oldName: String, newName: String) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = ReportUiState.Loading("Renaming...")
        fileRepository.renameFile(userId, oldName, newName, {
            _uiState.postValue(ReportUiState.Idle)
        }, {
            _uiState.postValue(ReportUiState.Error(it.message ?: "Rename failed"))
        })
    }

    fun copyFile(oldName: String, newName: String) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = ReportUiState.Loading("Copying...")
        fileRepository.copyFile(userId, oldName, newName, {
            // Also mark as copy to exclude from Home
            fileRepository.updateFileContent(userId, newName, mapOf("isCopy" to true), {
                _uiState.postValue(ReportUiState.Idle)
            }, {
                _uiState.postValue(ReportUiState.Idle) // Silent fail for metadata
            })
        }, {
            _uiState.postValue(ReportUiState.Error(it.message ?: "Copy failed"))
        })
    }

    fun fetchFileDetails(fileName: String) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = ReportUiState.Loading("Fetching details...")
        fileRepository.getFileDetails(userId, fileName, { data ->
            val resp = data?.get("Response") as? String ?: "No response"
            _reportEvent.postValue(Event(ReportEvent.FetchDetailsSuccess(resp.replace("*", "").trim())))
            _uiState.postValue(ReportUiState.Idle)
        }, {
            _reportEvent.postValue(Event(ReportEvent.FetchDetailsError(it.message ?: "Failed to fetch details")))
            _uiState.postValue(ReportUiState.Idle)
        })
    }

    fun getFullFileName(shortName: String): String? {
        return _rawFiles.value?.find { it.first.substringBeforeLast(".") == shortName }?.first
    }

    sealed class ReportUiState {
        object Idle : ReportUiState()
        data class Loading(val message: String) : ReportUiState()
        data class Error(val message: String) : ReportUiState()
    }
}
