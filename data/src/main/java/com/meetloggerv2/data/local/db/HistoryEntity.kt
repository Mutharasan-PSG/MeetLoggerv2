package com.meetloggerv2.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp

/**
 * Local cache of the user's Home activity history — a separate track from
 * [LocalFileEntity]. It mirrors the backend `History` collection, which is
 * retained regardless of rename/delete/copy on other pages, so the Home list is
 * independent of the Report/Audio file documents.
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val fileName: String,
    val userId: String,
    val status: String,
    val timestampMillis: Long
) {
    fun toMap(): Map<String, Any> = mapOf(
        "fileName" to fileName,
        "status" to status,
        "timestamp_clientUpload" to Timestamp(
            timestampMillis / 1000,
            ((timestampMillis % 1000) * 1_000_000).toInt()
        )
    )

    companion object {
        fun fromMap(userId: String, data: Map<String, Any>): HistoryEntity {
            // Reuse LocalFileEntity's robust timestamp parsing to stay consistent.
            val parsed = LocalFileEntity.fromMap(userId, data)
            return HistoryEntity(
                fileName = parsed.fileName,
                userId = userId,
                status = parsed.status,
                timestampMillis = parsed.timestampMillis
            )
        }
    }
}
