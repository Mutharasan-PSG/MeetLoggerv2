package com.example.meetloggerv2.ui.profile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.meetloggerv2.R
import com.example.meetloggerv2.core.session.AuthSession
import com.example.meetloggerv2.core.session.SessionManager
import com.example.meetloggerv2.data.local.ProfileDataStore
import com.example.meetloggerv2.data.repository.FileRepository
import com.example.meetloggerv2.data.repository.IFileRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileViewModel @JvmOverloads constructor(
    application: Application,
    private val fileRepository: IFileRepository = FileRepository(),
    private val authSession: AuthSession = AuthSession()
) : AndroidViewModel(application) {

    sealed class SignOutState {
        object Idle : SignOutState()
        object Loading : SignOutState()
        object Success : SignOutState()
        data class Error(val message: String) : SignOutState()
    }

    private val _userProfile = MutableLiveData<Map<String, Any>?>()
    val userProfile: LiveData<Map<String, Any>?> = _userProfile

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _signOutState = MutableLiveData<SignOutState>(SignOutState.Idle)
    val signOutState: LiveData<SignOutState> = _signOutState

    private val profileDataStore = ProfileDataStore(application)
    private val sessionManager = SessionManager(application)
    private val googleSignInClient: GoogleSignInClient

    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(application.getString(R.string.default_web_client_id))
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

    fun signOut() {
        _signOutState.value = SignOutState.Loading
        authSession.signOut()
        googleSignInClient.signOut().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                sessionManager.clearSession()
                viewModelScope.launch {
                    profileDataStore.clear()
                    _signOutState.postValue(SignOutState.Success)
                }
            } else {
                _signOutState.postValue(SignOutState.Error("Google sign out failed"))
            }
        }
    }

    private fun getCurrentDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }
}
