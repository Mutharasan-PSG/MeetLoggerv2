package com.meetloggerv2.data.local.db

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
            
            val timestampRaw = data["timestamp_clientUpload"]
            val timestamp = when (timestampRaw) {
                is Timestamp -> timestampRaw
                is String -> {
                    try {
                        var normalized = timestampRaw
                        if (normalized.contains(".")) {
                            val dotIdx = normalized.indexOf('.')
                            val rest = normalized.substring(dotIdx + 1)
                            var offsetIdx = -1
                            for (i in rest.indices) {
                                val c = rest[i]
                                if (c == '+' || c == '-' || c == 'Z') {
                                    offsetIdx = i
                                    break
                                }
                            }
                            if (offsetIdx != -1) {
                                val fraction = rest.substring(0, offsetIdx)
                                val offset = rest.substring(offsetIdx)
                                normalized = normalized.substring(0, dotIdx) + "." + fraction.take(3) + offset
                            } else {
                                normalized = normalized.substring(0, dotIdx) + "." + rest.take(3)
                            }
                        }
                        
                        val hasOffset = normalized.endsWith("Z") || normalized.contains("+") || (normalized.lastIndexOf("-") > 10)
                        
                        val cleaned = if (normalized.endsWith("Z")) {
                            normalized.substring(0, normalized.length - 1) + "+00:00"
                        } else {
                            normalized
                        }
                        
                        val pattern = if (cleaned.contains(".")) {
                            "yyyy-MM-dd'T'HH:mm:ss.SSS" + (if (hasOffset) "XXX" else "")
                        } else {
                            "yyyy-MM-dd'T'HH:mm:ss" + (if (hasOffset) "XXX" else "")
                        }
                        
                        val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                        if (!hasOffset) {
                            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }
                        val date = sdf.parse(cleaned)
                        if (date != null) Timestamp(date) else Timestamp(0, 0)
                    } catch (e: Exception) {
                        try {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                                timeZone = java.util.TimeZone.getTimeZone("UTC")
                            }
                            val date = sdf.parse(timestampRaw)
                            if (date != null) Timestamp(date) else Timestamp(0, 0)
                        } catch (e2: Exception) {
                            Timestamp(0, 0)
                        }
                    }
                }
                else -> Timestamp(0, 0)
            }

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
