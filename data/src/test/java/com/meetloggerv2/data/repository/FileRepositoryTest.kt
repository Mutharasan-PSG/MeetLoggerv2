package com.meetloggerv2.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.meetloggerv2.MainDispatcherRule
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.data.local.db.HistoryDao
import com.meetloggerv2.data.local.db.LocalFileDao
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
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class FileRepositoryTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val localFileDao = mockk<LocalFileDao>(relaxed = true)
    private val historyDao = mockk<HistoryDao>(relaxed = true)
    private val apiService = mockk<ApiService>(relaxed = true)

    private val firebaseAuth = mockk<FirebaseAuth>()
    private val firebaseUser = mockk<FirebaseUser>(relaxed = true)
    private val mockTokenResult = mockk<GetTokenResult>()
    private val mockTask = mockk<Task<GetTokenResult>>(relaxed = true)

    private lateinit var fileRepository: FileRepository

    @Before
    fun setUp() {
        mockkStatic(FirebaseAuth::class)
        every { FirebaseAuth.getInstance() } returns firebaseAuth
        every { firebaseAuth.currentUser } returns firebaseUser

        every { mockTokenResult.token } returns "fake_token"
        every { firebaseUser.getIdToken(false) } returns mockTask

        val successSlot = slot<OnSuccessListener<GetTokenResult>>()
        every { mockTask.addOnSuccessListener(capture(successSlot)) } answers {
            successSlot.captured.onSuccess(mockTokenResult)
            mockTask
        }
        every { mockTask.addOnFailureListener(any()) } returns mockTask

        fileRepository = FileRepository(localFileDao, historyDao, apiService)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun listFilesFromBackend_success_syncsLocalFilesAndReturnsData() = runTest {
        val files = listOf(
            mapOf("fileName" to "file1.mp3", "status" to "processed"),
            mapOf("fileName" to "file2.mp3", "status" to "processing")
        )
        coEvery { apiService.listFiles("Bearer fake_token", "user123") } returns Response.success(files)

        val result = fileRepository.listFilesFromBackend("user123")

        assertTrue(result is NetworkResult.Success)
        assertEquals(2, (result as NetworkResult.Success).data?.size)
        coVerify { localFileDao.syncUserFiles("user123", any()) }
    }

    @Test
    fun listHistoryFromBackend_success_syncsHistoryAndReturnsData() = runTest {
        val historyList = listOf(
            mapOf("fileName" to "h1.mp3", "status" to "saved"),
            mapOf("fileName" to "h2.mp3", "status" to "processing")
        )
        coEvery { apiService.listHistory("Bearer fake_token", "user123") } returns Response.success(historyList)

        val result = fileRepository.listHistoryFromBackend("user123")

        assertTrue(result is NetworkResult.Success)
        assertEquals(2, (result as NetworkResult.Success).data?.size)
        coVerify { historyDao.syncHistory("user123", any()) }
    }

    @Test
    fun renameFileOnBackend_success_updatesLocalCacheAndReturnsNewName() = runTest {
        val returnedName = "meeting_renamed.mp3"
        coEvery {
            apiService.renameFile("Bearer fake_token", "user123", "meeting.mp3", mapOf("newName" to "meeting_renamed"), "file")
        } returns Response.success(mapOf("newName" to returnedName))

        val result = fileRepository.renameFileOnBackend("user123", "meeting.mp3", "meeting_renamed", "file")

        assertTrue(result is NetworkResult.Success)
        assertEquals(returnedName, (result as NetworkResult.Success).data)
    }

    @Test
    fun deleteFileOnBackend_success_sendsDeleteRequest() = runTest {
        coEvery {
            apiService.deleteFile("Bearer fake_token", "user123", "meeting.mp3", "file")
        } returns Response.success(mapOf("status" to "deleted"))

        val result = fileRepository.deleteFileOnBackend("user123", "meeting.mp3", "file")

        assertTrue(result is NetworkResult.Success)
        coVerify { apiService.deleteFile("Bearer fake_token", "user123", "meeting.mp3", "file") }
    }

    @Test
    fun copyFileOnBackend_success_sendsCopyRequestAndReturnsNewName() = runTest {
        coEvery {
            apiService.copyFile("Bearer fake_token", "user123", "meeting.mp3", mapOf("newName" to "copy.mp3"))
        } returns Response.success(mapOf("newName" to "copy.mp3"))

        val result = fileRepository.copyFileOnBackend("user123", "meeting.mp3", "copy.mp3")

        assertTrue(result is NetworkResult.Success)
        assertEquals("copy.mp3", (result as NetworkResult.Success).data)
    }

    @Test
    fun saveAsNewCopyOnBackend_success_sendsSaveRequestAndReturnsNewName() = runTest {
        val payload = mapOf("content" to "Transcribed payload details")
        coEvery {
            apiService.saveAsNewCopy("Bearer fake_token", "user123", "new.mp3", com.meetloggerv2.data.remote.SaveAsNewRequest(payload))
        } returns Response.success(mapOf("newName" to "new (1).mp3"))

        val result = fileRepository.saveAsNewCopyOnBackend("user123", "new.mp3", payload)

        assertTrue(result is NetworkResult.Success)
        assertEquals("new (1).mp3", (result as NetworkResult.Success).data)
    }

    @Test
    fun updateUserProfileOnBackend_success_sendsProfileUpdates() = runTest {
        val updates = mapOf("name" to "Updated Name")
        coEvery {
            apiService.updateUserProfile("Bearer fake_token", "user123", com.meetloggerv2.data.remote.ProfileUpdateRequest(updates))
        } returns Response.success(mapOf("status" to "ok"))

        val result = fileRepository.updateUserProfileOnBackend("user123", updates)

        assertTrue(result is NetworkResult.Success)
        coVerify { apiService.updateUserProfile("Bearer fake_token", "user123", com.meetloggerv2.data.remote.ProfileUpdateRequest(updates)) }
    }
}
