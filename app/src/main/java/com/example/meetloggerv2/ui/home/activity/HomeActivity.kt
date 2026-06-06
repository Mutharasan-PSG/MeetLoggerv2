package com.example.meetloggerv2.ui.home.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.meetloggerv2.core.R
import com.example.meetloggerv2.core.navigation.NavigationRouter
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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeActivity : AppCompatActivity(), NavigationRouter {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Load HomeFragment by default
        if (savedInstanceState == null) {
            navigateToHome()
        }
    }

    private fun loadFragment(fragment: Fragment, addToBackStack: Boolean = false) {
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
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
}
