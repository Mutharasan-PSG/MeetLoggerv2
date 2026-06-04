package com.example.meetloggerv2.ui.main.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.meetloggerv2.core.session.SessionManager
import com.example.meetloggerv2.data.repository.AuthRepository

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val authRepository: AuthRepository = AuthRepository()
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
