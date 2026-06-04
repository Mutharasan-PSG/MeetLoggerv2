package com.example.meetloggerv2.data.repository

import com.example.meetloggerv2.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.AuthCredential

class AuthRepository() : IAuthRepository {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun getCurrentUser(): FirebaseUser? = auth.currentUser

    override fun signInWithCredential(credential: AuthCredential, onComplete: (FirebaseUser?, Exception?) -> Unit) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(task.result?.user, null)
                } else {
                    onComplete(null, task.exception)
                }
            }
    }

    override fun signUpWithEmailAndPassword(email: String, password: String, onComplete: (FirebaseUser?, Exception?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(task.result?.user, null)
                } else {
                    onComplete(null, task.exception)
                }
            }
    }

    override fun signInWithEmailAndPassword(email: String, password: String, onComplete: (FirebaseUser?, Exception?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(task.result?.user, null)
                } else {
                    onComplete(null, task.exception)
                }
            }
    }

    override fun sendPasswordResetEmail(email: String, onComplete: (Boolean, Exception?) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                onComplete(task.isSuccessful, task.exception)
            }
    }

    override fun sendEmailVerification(user: FirebaseUser, onComplete: (Boolean, Exception?) -> Unit) {
        user.sendEmailVerification()
            .addOnCompleteListener { task ->
                onComplete(task.isSuccessful, task.exception)
            }
    }

    override fun reloadUser(user: FirebaseUser, onComplete: (Boolean, Exception?) -> Unit) {
        user.reload()
            .addOnCompleteListener { task ->
                onComplete(task.isSuccessful, task.exception)
            }
    }

    override fun signOut() {
        auth.signOut()
    }
}
