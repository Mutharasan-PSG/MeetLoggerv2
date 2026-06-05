package com.example.meetloggerv2.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp

@Entity(tableName = "local_files")
data class LocalFileEntity(
    @PrimaryKey val fileName: String,
    val userId: String,
    val audioUrl: String?,
    val status: String,
    val originalLanguage: String?,
    val timestampMillis: Long,
    val followUpFileName: String?,
    val isCopy: Boolean,
    val notification: String?,
    val response: String?
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "fileName" to fileName,
            "status" to status,
            "isCopy" to isCopy,
            "timestamp_clientUpload" to Timestamp(timestampMillis / 1000, ((timestampMillis % 1000) * 1_000_000).toInt())
        )
        audioUrl?.let { map["audioUrl"] = it }
        originalLanguage?.let { map["OriginalLanguage"] = it }
        followUpFileName?.let { map["followUpFileName"] = it }
        notification?.let { map["Notification"] = it }
        response?.let { map["Response"] = it }
        return map
    }

    companion object {
        fun fromMap(userId: String, data: Map<String, Any>): LocalFileEntity {
            val fileName = data["fileName"] as? String ?: (data["id"] as? String) ?: ""
            val audioUrl = data["audioUrl"] as? String ?: data["AudioLink"] as? String
            val status = data["status"] as? String ?: "processing"
            val originalLanguage = data["OriginalLanguage"] as? String
            val timestamp = data["timestamp_clientUpload"] as? Timestamp ?: Timestamp(0, 0)
            val followUpFileName = data["followUpFileName"] as? String
            val isCopy = data["isCopy"] as? Boolean ?: false
            val notification = data["Notification"] as? String
            val response = data["Response"] as? String

            return LocalFileEntity(
                fileName = fileName,
                userId = userId,
                audioUrl = audioUrl,
                status = status,
                originalLanguage = originalLanguage,
                timestampMillis = timestamp.seconds * 1000 + timestamp.nanoseconds / 1_000_000,
                followUpFileName = followUpFileName,
                isCopy = isCopy,
                notification = notification,
                response = response
            )
        }
    }
}
