package com.jarvis.robot

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The landing page's colour tokens, dark and light. */
data class Palette(
    val bg: Color,
    val card: Color,
    val border: Color,
    val accent: Color,
    val text: Color,
    val muted: Color,
    val danger: Color,
    val success: Color,
    val instant: Color,
    val field: Color,
    val isDark: Boolean,
)

val DarkPalette = Palette(
    bg = Color(0xFF0B1416), card = Color(0xFF101F22), border = Color(0xFF1C3236),
    accent = Color(0xFF20E0E0), text = Color(0xFFE8F6F5), muted = Color(0xFF8FB9B6),
    danger = Color(0xFFFF8A8A), success = Color(0xFF6BE3AB), instant = Color(0xFFFFB86B), field = Color(0xFF0C1A1C), isDark = true,
)

val LightPalette = Palette(
    bg = Color(0xFFEEF5F5), card = Color(0xFFFFFFFF), border = Color(0xFFC9DCDC),
    accent = Color(0xFF0A8F95), text = Color(0xFF12292B), muted = Color(0xFF55777A),
    danger = Color(0xFFC0392B), success = Color(0xFF1B8F5A), instant = Color(0xFFB3560A), field = Color(0xFFF3F8F8), isDark = false,
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

@Composable
fun JarvisTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.bg, background = p.bg, onBackground = p.text,
            surface = p.card, onSurface = p.text, surfaceVariant = p.field, onSurfaceVariant = p.muted,
            outline = p.border, error = p.danger,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = Color.White, background = p.bg, onBackground = p.text,
            surface = p.card, onSurface = p.text, surfaceVariant = p.field, onSurfaceVariant = p.muted,
            outline = p.border, error = p.danger,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
