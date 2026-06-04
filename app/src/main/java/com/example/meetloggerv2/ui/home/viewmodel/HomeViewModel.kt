package com.example.meetloggerv2.ui.home.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.meetloggerv2.core.session.AuthSession
import com.example.meetloggerv2.data.local.ProfileDataStore
import com.example.meetloggerv2.data.model.ProcessedFile
import com.example.meetloggerv2.data.repository.FileRepository
import com.example.meetloggerv2.data.repository.IFileRepository
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeViewModel @JvmOverloads constructor(
    application: Application,
    private val fileRepository: IFileRepository = FileRepository(),
    private val authSession: AuthSession = AuthSession()
) : AndroidViewModel(application) {

    private val _files = MutableLiveData<List<Triple<String, String, Timestamp>>>()
    val files: LiveData<List<Triple<String, String, Timestamp>>> = _files

    private val _userProfile = MutableLiveData<Map<String, Any>?>()
    val userProfile: LiveData<Map<String, Any>?> = _userProfile

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private var listenerRegistration: ListenerRegistration? = null
    private val profileDataStore = ProfileDataStore(application)

    fun fetchFiles() {
        val userId = authSession.currentUserId() ?: return
        fetchFiles(userId)
    }

    fun fetchFiles(userId: String) {
        listenerRegistration?.remove()
        listenerRegistration = fileRepository.getUserFiles(userId, { dataList ->
            val tripleList = dataList.mapNotNull { data ->
                val file = ProcessedFile.fromMap(data) ?: return@mapNotNull null
                if (file.isCopy) return@mapNotNull null
                Triple(file.fileName, file.status, file.timestamp)
            }
            _files.value = tripleList
        }, {
            _error.value = it.message ?: "Failed to fetch files"
        })
    }

    fun loadUserProfile() {
        val userId = authSession.currentUserId() ?: return
        loadUserProfile(userId)
    }

    fun loadUserProfile(userId: String) {
        viewModelScope.launch {
            val dateStr = getCurrentDateString()
            val cached = profileDataStore.getProfile()
            
            if (cached != null && cached.lastFetchDate == dateStr) {
                // Return cached data
                val profileMap = mapOf(
                    "name" to cached.name,
                    "email" to cached.email,
                    "photoUrl" to cached.photoUrl
                )
                _userProfile.value = profileMap
            } else {
                // Fetch from database
                fileRepository.getUser(userId, { data ->
                    if (data != null) {
                        _userProfile.value = data
                        val name = data["name"] as? String ?: ""
                        val email = data["email"] as? String ?: ""
                        val photoUrl = data["photoUrl"] as? String ?: ""
                        viewModelScope.launch {
                            profileDataStore.saveProfile(name, email, photoUrl, dateStr)
                        }
                    } else {
                        // Fallback to cache if database returns null
                        if (cached != null) {
                            val profileMap = mapOf(
                                "name" to cached.name,
                                "email" to cached.email,
                                "photoUrl" to cached.photoUrl
                            )
                            _userProfile.value = profileMap
                        } else {
                            _userProfile.value = null
                        }
                    }
                }, { error ->
                    // Fallback to cache on error
                    if (cached != null) {
                        val profileMap = mapOf(
                            "name" to cached.name,
                            "email" to cached.email,
                            "photoUrl" to cached.photoUrl
                        )
                        _userProfile.value = profileMap
                    } else {
                        _error.value = error.message ?: "Failed to load profile"
                    }
                })
            }
        }
    }

    private fun getCurrentDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
