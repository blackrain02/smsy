package com.binacity.spamguard.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class BubblePalette(val displayName: String, val primaryColor: Color, val onPrimaryColor: Color) {
    BLUE("Classic Blue", Color(0xFF0284C7), Color.White),
    EMERALD("Emerald Green", Color(0xFF059669), Color.White),
    VIOLET("Royal Violet", Color(0xFF7C3AED), Color.White),
    AMBER("Sunset Amber", Color(0xFFD97706), Color.White)
}

enum class AppLanguage(val code: String, val displayName: String) {
    EN("en", "English"),
    FA("fa", "فارسی")
}

object ThemePreferences {
    private const val PREFS_NAME = "smsy_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_BUBBLE_PALETTE = "bubble_palette"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_APP_LANGUAGE = "app_language"

    val currentThemeMode = mutableStateOf(ThemeMode.SYSTEM)
    val currentBubblePalette = mutableStateOf(BubblePalette.BLUE)
    val currentLanguage = mutableStateOf(AppLanguage.EN)
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentThemeMode.value = try {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        } catch (_: Throwable) { ThemeMode.SYSTEM }

        currentBubblePalette.value = try {
            BubblePalette.valueOf(prefs.getString(KEY_BUBBLE_PALETTE, BubblePalette.BLUE.name) ?: BubblePalette.BLUE.name)
        } catch (_: Throwable) { BubblePalette.BLUE }

        currentLanguage.value = try {
            AppLanguage.valueOf(prefs.getString(KEY_APP_LANGUAGE, AppLanguage.EN.name) ?: AppLanguage.EN.name)
        } catch (_: Throwable) { AppLanguage.EN }

        isInitialized = true
    }

    fun getThemeMode(context: Context): ThemeMode {
        init(context)
        return currentThemeMode.value
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        init(context)
        currentThemeMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getBubblePalette(context: Context): BubblePalette {
        init(context)
        return currentBubblePalette.value
    }

    fun setBubblePalette(context: Context, palette: BubblePalette) {
        init(context)
        currentBubblePalette.value = palette
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BUBBLE_PALETTE, palette.name).apply()
    }

    fun getLanguage(context: Context): AppLanguage {
        init(context)
        return currentLanguage.value
    }

    fun setLanguage(context: Context, lang: AppLanguage) {
        init(context)
        currentLanguage.value = lang
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_APP_LANGUAGE, lang.name).apply()
    }

    fun getFontScale(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_FONT_SCALE, 1.0f)
    }

    fun setFontScale(context: Context, scale: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_FONT_SCALE, scale).apply()
    }
}
