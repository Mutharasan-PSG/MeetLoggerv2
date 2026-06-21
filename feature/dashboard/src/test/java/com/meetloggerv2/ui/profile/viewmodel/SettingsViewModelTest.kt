package com.meetloggerv2.ui.profile.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.meetloggerv2.MainDispatcherRule
import com.meetloggerv2.core.network.NetworkResult
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.data.local.SettingsDataStore
import com.meetloggerv2.data.repository.IFileRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
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
class SettingsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application = mockk<Application>(relaxed = true)
    private val fileRepository = mockk<IFileRepository>(relaxed = true)
    private val authSession = mockk<AuthSession>(relaxed = true)
    private val googleSignInClient = mockk<GoogleSignInClient>(relaxed = true)

    private val themeModeFlow = MutableStateFlow(0)
    private val autoSendFlow = MutableStateFlow(false)
    private val recordingQualityFlow = MutableStateFlow("High")
    private val biometricLockFlow = MutableStateFlow(false)

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.isEmpty(any()) } answers {
            val s = firstArg<CharSequence?>()
            s == null || s.length == 0
        }

        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        mockkConstructor(SettingsDataStore::class)
        every { anyConstructed<SettingsDataStore>().themeMode } returns themeModeFlow
        every { anyConstructed<SettingsDataStore>().autoSendEmail } returns autoSendFlow
        every { anyConstructed<SettingsDataStore>().recordingQuality } returns recordingQualityFlow
        every { anyConstructed<SettingsDataStore>().biometricLock } returns biometricLockFlow

        coEvery { anyConstructed<SettingsDataStore>().setThemeMode(any()) } just Runs
        coEvery { anyConstructed<SettingsDataStore>().setAutoSendEmail(any()) } just Runs
        coEvery { anyConstructed<SettingsDataStore>().setRecordingQuality(any()) } just Runs
        coEvery { anyConstructed<SettingsDataStore>().setBiometricLock(any()) } just Runs

        mockkStatic(GoogleSignIn::class)
        every { GoogleSignIn.getClient(any<android.content.Context>(), any<GoogleSignInOptions>()) } returns googleSignInClient

        viewModel = SettingsViewModel(application, fileRepository, authSession)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun flowObservables_emitsCorrectDataStoreValues() = runTest {
        viewModel.themeMode.test {
            assertEquals(0, awaitItem())
            themeModeFlow.value = 2
            assertEquals(2, awaitItem())
        }

        viewModel.recordingQuality.test {
            assertEquals("High", awaitItem())
            recordingQualityFlow.value = "Medium"
            assertEquals("Medium", awaitItem())
        }
    }

    @Test
    fun setThemeMode_callsDataStore() = runTest {
        viewModel.setThemeMode(1)
        coVerify { anyConstructed<SettingsDataStore>().setThemeMode(1) }
    }

    @Test
    fun setAutoSendEmail_callsDataStore() = runTest {
        viewModel.setAutoSendEmail(true)
        coVerify { anyConstructed<SettingsDataStore>().setAutoSendEmail(true) }
    }

    @Test
    fun deleteAccount_success_setsDeleteAccountSuccessState() = runTest {
        coEvery { fileRepository.deleteUserAccountFromBackend("user123") } returns NetworkResult.Success(Unit)
        val mockTask = mockk<Task<Void>>(relaxed = true)
        every { googleSignInClient.signOut() } returns mockTask
        
        val listenerSlot = slot<com.google.android.gms.tasks.OnCompleteListener<Void>>()
        every { mockTask.addOnCompleteListener(capture(listenerSlot)) } answers {
            listenerSlot.captured.onComplete(mockTask)
            mockTask
        }

        viewModel.deleteAccount("user123")

        viewModel.deleteAccountState.test {
            assertEquals(SettingsViewModel.DeleteAccountState.Success, awaitItem())
        }

        coVerify { authSession.signOut() }
    }

    @Test
    fun deleteAccount_error_setsDeleteAccountErrorState() = runTest {
        val errorMsg = "Deletion denied by backend"
        coEvery { fileRepository.deleteUserAccountFromBackend("user123") } returns NetworkResult.Error(errorMsg)

        viewModel.deleteAccount("user123")

        viewModel.deleteAccountState.test {
            val state = awaitItem()
            assertTrue(state is SettingsViewModel.DeleteAccountState.Error)
            assertEquals(errorMsg, (state as SettingsViewModel.DeleteAccountState.Error).message)
        }
    }
}
