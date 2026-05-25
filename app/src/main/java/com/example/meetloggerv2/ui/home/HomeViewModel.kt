package com.example.meetloggerv2.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.meetloggerv2.data.repository.FileRepository
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp

class HomeViewModel(private val fileRepository: FileRepository = FileRepository()) : ViewModel() {

    private val _files = MutableLiveData<List<Triple<String, String, Timestamp>>>()
    val files: LiveData<List<Triple<String, String, Timestamp>>> = _files

    private val _userProfile = MutableLiveData<Map<String, Any>?>()
    val userProfile: LiveData<Map<String, Any>?> = _userProfile

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private var listenerRegistration: ListenerRegistration? = null

    fun fetchFiles(userId: String) {
        listenerRegistration?.remove()
        listenerRegistration = fileRepository.getUserFiles(userId, { dataList ->
            val tripleList = dataList.mapNotNull { data ->
                val isCopy = data["isCopy"] as? Boolean ?: false
                if (isCopy) return@mapNotNull null

                val fileName = data["fileName"] as? String ?: return@mapNotNull null
                val status = data["status"] as? String ?: "processing"
                val timestamp = data["timestamp_clientUpload"] as? Timestamp ?: return@mapNotNull null
                Triple(fileName, status, timestamp)
            }
            _files.value = tripleList
        }, {
            _error.value = it.message ?: "Failed to fetch files"
        })
    }

    fun loadUserProfile(userId: String) {
        fileRepository.getUser(userId, { data ->
            _userProfile.value = data
        }, {
            _error.value = it.message ?: "Failed to load profile"
        })
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
