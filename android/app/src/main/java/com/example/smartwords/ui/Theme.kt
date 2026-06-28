// Design system for the Lexica redesign — mirrors ios/Shared/Theme.swift exactly.
// Palette + accents + the resolved theme that flows down through CompositionLocals.

package com.example.smartwords.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.smartwords.R
import com.example.smartwords.data.ThemeMode

// MARK: - Accents

@Immutable
data class Accent(val id: String, val name: String, val color: Color)

object Accents {
    val all: List<Accent> = listOf(
        Accent("amber", "Amber", Color(0xFFC87D2A)),
        Accent("gold", "Gold", Color(0xFFB8862A)),
        Accent("coral", "Coral", Color(0xFFD96B52)),
        Accent("terracotta", "Clay", Color(0xFFC25E3A)),
        Accent("rose", "Rose", Color(0xFFC25A73)),
        Accent("plum", "Plum", Color(0xFFA85A86)),
    )

    fun by(id: String): Accent = all.firstOrNull { it.id == id } ?: all[0]
}

// MARK: - Palette (per theme)

@Immutable
data class Palette(
    val bg: Color,
    val surface: Color,
    val fg: Color,
    val muted: Color,
    val line: Color,
    val line2: Color,
) {
    companion object {
        val Light = Palette(
            bg = Color(0xFFF6F3EE),
            surface = Color(0xFFFFFFFF),
            fg = Color(0xFF1B1714),
            muted = Color(0xFF8C837B),
            line = Color(0xFF1B1714).copy(alpha = 0.09f),
            line2 = Color(0xFF1B1714).copy(alpha = 0.17f),
        )
        val Dark = Palette(
            bg = Color(0xFF15120F),
            surface = Color(0xFF1F1B17),
            fg = Color(0xFFF1ECE4),
            muted = Color(0xFF9A9088),
            line = Color(0xFFFFFFFF).copy(alpha = 0.10f),
            line2 = Color(0xFFFFFFFF).copy(alpha = 0.20f),
        )
    }
}

// MARK: - Resolved theme

@Immutable
data class ResolvedTheme(
    val palette: Palette,
    val accent: Color,
) {
    // Quote/widget tint: accent mixed into the surface (~13%).
    val accentWash: Color get() = accent.copy(alpha = 0.13f).compositeOver(palette.surface)
}

// color-mix(accent 13%, surface) over an opaque surface.
private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f,
    )
}

val LocalTheme: ProvidableCompositionLocal<ResolvedTheme> = staticCompositionLocalOf {
    ResolvedTheme(palette = Palette.Light, accent = Accents.all[0].color)
}

@Composable
fun resolvePalette(mode: ThemeMode): Palette = when (mode) {
    ThemeMode.LIGHT -> Palette.Light
    ThemeMode.DARK -> Palette.Dark
    ThemeMode.AUTO -> if (isSystemInDarkTheme()) Palette.Dark else Palette.Light
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.AUTO -> isSystemInDarkTheme()
}

// MARK: - Fonts (bundled Lexica design fonts — variable; wght axis per weight)
// serif = Newsreader, sans = Hanken Grotesk, mono = Spline Sans Mono.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun wght(resId: Int, w: FontWeight, style: FontStyle = FontStyle.Normal) =
    Font(resId, weight = w, style = style,
        variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))

object Fonts {
    val serif = FontFamily(
        wght(R.font.newsreader, FontWeight.Normal),
        wght(R.font.newsreader, FontWeight.Medium),
        wght(R.font.newsreader, FontWeight.SemiBold),
        wght(R.font.newsreader_italic, FontWeight.Normal, FontStyle.Italic),
        wght(R.font.newsreader_italic, FontWeight.Medium, FontStyle.Italic),
    )
    val sans = FontFamily(
        wght(R.font.hanken_grotesk, FontWeight.Normal),
        wght(R.font.hanken_grotesk, FontWeight.Medium),
        wght(R.font.hanken_grotesk, FontWeight.SemiBold),
        wght(R.font.hanken_grotesk, FontWeight.Bold),
    )
    val mono = FontFamily(
        wght(R.font.spline_sans_mono, FontWeight.Normal),
        wght(R.font.spline_sans_mono, FontWeight.Medium),
        wght(R.font.spline_sans_mono, FontWeight.SemiBold),
    )
}

@Composable
fun LexicaTheme(theme: ResolvedTheme, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTheme provides theme, content = content)
}
