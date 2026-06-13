package com.example.meetloggerv2.ui.splash.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.viewModels
import com.example.meetloggerv2.core.R
import com.example.meetloggerv2.ui.home.activity.HomeActivity
import com.example.meetloggerv2.ui.login.activity.IntroActivity
import com.example.meetloggerv2.ui.main.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        window.navigationBarColor = ContextCompat.getColor(this, R.color.black)

        viewModel.isSessionValid.observe(this) { isValid ->
            Handler(Looper.getMainLooper()).postDelayed({
                if (isValid) {
                    startActivity(Intent(this, HomeActivity::class.java))
                } else {
                    startActivity(Intent(this, IntroActivity::class.java))
                }
                finish()
            }, 1000)
        }

        viewModel.checkSession()
    }
}
