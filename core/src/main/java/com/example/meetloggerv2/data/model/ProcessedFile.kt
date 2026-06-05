package com.example.meetloggerv2.data.model

import com.google.firebase.Timestamp

data class ProcessedFile(
    val fileName: String,
    val status: String,
    val timestamp: Timestamp,
    val isCopy: Boolean
) {
    companion object {
        fun fromMap(data: Map<String, Any>): ProcessedFile? {
            val fileName = data["fileName"] as? String ?: return null
            val status = data["status"] as? String ?: "processing"
            val timestamp = data["timestamp_clientUpload"] as? Timestamp ?: Timestamp(0, 0)
            val isCopy = data["isCopy"] as? Boolean ?: false

            return ProcessedFile(
                fileName = fileName,
                status = status,
                timestamp = timestamp,
                isCopy = isCopy,
            )
        }
    }
}
