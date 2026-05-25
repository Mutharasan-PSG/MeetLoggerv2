package com.example.meetloggerv2.data.repository

import com.example.meetloggerv2.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.AuthCredential

class AuthRepository() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun signInWithCredential(credential: AuthCredential, onComplete: (FirebaseUser?, Exception?) -> Unit) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(task.result?.user, null)
                } else {
                    onComplete(null, task.exception)
                }
            }
    }

    fun signOut() {
        auth.signOut()
    }
}
