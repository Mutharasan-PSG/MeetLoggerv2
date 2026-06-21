package com.meetloggerv2.ui.main.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.meetloggerv2.MainDispatcherRule
import com.meetloggerv2.core.session.SessionManager
import com.meetloggerv2.data.repository.IAuthRepository
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application = mockk<Application>(relaxed = true)
    private val authRepository = mockk<IAuthRepository>(relaxed = true)
    private val firebaseUser = mockk<FirebaseUser>(relaxed = true)

    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        mockkConstructor(SessionManager::class)
        coEvery { anyConstructed<SessionManager>().isLoggedIn() } returns false
        coEvery { anyConstructed<SessionManager>().clearSession() } just Runs

        viewModel = MainViewModel(application, authRepository)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun checkSession_noSession_returnsInvalidAndClearsSession() {
        coEvery { anyConstructed<SessionManager>().isLoggedIn() } returns false
        every { authRepository.getCurrentUser() } returns null

        viewModel.checkSession()

        assertEquals(false, viewModel.isSessionValid.value)
        coVerify { anyConstructed<SessionManager>().clearSession() }
    }

    @Test
    fun checkSession_validSession_returnsValid() {
        coEvery { anyConstructed<SessionManager>().isLoggedIn() } returns true
        every { authRepository.getCurrentUser() } returns firebaseUser

        viewModel.checkSession()

        assertEquals(true, viewModel.isSessionValid.value)
        coVerify(exactly = 0) { anyConstructed<SessionManager>().clearSession() }
    }
}
