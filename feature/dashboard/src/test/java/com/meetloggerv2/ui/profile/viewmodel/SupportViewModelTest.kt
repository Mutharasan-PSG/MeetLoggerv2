package com.meetloggerv2.ui.profile.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.meetloggerv2.MainDispatcherRule
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.data.remote.ApiService
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
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
class SupportViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authSession = mockk<AuthSession>()
    private val apiService = mockk<ApiService>(relaxed = true)

    private val firebaseAuth = mockk<FirebaseAuth>()
    private val firebaseUser = mockk<FirebaseUser>(relaxed = true)
    private val mockTokenResult = mockk<GetTokenResult>()
    private val mockTask = mockk<Task<GetTokenResult>>(relaxed = true)

    private lateinit var viewModel: SupportViewModel

    @Before
    fun setUp() {
        mockkStatic(FirebaseAuth::class)
        every { FirebaseAuth.getInstance() } returns firebaseAuth
        every { firebaseAuth.currentUser } returns firebaseUser

        every { mockTokenResult.token } returns "mock_firebase_token"
        every { firebaseUser.getIdToken(false) } returns mockTask

        val successSlot = slot<OnSuccessListener<GetTokenResult>>()
        every { mockTask.addOnSuccessListener(capture(successSlot)) } answers {
            successSlot.captured.onSuccess(mockTokenResult)
            mockTask
        }
        every { mockTask.addOnFailureListener(any()) } returns mockTask

        every { authSession.currentUserId() } returns "user123"
        every { authSession.currentUserEmail() } returns "test@example.com"
        every { authSession.currentUserName() } returns "Test User"

        viewModel = SupportViewModel(authSession, apiService)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun sendSupportRequest_success_setsSuccessState() = runTest {
        coEvery { apiService.submitSupport("Bearer mock_firebase_token", "user123", any()) } returns retrofit2.Response.success(okhttp3.ResponseBody.create(null, "ok"))

        viewModel.sendSupportRequest("Issue Subject", "Issue description content details")

        viewModel.uiState.test {
            assertEquals(SupportViewModel.SupportUiState.Success, awaitItem())
        }

        coVerify { apiService.submitSupport("Bearer mock_firebase_token", "user123", any()) }
    }

    @Test
    fun sendSupportRequest_error_setsErrorState() = runTest {
        coEvery { apiService.submitSupport("Bearer mock_firebase_token", "user123", any()) } returns retrofit2.Response.error(500, okhttp3.ResponseBody.create(null, "Internal Server Error"))

        viewModel.sendSupportRequest("Issue Subject", "Issue description content details")

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is SupportViewModel.SupportUiState.Error)
        }
    }
}
