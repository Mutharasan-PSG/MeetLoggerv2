package com.example.meetloggerv2.core.session

import com.example.meetloggerv2.data.repository.IAuthRepository
import javax.inject.Inject

class AuthSession @Inject constructor(
    private val authRepository: IAuthRepository
) {
    fun currentUserId(): String? = authRepository.getCurrentUser()?.uid

    fun signOut() {
        authRepository.signOut()
    }
}
