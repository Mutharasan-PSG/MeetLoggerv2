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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
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

        // Set status bar color to black
      //window.statusBarColor = ContextCompat.getColor(this, R.color.black)

        // Set the navigation bar color (below the screen content) to black as well
       // window.navigationBarColor = ContextCompat.getColor(this, R.color.black)

        // Hide the action bar (if applicable), if you don't want it to show
      // supportActionBar?.hide()


        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Ensure this is correct
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Initialize Firestore
        firestore = FirebaseFirestore.getInstance()

        // Initialize ActivityResultLauncher
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
                showCustomToast("No internet connection", 2000)
            }
        }

        // Apply color changes to specific words in Privacy text
        val privacyTextView = findViewById<TextView>(R.id.privacy)
        val privacyText = "By signing in, you agree to our Terms, Privacy Policy, and Cookies Use."

        // Create SpannableString
        val spannableString = SpannableString(privacyText)

        // Change text color for the specified words to blue
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

        // Set the styled text to the TextView
        privacyTextView.text = spannableString
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        signInResultLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(account: GoogleSignInAccount?) {
        if (account != null) {
            showCustomToast("Please wait...", 2000)

            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential).addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        navigateToHome(user)
                    }
                } else {
                    Log.e("LoginActivity", "Firebase sign-in failed", task.exception)
                }
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
                            Log.e("LoginActivity", "Failed to add user to Firestore: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("LoginActivity", "Error checking Firestore for existing user: ${e.message}")
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
  /* private fun customizeGoogleSignInButton(signInButton: LinearLayout) {
        val googleLogo = signInButton.findViewById<ImageView>(R.id.google_logo)
        val googleSignInText = signInButton.findViewById<TextView>(R.id.google_sign_in_text)

        // Set text color and background color from colors.xml
        googleSignInText.setTextColor(ContextCompat.getColor(this, R.color.black))

        signInButton.background = ContextCompat.getDrawable(this, R.drawable.rounded_corners)
        // Optionally, you can set other customizations like text size, padding, etc.
    } */

