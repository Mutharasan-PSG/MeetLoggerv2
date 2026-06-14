package com.example.meetloggerv2.ui.home.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.meetloggerv2.core.navigation.NavigationRouter
import com.example.meetloggerv2.core.navigation.AppLayoutIds
import com.example.meetloggerv2.ui.audio.fragment.AudioListFragment
import com.example.meetloggerv2.ui.details.fragment.FileDetailsFragment
import com.example.meetloggerv2.ui.home.fragment.HomeFragment
import com.example.meetloggerv2.ui.login.activity.LoginActivity
import com.example.meetloggerv2.ui.profile.fragment.HelpSupportFragment
import com.example.meetloggerv2.ui.profile.fragment.LegalContentFragment
import com.example.meetloggerv2.ui.profile.fragment.ProfileFragment
import com.example.meetloggerv2.ui.profile.fragment.SettingsFragment
import com.example.meetloggerv2.ui.profile.fragment.SubscriptionFragment
import com.example.meetloggerv2.ui.report.fragment.ReportFragment
import com.example.meetloggerv2.MeetLoggerApp
import com.example.meetloggerv2.data.local.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meetloggerv2.core.theme.GradientStart
import com.example.meetloggerv2.core.theme.GradientEnd
import com.example.meetloggerv2.core.theme.MeetLoggerTheme
import com.example.meetloggerv2.core.theme.pressScaleClick

@AndroidEntryPoint
class HomeActivity : AppCompatActivity(), NavigationRouter {

    private lateinit var settingsDataStore: SettingsDataStore
    private var isAppUnlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val rootView = android.widget.RelativeLayout(this).apply {
            layoutParams = android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
            )
        }

        val fragmentContainer = FrameLayout(this).apply {
            id = AppLayoutIds.FRAGMENT_CONTAINER
            layoutParams = android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootView.addView(fragmentContainer)

        val lockScreenContainer = FrameLayout(this).apply {
            id = AppLayoutIds.LOCK_SCREEN_CONTAINER
            layoutParams = android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
            )
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            setBackgroundColor(typedValue.data)
            visibility = View.GONE
            isClickable = true
            isFocusable = true
        }
        rootView.addView(lockScreenContainer)

        setContentView(rootView)

        // Register FCM token whenever HomeActivity opens (after login or app relaunch)
        MeetLoggerApp.initFcmToken()

        settingsDataStore = SettingsDataStore(this)

        lifecycleScope.launch {
            val isLocked = settingsDataStore.biometricLock.first()
            if (isLocked && !isAppUnlocked) {
                showLockScreen()
            } else {
                if (savedInstanceState == null) {
                    navigateToHome()
                }
            }
        }
    }

    private fun loadFragment(fragment: Fragment, addToBackStack: Boolean = false) {
        val transaction = supportFragmentManager.beginTransaction()
            .replace(AppLayoutIds.FRAGMENT_CONTAINER, fragment)
        if (addToBackStack) {
            transaction.addToBackStack(null)
        }
        transaction.commit()
    }

    override fun navigateToHome() {
        loadFragment(HomeFragment())
    }

    override fun navigateToAudioList() {
        loadFragment(AudioListFragment(), addToBackStack = true)
    }

    override fun navigateToReportList() {
        loadFragment(ReportFragment(), addToBackStack = true)
    }

    override fun navigateToProfile() {
        loadFragment(ProfileFragment(), addToBackStack = true)
    }

    override fun navigateToFileDetails(fileName: String) {
        val fragment = FileDetailsFragment().apply {
            arguments = Bundle().apply {
                putString("fileName", fileName)
            }
        }
        loadFragment(fragment, addToBackStack = true)
    }

    override fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun navigateToLegal(type: String) {
        loadFragment(LegalContentFragment.newInstance(type), addToBackStack = true)
    }

    override fun navigateToHelpSupport() {
        loadFragment(HelpSupportFragment(), addToBackStack = true)
    }

    override fun navigateToSettings() {
        loadFragment(SettingsFragment(), addToBackStack = true)
    }

    override fun navigateToSubscriptions() {
        loadFragment(SubscriptionFragment(), addToBackStack = true)
    }

    private fun showLockScreen() {
        val lockContainer = findViewById<FrameLayout>(AppLayoutIds.LOCK_SCREEN_CONTAINER)
        lockContainer.visibility = View.VISIBLE

        val composeView = ComposeView(this).apply {
            setContent {
                MeetLoggerTheme {
                    AppLockScreen(
                        onUnlockClick = {
                            triggerBiometricPrompt()
                        }
                    )
                }
            }
        }
        lockContainer.removeAllViews()
        lockContainer.addView(composeView)

        triggerBiometricPrompt()
    }

    private fun triggerBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        
        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // Biometrics not set up or not available, bypass app lock to prevent lockout
            Toast.makeText(this, "Biometrics unavailable. App unlocked.", Toast.LENGTH_SHORT).show()
            unlockApp()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(this@HomeActivity, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Toast.makeText(this@HomeActivity, "App unlocked successfully!", Toast.LENGTH_SHORT).show()
                unlockApp()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(this@HomeActivity, "Authentication failed.", Toast.LENGTH_SHORT).show()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MeetLogger")
            .setSubtitle("Confirm your fingerprint to access the app")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun unlockApp() {
        isAppUnlocked = true
        val lockContainer = findViewById<FrameLayout>(AppLayoutIds.LOCK_SCREEN_CONTAINER)
        lockContainer.visibility = View.GONE
        navigateToHome()
    }
}

@Composable
fun AppLockScreen(onUnlockClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon Badge
            Surface(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color.Transparent),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Fingerprint Lock",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = "MeetLogger Locked",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle
            Text(
                text = "Authentication is required to view your secure meeting minutes and recordings.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Unlock Button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressScaleClick { onUnlockClick() },
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Unlock with Fingerprint",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
