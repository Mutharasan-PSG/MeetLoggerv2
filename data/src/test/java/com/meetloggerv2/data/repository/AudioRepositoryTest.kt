package com.meetloggerv2.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.meetloggerv2.MainDispatcherRule
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.data.remote.ApiService
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AudioRepositoryTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apiService = mockk<ApiService>(relaxed = true)

    private val firebaseAuth = mockk<FirebaseAuth>()
    private val firebaseUser = mockk<FirebaseUser>(relaxed = true)
    private val mockTokenResult = mockk<GetTokenResult>()
    private val mockTask = mockk<Task<GetTokenResult>>(relaxed = true)

    private lateinit var audioRepository: AudioRepository

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

        audioRepository = AudioRepository(apiService)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun uploadAudioToBackend_success_returnsResponseBody() = runTest {
        val file = File("dummy.mp3")
        val responseBody = mockk<ResponseBody>()
        coEvery {
            apiService.uploadAudio(
                "Bearer fake_token",
                "user123",
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns Response.success(responseBody)

        val result = audioRepository.uploadAudioToBackend(
            file, "user123", "dummy.mp3", "[]", "", true, "email@example.com", "User"
        )

        assertTrue(result is NetworkResult.Success)
        assertEquals(responseBody, (result as NetworkResult.Success).data)
    }

    @Test
    fun getUploadUrl_success_returnsUrlString() = runTest {
        coEvery {
            apiService.getUploadUrl("Bearer fake_token", "user123", mapOf("fileName" to "dummy.mp3"))
        } returns Response.success(mapOf("uploadUrl" to "https://example.com/upload"))

        val result = audioRepository.getUploadUrl("user123", "dummy.mp3")

        assertTrue(result is NetworkResult.Success)
        assertEquals("https://example.com/upload", (result as NetworkResult.Success).data)
    }

    @Test
    fun getPlaybackUrl_success_returnsUrlString() = runTest {
        coEvery {
            apiService.getPlaybackUrl("Bearer fake_token", "user123", "dummy.mp3")
        } returns Response.success(mapOf("playbackUrl" to "https://example.com/playback"))

        val result = audioRepository.getPlaybackUrl("user123", "dummy.mp3")

        assertTrue(result is NetworkResult.Success)
        assertEquals("https://example.com/playback", (result as NetworkResult.Success).data)
    }

    @Test
    fun uploadToSignedUrl_success_sendsPutRequest() = runTest {
        val file = File("dummy.mp3")
        coEvery {
            apiService.uploadToSignedUrl("https://example.com/upload", any(), "audio/mpeg")
        } returns Response.success<Void>(null)

        val result = audioRepository.uploadToSignedUrl("https://example.com/upload", file)

        assertTrue(result is NetworkResult.Success)
    }
}
