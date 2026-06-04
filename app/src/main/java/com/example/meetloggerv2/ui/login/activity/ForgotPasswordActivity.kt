package com.example.meetloggerv2.ui.login.activity

import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.meetloggerv2.R
import com.example.meetloggerv2.core.network.NetworkUtil
import com.example.meetloggerv2.ui.login.viewmodel.LoginViewModel

import android.text.Editable
import android.text.TextWatcher
import com.google.android.material.textfield.TextInputLayout
import com.example.meetloggerv2.core.util.clearErrorOnTextChanged

class ForgotPasswordActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewModel.resetPasswordState.observe(this) { state ->
            when (state) {
                is LoginViewModel.ResetPasswordState.Loading -> {
                    Toast.makeText(this, "Sending reset link...", Toast.LENGTH_SHORT).show()
                }
                is LoginViewModel.ResetPasswordState.Success -> {
                    Toast.makeText(this, getString(R.string.toast_password_reset_sent), Toast.LENGTH_LONG).show()
                    finish()
                }
                is LoginViewModel.ResetPasswordState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                is LoginViewModel.ResetPasswordState.Idle -> {
                    // Do nothing
                }
            }
        }
    }

    private fun setupListeners() {
        val emailEditText = findViewById<EditText>(R.id.editTextEmail)
        val sendResetButton = findViewById<Button>(R.id.buttonSendReset)
        val backToLoginText = findViewById<TextView>(R.id.textViewBackToLogin)

        val emailInputLayout = findViewById<TextInputLayout>(R.id.emailInputLayout)
        emailInputLayout.clearErrorOnTextChanged()

        sendResetButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()

            if (validateInput(email)) {
                if (NetworkUtil.isNetworkAvailable(this)) {
                    viewModel.sendPasswordReset(email)
                } else {
                    Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
                }
            }
        }

        backToLoginText.setOnClickListener {
            finish()
        }
    }

    private fun validateInput(email: String): Boolean {
        val emailInputLayout = findViewById<TextInputLayout>(R.id.emailInputLayout)

        if (email.isEmpty()) {
            emailInputLayout.error = "Please enter your email address"
            Toast.makeText(this, "Please enter your email address", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.error = "Please enter a valid email address"
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
}
