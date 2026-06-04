package com.example.meetloggerv2.ui.login.activity

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.meetloggerv2.R
import com.example.meetloggerv2.ui.login.fragment.TermsPolicyBottomSheetFragment

class IntroActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro)

        setupListeners()
        setupPrivacyPolicyText()
    }

    private fun setupListeners() {
        val getStartedButton = findViewById<Button>(R.id.buttonGetStarted)
        getStartedButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun setupPrivacyPolicyText() {
        val privacyTextView = findViewById<TextView>(R.id.privacy)
        com.example.meetloggerv2.core.util.UIUtils.setupPrivacyPolicyText(
            this,
            privacyTextView,
            { showPolicyDialog("terms") },
            { showPolicyDialog("policy") }
        )
    }

    private fun showPolicyDialog(type: String) {
        val bottomSheet = TermsPolicyBottomSheetFragment.newInstance(type)
        bottomSheet.show(supportFragmentManager, "TermsPolicyBottomSheet")
    }
}
