package com.example.meetloggerv2

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import java.util.Calendar

class NotificationListener : Application() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val TAG = "NotificationListener"

    override fun onCreate() {
        super.onCreate()
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        setupNotificationListener()
    }

    private fun setupNotificationListener() {
        val userId = auth.currentUser?.uid ?: return
        val userFilesRef = firestore.collection("ProcessedDocs").document(userId).collection("UserFiles")

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val sevenDaysAgo = Timestamp(calendar.time)

        userFilesRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Snapshot listener error: ${error.message}", error)
                return@addSnapshotListener
            }

            snapshot?.documents?.forEach { document ->
                val fileName = document.getString("fileName") ?: return@forEach
                val status = document.getString("status") ?: "processing"
                val notificationStatus = document.getString("Notification") ?: "Off"
                val timestamp = document.getTimestamp("timestamp_clientUpload") ?: return@forEach

                if (timestamp.toDate().after(sevenDaysAgo.toDate()) || timestamp.toDate() == sevenDaysAgo.toDate()) {
                    if (status.equals("processed", ignoreCase = true) && notificationStatus.equals("On", ignoreCase = true)) {
                        triggerNotification(fileName)
                        updateNotificationStatus(document.id)
                    }
                }
            }
        }
    }

    private fun triggerNotification(fileName: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "file_notification_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "File Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.launchlogo)
            .setContentTitle("File Processed")
            .setContentText("$fileName documented successfully, check it out!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val uniqueId = fileName.hashCode()
        notificationManager.notify(uniqueId, notification)
    }

    private fun updateNotificationStatus(documentId: String) {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("ProcessedDocs").document(userId)
            .collection("UserFiles").document(documentId)
            .update("Notification", "Off")
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update notification status: ${e.message}", e)
            }
    }
}