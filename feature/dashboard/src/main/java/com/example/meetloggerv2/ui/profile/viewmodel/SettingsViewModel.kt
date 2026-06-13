package com.example.meetloggerv2.ui.profile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.meetloggerv2.data.local.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsDataStore = SettingsDataStore(application)

    val themeMode: StateFlow<Int> = settingsDataStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val autoSendEmail: StateFlow<Boolean> = settingsDataStore.autoSendEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val recordingQuality: StateFlow<String> = settingsDataStore.recordingQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "High")

    val biometricLock: StateFlow<Boolean> = settingsDataStore.biometricLock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(mode)
        }
    }

    fun setAutoSendEmail(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAutoSendEmail(enabled)
        }
    }

    fun setRecordingQuality(quality: String) {
        viewModelScope.launch {
            settingsDataStore.setRecordingQuality(quality)
        }
    }

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setBiometricLock(enabled)
        }
    }
}
