package com.meetloggerv2.ui.details.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.meetloggerv2.MainDispatcherRule
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.data.repository.IFileRepository
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import app.cash.turbine.test

@OptIn(ExperimentalCoroutinesApi::class)
class FileDetailsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fileRepository = mockk<IFileRepository>(relaxed = true)
    private val authSession = mockk<AuthSession>()
    private val translator = mockk<Translator>(relaxed = true)

    private lateinit var viewModel: FileDetailsViewModel

    @Before
    fun setUp() {
        // Mock static objects/extensions for ML Kit
        mockkStatic(Translation::class)
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")

        every { Translation.getClient(any<TranslatorOptions>()) } returns translator

        every { authSession.currentUserId() } returns "user123"
        every { authSession.currentUserSubscription() } returns "premium"

        viewModel = FileDetailsViewModel(fileRepository, authSession)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun fetchDetails_success_updatesFileDetailsAndSetsStateToIdle() = runTest {
        val mockDetails = mapOf("fileName" to "meeting.mp3", "Response" to "Transcription content")
        coEvery { fileRepository.getFileDetailsFromBackend("user123", "meeting.mp3") } returns NetworkResult.Success(mockDetails)

        viewModel.fetchDetails("meeting.mp3")

        viewModel.fileDetails.test {
            val details = awaitItem()
            assertNotNull(details)
            assertEquals("meeting.mp3", details?.get("fileName"))
            assertEquals("Transcription content", details?.get("Response"))
        }

        viewModel.uiState.test {
            assertEquals(FileDetailsViewModel.DetailsUiState.Idle, awaitItem())
        }
    }

    @Test
    fun fetchDetails_error_updatesUiStateToError() = runTest {
        val errorMsg = "Details unavailable"
        coEvery { fileRepository.getFileDetailsFromBackend("user123", "meeting.mp3") } returns NetworkResult.Error(errorMsg)

        viewModel.fetchDetails("meeting.mp3")

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is FileDetailsViewModel.DetailsUiState.Error)
            assertEquals(errorMsg, (state as FileDetailsViewModel.DetailsUiState.Error).message)
        }
    }

    @Test
    fun translateContent_success_updatesTranslatedTextAndSetsStateToIdle() = runTest {
        val textToTranslate = "Hello world"
        val translatedText = "Bonjour le monde"

        val mockDownloadTask = mockk<Task<Void?>>(relaxed = true)
        val mockTranslateTask = mockk<Task<String>>(relaxed = true)

        every { translator.downloadModelIfNeeded(any()) } returns mockDownloadTask
        every { translator.translate(any()) } returns mockTranslateTask

        coEvery { mockDownloadTask.await() } returns null
        coEvery { mockTranslateTask.await() } returns translatedText

        viewModel.translateContent(textToTranslate, "en", "fr")

        viewModel.translatedText.test {
            assertEquals(translatedText, awaitItem())
        }

        viewModel.uiState.test {
            assertEquals(FileDetailsViewModel.DetailsUiState.Idle, awaitItem())
        }

        verify { translator.close() }
    }

    @Test
    fun translateContent_error_setsUiStateToError() = runTest {
        val mockDownloadTask = mockk<Task<Void?>>(relaxed = true)
        every { translator.downloadModelIfNeeded(any()) } returns mockDownloadTask
        coEvery { mockDownloadTask.await() } throws Exception("Model download failed")

        viewModel.translateContent("Hello", "en", "fr")

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is FileDetailsViewModel.DetailsUiState.Error)
            assertTrue((state as FileDetailsViewModel.DetailsUiState.Error).message.contains("Translation failed"))
        }
    }

    @Test
    fun updateContent_success_updatesUiStateAndRefetchesDetails() = runTest {
        val updates = mapOf(
            "Response" to "New transcript text",
            "OriginalLanguage" to "es"
        )
        coEvery { fileRepository.updateFileContentOnBackend("user123", "meeting.mp3", updates) } returns NetworkResult.Success(Unit)
        coEvery { fileRepository.getFileDetailsFromBackend("user123", "meeting.mp3") } returns NetworkResult.Success(emptyMap())

        viewModel.updateContent("meeting.mp3", "New transcript text", "es")

        assertEquals(FileDetailsViewModel.DetailsUiState.Idle, viewModel.uiState.value)
        coVerify { fileRepository.updateFileContentOnBackend("user123", "meeting.mp3", updates) }
        coVerify { fileRepository.getFileDetailsFromBackend("user123", "meeting.mp3") }
    }

    @Test
    fun updateContent_error_setsUiStateToError() = runTest {
        val updates = mapOf(
            "Response" to "New transcript text",
            "OriginalLanguage" to "es"
        )
        val errorMsg = "Update conflict"
        coEvery { fileRepository.updateFileContentOnBackend("user123", "meeting.mp3", updates) } returns NetworkResult.Error(errorMsg)

        viewModel.updateContent("meeting.mp3", "New transcript text", "es")

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is FileDetailsViewModel.DetailsUiState.Error)
            assertEquals(errorMsg, (state as FileDetailsViewModel.DetailsUiState.Error).message)
        }
    }

    @Test
    fun saveAsNewCopy_success_setsUiStateToNewFileCreated() = runTest {
        val mockData = mapOf("Response" to "Transcribed content")
        coEvery { fileRepository.saveAsNewCopyOnBackend("user123", "meeting_copy.mp3", mockData) } returns NetworkResult.Success("meeting_copy (1).mp3")

        viewModel.saveAsNewCopy("meeting_copy.mp3", mockData)

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is FileDetailsViewModel.DetailsUiState.NewFileCreated)
            assertEquals("meeting_copy (1).mp3", (state as FileDetailsViewModel.DetailsUiState.NewFileCreated).fileName)
        }
    }

    @Test
    fun saveAsNewCopy_error_setsUiStateToError() = runTest {
        val mockData = mapOf("Response" to "Transcribed content")
        val errorMsg = "Storage full"
        coEvery { fileRepository.saveAsNewCopyOnBackend("user123", "meeting_copy.mp3", mockData) } returns NetworkResult.Error(errorMsg)

        viewModel.saveAsNewCopy("meeting_copy.mp3", mockData)

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is FileDetailsViewModel.DetailsUiState.Error)
            assertEquals(errorMsg, (state as FileDetailsViewModel.DetailsUiState.Error).message)
        }
    }
}
