package com.meetloggerv2.ui.home.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.meetloggerv2.core.network.NetworkResult
import androidx.lifecycle.viewModelScope
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.core.session.SessionManager
import com.meetloggerv2.data.local.ProfileDataStore
import com.meetloggerv2.data.model.ProcessedFile
import com.meetloggerv2.data.model.User
import com.meetloggerv2.data.repository.IFileRepository
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
    private val authSession: AuthSession,
    private val sessionManager: SessionManager
) : AndroidViewModel(application) {

    private val _files = MutableStateFlow<List<Triple<String, String, Timestamp>>>(emptyList())
    val files: StateFlow<List<Triple<String, String, Timestamp>>> = _files.asStateFlow()

    private val _userProfile = MutableStateFlow<Map<String, Any>?>(null)
    val userProfile: StateFlow<Map<String, Any>?> = _userProfile.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // True only until the very first server fetch resolves (success or error).
    // Drives the initial list shimmer; never flips back to true so later
    // refreshes/actions can't re-trigger the shimmer.
    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private val profileDataStore = ProfileDataStore(application)

    companion object {
        // Poll faster while a file is still processing so the status flips
        // promptly; back off when everything is settled to save battery/data.
        private const val POLL_INTERVAL_ACTIVE_MS = 5_000L
        private const val POLL_INTERVAL_IDLE_MS = 12_000L
    }

    init {
        observeFiles()
    }

    private fun observeFiles() {
        val userId = authSession.currentUserId() ?: return
        viewModelScope.launch {
            // Home reads the dedicated history track, not the file documents, so
            // it is retained independently of rename/delete/copy on other pages.
            fileRepository.getHistoryFlow(userId).collect { dataList ->
                val tripleList = dataList.mapNotNull { data ->
                    val file = ProcessedFile.fromMap(data) ?: return@mapNotNull null
                    Triple(file.fileName, file.status, file.timestamp)
                }
                // Structural equality check to avoid unnecessary UI refreshes
                if (_files.value != tripleList) {
                    _files.value = tripleList
                }
                // The local cache flow has produced data: leave the initial
                // shimmer now so we go straight to the list, never flashing the
                // empty placeholder in between.
                if (tripleList.isNotEmpty()) {
                    _isInitialLoading.value = false
                }
            }
        }
    }

    fun fetchFiles() {
        val userId = authSession.currentUserId() ?: return
        refreshFilesFromServer(userId)
    }

    fun fetchFiles(userId: String) {
        refreshFilesFromServer(userId)
    }

    fun refreshFilesFromServer(userId: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = fileRepository.listHistoryFromBackend(userId)
            if (result is NetworkResult.Error) {
                val msg = result.message
                if (msg != "User is not authenticated" && msg != "User not authenticated") {
                    _error.value = msg ?: "Failed to fetch latest files"
                }
            }
            _isRefreshing.value = false
            _isInitialLoading.value = false
        }
    }

    /**
     * Keeps the file list in sync with the backend while the Home screen is
     * visible. Call from the fragment inside repeatOnLifecycle(STARTED) so it is
     * cancelled automatically when Home leaves the screen.
     *
     * Polls quickly while any file is still in progress so that newly uploaded
     * files and the processing -> processed transition appear without reopening
     * the screen, and backs off once everything is settled. This is a quiet
     * refresh: it does not toggle the pull-to-refresh spinner and only surfaces
     * an error when there is nothing on screen, to avoid toast spam.
     */
    suspend fun autoRefreshLoop() {
        val userId = authSession.currentUserId() ?: return
        while (true) {
            val currentId = authSession.currentUserId()
            if (currentId == null || currentId != userId) {
                break
            }
            val result = fileRepository.listHistoryFromBackend(userId)
            when (result) {
                is NetworkResult.Error -> if (_files.value.isEmpty()) {
                    val msg = result.message
                    if (msg != "User is not authenticated" && msg != "User not authenticated") {
                        _error.value = msg ?: "Failed to fetch latest files"
                    }
                }
                else -> _error.value = null
            }
            _isInitialLoading.value = false
            // Only "processing" is transient; "saved"/"processed" are terminal, so
            // don't keep fast-polling for them.
            val hasPending = _files.value.any { (_, status, _) ->
                status.equals("processing", ignoreCase = true)
            }
            delay(if (hasPending) POLL_INTERVAL_ACTIVE_MS else POLL_INTERVAL_IDLE_MS)
        }
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
                val result = fileRepository.getUserProfileFromBackend(userId)
                when (result) {
                    is NetworkResult.Success -> {
                        val data = result.data
                        if (data != null) {
                            _userProfile.value = data
                            val name = data["name"] as? String ?: ""
                            val email = data["email"] as? String ?: ""
                            val photoUrl = data["photoUrl"] as? String ?: ""
                            val subscription = data["subscription"] as? String ?: "free"
                            profileDataStore.saveProfile(name, email, photoUrl, dateStr)
                            
                            val currentUser = sessionManager.getUserDetails()
                            if (currentUser != null) {
                                val updatedUser = currentUser.copy(
                                    name = name,
                                    email = email,
                                    photoUrl = photoUrl,
                                    subscription = subscription
                                )
                                sessionManager.saveUserDetails(updatedUser)
                            } else {
                                val newUser = User(
                                    id = userId,
                                    name = name,
                                    email = email,
                                    photoUrl = photoUrl,
                                    subscription = subscription
                                )
                                sessionManager.saveUserDetails(newUser)
                            }
                        }
                    }
                    is NetworkResult.Error -> {
                        if (cached != null) {
                            val profileMap = mapOf(
                                "name" to cached.name,
                                "email" to cached.email,
                                "photoUrl" to cached.photoUrl
                            )
                            _userProfile.value = profileMap
                        } else {
                            val msg = result.message
                            if (msg != "User is not authenticated" && msg != "User not authenticated") {
                                _error.value = msg ?: "Failed to load profile"
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun getCurrentDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    override fun onCleared() {
        super.onCleared()
    }
}
