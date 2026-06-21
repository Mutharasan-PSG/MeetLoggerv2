package com.meetloggerv2.ui.profile.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.meetloggerv2.MainDispatcherRule
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.core.session.SessionManager
import com.meetloggerv2.data.local.CachedProfile
import com.meetloggerv2.data.local.ProfileDataStore
import com.meetloggerv2.data.repository.IFileRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
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
class ProfileViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application = mockk<Application>(relaxed = true)
    private val fileRepository = mockk<IFileRepository>(relaxed = true)
    private val authSession = mockk<AuthSession>(relaxed = true)
    private val googleSignInClient = mockk<GoogleSignInClient>(relaxed = true)

    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.isEmpty(any()) } answers {
            val s = firstArg<CharSequence?>()
            s == null || s.length == 0
        }

        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        mockkConstructor(ProfileDataStore::class)
        mockkConstructor(SessionManager::class)

        mockkStatic(GoogleSignIn::class)
        every { GoogleSignIn.getClient(any<android.content.Context>(), any<GoogleSignInOptions>()) } returns googleSignInClient

        coEvery { anyConstructed<ProfileDataStore>().getProfile() } returns null
        coEvery { anyConstructed<ProfileDataStore>().saveProfile(any(), any(), any(), any()) } just Runs

        coEvery { anyConstructed<SessionManager>().getUserDetails() } returns null
        coEvery { anyConstructed<SessionManager>().saveUserDetails(any()) } just Runs

        viewModel = ProfileViewModel(application, fileRepository, authSession)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun loadUserProfile_cachedMatchesDate_updatesProfileFlow() = runTest {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val cached = CachedProfile("Cached Name", "cached@example.com", "photo_url", todayStr)
        coEvery { anyConstructed<ProfileDataStore>().getProfile() } returns cached

        viewModel.loadUserProfile("user123")

        viewModel.userProfile.test {
            val profile = awaitItem()
            assertNotNull(profile)
            assertEquals("Cached Name", profile?.get("name"))
            assertEquals("cached@example.com", profile?.get("email"))
        }

        coVerify(exactly = 0) { fileRepository.getUserProfileFromBackend(any()) }
    }

    @Test
    fun loadUserProfile_backendSuccess_savesToCacheAndSession() = runTest {
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val backendData = mapOf("name" to "Backend Name", "email" to "backend@example.com", "photoUrl" to "photo", "subscription" to "premium")
        coEvery { fileRepository.getUserProfileFromBackend("user123") } returns NetworkResult.Success(backendData)

        viewModel.loadUserProfile("user123")

        viewModel.userProfile.test {
            val profile = awaitItem()
            assertNotNull(profile)
            assertEquals("Backend Name", profile?.get("name"))
        }

        coVerify { anyConstructed<ProfileDataStore>().saveProfile("Backend Name", "backend@example.com", "photo", dateStr) }
        coVerify { anyConstructed<SessionManager>().saveUserDetails(any()) }
    }

    @Test
    fun updateProfile_success_callsLoadProfile() = runTest {
        coEvery { fileRepository.updateUserProfileOnBackend("user123", any()) } returns NetworkResult.Success(Unit)
        coEvery { fileRepository.getUserProfileFromBackend("user123") } returns NetworkResult.Success(emptyMap())

        viewModel.updateProfile("user123", "New Name", "newPhoto")

        coVerify { fileRepository.updateUserProfileOnBackend("user123", mapOf("name" to "New Name", "photoUrl" to "newPhoto")) }
        coVerify { fileRepository.getUserProfileFromBackend("user123") }
    }

    @Test
    fun signOut_success_setsSignOutSuccess() = runTest {
        val mockTask = mockk<Task<Void>>(relaxed = true)
        every { googleSignInClient.signOut() } returns mockTask
        
        val listenerSlot = slot<com.google.android.gms.tasks.OnCompleteListener<Void>>()
        every { mockTask.addOnCompleteListener(capture(listenerSlot)) } answers {
            listenerSlot.captured.onComplete(mockTask)
            mockTask
        }

        viewModel.signOut()

        viewModel.signOutState.test {
            assertEquals(ProfileViewModel.SignOutState.Success, awaitItem())
        }

        coVerify { authSession.signOut() }
    }
}
