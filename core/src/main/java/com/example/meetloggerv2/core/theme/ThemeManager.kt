package com.example.meetloggerv2.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeManager {
    private val _themeMode = MutableStateFlow(0) // 0: System, 1: Light, 2: Dark
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    fun setThemeMode(mode: Int) {
        _themeMode.value = mode
    }

    @Composable
    fun isDarkTheme(): Boolean {
        val mode by themeMode.collectAsState()
        return when (mode) {
            1 -> false
            2 -> true
            else -> isSystemInDarkTheme()
        }
    }
}
