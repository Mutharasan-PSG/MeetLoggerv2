package com.meetloggerv2.ui.profile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meetloggerv2.core.theme.AppStrings
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.core.session.SessionManager
import com.meetloggerv2.data.local.ProfileDataStore
import com.meetloggerv2.data.model.User
import com.meetloggerv2.data.repository.IFileRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
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
class ProfileViewModel @Inject constructor(
    application: Application,
    private val fileRepository: IFileRepository,
    private val authSession: AuthSession
) : AndroidViewModel(application) {

    sealed class SignOutState {
        object Idle : SignOutState()
        object Loading : SignOutState()
        object Success : SignOutState()
        data class Error(val message: String) : SignOutState()
    }

    private val _userProfile = MutableStateFlow<Map<String, Any>?>(null)
    val userProfile: StateFlow<Map<String, Any>?> = _userProfile.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _signOutState = MutableStateFlow<SignOutState>(SignOutState.Idle)
    val signOutState: StateFlow<SignOutState> = _signOutState.asStateFlow()

    private val profileDataStore = ProfileDataStore(application)
    private val sessionManager = SessionManager(application)
    private val googleSignInClient: GoogleSignInClient

    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(AppStrings.DEFAULT_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(application, gso)
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
                // Use Backend API
                val result = fileRepository.getUserProfileFromBackend(userId)
                when (result) {
                    is com.meetloggerv2.core.network.NetworkResult.Success -> {
                        val data = result.data ?: emptyMap()
                        _userProfile.value = data
                        val name = data["name"] as? String ?: ""
                        val email = data["email"] as? String ?: ""
                        val photoUrl = data["photoUrl"] as? String ?: ""
                        val subscription = data["subscription"] as? String ?: "free"
                        viewModelScope.launch {
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
                    is com.meetloggerv2.core.network.NetworkResult.Error -> {
                        if (cached != null) {
                            val profileMap = mapOf(
                                "name" to cached.name,
                                "email" to cached.email,
                                "photoUrl" to cached.photoUrl
                            )
                            _userProfile.value = profileMap
                        } else {
                            _error.value = result.message ?: "Failed to load profile"
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun updateProfile(userId: String, name: String, photoUrl: String) {
        viewModelScope.launch {
            val updates = mutableMapOf<String, Any>()
            if (name.isNotEmpty()) updates["name"] = name
            if (photoUrl.isNotEmpty()) updates["photoUrl"] = photoUrl
            
            if (updates.isEmpty()) return@launch

            val result = fileRepository.updateUserProfileOnBackend(userId, updates)
            if (result is com.meetloggerv2.core.network.NetworkResult.Success) {
                // Reload profile after successful update
                loadUserProfile(userId)
            } else if (result is com.meetloggerv2.core.network.NetworkResult.Error) {
                _error.value = result.message ?: "Update failed"
            }
        }
    }

    fun signOut() {
        _signOutState.value = SignOutState.Loading
        authSession.signOut()
        googleSignInClient.signOut().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                sessionManager.clearSession()
                viewModelScope.launch {
                    profileDataStore.clear()
                    _signOutState.value = SignOutState.Success
                }
            } else {
                _signOutState.value = SignOutState.Error("Google sign out failed")
            }
        }
    }

    private fun getCurrentDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }
}
