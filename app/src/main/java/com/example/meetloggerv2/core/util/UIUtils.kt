package com.example.meetloggerv2.core.util

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.TypefaceSpan
import androidx.core.content.res.ResourcesCompat
import com.example.meetloggerv2.R
import com.google.android.material.bottomnavigation.BottomNavigationView

object UIUtils {

    fun applyPoppinsFontToBottomNav(context: Context, bottomNavBar: BottomNavigationView) {
        val poppinsFont = ResourcesCompat.getFont(context, R.font.poppins_medium) ?: return
        val menu = bottomNavBar.menu
        for (i in 0 until menu.size()) {
            val menuItem = menu.getItem(i)
            val spannableTitle = SpannableString(menuItem.title)
            spannableTitle.setSpan(
                CustomTypefaceSpan("", poppinsFont),
                0,
                spannableTitle.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            menuItem.title = spannableTitle
        }
    }

    class CustomTypefaceSpan(family: String, private val typeface: Typeface) : TypefaceSpan(family) {
        override fun updateDrawState(ds: TextPaint) {
            applyCustomTypeface(ds, typeface)
        }

        override fun updateMeasureState(paint: TextPaint) {
            applyCustomTypeface(paint, typeface)
        }

        private fun applyCustomTypeface(paint: TextPaint, tf: Typeface) {
            paint.typeface = tf
        }
    }
}
