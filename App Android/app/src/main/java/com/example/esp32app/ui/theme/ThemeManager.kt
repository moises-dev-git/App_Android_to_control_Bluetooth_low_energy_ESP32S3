package com.example.esp32app.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateOf

object ThemeManager {
    private const val PREF_NAME = "theme_prefs"
    private const val KEY_DARK_MODE = "is_dark_mode"

    val isDarkModeState = mutableStateOf(true)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        isDarkModeState.value = prefs.getBoolean(KEY_DARK_MODE, true)
    }

    fun toggleTheme(context: Context) {
        val newValue = !isDarkModeState.value
        isDarkModeState.value = newValue
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, newValue)
            .apply()
    }
}
