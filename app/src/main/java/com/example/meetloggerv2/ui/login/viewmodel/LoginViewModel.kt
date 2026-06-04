package com.example.meetloggerv2.ui.login.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.meetloggerv2.data.model.User
import com.example.meetloggerv2.data.repository.AuthRepository
import com.example.meetloggerv2.data.repository.FileRepository
import com.example.meetloggerv2.data.repository.IAuthRepository
import com.example.meetloggerv2.data.repository.IFileRepository
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser

class LoginViewModel @JvmOverloads constructor(
    private val authRepository: IAuthRepository = AuthRepository(),
    private val fileRepository: IFileRepository = FileRepository()
) : ViewModel() {

    private val _loginState = MutableLiveData<LoginState>(LoginState.Idle)
    val loginState: LiveData<LoginState> = _loginState

    private val _signUpState = MutableLiveData<SignUpState>(SignUpState.Idle)
    val signUpState: LiveData<SignUpState> = _signUpState

    private val _resetPasswordState = MutableLiveData<ResetPasswordState>(ResetPasswordState.Idle)
    val resetPasswordState: LiveData<ResetPasswordState> = _resetPasswordState

    private val _resendVerificationState = MutableLiveData<VerificationResendState>(VerificationResendState.Idle)
    val resendVerificationState: LiveData<VerificationResendState> = _resendVerificationState

    fun resetStates() {
        _loginState.value = LoginState.Idle
        _signUpState.value = SignUpState.Idle
        _resetPasswordState.value = ResetPasswordState.Idle
        _resendVerificationState.value = VerificationResendState.Idle
    }

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

    fun signInWithEmail(email: String, password: String) {
        _loginState.value = LoginState.Loading
        authRepository.signInWithEmailAndPassword(email, password) { firebaseUser, exception ->
            if (firebaseUser != null) {
                // Check if user has verified their email
                if (firebaseUser.isEmailVerified) {
                    handleFirebaseUser(firebaseUser)
                } else {
                    _loginState.value = LoginState.EmailNotVerified("Your email address is not verified yet.\n\nWe sent a verification link to:\n${firebaseUser.email}\n\nPlease click the link in the email to activate your account.")
                }
            } else {
                _loginState.value = LoginState.Error(exception?.message ?: "Login failed")
            }
        }
    }

    fun signUpWithEmail(name: String, email: String, password: String) {
        _signUpState.value = SignUpState.Loading
        authRepository.signUpWithEmailAndPassword(email, password) { firebaseUser, exception ->
            if (firebaseUser != null) {
                authRepository.sendEmailVerification(firebaseUser) { success, verificationException ->
                    if (success) {
                        val user = User(
                            id = firebaseUser.uid,
                            name = name,
                            email = firebaseUser.email.orEmpty(),
                            photoUrl = null
                        )
                        fileRepository.saveUser(user, {
                            _signUpState.value = SignUpState.Success
                        }, { dbException ->
                            _signUpState.value = SignUpState.Error(dbException.message ?: "Failed to save user profile")
                        })
                    } else {
                        _signUpState.value = SignUpState.Error(verificationException?.message ?: "Failed to send verification email")
                    }
                }
            } else {
                _signUpState.value = SignUpState.Error(exception?.message ?: "Sign up failed")
            }
        }
    }

    fun sendPasswordReset(email: String) {
        _resetPasswordState.value = ResetPasswordState.Loading
        authRepository.sendPasswordResetEmail(email) { success, exception ->
            if (success) {
                _resetPasswordState.value = ResetPasswordState.Success
            } else {
                _resetPasswordState.value = ResetPasswordState.Error(exception?.message ?: "Failed to send reset email")
            }
        }
    }

    fun resendVerificationEmail() {
        _resendVerificationState.value = VerificationResendState.Loading
        val firebaseUser = authRepository.getCurrentUser()
        if (firebaseUser != null) {
            authRepository.sendEmailVerification(firebaseUser) { success, exception ->
                if (success) {
                    _resendVerificationState.postValue(VerificationResendState.Success("Verification email resent successfully."))
                } else {
                    _resendVerificationState.postValue(VerificationResendState.Error(exception?.message ?: "Failed to resend verification email."))
                }
            }
        } else {
            _resendVerificationState.postValue(VerificationResendState.Error("No authenticated user session found."))
        }
    }

    private fun handleFirebaseUser(firebaseUser: FirebaseUser) {
        val user = User(
            id = firebaseUser.uid,
            name = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@").orEmpty(),
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
        object Idle : LoginState()
        object Loading : LoginState()
        data class Success(val user: User) : LoginState()
        data class EmailNotVerified(val message: String) : LoginState()
        data class Error(val message: String) : LoginState()
    }

    sealed class SignUpState {
        object Idle : SignUpState()
        object Loading : SignUpState()
        object Success : SignUpState()
        data class Error(val message: String) : SignUpState()
    }

    sealed class ResetPasswordState {
        object Idle : ResetPasswordState()
        object Loading : ResetPasswordState()
        object Success : ResetPasswordState()
        data class Error(val message: String) : ResetPasswordState()
    }

    sealed class VerificationResendState {
        object Idle : VerificationResendState()
        object Loading : VerificationResendState()
        data class Success(val message: String) : VerificationResendState()
        data class Error(val message: String) : VerificationResendState()
    }
}
