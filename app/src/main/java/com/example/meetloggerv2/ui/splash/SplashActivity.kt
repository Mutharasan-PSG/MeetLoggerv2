package com.example.meetloggerv2.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.viewModels
import com.example.meetloggerv2.R
import com.example.meetloggerv2.data.local.SessionManager
import com.example.meetloggerv2.ui.home.HomeActivity
import com.example.meetloggerv2.ui.login.LoginActivity
import com.example.meetloggerv2.ui.main.MainViewModel

class SplashActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        sessionManager = SessionManager(this)

        window.navigationBarColor = ContextCompat.getColor(this, R.color.black)

        viewModel.isSessionValid.observe(this) { isValid ->
            Handler(Looper.getMainLooper()).postDelayed({
                if (isValid) {
                    startActivity(Intent(this, HomeActivity::class.java))
                } else {
                    startActivity(Intent(this, LoginActivity::class.java))
                }
                finish()
            }, 1000)
        }

        viewModel.checkSession(sessionManager)
    }
}
