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
import com.example.meetloggerv2.core.network.NetworkResult
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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
        
        // ALWAYS check backend first for account existence and methods
        viewModelScope.launch {
            val checkResult = authRepository.checkEmailOnBackend(email)
            if (checkResult is NetworkResult.Success && checkResult.data != null) {
                val response = checkResult.data!!
                if (!response.exists) {
                    // 1. User not found -> Navigate to SignUp
                    _loginState.value = LoginState.UserNotFound("This email address is not registered. Would you like to create a new account?")
                } else {
                    val methods = response.methods
                    val hasPassword = methods.contains("password")
                    val hasGoogle = methods.contains("google.com")
                    
                    if (hasPassword) {
                        // 2. User exists with password -> Proceed to Firebase Login
                        authRepository.signInWithEmailAndPassword(email, password) { firebaseUser, exception ->
                            if (firebaseUser != null) {
                                if (firebaseUser.isEmailVerified) {
                                    handleFirebaseUser(firebaseUser)
                                } else {
                                    _loginState.value = LoginState.EmailNotVerified("Your email address is not verified yet.\n\nWe sent a verification link to:\n${firebaseUser.email}\n\nPlease click the link in the email to activate your account.")
                                }
                            } else {
                                // Most common reason here is Incorrect Password
                                _loginState.value = LoginState.Error(exception?.message ?: "Incorrect password")
                            }
                        }
                    } else if (hasGoogle) {
                        // 3. User exists but only with Google
                        _loginState.value = LoginState.Error("This account is registered with Google. Please sign in using Google.")
                    } else {
                        // 4. Other registration method
                        _loginState.value = LoginState.Error("Please sign in using your original registration method.")
                    }
                }
            } else {
                // Backend API failed (Network or Server Error)
                _loginState.value = LoginState.Error("Unable to verify account. Please check your connection.")
            }
        }
    }

    fun signUpWithEmail(name: String, email: String, password: String) {
        Log.d(TAG, "signUpWithEmail: $email")
        _signUpState.value = SignUpState.Loading
        
        // First check if email already exists using Backend API
        viewModelScope.launch {
            val checkResult = authRepository.checkEmailOnBackend(email)
            if (checkResult is NetworkResult.Success && checkResult.data?.exists == true) {
                val methods = checkResult.data!!.methods
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
                                viewModelScope.launch {
                                    val updates = mapOf(
                                        "name" to user.name,
                                        "email" to user.email
                                    )
                                    val result = fileRepository.updateUserProfileOnBackend(user.id, updates)
                                    when (result) {
                                        is NetworkResult.Success -> {
                                            _signUpState.value = SignUpState.Success
                                        }
                                        is NetworkResult.Error -> {
                                            _signUpState.value = SignUpState.Error(result.message ?: "Failed to save user profile")
                                        }
                                        else -> {}
                                    }
                                }
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
        
        // First check if email exists and has password method using Backend API
        viewModelScope.launch {
            val checkResult = authRepository.checkEmailOnBackend(email)
            if (checkResult is NetworkResult.Success && checkResult.data != null) {
                val response = checkResult.data!!
                if (!response.exists) {
                    _resetPasswordState.value = ResetPasswordState.Error("This email address is not registered")
                } else {
                    val methods = response.methods
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
            } else {
                // Fallback or handle backend error
                _resetPasswordState.value = ResetPasswordState.Error("Failed to verify email. Please try again.")
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

        viewModelScope.launch {
            val result = fileRepository.getUserProfileFromBackend(user.id)
            when (result) {
                is NetworkResult.Success -> {
                    // User exists, proceed
                    val profile = result.data
                    val subscription = profile?.get("subscription") as? String ?: "free"
                    val backendUser = user.copy(subscription = subscription)
                    _loginState.value = LoginState.Success(backendUser)
                }
                is NetworkResult.Error -> {
                    if (result.message?.contains("404") == true) {
                        // User not found, create them
                        _loginState.value = LoginState.CreatingUser
                        val updates = mapOf(
                            "name" to user.name,
                            "email" to user.email,
                            "photoUrl" to (user.photoUrl ?: "")
                        )
                        val createResult = fileRepository.updateUserProfileOnBackend(user.id, updates)
                        if (createResult is NetworkResult.Success) {
                            _loginState.value = LoginState.Success(user)
                        } else {
                            _loginState.value = LoginState.Error(
                                (createResult as? NetworkResult.Error)?.message ?: "Failed to create user"
                            )
                        }
                    } else {
                        _loginState.value = LoginState.Error(result.message ?: "Failed to verify user")
                    }
                }
                else -> {}
            }
        }
    }

    sealed class LoginState {
        object Idle : LoginState()
        object Loading : LoginState()
        object CreatingUser : LoginState()
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
