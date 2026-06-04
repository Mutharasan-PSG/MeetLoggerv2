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
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.text.Editable
import android.text.TextWatcher
import com.google.android.material.textfield.TextInputLayout
import com.example.meetloggerv2.core.util.clearErrorOnTextChanged

class SignUpActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewModel.signUpState.observe(this) { state ->
            when (state) {
                is LoginViewModel.SignUpState.Loading -> {
                    Toast.makeText(this, "Creating account...", Toast.LENGTH_SHORT).show()
                }
                is LoginViewModel.SignUpState.Success -> {
                    val email = findViewById<EditText>(R.id.editTextEmail).text.toString().trim()
                    showSignUpSuccessDialog(email)
                }
                is LoginViewModel.SignUpState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                is LoginViewModel.SignUpState.Idle -> {
                    // Do nothing
                }
            }
        }
    }

    private fun setupListeners() {
        val nameEditText = findViewById<EditText>(R.id.editTextName)
        val emailEditText = findViewById<EditText>(R.id.editTextEmail)
        val passwordEditText = findViewById<EditText>(R.id.editTextPassword)
        val confirmPasswordEditText = findViewById<EditText>(R.id.editTextConfirmPassword)
        val signUpButton = findViewById<Button>(R.id.buttonSignUp)
        val signInText = findViewById<TextView>(R.id.textViewSignIn)

        val nameInputLayout = findViewById<TextInputLayout>(R.id.nameInputLayout)
        val emailInputLayout = findViewById<TextInputLayout>(R.id.emailInputLayout)
        val passwordInputLayout = findViewById<TextInputLayout>(R.id.passwordInputLayout)
        val confirmPasswordInputLayout = findViewById<TextInputLayout>(R.id.confirmPasswordInputLayout)

        nameInputLayout.clearErrorOnTextChanged()
        emailInputLayout.clearErrorOnTextChanged()
        passwordInputLayout.clearErrorOnTextChanged()
        confirmPasswordInputLayout.clearErrorOnTextChanged()

        signUpButton.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()

            if (validateInputs(name, email, password, confirmPassword)) {
                if (NetworkUtil.isNetworkAvailable(this)) {
                    viewModel.signUpWithEmail(name, email, password)
                } else {
                    Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
                }
            }
        }

        signInText.setOnClickListener {
            finish()
        }
    }

    private fun validateInputs(name: String, email: String, password: String, confirmPassword: String): Boolean {
        val nameInputLayout = findViewById<TextInputLayout>(R.id.nameInputLayout)
        val emailInputLayout = findViewById<TextInputLayout>(R.id.emailInputLayout)
        val passwordInputLayout = findViewById<TextInputLayout>(R.id.passwordInputLayout)
        val confirmPasswordInputLayout = findViewById<TextInputLayout>(R.id.confirmPasswordInputLayout)

        if (name.isEmpty()) {
            nameInputLayout.error = getString(R.string.error_name_required)
            Toast.makeText(this, getString(R.string.error_name_required), Toast.LENGTH_SHORT).show()
            return false
        }
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
        if (password.isEmpty()) {
            passwordInputLayout.error = "Please enter a password"
            Toast.makeText(this, "Please enter a password", Toast.LENGTH_SHORT).show()
            return false
        }
        val passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!_\\-]).{8,}$".toRegex()
        if (!password.matches(passwordRegex)) {
            passwordInputLayout.error = getString(R.string.error_password_rules)
            Toast.makeText(this, getString(R.string.error_password_rules), Toast.LENGTH_LONG).show()
            return false
        }
        if (confirmPassword.isEmpty()) {
            confirmPasswordInputLayout.error = "Please confirm your password"
            Toast.makeText(this, "Please confirm your password", Toast.LENGTH_SHORT).show()
            return false
        }
        if (password != confirmPassword) {
            confirmPasswordInputLayout.error = getString(R.string.error_passwords_do_not_match)
            Toast.makeText(this, getString(R.string.error_passwords_do_not_match), Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun showSignUpSuccessDialog(email: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirm_message, null)
        val messageTextView = dialogView.findViewById<TextView>(R.id.dialog_message)
        messageTextView.text = android.text.Html.fromHtml(getString(R.string.dialog_msg_signup_success, email), android.text.Html.FROM_HTML_MODE_LEGACY)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setTitle(getString(R.string.dialog_title_signup_success))
            .setPositiveButton("OK") { d, _ ->
                d.dismiss()
                finish()
            }
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background_rounded)
        dialog.show()
    }
}
