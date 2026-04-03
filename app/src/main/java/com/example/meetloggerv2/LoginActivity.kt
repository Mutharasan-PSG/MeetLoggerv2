package com.example.meetloggerv2

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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var signInResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
        firestore = FirebaseFirestore.getInstance()

        signInResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
                showCustomToast("No internet connection", 2000)
            }
        }

        val privacyTextView = findViewById<TextView>(R.id.privacy)
        val privacyText =
            "By signing in, you agree to our Terms, Privacy Policy, and Cookies Use."

        val spannableString = SpannableString(privacyText)

        spannableString.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.BLUE)),
            privacyText.indexOf("Terms"),
            privacyText.indexOf("Terms") + "Terms".length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannableString.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.BLUE)),
            privacyText.indexOf("Privacy Policy"),
            privacyText.indexOf("Privacy Policy") + "Privacy Policy".length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannableString.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.BLUE)),
            privacyText.indexOf("Cookies Use"),
            privacyText.indexOf("Cookies Use") + "Cookies Use".length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        privacyTextView.text = spannableString

        val guestButton = findViewById<LinearLayout>(R.id.btn_guest_sign_in)
        val guestAuthManager = GuestAuthManager(this)

        guestButton.setOnClickListener {
            if (NetworkUtil.isNetworkAvailable(this)) {
                guestAuthManager.loginAsGuest()
            } else {
                showCustomToast("No internet connection", 2000)
            }
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        signInResultLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(account: GoogleSignInAccount?) {
        if (account != null) {
            showCustomToast("Please wait...", 2000)

            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val currentUser = FirebaseAuth.getInstance().currentUser

            if (currentUser != null) {
                currentUser.linkWithCredential(credential)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = FirebaseAuth.getInstance().currentUser
                            Log.d("LoginActivity", "Linked UID: ${user?.uid}")

                            showCustomToast("Account linked", 2000)

                            if (user != null) {
                                navigateToHome(user)
                            }
                        } else {
                            Log.e("LoginActivity", "Linking failed", task.exception)
                            handleLinkError(credential)
                        }
                    }
            } else {
                showCustomToast("Guest session missing", 2000)
            }
        }
    }

    private fun handleLinkError(credential: AuthCredential) {
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = FirebaseAuth.getInstance().currentUser
                    Log.d("LoginActivity", "Signed in existing user: ${user?.uid}")

                    showCustomToast("Logged into existing account", 2000)

                    if (user != null) {
                        navigateToHome(user)
                    }
                } else {
                    Log.e("LoginActivity", "Fallback sign-in failed", task.exception)
                    showCustomToast("Login failed", 2000)
                }
            }
    }

    private fun navigateToHome(user: FirebaseUser) {
        val userDetails = User(
            id = user.uid,
            name = user.displayName.orEmpty(),
            email = user.email.orEmpty(),
            photoUrl = user.photoUrl?.toString()
        )

        firestore.collection("Users").document(user.uid)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    firestore.collection("Users").document(user.uid).set(userDetails)
                        .addOnSuccessListener {
                            Log.d("LoginActivity", "User added to Firestore successfully")
                        }
                        .addOnFailureListener { e ->
                            Log.e("LoginActivity", "Failed to add user: ${e.message}")
                        }
                }
            }

        sessionManager.setLoggedIn(true)
        sessionManager.saveUserDetails(userDetails)

        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
        finish()
    }

    private fun showCustomToast(message: String, duration: Int) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.show()

        Handler(Looper.getMainLooper()).postDelayed({
            toast.cancel()
        }, duration.toLong())
    }
}