package com.meetloggerv2.core.session

import com.meetloggerv2.data.repository.IAuthRepository
import com.meetloggerv2.core.session.SessionManager
import javax.inject.Inject

class AuthSession @Inject constructor(
    private val authRepository: IAuthRepository,
    private val sessionManager: SessionManager
) {
    fun currentUserId(): String? = authRepository.getCurrentUser()?.uid

    fun currentUserEmail(): String? = authRepository.getCurrentUser()?.email

    fun currentUserName(): String? = sessionManager.getUserName()

    fun currentUserSubscription(): String = sessionManager.getUserDetails()?.subscription ?: "free"

    fun signOut() {
        authRepository.signOut()
    }
}
