package com.example.meetloggerv2.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "profile_prefs")

class ProfileDataStore(private val context: Context) {
    companion object {
        val KEY_NAME = stringPreferencesKey("profile_name")
        val KEY_EMAIL = stringPreferencesKey("profile_email")
        val KEY_PHOTO_URL = stringPreferencesKey("profile_photo_url")
        val KEY_LAST_FETCH_DATE = stringPreferencesKey("profile_last_fetch_date")
    }

    suspend fun saveProfile(name: String, email: String, photoUrl: String?, fetchDate: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NAME] = name
            preferences[KEY_EMAIL] = email
            preferences[KEY_PHOTO_URL] = photoUrl ?: ""
            preferences[KEY_LAST_FETCH_DATE] = fetchDate
        }
    }

    suspend fun getProfile(): CachedProfile? {
        val prefs = context.dataStore.data.first()
        val name = prefs[KEY_NAME] ?: return null
        val email = prefs[KEY_EMAIL] ?: ""
        val photoUrl = prefs[KEY_PHOTO_URL] ?: ""
        val lastFetchDate = prefs[KEY_LAST_FETCH_DATE] ?: ""
        return CachedProfile(name, email, photoUrl, lastFetchDate)
    }

    suspend fun clear() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

data class CachedProfile(
    val name: String,
    val email: String,
    val photoUrl: String,
    val lastFetchDate: String
)
