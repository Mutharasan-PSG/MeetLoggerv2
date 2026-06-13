package com.example.meetloggerv2.ui.report.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.meetloggerv2.MainDispatcherRule
import com.example.meetloggerv2.core.session.AuthSession
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
class ReportViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fileRepository = mockk<IFileRepository>(relaxed = true)
    private val authSession = mockk<AuthSession>()
    private val listenerRegistration = mockk<ListenerRegistration>(relaxed = true)

    private lateinit var viewModel: ReportViewModel

    @Before
    fun setUp() {
        every { authSession.currentUserId() } returns "user123"
        viewModel = ReportViewModel(fileRepository, authSession)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun fetchFiles_success_updatesFilteredFiles() = runTest {
        val mockData = listOf(
            mapOf("fileName" to "meeting1.mp3", "status" to "processed", "timestamp_clientUpload" to Timestamp(1000, 0)),
            mapOf("fileName" to "meeting2.mp3", "status" to "processing", "timestamp_clientUpload" to Timestamp(2000, 0))
        )

        every { fileRepository.getUserFiles("user123", any(), any()) } answers {
            val onUpdate = secondArg<(List<Map<String, Any>>) -> Unit>()
            onUpdate(mockData)
            listenerRegistration
        }

        viewModel.fetchFiles()

        viewModel.filteredFiles.test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("meeting1", items[0].first)
            assertEquals("processed", items[0].third)
        }
    }

    @Test
    fun fetchFiles_error_updatesUiStateToError() = runTest {
        val errorMsg = "Failed to fetch files"
        every { fileRepository.getUserFiles("user123", any(), any()) } answers {
            val onError = thirdArg<(Exception) -> Unit>()
            onError(Exception(errorMsg))
            listenerRegistration
        }

        viewModel.fetchFiles()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is ReportViewModel.ReportUiState.Error)
            assertEquals(errorMsg, (state as ReportViewModel.ReportUiState.Error).message)
        }
    }

    @Test
    fun filteredFiles_queryApplied_filtersCorrectly() = runTest {
        val mockData = listOf(
            mapOf("fileName" to "weekly_sync.mp3", "status" to "processed", "timestamp_clientUpload" to Timestamp(1000, 0)),
            mapOf("fileName" to "monthly_review.mp3", "status" to "processed", "timestamp_clientUpload" to Timestamp(2000, 0))
        )

        every { fileRepository.getUserFiles("user123", any(), any()) } answers {
            val onUpdate = secondArg<(List<Map<String, Any>>) -> Unit>()
            onUpdate(mockData)
            listenerRegistration
        }

        viewModel.fetchFiles()

        viewModel.setQuery("weekly")

        viewModel.filteredFiles.test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("weekly_sync", items[0].first)
        }
    }

    @Test
    fun deleteFiles_success_setsStateToIdle() = runTest {
        val fileNames = listOf("file1.mp3", "file2.mp3")
        
        every { fileRepository.deleteFile("user123", any(), any(), any()) } answers {
            val onSuccess = thirdArg<() -> Unit>()
            onSuccess()
        }

        viewModel.deleteFiles(fileNames)

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is ReportViewModel.ReportUiState.Idle)
        }

        verify(exactly = 2) { fileRepository.deleteFile("user123", any(), any(), any()) }
    }

    @Test
    fun renameFile_success_setsStateToIdle() = runTest {
        every { fileRepository.renameFile("user123", "old.mp3", "new.mp3", any(), any()) } answers {
            val onSuccess = args[3] as () -> Unit
            onSuccess()
        }

        viewModel.renameFile("old.mp3", "new.mp3")

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is ReportViewModel.ReportUiState.Idle)
        }
    }

    @Test
    fun copyFile_success_setsStateToIdle() = runTest {
        every { fileRepository.copyFile("user123", "old.mp3", "new.mp3", any(), any()) } answers {
            val onSuccess = args[3] as () -> Unit
            onSuccess()
        }
        every { fileRepository.updateFileContent("user123", "new.mp3", mapOf("isCopy" to true), any(), any()) } answers {
            val onSuccess = args[3] as () -> Unit
            onSuccess()
        }

        viewModel.copyFile("old.mp3", "new.mp3")

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is ReportViewModel.ReportUiState.Idle)
        }
    }

    @Test
    fun fetchFileDetails_success_emitsEventAndSetsStateToIdle() = runTest {
        val fileDetails = mapOf("Response" to "**Summary** of meeting")
        every { fileRepository.getFileDetails("user123", "file.mp3", any(), any()) } answers {
            val onSuccess = thirdArg<(Map<String, Any>?) -> Unit>()
            onSuccess(fileDetails)
        }

        viewModel.reportEvent.test {
            viewModel.fetchFileDetails("file.mp3")
            val event = awaitItem().getContentIfNotHandled()
            assertNotNull(event)
            assertTrue(event is ReportViewModel.ReportEvent.FetchDetailsSuccess)
            assertEquals("Summary of meeting", (event as ReportViewModel.ReportEvent.FetchDetailsSuccess).content)
        }

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is ReportViewModel.ReportUiState.Idle)
        }
    }
}
