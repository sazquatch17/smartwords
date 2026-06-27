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
import androidx.compose.ui.text.font.FontFamily
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

// MARK: - Fonts (system equivalents of the design's Google fonts)
// serif = Newsreader, sans = Hanken Grotesk (default), mono = Spline Sans Mono.
object Fonts {
    val serif = FontFamily.Serif
    val sans = FontFamily.Default
    val mono = FontFamily.Monospace
}

@Composable
fun LexicaTheme(theme: ResolvedTheme, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTheme provides theme, content = content)
}
