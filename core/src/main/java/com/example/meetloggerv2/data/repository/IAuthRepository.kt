package com.example.meetloggerv2.data.repository

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.AuthCredential
import com.example.meetloggerv2.core.model.CheckEmailResponse
import com.example.meetloggerv2.core.network.NetworkResult

interface IAuthRepository {
    fun getCurrentUser(): FirebaseUser?
    fun signInWithCredential(credential: AuthCredential, onComplete: (FirebaseUser?, Exception?) -> Unit)
    fun signUpWithEmailAndPassword(email: String, password: String, onComplete: (FirebaseUser?, Exception?) -> Unit)
    fun signInWithEmailAndPassword(email: String, password: String, onComplete: (FirebaseUser?, Exception?) -> Unit)
    fun sendPasswordResetEmail(email: String, onComplete: (Boolean, Exception?) -> Unit)
    fun sendEmailVerification(user: FirebaseUser, onComplete: (Boolean, Exception?) -> Unit)
    fun reloadUser(user: FirebaseUser, onComplete: (Boolean, Exception?) -> Unit)
    fun fetchSignInMethodsForEmail(email: String, onComplete: (List<String>?, Exception?) -> Unit)
    suspend fun checkEmailOnBackend(email: String): NetworkResult<CheckEmailResponse>
    fun signOut()
}
