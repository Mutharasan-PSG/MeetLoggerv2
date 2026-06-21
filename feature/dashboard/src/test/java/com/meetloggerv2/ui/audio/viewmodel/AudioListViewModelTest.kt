package com.meetloggerv2.ui.audio.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.meetloggerv2.MainDispatcherRule
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.core.util.Event
import com.meetloggerv2.data.local.SettingsDataStore
import com.meetloggerv2.data.repository.IAudioRepository
import com.meetloggerv2.data.repository.IFileRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import app.cash.turbine.test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AudioListViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val audioRepository = mockk<IAudioRepository>(relaxed = true)
    private val fileRepository = mockk<IFileRepository>(relaxed = true)
    private val authSession = mockk<AuthSession>()
    private val settingsDataStore = mockk<SettingsDataStore>()

    private val filesFlow = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    private val historyFlow = MutableStateFlow<List<Map<String, Any>>>(emptyList())

    private lateinit var viewModel: AudioListViewModel

    @Before
    fun setUp() {
        every { authSession.currentUserId() } returns "user123"
        every { authSession.currentUserEmail() } returns "test@example.com"
        every { authSession.currentUserName() } returns "Test User"
        every { settingsDataStore.autoSendEmail } returns flowOf(true)

        every { fileRepository.getFilesFlow("user123") } returns filesFlow
        every { fileRepository.getHistoryFlow("user123") } returns historyFlow

        viewModel = AudioListViewModel(audioRepository, fileRepository, authSession, settingsDataStore)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun fetchAudioFiles_success_updatesRawAudioFilesAndStateIdle() = runTest {
        val mockAudioFiles = listOf("meeting1.mp3", "meeting2.wav")
        coEvery { audioRepository.listRawFilesFromBackend("user123") } returns NetworkResult.Success(mockAudioFiles)

        viewModel.fetchAudioFiles(showLoading = true)

        viewModel.uiState.test {
            assertEquals(AudioListViewModel.AudioUiState.Idle, awaitItem())
        }

        viewModel.filteredAudioFiles.test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertEquals("meeting1", items[0])
            assertEquals("meeting2", items[1])
        }
    }

    @Test
    fun fetchAudioFiles_error_updatesUiStateToError() = runTest {
        val errorMsg = "Network error occurred"
        coEvery { audioRepository.listRawFilesFromBackend("user123") } returns NetworkResult.Error(errorMsg)

        viewModel.fetchAudioFiles(showLoading = true)

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is AudioListViewModel.AudioUiState.Error)
            assertEquals(errorMsg, (state as AudioListViewModel.AudioUiState.Error).message)
        }
    }

    @Test
    fun downloadAudioFile_success_emitsDownloadFileSuccessEvent() = runTest {
        val destFile = File("dummyPath")
        val callbackSlot = slot<(Boolean, Exception?) -> Unit>()
        every {
            audioRepository.downloadAudioToFile("user123", "meeting.mp3", destFile, capture(callbackSlot))
        } answers {
            callbackSlot.captured.invoke(true, null)
        }

        viewModel.audioEvent.test {
            viewModel.downloadAudioFile("meeting.mp3", destFile)
            val event = awaitItem().getContentIfNotHandled()
            assertNotNull(event)
            assertTrue(event is AudioListViewModel.AudioEvent.DownloadFileSuccess)
            assertEquals("meeting.mp3", (event as AudioListViewModel.AudioEvent.DownloadFileSuccess).fileName)
            assertEquals(destFile, event.localFile)
        }
    }

    @Test
    fun downloadAudioFile_error_emitsDownloadFileErrorEvent() = runTest {
        val destFile = File("dummyPath")
        val callbackSlot = slot<(Boolean, Exception?) -> Unit>()
        val exception = Exception("Download failed")
        every {
            audioRepository.downloadAudioToFile("user123", "meeting.mp3", destFile, capture(callbackSlot))
        } answers {
            callbackSlot.captured.invoke(false, exception)
        }

        viewModel.audioEvent.test {
            viewModel.downloadAudioFile("meeting.mp3", destFile)
            val event = awaitItem().getContentIfNotHandled()
            assertNotNull(event)
            assertTrue(event is AudioListViewModel.AudioEvent.DownloadFileError)
            assertEquals("meeting.mp3", (event as AudioListViewModel.AudioEvent.DownloadFileError).fileName)
            assertEquals("Download failed", event.errorMsg)
        }
    }

    @Test
    fun getAudioDownloadUrl_success_emitsDownloadUrlSuccessEvent() = runTest {
        val playbackUrl = "https://example.com/playback"
        coEvery { audioRepository.getPlaybackUrl("user123", "meeting.mp3") } returns NetworkResult.Success(playbackUrl)

        viewModel.audioEvent.test {
            viewModel.getAudioDownloadUrl("meeting.mp3")
            val event = awaitItem().getContentIfNotHandled()
            assertNotNull(event)
            assertTrue(event is AudioListViewModel.AudioEvent.DownloadUrlSuccess)
            assertEquals("meeting.mp3", (event as AudioListViewModel.AudioEvent.DownloadUrlSuccess).fileName)
            assertEquals(playbackUrl, event.url)
        }
    }

    @Test
    fun getAudioDownloadUrl_error_emitsDownloadUrlErrorEvent() = runTest {
        val errorMsg = "Token expired"
        coEvery { audioRepository.getPlaybackUrl("user123", "meeting.mp3") } returns NetworkResult.Error(errorMsg)

        viewModel.audioEvent.test {
            viewModel.getAudioDownloadUrl("meeting.mp3")
            val event = awaitItem().getContentIfNotHandled()
            assertNotNull(event)
            assertTrue(event is AudioListViewModel.AudioEvent.DownloadUrlError)
            assertEquals("meeting.mp3", (event as AudioListViewModel.AudioEvent.DownloadUrlError).fileName)
            assertEquals(errorMsg, event.errorMsg)
        }
    }

    @Test
    fun downloadAudioBytes_success_emitsDownloadBytesSuccessEvent() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val callbackSlot = slot<(ByteArray?, Exception?) -> Unit>()
        every {
            audioRepository.downloadAudioBytes("user123", "meeting.mp3", capture(callbackSlot))
        } answers {
            callbackSlot.captured.invoke(bytes, null)
        }

        viewModel.audioEvent.test {
            viewModel.downloadAudioBytes("meeting.mp3")
            val event = awaitItem().getContentIfNotHandled()
            assertNotNull(event)
            assertTrue(event is AudioListViewModel.AudioEvent.DownloadBytesSuccess)
            assertEquals("meeting.mp3", (event as AudioListViewModel.AudioEvent.DownloadBytesSuccess).fileName)
            assertArrayEquals(bytes, event.bytes)
        }
    }

    @Test
    fun downloadAudioBytes_error_emitsDownloadBytesErrorEvent() = runTest {
        val exception = Exception("Network timeout")
        val callbackSlot = slot<(ByteArray?, Exception?) -> Unit>()
        every {
            audioRepository.downloadAudioBytes("user123", "meeting.mp3", capture(callbackSlot))
        } answers {
            callbackSlot.captured.invoke(null, exception)
        }

        viewModel.audioEvent.test {
            viewModel.downloadAudioBytes("meeting.mp3")
            val event = awaitItem().getContentIfNotHandled()
            assertNotNull(event)
            assertTrue(event is AudioListViewModel.AudioEvent.DownloadBytesError)
            assertEquals("meeting.mp3", (event as AudioListViewModel.AudioEvent.DownloadBytesError).fileName)
            assertEquals("Network timeout", event.errorMsg)
        }
    }

    @Test
    fun fetchUserFiles_success_updatesUserFilesAndHistoryFlows() = runTest {
        val mockUserFiles = listOf(mapOf("fileName" to "file1.mp3"), mapOf("fileName" to "file2.mp3"))
        val mockHistoryList = listOf(mapOf("fileName" to "history1.mp3"), mapOf("fileName" to "history2.mp3"))
        val mockAudioList = listOf("audio1.mp3", "audio2.mp3")

        coEvery { audioRepository.listRawFilesFromBackend("user123") } returns NetworkResult.Success(mockAudioList)
        coEvery { fileRepository.listHistoryFromBackend("user123") } returns NetworkResult.Success(emptyList())

        viewModel.fetchUserFiles()

        // Push data to local flow streams
        filesFlow.value = mockUserFiles
        historyFlow.value = mockHistoryList

        viewModel.userFilesState.test {
            val names = awaitItem()
            assertEquals(2, names.size)
            assertEquals("file1.mp3", names[0])
            assertEquals("file2.mp3", names[1])
        }

        viewModel.historyCountState.test {
            assertEquals(2, awaitItem())
        }

        viewModel.uiState.test {
            assertEquals(AudioListViewModel.AudioUiState.Idle, awaitItem())
        }
    }

    @Test
    fun deleteAudioFiles_success_removesFromRawAudioFilesList() = runTest {
        // Pre-populate raw audio files list
        val mockAudioFiles = listOf("meeting1.mp3", "meeting2.wav")
        coEvery { audioRepository.listRawFilesFromBackend("user123") } returns NetworkResult.Success(mockAudioFiles)
        viewModel.fetchAudioFiles()

        coEvery { fileRepository.deleteFileOnBackend("user123", "meeting1", "audio") } returns NetworkResult.Success(Unit)

        viewModel.deleteAudioFiles(listOf("meeting1"))

        viewModel.filteredAudioFiles.test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("meeting2", items[0])
        }
    }

    @Test
    fun deleteAudioFiles_partialError_updatesStateToError() = runTest {
        coEvery { fileRepository.deleteFileOnBackend("user123", "meeting1", "audio") } returns NetworkResult.Success(Unit)
        coEvery { fileRepository.deleteFileOnBackend("user123", "meeting2", "audio") } returns NetworkResult.Error("Failed to delete")

        viewModel.deleteAudioFiles(listOf("meeting1", "meeting2"))

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is AudioListViewModel.AudioUiState.Error)
            assertEquals("Some files failed to delete", (state as AudioListViewModel.AudioUiState.Error).message)
        }
    }

    @Test
    fun renameAudioFile_success_updatesFilteredAudioFilesList() = runTest {
        // Pre-populate raw audio files list
        val mockAudioFiles = listOf("meeting1.mp3", "meeting2.wav")
        coEvery { audioRepository.listRawFilesFromBackend("user123") } returns NetworkResult.Success(mockAudioFiles)
        viewModel.fetchAudioFiles()

        coEvery { fileRepository.renameFileOnBackend("user123", "meeting1", "meeting_renamed", "audio") } returns NetworkResult.Success("meeting_renamed")

        viewModel.renameAudioFile("meeting1", "meeting_renamed")

        viewModel.filteredAudioFiles.test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertTrue(items.contains("meeting_renamed"))
            assertFalse(items.contains("meeting1"))
        }

        viewModel.uiState.test {
            assertEquals(AudioListViewModel.AudioUiState.Idle, awaitItem())
        }
    }

    @Test
    fun renameAudioFile_error_setsStateToError() = runTest {
        val errorMsg = "Rename restricted"
        coEvery { fileRepository.renameFileOnBackend("user123", "meeting1", "meeting_renamed", "audio") } returns NetworkResult.Error(errorMsg)

        viewModel.renameAudioFile("meeting1", "meeting_renamed")

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is AudioListViewModel.AudioUiState.Error)
            assertEquals(errorMsg, (state as AudioListViewModel.AudioUiState.Error).message)
        }
    }

    @Test
    fun processAudio_success_setsUiStateToProcessed() = runTest {
        val audioFile = File("dummyPath")
        val finalFileName = "meeting.mp3"
        val audioUrl = "AudioFiles/user123/meeting.mp3"

        coEvery { fileRepository.saveAsNewCopyOnBackend("user123", finalFileName, any()) } returns NetworkResult.Success("meeting.mp3")
        coEvery {
            audioRepository.uploadAudioToBackend(audioFile, "user123", "meeting.mp3", any(), "followUp", true, "test@example.com", "Test User")
        } returns NetworkResult.Success(mockk<ResponseBody>())

        viewModel.processAudio(audioFile, listOf("Speaker1"), "followUp", finalFileName, audioUrl)

        viewModel.uiState.test {
            assertEquals(AudioListViewModel.AudioUiState.Processed, awaitItem())
        }
    }

    @Test
    fun processAudio_error_setsUiStateToError() = runTest {
        val audioFile = File("dummyPath")
        val finalFileName = "meeting.mp3"
        val audioUrl = "AudioFiles/user123/meeting.mp3"
        val errorMsg = "Upload limit exceeded"

        coEvery { fileRepository.saveAsNewCopyOnBackend("user123", finalFileName, any()) } returns NetworkResult.Success("meeting.mp3")
        coEvery {
            audioRepository.uploadAudioToBackend(audioFile, "user123", "meeting.mp3", any(), "followUp", true, "test@example.com", "Test User")
        } returns NetworkResult.Error(errorMsg)

        viewModel.processAudio(audioFile, listOf("Speaker1"), "followUp", finalFileName, audioUrl)

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is AudioListViewModel.AudioUiState.Error)
            assertEquals(errorMsg, (state as AudioListViewModel.AudioUiState.Error).message)
        }
    }
}
