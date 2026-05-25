package com.example.meetloggerv2.ui.login.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.meetloggerv2.R
import com.example.meetloggerv2.core.session.SessionManager
import com.example.meetloggerv2.data.model.User
import com.example.meetloggerv2.ui.login.viewmodel.LoginViewModel
import com.example.meetloggerv2.ui.main.activity.MainActivity
import com.example.meetloggerv2.core.network.NetworkUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import androidx.activity.viewModels

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

        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginViewModel.LoginState.Loading -> {
                    Toast.makeText(this, "Please wait...", Toast.LENGTH_SHORT).show()
                }
                is LoginViewModel.LoginState.Success -> {
                    navigateToHome(state.user)
                }
                is LoginViewModel.LoginState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                    Log.e("LoginActivity", "Login error: ${state.message}")
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

        val signInButton = findViewById<LinearLayout>(R.id.btn_google_sign_in)
        signInButton.setOnClickListener {
            if (NetworkUtil.isNetworkAvailable(this)) {
                signInWithGoogle()
            } else {
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            }
        }

        val privacyTextView = findViewById<TextView>(R.id.privacy)
        val privacyText = "By signing in, you agree to our Terms, Privacy Policy, and Cookies Use."
        val spannableString = SpannableString(privacyText)
        val blue = ContextCompat.getColor(this, R.color.BLUE)

        val termsIdx = privacyText.indexOf("Terms")
        spannableString.setSpan(ForegroundColorSpan(blue), termsIdx, termsIdx + 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        
        val policyIdx = privacyText.indexOf("Privacy Policy")
        spannableString.setSpan(ForegroundColorSpan(blue), policyIdx, policyIdx + 14, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        
        val cookiesIdx = privacyText.indexOf("Cookies Use")
        spannableString.setSpan(ForegroundColorSpan(blue), cookiesIdx, cookiesIdx + 11, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        privacyTextView.text = spannableString
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
        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
        finish()
    }
}
