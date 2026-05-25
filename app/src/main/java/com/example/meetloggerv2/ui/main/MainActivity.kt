package com.example.meetloggerv2.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import com.example.meetloggerv2.R
import com.example.meetloggerv2.data.local.SessionManager
import com.example.meetloggerv2.ui.home.HomeActivity
import com.example.meetloggerv2.ui.login.LoginActivity

class MainActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)

        viewModel.isSessionValid.observe(this) { isValid ->
            if (isValid) {
                startActivity(Intent(this, HomeActivity::class.java))
            } else {
                sessionManager.clearSession()
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }

        viewModel.checkSession(sessionManager)
    }
}
