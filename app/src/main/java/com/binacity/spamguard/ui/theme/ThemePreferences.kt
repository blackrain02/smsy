package com.binacity.spamguard.ui.theme

import android.content.Context
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

object ThemePreferences {
    private const val PREFS_NAME = "smsy_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_BUBBLE_PALETTE = "bubble_palette"
    private const val KEY_FONT_SCALE = "font_scale"

    fun getThemeMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try { ThemeMode.valueOf(name) } catch (e: Exception) { ThemeMode.SYSTEM }
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getBubblePalette(context: Context): BubblePalette {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_BUBBLE_PALETTE, BubblePalette.BLUE.name) ?: BubblePalette.BLUE.name
        return try { BubblePalette.valueOf(name) } catch (e: Exception) { BubblePalette.BLUE }
    }

    fun setBubblePalette(context: Context, palette: BubblePalette) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BUBBLE_PALETTE, palette.name).apply()
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
