package com.example.meetloggerv2.ui.splash.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.meetloggerv2.core.R
import com.example.meetloggerv2.ui.home.activity.HomeActivity
import com.example.meetloggerv2.ui.login.activity.IntroActivity
import com.example.meetloggerv2.ui.main.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set navigation bar color to android system black to avoid local colors.xml dependency
        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.splashlogo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(32.dp)
                )
            }
        }

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

