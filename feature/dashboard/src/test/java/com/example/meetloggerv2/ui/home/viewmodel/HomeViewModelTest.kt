package com.example.meetloggerv2.ui.home.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.meetloggerv2.MainDispatcherRule
import com.example.meetloggerv2.core.session.AuthSession
import com.example.meetloggerv2.data.local.CachedProfile
import com.example.meetloggerv2.data.local.ProfileDataStore
import com.example.meetloggerv2.data.repository.IFileRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
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
class HomeViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application = mockk<Application>(relaxed = true)
    private val fileRepository = mockk<IFileRepository>(relaxed = true)
    private val authSession = mockk<AuthSession>()
    private val listenerRegistration = mockk<ListenerRegistration>(relaxed = true)

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        mockkConstructor(ProfileDataStore::class)
        coEvery { anyConstructed<ProfileDataStore>().getProfile() } returns null
        coEvery { anyConstructed<ProfileDataStore>().saveProfile(any(), any(), any(), any()) } just Runs
        every { authSession.currentUserId() } returns "user123"
        
        viewModel = HomeViewModel(application, fileRepository, authSession)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun fetchFiles_success_updatesFilesFlow() = runTest {
        val mockData = listOf(
            mapOf("fileName" to "file1.mp3", "status" to "processed", "timestamp_clientUpload" to Timestamp(1000, 0), "isCopy" to false),
            mapOf("fileName" to "file2.mp3", "status" to "processing", "timestamp_clientUpload" to Timestamp(2000, 0), "isCopy" to true)
        )

        every { fileRepository.getUserFiles("user123", any(), any()) } answers {
            val onUpdate = secondArg<(List<Map<String, Any>>) -> Unit>()
            onUpdate(mockData)
            listenerRegistration
        }

        viewModel.fetchFiles()

        viewModel.files.test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("file1.mp3", items[0].first)
            assertEquals("processed", items[0].second)
            assertEquals(Timestamp(1000, 0), items[0].third)
        }
    }

    @Test
    fun fetchFiles_error_updatesErrorFlow() = runTest {
        val errorMsg = "Failed to fetch files"
        every { fileRepository.getUserFiles("user123", any(), any()) } answers {
            val onError = thirdArg<(Exception) -> Unit>()
            onError(Exception(errorMsg))
            listenerRegistration
        }

        viewModel.fetchFiles()

        viewModel.error.test {
            assertEquals(errorMsg, awaitItem())
        }
    }

    @Test
    fun loadUserProfile_cachedMatchesDate_updatesProfileFlow() = runTest {
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val cached = CachedProfile("John Doe", "john@example.com", "photo_url", dateStr)
        coEvery { anyConstructed<ProfileDataStore>().getProfile() } returns cached

        viewModel.loadUserProfile()

        viewModel.userProfile.test {
            val profile = awaitItem()
            assertNotNull(profile)
            assertEquals("John Doe", profile?.get("name"))
            assertEquals("john@example.com", profile?.get("email"))
            assertEquals("photo_url", profile?.get("photoUrl"))
        }

        verify(exactly = 0) { fileRepository.getUser(any(), any(), any()) }
    }

    @Test
    fun loadUserProfile_noCache_fetchesFromRepositoryAndSaves() = runTest {
        coEvery { anyConstructed<ProfileDataStore>().getProfile() } returns null
        val userData = mapOf("name" to "Jane Doe", "email" to "jane@example.com", "photoUrl" to "jane_photo")

        every { fileRepository.getUser("user123", any(), any()) } answers {
            val onSuccess = secondArg<(Map<String, Any>?) -> Unit>()
            onSuccess(userData)
        }

        viewModel.loadUserProfile()

        viewModel.userProfile.test {
            val profile = awaitItem()
            assertNotNull(profile)
            assertEquals("Jane Doe", profile?.get("name"))
            assertEquals("jane@example.com", profile?.get("email"))
        }

        coVerify { anyConstructed<ProfileDataStore>().saveProfile("Jane Doe", "jane@example.com", "jane_photo", any()) }
    }
}
