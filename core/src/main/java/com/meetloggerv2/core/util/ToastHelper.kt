package com.meetloggerv2.core.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Centralized utility for presenting Toast notifications.
 * Guarantees execution on the main UI thread to prevent thread crashes.
 */
object ToastHelper {
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Show a short-duration Toast message.
     */
    fun showShort(context: Context, message: String) {
        show(context, message, Toast.LENGTH_SHORT)
    }

    /**
     * Show a long-duration Toast message.
     */
    fun showLong(context: Context, message: String) {
        show(context, message, Toast.LENGTH_LONG)
    }

    private fun show(context: Context, message: String, duration: Int) {
        if (message.isBlank()) return

        val appCtx = context.applicationContext
        
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(appCtx, message, duration).show()
        } else {
            mainHandler.post {
                Toast.makeText(appCtx, message, duration).show()
            }
        }
    }
}
