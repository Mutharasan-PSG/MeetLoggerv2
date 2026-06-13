package com.example.meetloggerv2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.meetloggerv2.core.R
import com.example.meetloggerv2.data.remote.FcmTokenRequest
import com.example.meetloggerv2.data.remote.RetrofitClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * FCM service that handles incoming push notifications and token refresh events.
 *
 * - Receives data messages from the server when audio processing completes or fails.
 * - Automatically re-registers the FCM token when it is rotated by Firebase.
 */
class MeetLoggerMessagingService : FirebaseMessagingService() {

    private val TAG = "FCMService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "meetlogger_processing"
        const val CHANNEL_NAME = "Processing Alerts"

        /**
         * Registers the current FCM token with the backend server.
         * Called from MeetLoggerApp on startup and from onNewToken on refresh.
         */
        fun registerTokenWithServer(token: String) {
            val user = FirebaseAuth.getInstance().currentUser ?: return
            val userId = user.uid

            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val idToken = user.getIdToken(false).await().token ?: return@launch
                    val deviceId = Settings.Secure.getString(
                        com.example.meetloggerv2.MeetLoggerApp.appContext.contentResolver,
                        Settings.Secure.ANDROID_ID
                    )
                    val response = RetrofitClient.apiService.registerFcmToken(
                        "Bearer $idToken",
                        FcmTokenRequest(userId, token, deviceId)
                    )
                    if (response.isSuccessful) {
                        Log.d("FCMService", "FCM token registered with server successfully.")
                    } else {
                        Log.e("FCMService", "Failed to register FCM token: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e("FCMService", "Error registering FCM token: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Called when the FCM token is rotated. Re-registers with the backend.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed.")
        registerTokenWithServer(token)
    }

    /**
     * Called when a data message is received from the server.
     * This fires even if the app is in the background (data-only messages).
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received: ${message.data}")

        val title = message.data["title"] ?: "MeetLogger"
        val body = message.data["body"] ?: "You have a new update."
        val fileName = message.data["fileName"] ?: ""
        val type = message.data["type"] ?: "processing_complete"

        showNotification(title, body, fileName, type)
    }

    private fun showNotification(title: String, body: String, fileName: String, type: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel (required for Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when your audio processing completes or fails"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent to open the app when notification is tapped
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openFile", fileName)
            putExtra("notificationType", type)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, fileName.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val icon = if (type == "processing_failed") {
            R.drawable.launchlogo // Use your app icon
        } else {
            R.drawable.launchlogo
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()

        val notificationId = fileName.hashCode()
        notificationManager.notify(notificationId, notification)
    }
}
