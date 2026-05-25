package com.example.meetloggerv2.ui.report.viewmodel

import androidx.lifecycle.*
import com.example.meetloggerv2.data.model.ProcessedFile
import com.example.meetloggerv2.data.repository.FileRepository
import com.google.firebase.Timestamp
import java.util.*

class ReportViewModel(private val fileRepository: FileRepository = FileRepository()) : ViewModel() {

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

    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    fun setQuery(q: String) {
        _query.value = q
    }

    fun fetchFiles(userId: String) {
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

    fun deleteFiles(userId: String, fileNames: List<String>) {
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

    fun renameFile(userId: String, oldName: String, newName: String) {
        _uiState.value = ReportUiState.Loading("Renaming...")
        fileRepository.renameFile(userId, oldName, newName, {
            _uiState.postValue(ReportUiState.Idle)
        }, {
            _uiState.postValue(ReportUiState.Error(it.message ?: "Rename failed"))
        })
    }

    fun copyFile(userId: String, oldName: String, newName: String) {
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

    fun fetchFileDetails(userId: String, fileName: String, callback: (String) -> Unit) {
        _uiState.value = ReportUiState.Loading("Fetching details...")
        fileRepository.getFileDetails(userId, fileName, { data ->
            val resp = data?.get("Response") as? String ?: "No response"
            callback(resp.replace("*", "").trim())
            _uiState.postValue(ReportUiState.Idle)
        }, {
            _uiState.postValue(ReportUiState.Error(it.message ?: "Failed"))
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
