package com.meetloggerv2.ui.profile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.core.theme.AppStrings
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.data.local.SettingsDataStore
import com.meetloggerv2.data.repository.IFileRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val fileRepository: IFileRepository,
    private val authSession: AuthSession
) : AndroidViewModel(application) {

    sealed class DeleteAccountState {
        object Idle : DeleteAccountState()
        object Loading : DeleteAccountState()
        object Success : DeleteAccountState()
        data class Error(val message: String) : DeleteAccountState()
    }

    private val _deleteAccountState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteAccountState: StateFlow<DeleteAccountState> = _deleteAccountState.asStateFlow()

    private val settingsDataStore = SettingsDataStore(application)
    private val googleSignInClient: GoogleSignInClient

    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(AppStrings.DEFAULT_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(application, gso)
    }

    val themeMode: StateFlow<Int> = settingsDataStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val autoSendEmail: StateFlow<Boolean> = settingsDataStore.autoSendEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val recordingQuality: StateFlow<String> = settingsDataStore.recordingQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "High")

    val biometricLock: StateFlow<Boolean> = settingsDataStore.biometricLock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(mode)
        }
    }

    fun setAutoSendEmail(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAutoSendEmail(enabled)
        }
    }

    fun setRecordingQuality(quality: String) {
        viewModelScope.launch {
            settingsDataStore.setRecordingQuality(quality)
        }
    }

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setBiometricLock(enabled)
        }
    }

    fun deleteAccount(userId: String) {
        _deleteAccountState.value = DeleteAccountState.Loading
        viewModelScope.launch {
            val result = fileRepository.deleteUserAccountFromBackend(userId)
            if (result is NetworkResult.Success) {
                try {
                    authSession.signOut()
                    googleSignInClient.signOut().addOnCompleteListener {
                        _deleteAccountState.value = DeleteAccountState.Success
                    }
                } catch (e: Exception) {
                    _deleteAccountState.value = DeleteAccountState.Success
                }
            } else if (result is NetworkResult.Error) {
                _deleteAccountState.value = DeleteAccountState.Error(result.message ?: "Failed to delete account on backend")
            }
        }
    }
}
