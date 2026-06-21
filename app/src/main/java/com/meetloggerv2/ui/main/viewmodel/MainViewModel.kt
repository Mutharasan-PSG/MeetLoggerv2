package com.meetloggerv2.ui.main.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.meetloggerv2.core.config.AppConfig
import com.meetloggerv2.core.config.AppGate
import com.meetloggerv2.core.config.GateResult
import com.meetloggerv2.core.session.SessionManager
import com.meetloggerv2.core.util.AppLogger
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
        _isSessionValid.value = isSessionValidNow()
    }

    /** Synchronous session check used by the splash gate flow before routing. */
    fun isSessionValidNow(): Boolean {
        val isValid = sessionManager.isLoggedIn() && authRepository.getCurrentUser() != null
        if (!isValid) {
            sessionManager.clearSession()
        }
        return isValid
    }

    /**
     * Refreshes Remote Config (best-effort) and evaluates the access gate for
     * the installed app version and the signed-in user. Fail-open: any error
     * leaves the cached/default config in place, so a network hiccup never
     * blocks the user — the server stays authoritative for genuine blocks.
     */
    suspend fun evaluateGate(versionCode: Int): GateResult {
        try {
            AppConfig.ensureLimitValidated()
        } catch (e: Exception) {
            AppLogger.e("MainViewModel", "Remote Config refresh failed during gate check", e)
        }
        return AppGate.evaluate(
            config = AppConfig.snapshot(),
            currentVersionCode = versionCode,
            userId = authRepository.getCurrentUser()?.uid,
        )
    }

    /** Ejects a blocked user: clears the local session and Firebase auth. */
    fun signOut() {
        sessionManager.clearSession()
        authRepository.signOut()
    }
}
