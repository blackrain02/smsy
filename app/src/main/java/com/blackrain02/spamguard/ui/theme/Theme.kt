package com.blackrain02.spamguard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = LightSurface,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    background = LightBackground,
    onBackground = LightTextPrimary
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueDark,
    onPrimary = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    background = DarkBackground,
    onBackground = DarkTextPrimary
)

@Composable
fun SmsyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    ThemePreferences.init(context)
    val themeMode = ThemePreferences.currentThemeMode.value
    val palette = ThemePreferences.currentBubblePalette.value

    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> darkTheme
    }

    val baseScheme = if (isDark) DarkColorScheme else LightColorScheme
    val colorScheme = baseScheme.copy(
        primary = palette.primaryColor,
        onPrimary = palette.onPrimaryColor
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
