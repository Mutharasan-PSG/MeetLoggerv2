package com.example.meetloggerv2

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "user_prefs"
        private const val USER_DETAILS = "user_details"
        private const val IS_LOGGED_IN = "is_logged_in"
    }

    // Save user details to SharedPreferences
    fun saveUserDetails(user: User) {
        val userJson = gson.toJson(user)
        prefs.edit().putString(USER_DETAILS, userJson).apply()
    }

    // Retrieve user details from SharedPreferences
    fun getUserDetails(): User? {
        val userJson = prefs.getString(USER_DETAILS, null)
        return gson.fromJson(userJson, User::class.java)
    }

    // Retrieve user ID from SharedPreferences
    fun getUserId(): String? {
        return getUserDetails()?.id
    }

    // Clear all session data
    fun clearSession() {
        prefs.edit().clear().apply()
    }

    // Check if the session is valid based on user details
    fun isSessionValid(): Boolean {
        return getUserDetails() != null
    }

    // Set login status
    fun setLoggedIn(isLoggedIn: Boolean) {
        prefs.edit().putBoolean(IS_LOGGED_IN, isLoggedIn).apply()
    }

    // Check if the user is logged in
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(IS_LOGGED_IN, false)
    }
}
