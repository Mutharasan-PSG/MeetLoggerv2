package com.example.meetloggerv2

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SplashActivity : AppCompatActivity() {


    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        sessionManager = SessionManager(this)

        window.navigationBarColor = ContextCompat.getColor(this, R.color.black)

        Handler(Looper.getMainLooper()).postDelayed({
            if (sessionManager.isLoggedIn()) {
                // com.example.vs.User is signed in and session is valid, redirect to HomeActivity
                startActivity(Intent(this, HomeActivity::class.java))
            } else {
                // No user is signed in or session expired, redirect to LoginActivity
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish() // Finish SplashActivity so the user can't navigate back to it
        }, 1000) // Display the splash screen for 2 seconds
    }
}
