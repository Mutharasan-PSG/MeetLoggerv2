package com.example.meetloggerv2.data.repository

import android.util.Log
import com.example.meetloggerv2.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.AuthCredential

class AuthRepository() : IAuthRepository {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val TAG = "AuthRepository"

    override fun getCurrentUser(): FirebaseUser? = auth.currentUser

    override fun signInWithCredential(credential: AuthCredential, onComplete: (FirebaseUser?, Exception?) -> Unit) {
        Log.d(TAG, "signInWithCredential started")
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential: Success")
                    onComplete(task.result?.user, null)
                } else {
                    Log.e(TAG, "signInWithCredential: Error", task.exception)
                    onComplete(null, task.exception)
                }
            }
    }

    override fun signUpWithEmailAndPassword(email: String, password: String, onComplete: (FirebaseUser?, Exception?) -> Unit) {
        Log.d(TAG, "signUpWithEmailAndPassword: $email")
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signUpWithEmailAndPassword: Success")
                    onComplete(task.result?.user, null)
                } else {
                    Log.e(TAG, "signUpWithEmailAndPassword: Error", task.exception)
                    onComplete(null, task.exception)
                }
            }
    }

    override fun signInWithEmailAndPassword(email: String, password: String, onComplete: (FirebaseUser?, Exception?) -> Unit) {
        Log.d(TAG, "signInWithEmailAndPassword: $email")
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmailAndPassword: Success")
                    onComplete(task.result?.user, null)
                } else {
                    Log.e(TAG, "signInWithEmailAndPassword: Error", task.exception)
                    onComplete(null, task.exception)
                }
            }
    }

    override fun sendPasswordResetEmail(email: String, onComplete: (Boolean, Exception?) -> Unit) {
        Log.d(TAG, "sendPasswordResetEmail: $email")
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "sendPasswordResetEmail: Success")
                } else {
                    Log.e(TAG, "sendPasswordResetEmail: Error", task.exception)
                }
                onComplete(task.isSuccessful, task.exception)
            }
    }

    override fun sendEmailVerification(user: FirebaseUser, onComplete: (Boolean, Exception?) -> Unit) {
        Log.d(TAG, "sendEmailVerification for: ${user.email}")
        user.sendEmailVerification()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "sendEmailVerification: Success")
                } else {
                    Log.e(TAG, "sendEmailVerification: Error", task.exception)
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
        Log.d(TAG, "fetchSignInMethodsForEmail: $email")
        auth.fetchSignInMethodsForEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val methods = task.result?.signInMethods
                    Log.d(TAG, "fetchSignInMethodsForEmail: Success - Methods: $methods")
                    onComplete(methods, null)
                } else {
                    Log.e(TAG, "fetchSignInMethodsForEmail: Error", task.exception)
                    onComplete(null, task.exception)
                }
            }
    }

    override fun signOut() {
        auth.signOut()
    }
}
