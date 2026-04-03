package com.example.meetloggerv2

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

class GuestAuthManager(private val context: Context) {

    private val client = OkHttpClient()

    @SuppressLint("HardwareIds")
    fun loginAsGuest() {
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        Log.d("LoginActivity", "Device ID: $deviceId")

        val json = JSONObject()
        json.put("deviceId", deviceId)

        val body = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            json.toString()
        )

        val request = Request.Builder()
            .url("http://10.66.49.107:3000/auth/guest")
            .post(body)
            .build()

        Toast.makeText(context, "Connecting to server...", Toast.LENGTH_SHORT).show()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LoginActivity", "API Error: ${e.message}")
                showToast("Server error")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                Log.d("LoginActivity", "Response: $responseBody")

                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    val token = json.getString("token")

                    signInWithCustomToken(token)
                } else {
                    showToast("Login failed")
                }
            }
        })
    }

    private fun signInWithCustomToken(token: String) {
        FirebaseAuth.getInstance()
            .signInWithCustomToken(token)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = FirebaseAuth.getInstance().currentUser
                    Log.d("LoginActivity", "Logged in UID: ${user?.uid}")

                    showToast("Guest Login Success")
                } else {
                    Log.e("LoginActivity", "Firebase Auth failed", task.exception)
                    showToast("Firebase login failed")
                }
            }
    }

    private fun showToast(message: String) {
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}