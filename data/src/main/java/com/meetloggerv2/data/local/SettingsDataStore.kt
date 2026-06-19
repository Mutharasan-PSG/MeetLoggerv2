package com.meetloggerv2.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

class SettingsDataStore(private val context: Context) {
    companion object {
        val KEY_THEME_MODE = intPreferencesKey("theme_mode") // 0: System, 1: Light, 2: Dark
        val KEY_AUTO_SEND_EMAIL = booleanPreferencesKey("auto_send_email")
        val KEY_RECORDING_QUALITY = stringPreferencesKey("recording_quality") // High, Medium, Low
        val KEY_BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
    }

    val themeMode: Flow<Int> = context.settingsDataStore.data.map { it[KEY_THEME_MODE] ?: 0 }
    val autoSendEmail: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_AUTO_SEND_EMAIL] ?: false }
    val recordingQuality: Flow<String> = context.settingsDataStore.data.map { it[KEY_RECORDING_QUALITY] ?: "High" }
    val biometricLock: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_BIOMETRIC_LOCK] ?: false }

    suspend fun setThemeMode(mode: Int) {
        context.settingsDataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setAutoSendEmail(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_SEND_EMAIL] = enabled }
    }

    suspend fun setRecordingQuality(quality: String) {
        context.settingsDataStore.edit { it[KEY_RECORDING_QUALITY] = quality }
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_BIOMETRIC_LOCK] = enabled }
    }
}
