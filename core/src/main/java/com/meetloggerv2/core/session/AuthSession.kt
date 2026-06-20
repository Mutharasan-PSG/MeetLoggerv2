package com.meetloggerv2.core.session

import com.meetloggerv2.data.repository.IAuthRepository
import javax.inject.Inject

class AuthSession @Inject constructor(
    private val authRepository: IAuthRepository,
    private val sessionManager: SessionManager,
    private val sessionCleanup: ISessionCleanup
) {
    fun currentUserId(): String? = authRepository.getCurrentUser()?.uid

    fun currentUserEmail(): String? = authRepository.getCurrentUser()?.email

    fun currentUserName(): String? = sessionManager.getUserName()

    fun currentUserSubscription(): String = sessionManager.getUserDetails()?.subscription ?: "free"

    suspend fun signOut() {
        sessionCleanup.clearAllLocalData()
        sessionManager.clearSession()
        authRepository.signOut()
    }
}
