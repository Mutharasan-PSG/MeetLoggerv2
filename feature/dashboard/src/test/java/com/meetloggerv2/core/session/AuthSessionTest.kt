package com.meetloggerv2.core.session

import com.meetloggerv2.data.repository.IAuthRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionTest {

    private val authRepository = mockk<IAuthRepository>(relaxed = true)
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val sessionCleanup = mockk<ISessionCleanup>(relaxed = true)

    private lateinit var authSession: AuthSession

    @Before
    fun setUp() {
        authSession = AuthSession(authRepository, sessionManager, sessionCleanup)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun signOut_triggersSessionCleanupSessionManagerAndAuthRepository() = runTest {
        authSession.signOut()

        coVerify(exactly = 1) { sessionCleanup.clearAllLocalData() }
        verify(exactly = 1) { sessionManager.clearSession() }
        verify(exactly = 1) { authRepository.signOut() }
    }
}
