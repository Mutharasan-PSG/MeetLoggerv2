package com.meetloggerv2.ui.profile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.core.network.SafeApiCall
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.data.remote.ApiService
import com.meetloggerv2.data.remote.SupportRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@HiltViewModel
class SupportViewModel @Inject constructor(
    private val authSession: AuthSession,
    private val apiService: ApiService
) : ViewModel(), SafeApiCall {

    private val _uiState = MutableStateFlow<SupportUiState>(SupportUiState.Idle)
    val uiState: StateFlow<SupportUiState> = _uiState.asStateFlow()

    fun sendSupportRequest(subject: String, body: String) {
        val userId = authSession.currentUserId() ?: return
        val email = authSession.currentUserEmail() ?: ""
        val name = authSession.currentUserName() ?: "User"
        
        _uiState.value = SupportUiState.Loading

        viewModelScope.launch {
            try {
                val token = getFirebaseIdToken()
                val request = SupportRequest(email, name, subject, body, token)
                
                val result = safeApiCall {
                    apiService.submitSupport("Bearer $token", userId, request)
                }
                
                when (result) {
                    is NetworkResult.Success -> _uiState.value = SupportUiState.Success
                    is NetworkResult.Error -> _uiState.value = SupportUiState.Error(result.message ?: "Failed to send request")
                    else -> {}
                }
            } catch (e: Exception) {
                _uiState.value = SupportUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun getFirebaseIdToken(): String = suspendCancellableCoroutine { continuation ->
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            continuation.resumeWithException(IllegalStateException("User not authenticated"))
            return@suspendCancellableCoroutine
        }
        user.getIdToken(false).addOnSuccessListener {
            continuation.resume(it.token ?: "")
        }.addOnFailureListener {
            continuation.resumeWithException(it)
        }
    }

    sealed class SupportUiState {
        object Idle : SupportUiState()
        object Loading : SupportUiState()
        object Success : SupportUiState()
        data class Error(val message: String) : SupportUiState()
    }
}
