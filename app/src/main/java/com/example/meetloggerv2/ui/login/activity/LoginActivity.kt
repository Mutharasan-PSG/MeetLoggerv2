package com.example.meetloggerv2.ui.login.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.method.LinkMovementMethod
import android.text.TextPaint
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.meetloggerv2.R
import com.example.meetloggerv2.core.session.SessionManager
import com.example.meetloggerv2.data.model.User
import com.example.meetloggerv2.ui.login.viewmodel.LoginViewModel
import com.example.meetloggerv2.ui.main.activity.MainActivity
import com.example.meetloggerv2.core.network.NetworkUtil
import com.example.meetloggerv2.ui.login.fragment.TermsPolicyBottomSheetFragment
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.GoogleAuthProvider

import android.text.Editable
import android.text.TextWatcher
import com.google.android.material.textfield.TextInputLayout
import androidx.activity.OnBackPressedCallback
import com.example.meetloggerv2.core.util.clearErrorOnTextChanged

class LoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var signInResultLauncher: ActivityResultLauncher<Intent>
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setupObservers()
        setupListeners()
        setupPrivacyPolicyText()
        setupBackPressed()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resetStates()
    }

    private fun setupObservers() {
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginViewModel.LoginState.Loading -> {
                    Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show()
                }
                is LoginViewModel.LoginState.Success -> {
                    navigateToHome(state.user)
                }
                is LoginViewModel.LoginState.EmailNotVerified -> {
                    showEmailNotVerifiedDialog(state.message)
                }
                is LoginViewModel.LoginState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    Log.e("LoginActivity", "Login error: ${state.message}")
                }
                is LoginViewModel.LoginState.Idle -> {
                    // Do nothing
                }
            }
        }

        viewModel.resendVerificationState.observe(this) { state ->
            when (state) {
                is LoginViewModel.VerificationResendState.Loading -> {
                    Toast.makeText(this, "Resending verification email...", Toast.LENGTH_SHORT).show()
                }
                is LoginViewModel.VerificationResendState.Success -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                is LoginViewModel.VerificationResendState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                is LoginViewModel.VerificationResendState.Idle -> {
                    // Do nothing
                }
            }
        }

        signInResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    handleSignInResult(account)
                } catch (e: ApiException) {
                    Log.e("LoginActivity", "Google sign-in failed: ${e.message}")
                }
            }
        }
    }

    private fun setupListeners() {
        val emailEditText = findViewById<EditText>(R.id.editTextEmail)
        val passwordEditText = findViewById<EditText>(R.id.editTextPassword)
        val loginButton = findViewById<Button>(R.id.buttonLogin)
        val forgotPasswordText = findViewById<TextView>(R.id.textViewForgotPassword)
        val signUpText = findViewById<TextView>(R.id.textViewSignUp)
        val googleSignInButton = findViewById<LinearLayout>(R.id.btn_google_sign_in)

        val emailInputLayout = findViewById<TextInputLayout>(R.id.emailInputLayout)
        val passwordInputLayout = findViewById<TextInputLayout>(R.id.passwordInputLayout)

        emailInputLayout.clearErrorOnTextChanged()
        passwordInputLayout.clearErrorOnTextChanged()

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (validateInputs(email, password)) {
                if (NetworkUtil.isNetworkAvailable(this)) {
                    viewModel.signInWithEmail(email, password)
                } else {
                    Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
                }
            }
        }

        forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        signUpText.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        googleSignInButton.setOnClickListener {
            if (NetworkUtil.isNetworkAvailable(this)) {
                signInWithGoogle()
            } else {
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        val emailInputLayout = findViewById<TextInputLayout>(R.id.emailInputLayout)
        val passwordInputLayout = findViewById<TextInputLayout>(R.id.passwordInputLayout)

        if (email.isEmpty()) {
            emailInputLayout.error = "Please enter your email"
            Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.error = "Please enter a valid email address"
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return false
        }
        if (password.isEmpty()) {
            passwordInputLayout.error = "Please enter your password"
            Toast.makeText(this, "Please enter your password", Toast.LENGTH_SHORT).show()
            return false
        }
        val passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!_\\-]).{8,}$".toRegex()
        if (!password.matches(passwordRegex)) {
            passwordInputLayout.error = getString(R.string.error_password_rules)
            Toast.makeText(this, getString(R.string.error_password_rules), Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun showEmailNotVerifiedDialog(message: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirm_message, null)
        val messageTextView = dialogView.findViewById<TextView>(R.id.dialog_message)
        messageTextView.text = message

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setTitle("Email Verification Required")
            .setPositiveButton("Resend Email") { d, _ ->
                d.dismiss()
                viewModel.resendVerificationEmail()
            }
            .setNegativeButton("Dismiss") { d, _ ->
                d.dismiss()
            }
            .create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background_rounded)
        dialog.show()
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

    private fun signInWithGoogle() {
        signInResultLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun handleSignInResult(account: GoogleSignInAccount?) {
        if (account != null) {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            viewModel.signInWithCredential(credential)
        }
    }

    private fun navigateToHome(user: User) {
        sessionManager.setLoggedIn(true)
        sessionManager.saveUserDetails(user)
        val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@LoginActivity, IntroActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            }
        })
    }
}
