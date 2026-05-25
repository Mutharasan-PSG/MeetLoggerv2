package com.example.meetloggerv2.core.session

import com.example.meetloggerv2.data.repository.AuthRepository

class AuthSession(
    private val authRepository: AuthRepository = AuthRepository()
) {
    fun currentUserId(): String? = authRepository.getCurrentUser()?.uid

    fun signOut() {
        authRepository.signOut()
    }
}
