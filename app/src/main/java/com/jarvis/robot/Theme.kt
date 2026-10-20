package com.jarvis.robot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

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
    val ha: Color,
    val field: Color,
    val isDark: Boolean,
    val photo: Int? = null, // drawable shown at 40% behind the screen
)

/** The colour themes (same three as the backend panel); each has a dark and a light version. */
enum class AppPalette(val label: String) { CLASSIC("Classic"), BLUEBERRY("Blueberry"), SUCCULENT("Succulent") }

val DarkPalette = Palette(
    bg = Color(0xFF0B1416), card = Color(0xFF101F22), border = Color(0xFF1C3236),
    accent = Color(0xFF20E0E0), text = Color(0xFFE8F6F5), muted = Color(0xFF8FB9B6),
    danger = Color(0xFFFF8A8A), success = Color(0xFF6BE3AB), instant = Color(0xFFFFB86B), ha = Color(0xFFB69CFF), field = Color(0xFF0C1A1C), isDark = true,
)

val LightPalette = Palette(
    bg = Color(0xFFEEF5F5), card = Color(0xFFFFFFFF), border = Color(0xFFC9DCDC),
    accent = Color(0xFF0A8F95), text = Color(0xFF12292B), muted = Color(0xFF55777A),
    danger = Color(0xFFC0392B), success = Color(0xFF1B8F5A), instant = Color(0xFFB3560A), ha = Color(0xFF6B46C1), field = Color(0xFFF3F8F8), isDark = false,
)

private val BlueberryDark = DarkPalette.copy(
    bg = Color(0xFF12181D), card = Color(0xFF112A40), border = Color(0xFF1D4668), accent = Color(0xFF7FB2E5),
    text = Color(0xFFE8F2FC), muted = Color(0xFFA7BDD6), field = Color(0xFF0E1D2C), photo = R.drawable.theme_blueberry,
)
private val BlueberryLight = LightPalette.copy(
    bg = Color(0xFFE8F2FC), card = Color(0xFFF7FAFE), border = Color(0xFFBFD4EB), accent = Color(0xFF3273AF),
    text = Color(0xFF1C2227), muted = Color(0xFF4D6580), field = Color(0xFFEEF4FB), photo = R.drawable.theme_blueberry,
)
private val SucculentDark = DarkPalette.copy(
    bg = Color(0xFF11171B), card = Color(0xFF11322F), border = Color(0xFF1D5A4C), accent = Color(0xFF7FC1A6),
    text = Color(0xFFE6EEE2), muted = Color(0xFFA3B8AA), field = Color(0xFF0D2224), photo = R.drawable.theme_succulent,
)
private val SucculentLight = LightPalette.copy(
    bg = Color(0xFFE8EDE4), card = Color(0xFFF6F8F3), border = Color(0xFFBBC5B4), accent = Color(0xFF1B6B55),
    text = Color(0xFF171D24), muted = Color(0xFF556A5E), field = Color(0xFFEDF1E9), photo = R.drawable.theme_succulent,
)

fun paletteFor(palette: AppPalette, dark: Boolean): Palette = when (palette) {
    AppPalette.CLASSIC -> if (dark) DarkPalette else LightPalette
    AppPalette.BLUEBERRY -> if (dark) BlueberryDark else BlueberryLight
    AppPalette.SUCCULENT -> if (dark) SucculentDark else SucculentLight
}

val LocalPalette = staticCompositionLocalOf { DarkPalette }

@Composable
fun JarvisTheme(mode: ThemeMode, palette: AppPalette = AppPalette.CLASSIC, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val p = paletteFor(palette, dark)
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

/** The screen background: the palette's colour, plus its photo at 40% when the theme has one. */
@Composable
fun ThemeBackdrop(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(modifier.fillMaxSize().background(p.bg)) {
        p.photo?.let {
            Image(painterResource(it), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(PHOTO_ALPHA))
        }
    }
}

const val PHOTO_ALPHA = 0.4f
