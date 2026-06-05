package com.example.meetloggerv2.ui.home.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.meetloggerv2.core.session.AuthSession
import com.example.meetloggerv2.data.local.ProfileDataStore
import com.example.meetloggerv2.data.model.ProcessedFile
import com.example.meetloggerv2.data.repository.IFileRepository
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val fileRepository: IFileRepository,
    private val authSession: AuthSession
) : AndroidViewModel(application) {

    private val _files = MutableStateFlow<List<Triple<String, String, Timestamp>>>(emptyList())
    val files: StateFlow<List<Triple<String, String, Timestamp>>> = _files.asStateFlow()

    private val _userProfile = MutableStateFlow<Map<String, Any>?>(null)
    val userProfile: StateFlow<Map<String, Any>?> = _userProfile.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

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
                val profileMap = mapOf(
                    "name" to cached.name,
                    "email" to cached.email,
                    "photoUrl" to cached.photoUrl
                )
                _userProfile.value = profileMap
            } else {
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
