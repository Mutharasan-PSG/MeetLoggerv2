package com.example.meetloggerv2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Load HomeFragment by default
        if (savedInstanceState == null) {
            loadFragment(HomeFragment()) // Start with HomeFragment
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)  // Dynamically replace the container with the fragment
            .commit()
    }
}
