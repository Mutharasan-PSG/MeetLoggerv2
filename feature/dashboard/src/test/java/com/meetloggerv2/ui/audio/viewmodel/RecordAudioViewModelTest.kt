package com.meetloggerv2.ui.audio.viewmodel

import android.app.Application
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.meetloggerv2.MainDispatcherRule
import com.meetloggerv2.core.media.AudioRecorderManager
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.data.local.SettingsDataStore
import com.meetloggerv2.data.repository.IAudioRepository
import com.meetloggerv2.data.repository.IFileRepository
import com.meetloggerv2.data.work.AudioUploadWorker
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class RecordAudioViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application = mockk<Application>(relaxed = true)
    private val audioRepository = mockk<IAudioRepository>(relaxed = true)
    private val fileRepository = mockk<IFileRepository>(relaxed = true)
    private val authSession = mockk<AuthSession>()
    private val settingsDataStore = mockk<SettingsDataStore>()
    private val workManager = mockk<WorkManager>(relaxed = true)

    private val historyFlow = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    private val workInfoFlow = MutableStateFlow<WorkInfo?>(null)

    private lateinit var viewModel: RecordAudioViewModel

    @Before
    fun setUp() {
        mockkConstructor(AudioRecorderManager::class)
        every { anyConstructed<AudioRecorderManager>().start(any()) } just Runs
        every { anyConstructed<AudioRecorderManager>().stop() } just Runs
        every { anyConstructed<AudioRecorderManager>().pause() } just Runs
        every { anyConstructed<AudioRecorderManager>().resume() } just Runs
        every { anyConstructed<AudioRecorderManager>().release() } just Runs

        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManager
        every { workManager.getWorkInfoByIdFlow(any()) } returns workInfoFlow

        every { authSession.currentUserId() } returns "user123"
        every { authSession.currentUserSubscription() } returns "free"
        every { authSession.currentUserEmail() } returns "test@example.com"
        every { authSession.currentUserName() } returns "Test User"

        every { settingsDataStore.autoSendEmail } returns flowOf(true)
        every { fileRepository.getHistoryFlow("user123") } returns historyFlow

        viewModel = RecordAudioViewModel(application, audioRepository, fileRepository, authSession, settingsDataStore)
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
    fun startRecording_success_setsStateRecording() {
        val tempFile = File("dummy.mp3")
        viewModel.startRecording(tempFile)

        assertEquals(RecordAudioViewModel.RecordState.RECORDING, viewModel.recordState.value)
        assertEquals(0, viewModel.elapsedTime.value)
        verify { anyConstructed<AudioRecorderManager>().start(tempFile) }
    }

    @Test
    fun pauseRecording_success_setsStatePaused() {
        val tempFile = File("dummy.mp3")
        viewModel.startRecording(tempFile)
        viewModel.pauseRecording()

        assertEquals(RecordAudioViewModel.RecordState.PAUSED, viewModel.recordState.value)
        verify { anyConstructed<AudioRecorderManager>().pause() }
    }

    @Test
    fun resumeRecording_success_setsStateRecording() {
        val tempFile = File("dummy.mp3")
        viewModel.startRecording(tempFile)
        viewModel.pauseRecording()
        viewModel.resumeRecording()

        assertEquals(RecordAudioViewModel.RecordState.RECORDING, viewModel.recordState.value)
        verify { anyConstructed<AudioRecorderManager>().resume() }
    }

    @Test
    fun stopRecording_success_setsStateStopped() {
        val tempFile = File("dummy.mp3")
        viewModel.startRecording(tempFile)
        viewModel.stopRecording()

        assertEquals(RecordAudioViewModel.RecordState.STOPPED, viewModel.recordState.value)
        verify { anyConstructed<AudioRecorderManager>().stop() }
    }

    @Test
    fun saveAudio_success_enqueuesWorkAndObservesProgress() = runTest {
        val audioFile = File("dummy.mp3")
        val uri = mockk<Uri>()

        val mockWorkInfo = mockk<WorkInfo>(relaxed = true)
        every { mockWorkInfo.state } returns WorkInfo.State.SUCCEEDED
        every { mockWorkInfo.outputData } returns workDataOf(
            AudioUploadWorker.KEY_ACTION to AudioUploadWorker.ACTION_SAVE,
            AudioUploadWorker.KEY_FILE_NAME to "dummy.mp3"
        )

        viewModel.saveAudio("user123", audioFile, uri)
        workInfoFlow.value = mockWorkInfo

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is RecordAudioViewModel.UiState.Saved)
            assertEquals("dummy.mp3", (state as RecordAudioViewModel.UiState.Saved).fileName)
        }

        verify { workManager.enqueueUniqueWork(any<String>(), any<androidx.work.ExistingWorkPolicy>(), any<androidx.work.OneTimeWorkRequest>()) }
    }

    @Test
    fun processAudio_success_enqueuesWorkAndObservesProgress() = runTest {
        val audioFile = File("dummy.mp3")
        val uri = mockk<Uri>()

        val mockWorkInfo = mockk<WorkInfo>(relaxed = true)
        every { mockWorkInfo.state } returns WorkInfo.State.SUCCEEDED
        every { mockWorkInfo.outputData } returns workDataOf(
            AudioUploadWorker.KEY_ACTION to AudioUploadWorker.ACTION_PROCESS,
            AudioUploadWorker.KEY_FILE_NAME to "dummy.mp3"
        )

        viewModel.processAudio("user123", audioFile, uri, listOf("Speaker1"), "followup.mp3")
        workInfoFlow.value = mockWorkInfo

        viewModel.uiState.test {
            assertEquals(RecordAudioViewModel.UiState.Processed, awaitItem())
        }

        verify { workManager.enqueueUniqueWork(any<String>(), any<androidx.work.ExistingWorkPolicy>(), any<androidx.work.OneTimeWorkRequest>()) }
    }
}
