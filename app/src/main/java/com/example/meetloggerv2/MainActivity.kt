package com.example.meetloggerv2

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)

        if (sessionManager.isSessionValid() && sessionManager.isLoggedIn()) {
            // If logged in and session is valid, redirect to HomeActivity
            startActivity(Intent(this, HomeActivity::class.java))
        } else {
            // Otherwise, show the login screen
            sessionManager.clearSession() // Clear session if it is not valid
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}
