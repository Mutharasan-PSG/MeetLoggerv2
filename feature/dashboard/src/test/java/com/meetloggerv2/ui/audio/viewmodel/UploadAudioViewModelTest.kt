package com.meetloggerv2.ui.audio.viewmodel

import android.content.Context
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.meetloggerv2.MainDispatcherRule
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.data.local.SettingsDataStore
import com.meetloggerv2.data.repository.IAudioRepository
import com.meetloggerv2.data.repository.IFileRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import app.cash.turbine.test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UploadAudioViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context = mockk<Context>(relaxed = true)
    private val audioRepository = mockk<IAudioRepository>(relaxed = true)
    private val fileRepository = mockk<IFileRepository>(relaxed = true)
    private val authSession = mockk<AuthSession>()
    private val settingsDataStore = mockk<SettingsDataStore>()
    private val workManager = mockk<WorkManager>(relaxed = true)

    private val historyFlow = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    private val workInfoFlow = MutableStateFlow<WorkInfo?>(null)

    private lateinit var viewModel: UploadAudioViewModel

    @Before
    fun setUp() {
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManager
        every { workManager.getWorkInfoByIdFlow(any()) } returns workInfoFlow

        every { authSession.currentUserId() } returns "user123"
        every { authSession.currentUserEmail() } returns "test@example.com"
        every { authSession.currentUserName() } returns "Test User"

        every { settingsDataStore.autoSendEmail } returns flowOf(true)
        every { fileRepository.getHistoryFlow("user123") } returns historyFlow

        viewModel = UploadAudioViewModel(context, audioRepository, fileRepository, authSession, settingsDataStore)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun fetchUserFiles_success_updatesUserFilesAndHistoryCount() = runTest {
        val mockFiles = listOf(mapOf("fileName" to "file1.mp3"), mapOf("fileName" to "file2.mp3"))
        coEvery { fileRepository.listFilesFromBackend("user123") } returns NetworkResult.Success(mockFiles)
        coEvery { fileRepository.listHistoryFromBackend("user123") } returns NetworkResult.Success(emptyList())

        viewModel.fetchUserFiles("user123")
        historyFlow.value = listOf(mapOf("fileName" to "history1.mp3"))

        viewModel.userFilesState.test {
            val names = awaitItem()
            assertEquals(2, names.size)
            assertEquals("file1.mp3", names[0])
            assertEquals("file2.mp3", names[1])
        }

        viewModel.historyCountState.test {
            assertEquals(1, awaitItem())
        }
    }

    @Test
    fun processAudio_success_enqueuesWorkAndObservesState() = runTest {
        val audioFile = File("dummy.mp3")
        val uri = mockk<Uri>()

        val mockWorkInfo = mockk<WorkInfo>(relaxed = true)
        every { mockWorkInfo.state } returns WorkInfo.State.SUCCEEDED

        viewModel.processAudio("user123", audioFile, uri, listOf("Speaker1"), "followup.mp3")
        workInfoFlow.value = mockWorkInfo

        viewModel.uiState.test {
            assertEquals(UploadAudioViewModel.UploadUiState.Processed, awaitItem())
        }

        verify { workManager.enqueueUniqueWork(any<String>(), any<androidx.work.ExistingWorkPolicy>(), any<androidx.work.OneTimeWorkRequest>()) }
    }

    @Test
    fun processAudio_error_setsUiStateError() = runTest {
        val audioFile = File("dummy.mp3")
        val uri = mockk<Uri>()

        val mockWorkInfo = mockk<WorkInfo>(relaxed = true)
        every { mockWorkInfo.state } returns WorkInfo.State.FAILED
        every { mockWorkInfo.outputData } returns workDataOf("error" to "Storage quota full")

        viewModel.processAudio("user123", audioFile, uri, listOf("Speaker1"), "followup.mp3")
        workInfoFlow.value = mockWorkInfo

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is UploadAudioViewModel.UploadUiState.Error)
            assertEquals("Storage quota full", (state as UploadAudioViewModel.UploadUiState.Error).message)
        }
    }
}
