package com.meetloggerv2.core.util

import android.util.Log

/**
 * Centralized logging utility for the application.
 * Prepend a global app prefix and provides safe switches for release builds.
 */
object AppLogger {
    private const val GLOBAL_TAG = "MeetLogger"
    
    // Switch logging levels easily (can be bound to BuildConfig.DEBUG in production)
    private var isDebugEnabled = true

    fun setDebugEnabled(enabled: Boolean) {
        isDebugEnabled = enabled
    }

    fun d(tag: String, message: String) {
        if (isDebugEnabled) {
            Log.d("$GLOBAL_TAG:$tag", message)
        }
    }

    fun i(tag: String, message: String) {
        Log.i("$GLOBAL_TAG:$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w("$GLOBAL_TAG:$tag", message, throwable)
        } else {
            Log.w("$GLOBAL_TAG:$tag", message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$GLOBAL_TAG:$tag", message, throwable)
        } else {
            Log.e("$GLOBAL_TAG:$tag", message)
        }
    }
}
