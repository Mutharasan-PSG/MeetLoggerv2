package com.meetloggerv2.ui.main.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.meetloggerv2.core.session.SessionManager
import com.meetloggerv2.data.repository.IAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val authRepository: IAuthRepository
) : AndroidViewModel(application) {

    private val _isSessionValid = MutableLiveData<Boolean>()
    val isSessionValid: LiveData<Boolean> = _isSessionValid

    private val sessionManager = SessionManager(application)

    fun checkSession() {
        val isValid = sessionManager.isLoggedIn() && authRepository.getCurrentUser() != null
        if (!isValid) {
            sessionManager.clearSession()
        }
        _isSessionValid.value = isValid
    }
}
