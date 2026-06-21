package com.meetloggerv2.ui.login.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.meetloggerv2.MainDispatcherRule
import com.meetloggerv2.core.model.CheckEmailResponse
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.data.repository.IAuthRepository
import com.meetloggerv2.data.repository.IFileRepository
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import app.cash.turbine.test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = mockk<IAuthRepository>(relaxed = true)
    private val fileRepository = mockk<IFileRepository>(relaxed = true)
    private val firebaseUser = mockk<FirebaseUser>(relaxed = true)

    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        every { firebaseUser.uid } returns "user123"
        every { firebaseUser.email } returns "test@example.com"
        every { firebaseUser.displayName } returns "Test User"
        every { firebaseUser.isEmailVerified } returns true

        viewModel = LoginViewModel(authRepository, fileRepository)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun signInWithCredential_success_handlesUserAndSetsSuccess() = runTest {
        val credential = mockk<AuthCredential>()
        val callbackSlot = slot<(FirebaseUser?, Exception?) -> Unit>()

        every { authRepository.signInWithCredential(credential, capture(callbackSlot)) } answers {
            callbackSlot.captured.invoke(firebaseUser, null)
        }
        coEvery { fileRepository.getUserProfileFromBackend("user123") } returns NetworkResult.Success(mapOf("subscription" to "premium"))

        viewModel.signInWithCredential(credential)

        viewModel.loginState.test {
            val state = awaitItem()
            assertTrue(state is LoginViewModel.LoginState.Success)
            assertEquals("user123", (state as LoginViewModel.LoginState.Success).user.id)
            assertEquals("premium", state.user.subscription)
        }
    }

    @Test
    fun signInWithEmail_userNotFound_setsUserNotFoundState() = runTest {
        val checkResponse = CheckEmailResponse(exists = false, methods = emptyList())
        coEvery { authRepository.checkEmailOnBackend("new@example.com") } returns NetworkResult.Success(checkResponse)

        viewModel.signInWithEmail("new@example.com", "password123")

        viewModel.loginState.test {
            val state = awaitItem()
            assertTrue(state is LoginViewModel.LoginState.UserNotFound)
        }
    }

    @Test
    fun signInWithEmail_passwordMethodSuccess_setsSuccessState() = runTest {
        val checkResponse = CheckEmailResponse(exists = true, methods = listOf("password"))
        coEvery { authRepository.checkEmailOnBackend("test@example.com") } returns NetworkResult.Success(checkResponse)

        val callbackSlot = slot<(FirebaseUser?, Exception?) -> Unit>()
        every { authRepository.signInWithEmailAndPassword("test@example.com", "password123", capture(callbackSlot)) } answers {
            callbackSlot.captured.invoke(firebaseUser, null)
        }
        coEvery { fileRepository.getUserProfileFromBackend("user123") } returns NetworkResult.Success(mapOf("subscription" to "free"))

        viewModel.signInWithEmail("test@example.com", "password123")

        viewModel.loginState.test {
            val state = awaitItem()
            assertTrue(state is LoginViewModel.LoginState.Success)
            assertEquals("free", (state as LoginViewModel.LoginState.Success).user.subscription)
        }
    }

    @Test
    fun signUpWithEmail_newUser_sendsVerificationAndSavesProfile() = runTest {
        val checkResponse = CheckEmailResponse(exists = false, methods = emptyList())
        coEvery { authRepository.checkEmailOnBackend("new@example.com") } returns NetworkResult.Success(checkResponse)

        val callbackSlot = slot<(FirebaseUser?, Exception?) -> Unit>()
        every { authRepository.signUpWithEmailAndPassword("new@example.com", "password123", capture(callbackSlot)) } answers {
            callbackSlot.captured.invoke(firebaseUser, null)
        }

        val verificationSlot = slot<(Boolean, Exception?) -> Unit>()
        every { authRepository.sendEmailVerification(firebaseUser, capture(verificationSlot)) } answers {
            verificationSlot.captured.invoke(true, null)
        }

        coEvery { fileRepository.updateUserProfileOnBackend("user123", any()) } returns NetworkResult.Success(Unit)

        viewModel.signUpWithEmail("New User", "new@example.com", "password123")

        viewModel.signUpState.test {
            assertEquals(LoginViewModel.SignUpState.Success, awaitItem())
        }
    }

    @Test
    fun sendPasswordReset_success_setsSuccessState() = runTest {
        val checkResponse = CheckEmailResponse(exists = true, methods = listOf("password"))
        coEvery { authRepository.checkEmailOnBackend("test@example.com") } returns NetworkResult.Success(checkResponse)

        val callbackSlot = slot<(Boolean, Exception?) -> Unit>()
        every { authRepository.sendPasswordResetEmail("test@example.com", capture(callbackSlot)) } answers {
            callbackSlot.captured.invoke(true, null)
        }

        viewModel.sendPasswordReset("test@example.com")

        viewModel.resetPasswordState.test {
            assertEquals(LoginViewModel.ResetPasswordState.Success, awaitItem())
        }
    }

    @Test
    fun resendVerificationEmail_success_setsResendSuccess() = runTest {
        every { authRepository.getCurrentUser() } returns firebaseUser
        val callbackSlot = slot<(Boolean, Exception?) -> Unit>()
        every { authRepository.sendEmailVerification(firebaseUser, capture(callbackSlot)) } answers {
            callbackSlot.captured.invoke(true, null)
        }

        viewModel.resendVerificationEmail()

        viewModel.resendVerificationState.test {
            val state = awaitItem()
            assertTrue(state is LoginViewModel.VerificationResendState.Success)
        }
    }
}
