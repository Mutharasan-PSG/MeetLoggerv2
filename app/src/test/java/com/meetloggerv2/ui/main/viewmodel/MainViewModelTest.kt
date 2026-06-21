package com.meetloggerv2.ui.main.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.meetloggerv2.MainDispatcherRule
import com.meetloggerv2.core.config.AppConfig
import com.meetloggerv2.core.config.GateConfig
import com.meetloggerv2.core.config.GateResult
import com.meetloggerv2.core.session.SessionManager
import com.meetloggerv2.data.repository.IAuthRepository
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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

    private fun gateConfig(
        minSupportedVersion: Int = 1,
        maintenanceMode: Boolean = false,
        blockedUserIds: Set<String> = emptySet(),
    ) = GateConfig(
        minSupportedVersion = minSupportedVersion,
        maintenanceMode = maintenanceMode,
        maintenanceMessage = "down",
        blockedUserIds = blockedUserIds,
        updateUrl = "https://store/x",
    )

    @Test
    fun evaluateGate_allowed_whenConfigClean() = runTest {
        mockkObject(AppConfig)
        coEvery { AppConfig.ensureLimitValidated() } just Runs
        every { AppConfig.snapshot() } returns gateConfig()
        every { authRepository.getCurrentUser() } returns firebaseUser
        every { firebaseUser.uid } returns "u1"

        assertEquals(GateResult.Allowed, viewModel.evaluateGate(versionCode = 3))
    }

    @Test
    fun evaluateGate_forceUpdate_whenBelowMinVersion() = runTest {
        mockkObject(AppConfig)
        coEvery { AppConfig.ensureLimitValidated() } just Runs
        every { AppConfig.snapshot() } returns gateConfig(minSupportedVersion = 5)
        every { authRepository.getCurrentUser() } returns firebaseUser
        every { firebaseUser.uid } returns "u1"

        assertTrue(viewModel.evaluateGate(versionCode = 4) is GateResult.ForceUpdate)
    }

    @Test
    fun evaluateGate_blocked_whenUidBlocklisted() = runTest {
        mockkObject(AppConfig)
        coEvery { AppConfig.ensureLimitValidated() } just Runs
        every { AppConfig.snapshot() } returns gateConfig(blockedUserIds = setOf("u1"))
        every { authRepository.getCurrentUser() } returns firebaseUser
        every { firebaseUser.uid } returns "u1"

        assertEquals(GateResult.Blocked, viewModel.evaluateGate(versionCode = 9))
    }

    @Test
    fun evaluateGate_failsOpen_whenConfigRefreshThrows() = runTest {
        // The fail-open path logs via AppLogger -> android.util.Log, which is not
        // available on the JVM; stub it so the catch block runs cleanly.
        mockkObject(com.meetloggerv2.core.util.AppLogger)
        every { com.meetloggerv2.core.util.AppLogger.e(any(), any(), any()) } just Runs
        mockkObject(AppConfig)
        coEvery { AppConfig.ensureLimitValidated() } throws RuntimeException("offline")
        every { AppConfig.snapshot() } returns gateConfig()
        every { authRepository.getCurrentUser() } returns firebaseUser
        every { firebaseUser.uid } returns "u1"

        assertEquals(GateResult.Allowed, viewModel.evaluateGate(versionCode = 1))
    }
}
