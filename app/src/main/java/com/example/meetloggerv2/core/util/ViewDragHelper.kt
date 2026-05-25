package com.example.meetloggerv2.core.util

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class ViewDragHelper(
    private val clickTolerancePx: Float = 10f
) {
    private var initialX = 0f
    private var initialY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    @SuppressLint("ClickableViewAccessibility")
    fun attach(view: View) {
        view.setOnTouchListener { target, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = target.x
                    initialY = target.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    target.x = initialX + (event.rawX - initialTouchX)
                    target.y = initialY + (event.rawY - initialTouchY)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (
                        abs(event.rawX - initialTouchX) < clickTolerancePx &&
                        abs(event.rawY - initialTouchY) < clickTolerancePx
                    ) {
                        target.performClick()
                    }
                    true
                }

                else -> false
            }
        }
    }
}
