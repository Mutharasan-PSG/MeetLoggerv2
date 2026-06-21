package com.meetloggerv2.data.work

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.meetloggerv2.MainDispatcherRule
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.data.repository.IAudioRepository
import com.meetloggerv2.data.repository.IFileRepository
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AudioUploadWorkerTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context = mockk<Context>(relaxed = true)
    private val workerParams = mockk<WorkerParameters>(relaxed = true)
    private val audioRepository = mockk<IAudioRepository>(relaxed = true)
    private val fileRepository = mockk<IFileRepository>(relaxed = true)

    private lateinit var tempFile: File

    @Before
    fun setUp() {
        tempFile = File.createTempFile("test_audio", ".mp3")

        val progressUpdater = mockk<androidx.work.ProgressUpdater>()
        val mockFuture = mockk<com.google.common.util.concurrent.ListenableFuture<Void>>()
        every { mockFuture.isDone } returns true
        every { mockFuture.get() } returns null
        every { workerParams.progressUpdater } returns progressUpdater
        every { progressUpdater.updateProgress(any(), any(), any()) } returns mockFuture

        mockkStatic(EntryPointAccessors::class)
        val entryPoint = mockk<AudioUploadWorker.WorkerEntryPoint>()
        every { EntryPointAccessors.fromApplication(any(), AudioUploadWorker.WorkerEntryPoint::class.java) } returns entryPoint
        every { entryPoint.audioRepository() } returns audioRepository
        every { entryPoint.fileRepository() } returns fileRepository
    }

    @After
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
        unmockkAll()
    }

    @Test
    fun doWork_saveActionSuccess_enqueuesUploadAndSavesMetadata() = runTest {
        val inputData = workDataOf(
            AudioUploadWorker.KEY_USER_ID to "user123",
            AudioUploadWorker.KEY_FILE_PATH to tempFile.absolutePath,
            AudioUploadWorker.KEY_FILE_NAME to "meeting.mp3",
            AudioUploadWorker.KEY_ACTION to AudioUploadWorker.ACTION_SAVE
        )
        every { workerParams.inputData } returns inputData

        coEvery { fileRepository.insertLocalHistory("user123", "meeting.mp3", "saved") } just Runs
        coEvery { audioRepository.getUploadUrl("user123", "meeting.mp3") } returns NetworkResult.Success("https://example.com/upload")
        coEvery { audioRepository.uploadToSignedUrl("https://example.com/upload", any()) } returns NetworkResult.Success(Unit)
        coEvery { fileRepository.saveAsNewCopyOnBackend("user123", "meeting.mp3", any()) } returns NetworkResult.Success("meeting.mp3")
        coEvery { fileRepository.listHistoryFromBackend("user123") } returns NetworkResult.Success(emptyList())

        val worker = AudioUploadWorker(context, workerParams)
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertEquals("meeting.mp3", output.getString(AudioUploadWorker.KEY_FILE_NAME))
        assertEquals(AudioUploadWorker.ACTION_SAVE, output.getString(AudioUploadWorker.KEY_ACTION))

        coVerify { fileRepository.insertLocalHistory("user123", "meeting.mp3", "saved") }
        coVerify { audioRepository.getUploadUrl("user123", "meeting.mp3") }
        coVerify { audioRepository.uploadToSignedUrl("https://example.com/upload", any()) }
        coVerify { fileRepository.saveAsNewCopyOnBackend("user123", "meeting.mp3", any()) }
    }

    @Test
    fun doWork_saveActionDuplicateName_deDuplicatesAndResyncsHistory() = runTest {
        val inputData = workDataOf(
            AudioUploadWorker.KEY_USER_ID to "user123",
            AudioUploadWorker.KEY_FILE_PATH to tempFile.absolutePath,
            AudioUploadWorker.KEY_FILE_NAME to "meeting.mp3",
            AudioUploadWorker.KEY_ACTION to AudioUploadWorker.ACTION_SAVE
        )
        every { workerParams.inputData } returns inputData

        coEvery { fileRepository.insertLocalHistory("user123", "meeting.mp3", "saved") } just Runs
        coEvery { audioRepository.getUploadUrl("user123", "meeting.mp3") } returns NetworkResult.Success("https://example.com/upload")
        coEvery { audioRepository.uploadToSignedUrl("https://example.com/upload", any()) } returns NetworkResult.Success(Unit)
        
        // Return de-duplicated name: "meeting (1).mp3"
        coEvery { fileRepository.saveAsNewCopyOnBackend("user123", "meeting.mp3", any()) } returns NetworkResult.Success("meeting (1).mp3")
        coEvery { fileRepository.removeLocalHistory("user123", "meeting.mp3") } just Runs
        coEvery { fileRepository.listHistoryFromBackend("user123") } returns NetworkResult.Success(emptyList())

        val worker = AudioUploadWorker(context, workerParams)
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertEquals("meeting (1).mp3", output.getString(AudioUploadWorker.KEY_FILE_NAME))

        coVerify { fileRepository.removeLocalHistory("user123", "meeting.mp3") }
        coVerify { fileRepository.listHistoryFromBackend("user123") }
    }
}
