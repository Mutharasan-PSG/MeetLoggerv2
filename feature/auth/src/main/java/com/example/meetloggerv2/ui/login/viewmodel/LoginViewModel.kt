package com.example.meetloggerv2.ui.login.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.meetloggerv2.data.model.User
import com.example.meetloggerv2.data.repository.IAuthRepository
import com.example.meetloggerv2.data.repository.IFileRepository
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: IAuthRepository,
    private val fileRepository: IFileRepository
) : ViewModel() {

    private val TAG = "LoginViewModel"

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _signUpState = MutableStateFlow<SignUpState>(SignUpState.Idle)
    val signUpState: StateFlow<SignUpState> = _signUpState.asStateFlow()

    private val _resetPasswordState = MutableStateFlow<ResetPasswordState>(ResetPasswordState.Idle)
    val resetPasswordState: StateFlow<ResetPasswordState> = _resetPasswordState.asStateFlow()

    private val _resendVerificationState = MutableStateFlow<VerificationResendState>(VerificationResendState.Idle)
    val resendVerificationState: StateFlow<VerificationResendState> = _resendVerificationState.asStateFlow()

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
        Log.d(TAG, "signInWithEmail: $email")
        _loginState.value = LoginState.Loading
        authRepository.signInWithEmailAndPassword(email, password) { firebaseUser, exception ->
            if (firebaseUser != null) {
                Log.d(TAG, "signInWithEmail: Success")
                // Check if user has verified their email
                if (firebaseUser.isEmailVerified) {
                    handleFirebaseUser(firebaseUser)
                } else {
                    _loginState.value = LoginState.EmailNotVerified("Your email address is not verified yet.\n\nWe sent a verification link to:\n${firebaseUser.email}\n\nPlease click the link in the email to activate your account.")
                }
            } else {
                Log.e(TAG, "signInWithEmail: Error", exception)
                // Check if the account exists and what methods are available
                authRepository.fetchSignInMethodsForEmail(email) { methods, fetchException ->
                    Log.d(TAG, "fetchSignInMethodsForEmail reponse: $methods")
                    if (methods.isNullOrEmpty()) {
                        _loginState.value = LoginState.UserNotFound("This email address is not registered. Would you like to create a new account?")
                    } else {
                        val hasPassword = methods.contains("password")
                        val hasGoogle = methods.contains("google.com")
                        
                        when {
                            hasPassword && hasGoogle -> {
                                _loginState.value = LoginState.Error("Incorrect password")
                            }
                            hasPassword -> {
                                _loginState.value = LoginState.Error("Incorrect password")
                            }
                            hasGoogle -> {
                                _loginState.value = LoginState.Error("This account is registered with Google")
                            }
                            else -> {
                                _loginState.value = LoginState.Error("Please sign in using your original registration method.")
                            }
                        }
                    }
                }
            }
        }
    }

    fun signUpWithEmail(name: String, email: String, password: String) {
        Log.d(TAG, "signUpWithEmail: $email")
        _signUpState.value = SignUpState.Loading
        
        // First check if email already exists
        authRepository.fetchSignInMethodsForEmail(email) { methods, exception ->
            Log.d(TAG, "signUp fetchMethods: $methods")
            if (!methods.isNullOrEmpty()) {
                val hasPassword = methods.contains("password")
                val hasGoogle = methods.contains("google.com")
                
                val message = when {
                    hasPassword && hasGoogle -> "An account with this email already exists. You can log in with your password or Google."
                    hasPassword -> "An account with this email already exists. Please log in instead."
                    hasGoogle -> "You already have an account registered with Google. Please sign in with Google or use a different email."
                    else -> "An account with this email already exists."
                }
                _signUpState.value = SignUpState.UserAlreadyExists(message)
            } else {
                authRepository.signUpWithEmailAndPassword(email, password) { firebaseUser, signUpException ->
                    if (firebaseUser != null) {
                        Log.d(TAG, "signUp: Success")
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
                        Log.e(TAG, "signUp: Error", signUpException)
                        _signUpState.value = SignUpState.Error(signUpException?.message ?: "Sign up failed")
                    }
                }
            }
        }
    }

    fun sendPasswordReset(email: String) {
        Log.d(TAG, "sendPasswordReset: $email")
        _resetPasswordState.value = ResetPasswordState.Loading
        
        // First check if email exists and has password method
        authRepository.fetchSignInMethodsForEmail(email) { methods, exception ->
            Log.d(TAG, "resetPassword fetchMethods: $methods")
            if (methods.isNullOrEmpty()) {
                _resetPasswordState.value = ResetPasswordState.Error("This email address is not registered")
            } else {
                val hasPassword = methods.contains("password")
                val hasGoogle = methods.contains("google.com")
                
                if (!hasPassword) {
                    val method = if (hasGoogle) "Google" else "another method"
                    _resetPasswordState.value = ResetPasswordState.Error("This account is registered with $method")
                } else {
                    authRepository.sendPasswordResetEmail(email) { success, resetException ->
                        if (success) {
                            Log.d(TAG, "resetPassword: Success")
                            _resetPasswordState.value = ResetPasswordState.Success
                        } else {
                            Log.e(TAG, "resetPassword: Error", resetException)
                            _resetPasswordState.value = ResetPasswordState.Error(resetException?.message ?: "Failed to send reset email")
                        }
                    }
                }
            }
        }
    }

    fun resendVerificationEmail() {
        _resendVerificationState.value = VerificationResendState.Loading
        val firebaseUser = authRepository.getCurrentUser()
        if (firebaseUser != null) {
            authRepository.sendEmailVerification(firebaseUser) { success, exception ->
                if (success) {
                    _resendVerificationState.value = VerificationResendState.Success("Verification email resent successfully")
                } else {
                    _resendVerificationState.value = VerificationResendState.Error(exception?.message ?: "Failed to resend verification email")
                }
            }
        } else {
            _resendVerificationState.value = VerificationResendState.Error("No authenticated user session found")
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
        data class UserNotFound(val message: String) : LoginState()
        data class Error(val message: String) : LoginState()
    }

    sealed class SignUpState {
        object Idle : SignUpState()
        object Loading : SignUpState()
        object Success : SignUpState()
        data class UserAlreadyExists(val message: String) : SignUpState()
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
