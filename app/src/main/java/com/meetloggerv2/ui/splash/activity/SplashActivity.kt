package com.meetloggerv2.ui.splash.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.meetloggerv2.BuildConfig
import com.meetloggerv2.core.R
import com.meetloggerv2.core.config.GateResult
import com.meetloggerv2.core.theme.MeetLoggerTheme
import com.meetloggerv2.ui.gate.BlockedScreen
import com.meetloggerv2.ui.gate.ForceUpdateScreen
import com.meetloggerv2.ui.gate.MaintenanceScreen
import com.meetloggerv2.ui.home.activity.HomeActivity
import com.meetloggerv2.ui.login.activity.IntroActivity
import com.meetloggerv2.ui.main.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // null while the gate is being evaluated (shows the logo). Set to a blocking
    // GateResult to render the corresponding full-screen gate instead of routing.
    private var gateState by mutableStateOf<GateResult?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MeetLoggerTheme {
                when (val gate = gateState) {
                    is GateResult.ForceUpdate -> ForceUpdateScreen(onUpdate = { openUpdateUrl(gate.updateUrl) })
                    is GateResult.Maintenance -> MaintenanceScreen(message = gate.message, onRetry = { evaluateGateAndRoute() })
                    GateResult.Blocked -> BlockedScreen(onSignOut = { signOutToIntro() })
                    // null (loading) or Allowed: keep showing the splash logo while routing.
                    else -> SplashLogo()
                }
            }
        }

        evaluateGateAndRoute()
    }

    private fun evaluateGateAndRoute() {
        lifecycleScope.launch {
            when (val gate = viewModel.evaluateGate(BuildConfig.VERSION_CODE)) {
                is GateResult.Allowed -> {
                    gateState = GateResult.Allowed
                    val valid = viewModel.isSessionValidNow()
                    // Preserve the original brief splash dwell before navigating.
                    delay(1000)
                    val target = if (valid) HomeActivity::class.java else IntroActivity::class.java
                    startActivity(Intent(this@SplashActivity, target))
                    finish()
                }
                else -> gateState = gate
            }
        }
    }

    private fun openUpdateUrl(url: String) {
        // Prefer the Play Store app; fall back to a browser if it is unavailable.
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.android.vending"))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
            }
        }
    }

    private fun signOutToIntro() {
        viewModel.signOut()
        startActivity(Intent(this, IntroActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    @Composable
    private fun SplashLogo() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
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
}
