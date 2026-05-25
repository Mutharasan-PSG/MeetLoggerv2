package com.example.meetloggerv2.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.meetloggerv2.data.model.User
import com.example.meetloggerv2.data.repository.AuthRepository
import com.example.meetloggerv2.data.repository.FileRepository
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val fileRepository: FileRepository = FileRepository()
) : ViewModel() {

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    fun signInWithCredential(credential: AuthCredential) {
        _loginState.value = LoginState.Loading
        authRepository.signInWithCredential(credential) { firebaseUser, exception ->
            if (firebaseUser != null) {
                handleFirebaseUser(firebaseUser)
            } else {
                _loginState.value = LoginState.Error(exception?.message ?: "Login failed")
            }
        }
    }

    private fun handleFirebaseUser(firebaseUser: FirebaseUser) {
        val user = User(
            id = firebaseUser.uid,
            name = firebaseUser.displayName.orEmpty(),
            email = firebaseUser.email.orEmpty(),
            photoUrl = firebaseUser.photoUrl?.toString()
        )

        fileRepository.checkUserExists(user.id, { exists ->
            if (!exists) {
                fileRepository.saveUser(user, {
                    _loginState.value = LoginState.Success(user)
                }, {
                    _loginState.value = LoginState.Error(it.message ?: "Failed to save user")
                })
            } else {
                _loginState.value = LoginState.Success(user)
            }
        }, {
            _loginState.value = LoginState.Error(it.message ?: "Failed to check user")
        })
    }

    sealed class LoginState {
        object Loading : LoginState()
        data class Success(val user: User) : LoginState()
        data class Error(val message: String) : LoginState()
    }
}
