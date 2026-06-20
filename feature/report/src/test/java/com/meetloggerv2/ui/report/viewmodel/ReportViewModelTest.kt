package com.meetloggerv2.ui.report.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.meetloggerv2.MainDispatcherRule
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.data.repository.IFileRepository
import com.meetloggerv2.core.network.NetworkResult
import com.google.firebase.Timestamp
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val filesFlow = MutableStateFlow<List<Map<String, Any>>>(emptyList())

    private lateinit var viewModel: ReportViewModel

    @Before
    fun setUp() {
        every { authSession.currentUserId() } returns "user123"
        every { fileRepository.getFilesFlow("user123") } returns filesFlow
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

        coEvery { fileRepository.listFilesFromBackend("user123") } returns NetworkResult.Success(mockData)

        // Emit local DB files flow
        filesFlow.value = mockData

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
        coEvery { fileRepository.listFilesFromBackend("user123") } returns NetworkResult.Error(errorMsg)

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

        filesFlow.value = mockData

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
        
        coEvery { fileRepository.deleteFileOnBackend("user123", any(), "file") } returns NetworkResult.Success(Unit)

        viewModel.deleteFiles(fileNames)

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is ReportViewModel.ReportUiState.Idle)
        }

        coVerify(exactly = 2) { fileRepository.deleteFileOnBackend("user123", any(), "file") }
    }

    @Test
    fun renameFile_success_setsStateToIdle() = runTest {
        coEvery { fileRepository.renameFileOnBackend("user123", "old.mp3", "new.mp3", "file") } returns NetworkResult.Success(Unit)

        viewModel.renameFile("old.mp3", "new.mp3")

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is ReportViewModel.ReportUiState.Idle)
        }
    }

    @Test
    fun copyFile_success_setsStateToIdle() = runTest {
        coEvery { fileRepository.copyFileOnBackend("user123", "old.mp3", "new.mp3") } returns NetworkResult.Success("new.mp3")

        viewModel.copyFile("old.mp3", "new.mp3")

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is ReportViewModel.ReportUiState.Idle)
        }
    }

    @Test
    fun fetchFileDetails_success_emitsEventAndSetsStateToIdle() = runTest {
        val fileDetails = mapOf("Response" to "**Summary** of meeting")
        coEvery { fileRepository.getFileDetailsFromBackend("user123", "file.mp3") } returns NetworkResult.Success(fileDetails)

        viewModel.reportEvent.test {
            viewModel.fetchFileDetails("file.mp3")
            val event = awaitItem().getContentIfNotHandled()
            assertNotNull(event)
            assertTrue(event is ReportViewModel.ReportEvent.FetchDetailsSuccess)
            assertEquals("**Summary** of meeting", (event as ReportViewModel.ReportEvent.FetchDetailsSuccess).content)
        }

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is ReportViewModel.ReportUiState.Idle)
        }
    }
}
