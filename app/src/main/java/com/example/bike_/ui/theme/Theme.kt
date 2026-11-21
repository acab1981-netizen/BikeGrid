package com.example.bike_.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.bike_.data.settings.CyclingSettings

@Immutable
data class BikeUiPalette(
    val accent: Color,
    val screenBg: Color,
    val cardBg: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val mutedBg: Color,
    val danger: Color,
    val success: Color,
    val isDark: Boolean
)

fun bikeAccentColor(key: String): Color = when (key) {
    CyclingSettings.UI_ACCENT_BLUE -> AccentBlue
    CyclingSettings.UI_ACCENT_GREEN -> AccentGreen
    CyclingSettings.UI_ACCENT_TEAL -> AccentTeal
    CyclingSettings.UI_ACCENT_PURPLE -> AccentPurple
    else -> AccentOrange
}

fun bikeUiPalette(themeKey: String, accentKey: String): BikeUiPalette {
    val accent = bikeAccentColor(accentKey)
    return when (themeKey) {
        CyclingSettings.UI_THEME_WARM -> BikeUiPalette(
            accent = accent,
            screenBg = Color(0xFFF3EEE7),
            cardBg = Color(0xFFFFFBF6),
            border = Color(0xFFD8CFC2),
            textPrimary = Color(0xFF2B241D),
            textSecondary = Color(0xFF6D6258),
            mutedBg = Color(0xFFEAE2D8),
            danger = Color(0xFFD32F2F),
            success = Color(0xFF2E7D32),
            isDark = false
        )
        CyclingSettings.UI_THEME_COOL -> BikeUiPalette(
            accent = accent,
            screenBg = Color(0xFFEEF3F6),
            cardBg = Color(0xFFF9FCFF),
            border = Color(0xFFC9D6E2),
            textPrimary = Color(0xFF1D2730),
            textSecondary = Color(0xFF5E6B78),
            mutedBg = Color(0xFFE3EBF2),
            danger = Color(0xFFD32F2F),
            success = Color(0xFF2E7D32),
            isDark = false
        )
        CyclingSettings.UI_THEME_DARK -> BikeUiPalette(
            accent = accent,
            screenBg = Color(0xFF121417),
            cardBg = Color(0xFF1C2026),
            border = Color(0xFF334050),
            textPrimary = Color(0xFFEAF0F6),
            textSecondary = Color(0xFFA9B4C0),
            mutedBg = Color(0xFF242A33),
            danger = Color(0xFFEF5350),
            success = Color(0xFF66BB6A),
            isDark = true
        )
        else -> BikeUiPalette( // SOFT default
            accent = accent,
            screenBg = Color(0xFFF1F3F5),
            cardBg = Color(0xFFFAFBFC),
            border = Color(0xFFD7DEE5),
            textPrimary = Color(0xFF1F2428),
            textSecondary = Color(0xFF66707A),
            mutedBg = Color(0xFFE8EDF2),
            danger = Color(0xFFD32F2F),
            success = Color(0xFF2E7D32),
            isDark = false
        )
    }
}

val LocalBikeUiPalette = staticCompositionLocalOf { bikeUiPalette(CyclingSettings.UI_THEME_SOFT, CyclingSettings.UI_ACCENT_ORANGE) }

@Composable
fun Bike_Theme(
    uiThemeKey: String = CyclingSettings.UI_THEME_SOFT,
    accentKey: String = CyclingSettings.UI_ACCENT_ORANGE,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = bikeUiPalette(uiThemeKey, accentKey)
    val context = LocalContext.current
    val baseDark = palette.isDark
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dyn = if (baseDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dyn.copy(
                primary = palette.accent,
                secondary = palette.accent,
                background = palette.screenBg,
                surface = palette.cardBg,
                surfaceVariant = palette.mutedBg,
                onBackground = palette.textPrimary,
                onSurface = palette.textPrimary,
                onSurfaceVariant = palette.textSecondary,
                outline = palette.border
            )
        }
        baseDark -> darkColorScheme(
            primary = palette.accent,
            secondary = palette.accent,
            tertiary = AccentTeal,
            background = palette.screenBg,
            surface = palette.cardBg,
            surfaceVariant = palette.mutedBg,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.danger
        )
        else -> lightColorScheme(
            primary = palette.accent,
            secondary = palette.accent,
            tertiary = AccentTeal,
            background = palette.screenBg,
            surface = palette.cardBg,
            surfaceVariant = palette.mutedBg,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.danger
        )
    }

    CompositionLocalProvider(LocalBikeUiPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}