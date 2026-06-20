package com.meetloggerv2.data.repository

import com.meetloggerv2.core.model.CheckEmailResponse
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.core.network.SafeApiCall
import com.meetloggerv2.core.util.AppLogger
import com.meetloggerv2.data.remote.ApiService
import com.meetloggerv2.data.remote.RetrofitClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.AuthCredential

class AuthRepository(
    private val apiService: ApiService = RetrofitClient.apiService
) : IAuthRepository, SafeApiCall {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val TAG = "AuthRepository"

    override fun getCurrentUser(): FirebaseUser? = auth.currentUser

    override fun signInWithCredential(credential: AuthCredential, onComplete: (FirebaseUser?, Exception?) -> Unit) {
        AppLogger.d(TAG, "signInWithCredential started")
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    AppLogger.d(TAG, "signInWithCredential: Success")
                    onComplete(task.result?.user, null)
                } else {
                    AppLogger.e(TAG, "signInWithCredential: Error", task.exception)
                    onComplete(null, task.exception)
                }
            }
    }

    override fun signUpWithEmailAndPassword(email: String, password: String, onComplete: (FirebaseUser?, Exception?) -> Unit) {
        AppLogger.d(TAG, "signUpWithEmailAndPassword: $email")
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    AppLogger.d(TAG, "signUpWithEmailAndPassword: Success")
                    onComplete(task.result?.user, null)
                } else {
                    AppLogger.e(TAG, "signUpWithEmailAndPassword: Error", task.exception)
                    onComplete(null, task.exception)
                }
            }
    }

    override fun signInWithEmailAndPassword(email: String, password: String, onComplete: (FirebaseUser?, Exception?) -> Unit) {
        AppLogger.d(TAG, "signInWithEmailAndPassword: $email")
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    AppLogger.d(TAG, "signInWithEmailAndPassword: Success")
                    onComplete(task.result?.user, null)
                } else {
                    AppLogger.e(TAG, "signInWithEmailAndPassword: Error", task.exception)
                    onComplete(null, task.exception)
                }
            }
    }

    override fun sendPasswordResetEmail(email: String, onComplete: (Boolean, Exception?) -> Unit) {
        AppLogger.d(TAG, "sendPasswordResetEmail: $email")
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    AppLogger.d(TAG, "sendPasswordResetEmail: Success")
                } else {
                    AppLogger.e(TAG, "sendPasswordResetEmail: Error", task.exception)
                }
                onComplete(task.isSuccessful, task.exception)
            }
    }

    override fun sendEmailVerification(user: FirebaseUser, onComplete: (Boolean, Exception?) -> Unit) {
        AppLogger.d(TAG, "sendEmailVerification for: ${user.email}")
        user.sendEmailVerification()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    AppLogger.d(TAG, "sendEmailVerification: Success")
                } else {
                    AppLogger.e(TAG, "sendEmailVerification: Error", task.exception)
                }
                onComplete(task.isSuccessful, task.exception)
            }
    }

    override fun reloadUser(user: FirebaseUser, onComplete: (Boolean, Exception?) -> Unit) {
        user.reload()
            .addOnCompleteListener { task ->
                onComplete(task.isSuccessful, task.exception)
            }
    }

    override fun fetchSignInMethodsForEmail(email: String, onComplete: (List<String>?, Exception?) -> Unit) {
        AppLogger.d(TAG, "fetchSignInMethodsForEmail: $email")
        auth.fetchSignInMethodsForEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val methods = task.result?.signInMethods
                    AppLogger.d(TAG, "fetchSignInMethodsForEmail: Success - Methods: $methods")
                    onComplete(methods, null)
                } else {
                    AppLogger.e(TAG, "fetchSignInMethodsForEmail: Error", task.exception)
                    onComplete(null, task.exception)
                }
            }
    }

    override fun signOut() {
        auth.signOut()
    }

    override suspend fun checkEmailOnBackend(email: String): NetworkResult<CheckEmailResponse> {
        return safeApiCall {
            apiService.checkEmail(mapOf("email" to email))
        }
    }
}
