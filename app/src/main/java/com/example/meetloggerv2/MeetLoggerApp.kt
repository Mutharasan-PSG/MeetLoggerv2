package com.example.meetloggerv2

import com.example.meetloggerv2.data.repository.IFileRepository
import com.example.meetloggerv2.data.repository.IAuthRepository
import com.example.meetloggerv2.data.local.SettingsDataStore
import com.example.meetloggerv2.core.R
import com.example.meetloggerv2.core.theme.ThemeManager
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltAndroidApp
class MeetLoggerApp : Application() {

    @Inject lateinit var fileRepository: IFileRepository
    @Inject lateinit var authRepository: IAuthRepository
    @Inject lateinit var settingsDataStore: SettingsDataStore
    private var listenerRegistration: ListenerRegistration? = null
    private val TAG = "MeetLoggerApp"
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        observeThemeSettings()
        setupNotificationListener()
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

    private fun setupNotificationListener() {
        val userId = authRepository.getCurrentUser()?.uid ?: return
        
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val sevenDaysAgo = Timestamp(calendar.time)

        listenerRegistration = fileRepository.getUserFiles(userId, { dataList ->
            dataList.forEach { data ->
                val fileName = data["fileName"] as? String ?: return@forEach
                val status = data["status"] as? String ?: "processing"
                val notificationStatus = data["Notification"] as? String ?: "Off"
                val timestamp = data["timestamp_clientUpload"] as? Timestamp ?: return@forEach

                if (timestamp.toDate().after(sevenDaysAgo.toDate()) || timestamp.toDate() == sevenDaysAgo.toDate()) {
                    if (status.equals("processed", ignoreCase = true) && notificationStatus.equals("On", ignoreCase = true)) {
                        triggerNotification(fileName)
                        updateNotificationStatus(data["id"] as? String ?: fileName) // Assuming ID is fileName or from doc
                    }
                }
            }
        }, {
            Log.e(TAG, "Snapshot listener error: ${it.message}", it)
        })
    }

    private fun triggerNotification(fileName: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "file_notification_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.launchlogo)
            .setContentTitle(getString(R.string.notif_title_processed))
            .setContentText(getString(R.string.notif_content_processed, fileName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val uniqueId = fileName.hashCode()
        notificationManager.notify(uniqueId, notification)
    }

    private fun updateNotificationStatus(documentId: String) {
        val userId = authRepository.getCurrentUser()?.uid ?: return
        fileRepository.updateFileContent(userId, documentId, mapOf("Notification" to "Off"), {}, {
            Log.e(TAG, "Failed to update notification status: ${it.message}", it)
        })
    }

    override fun onTerminate() {
        super.onTerminate()
        listenerRegistration?.remove()
    }
}
