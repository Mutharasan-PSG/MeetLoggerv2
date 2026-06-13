package com.example.meetloggerv2

import com.example.meetloggerv2.data.repository.IFileRepository
import com.example.meetloggerv2.data.repository.IAuthRepository
import com.example.meetloggerv2.data.local.SettingsDataStore
import com.example.meetloggerv2.core.R
import com.example.meetloggerv2.core.theme.ThemeManager
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MeetLoggerApp : Application() {

    @Inject lateinit var fileRepository: IFileRepository
    @Inject lateinit var authRepository: IAuthRepository
    @Inject lateinit var settingsDataStore: SettingsDataStore
    private val TAG = "MeetLoggerApp"
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        lateinit var appContext: Context
            private set

        /**
         * Fetches the FCM token and registers it with the backend.
         * Safe to call from anywhere — returns early if user is not logged in.
         * Should be called:
         *   - At app startup (Application.onCreate)
         *   - After login completes (HomeActivity.onCreate)
         */
        fun initFcmToken() {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                Log.d("MeetLoggerApp", "initFcmToken: No user logged in, skipping.")
                return
            }

            Log.d("MeetLoggerApp", "initFcmToken: User ${user.uid} found, fetching FCM token...")
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    Log.d("MeetLoggerApp", "FCM token obtained: ${token.take(20)}...")
                    MeetLoggerMessagingService.registerTokenWithServer(token)
                }
                .addOnFailureListener { e ->
                    Log.e("MeetLoggerApp", "Failed to get FCM token: ${e.message}", e)
                }
        }
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        FirebaseApp.initializeApp(this)
        observeThemeSettings()
        // Try to register FCM at startup (works if user is already signed in)
        initFcmToken()
    }

    private fun observeThemeSettings() {
        applicationScope.launch {
            settingsDataStore.themeMode.collect { mode ->
                ThemeManager.setThemeMode(mode)
                
                // Sync with XML if necessary, though we use Compose mostly
                val nightMode = when (mode) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(nightMode)
            }
        }
    }
}
