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
