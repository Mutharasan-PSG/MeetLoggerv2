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
import android.widget.TextView
import android.text.style.ClickableSpan
import android.text.Spanned
import androidx.core.content.ContextCompat
import android.text.method.LinkMovementMethod
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.LayoutInflater
import android.view.View

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

    fun setupSearchViewClickToFocus(searchView: androidx.appcompat.widget.SearchView) {
        val searchAutoComplete = searchView.findViewById<androidx.appcompat.widget.SearchView.SearchAutoComplete>(androidx.appcompat.R.id.search_src_text)
        val clickListener = android.view.View.OnClickListener {
            searchView.isIconified = false
            searchAutoComplete?.requestFocus()
            val imm = searchView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(searchAutoComplete, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        searchView.setOnClickListener(clickListener)
        searchView.findViewById<android.view.View>(androidx.appcompat.R.id.search_plate)?.setOnClickListener(clickListener)
        searchView.findViewById<android.view.View>(androidx.appcompat.R.id.search_edit_frame)?.setOnClickListener(clickListener)
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

    fun setupPrivacyPolicyText(
        context: Context,
        textView: TextView,
        onTermsClicked: () -> Unit,
        onPrivacyPolicyClicked: () -> Unit
    ) {
        val privacyText = context.getString(R.string.privacy_policy_agreement)
        val spannableString = SpannableString(privacyText)
        val blue = ContextCompat.getColor(context, R.color.BLUE)

        val termsWord = "Terms"
        val termsIdx = privacyText.indexOf(termsWord)
        if (termsIdx != -1) {
            val termsSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    onTermsClicked()
                }
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = blue
                    ds.isUnderlineText = false
                }
            }
            spannableString.setSpan(termsSpan, termsIdx, termsIdx + termsWord.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val policyWord = "Privacy Policy"
        val policyIdx = privacyText.indexOf(policyWord)
        if (policyIdx != -1) {
            val policySpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    onPrivacyPolicyClicked()
                }
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = blue
                    ds.isUnderlineText = false
                }
            }
            spannableString.setSpan(policySpan, policyIdx, policyIdx + policyWord.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        textView.text = spannableString
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = android.graphics.Color.TRANSPARENT
    }

    fun showFormatSelectionDialog(context: Context, onFormatSelected: (format: String) -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_export_options, null)
        val dialog = MaterialAlertDialogBuilder(context).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.8f)
        
        dialogView.findViewById<LinearLayout>(R.id.pdfButtonLayout).setOnClickListener {
            dialog.dismiss()
            onFormatSelected("PDF")
        }
        dialogView.findViewById<LinearLayout>(R.id.docxButtonLayout).setOnClickListener {
            dialog.dismiss()
            onFormatSelected("DOCX")
        }
        dialog.show()
    }
}

fun com.google.android.material.textfield.TextInputLayout.clearErrorOnTextChanged() {
    val editText = this.editText ?: return
    editText.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            this@clearErrorOnTextChanged.error = null
            this@clearErrorOnTextChanged.isErrorEnabled = false
        }
        override fun afterTextChanged(s: android.text.Editable?) {}
    })
}
