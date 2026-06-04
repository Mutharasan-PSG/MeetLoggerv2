package com.example.meetloggerv2.ui.main.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import com.example.meetloggerv2.R
import com.example.meetloggerv2.ui.home.activity.HomeActivity
import com.example.meetloggerv2.ui.login.activity.LoginActivity
import com.example.meetloggerv2.ui.main.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel.isSessionValid.observe(this) { isValid ->
            if (isValid) {
                startActivity(Intent(this, HomeActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }

        viewModel.checkSession()
    }
}
