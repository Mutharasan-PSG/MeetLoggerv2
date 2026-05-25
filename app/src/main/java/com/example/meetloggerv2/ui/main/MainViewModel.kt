package com.example.meetloggerv2.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.meetloggerv2.data.local.SessionManager
import com.example.meetloggerv2.data.repository.AuthRepository

class MainViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _isSessionValid = MutableLiveData<Boolean>()
    val isSessionValid: LiveData<Boolean> = _isSessionValid

    fun checkSession(manager: SessionManager) {
        val isValid = manager.isLoggedIn() && authRepository.getCurrentUser() != null
        _isSessionValid.value = isValid
    }
}
