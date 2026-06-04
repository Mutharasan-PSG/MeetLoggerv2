package com.example.meetloggerv2.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

object AvatarGenerator {
    fun getAvatar(context: Context, name: String, sizeDp: Int = 100): Drawable {
        val initials = getInitials(name)
        val size = (sizeDp * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Generate a background color based on name hash (Google-style)
        val colors = listOf(
            "#1abc9c", "#2ecc71", "#3498db", "#9b59b6", "#34495e",
            "#16a085", "#27ae60", "#2980b9", "#8e44ad", "#2c3e50",
            "#f1c40f", "#e67e22", "#e74c3c", "#95a5a6", "#f39c12",
            "#d35400", "#c0392b", "#7f8c8d"
        )
        val colorHex = colors[Math.abs(name.hashCode()) % colors.size]
        paint.color = Color.parseColor(colorHex)
        
        // Draw circle background
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // Draw text
        paint.color = Color.WHITE
        paint.textSize = size * 0.4f
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER

        val rect = Rect()
        paint.getTextBounds(initials, 0, initials.length, rect)
        val y = size / 2f - rect.exactCenterY()
        canvas.drawText(initials, size / 2f, y, paint)

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun getInitials(name: String): String {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return "?"
        
        val parts = cleanName.split("\\s+".toRegex())
        val firstChar = parts.first().firstOrNull()?.uppercaseChar() ?: '?'
        
        if (parts.size > 1) {
            val secondChar = parts[1].firstOrNull()?.uppercaseChar()
            if (secondChar != null) {
                return "$firstChar$secondChar"
            }
        }
        return firstChar.toString()
    }
}
