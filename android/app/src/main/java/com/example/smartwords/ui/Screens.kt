// The three Lexica screens (Today / Word Detail / Settings) plus the saved
// placeholder, the bottom navigation, and the root container that wires theme
// state, deep links, and live widget updates. Mirrors ios/App/SmartWordsApp.swift.

package com.example.smartwords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartwords.data.ThemeMode
import com.example.smartwords.data.Word
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// MARK: - Bottom navigation

enum class Tab(val label: String) { TODAY("Today"), SAVED("Saved"), SETTINGS("Settings") }

@Composable
fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val theme = LocalTheme.current
    Column(modifier = Modifier.background(theme.palette.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(theme.palette.line))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.palette.surface)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                .padding(top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { tab ->
                NavItem(tab = tab, selected = tab == current, onClick = { onSelect(tab) })
            }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalTheme.current
    val tint = if (selected) theme.accent else theme.palette.muted
    Column(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(30.dp)
                .then(
                    if (selected) Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(theme.accent.copy(alpha = 0.18f))
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            TabIcon(tab = tab, tint = tint)
        }
        Text(
            text = tab.label,
            fontFamily = Fonts.sans,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
        )
    }
}

// Simple glyphs drawn with boxes so no icon dependency is needed.
@Composable
private fun TabIcon(tab: Tab, tint: Color) {
    when (tab) {
        Tab.TODAY -> Box(
            Modifier.size(15.dp).border(1.7.dp, tint, RoundedCornerShape(4.dp))
        )
        Tab.SAVED -> Box(
            Modifier.size(width = 12.dp, height = 15.dp).background(tint)
        )
        Tab.SETTINGS -> Column(
            modifier = Modifier.size(width = 16.dp, height = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)).background(tint))
            Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)).background(tint))
            Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)).background(tint))
        }
    }
}

// MARK: - Today

@Composable
fun TodayScreen(word: Word, onOpenWord: () -> Unit) {
    val theme = LocalTheme.current

    val now = remember { Date() }
    val dateLine = remember {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(now).uppercase(Locale.getDefault())
    }
    val dayCount = remember {
        val d = Calendar.getInstance().apply { time = now }.get(Calendar.DAY_OF_YEAR)
        "$d / 365"
    }

    // Outer column has NO horizontal padding so the quote block can be edge-to-edge;
    // text content carries its own 26dp side padding (Compose forbids negative padding).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.palette.bg)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(dateLine, fontFamily = Fonts.mono, fontSize = 10.5.sp, letterSpacing = 1.2.sp, color = theme.palette.muted)
            Text(dayCount, fontFamily = Fonts.mono, fontSize = 10.5.sp, letterSpacing = 1.2.sp, color = theme.accent)
        }

        // Tapping the word opens Word Detail.
        Column(
            modifier = Modifier
                .padding(horizontal = 26.dp)
                .padding(top = 28.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenWord),
        ) {
            Text(
                text = word.word,
                fontFamily = Fonts.serif,
                fontSize = 60.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-1.5).sp,
                color = theme.palette.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(theme.accent),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    (word.pos ?: "").uppercase(Locale.getDefault()),
                    fontFamily = Fonts.mono, fontSize = 11.sp, letterSpacing = 1.5.sp, color = theme.palette.muted,
                )
                if (!word.ipa.isNullOrEmpty()) {
                    Text(word.ipa, fontFamily = Fonts.mono, fontSize = 11.sp, color = theme.palette.muted)
                }
            }
        }

        Text(
            text = word.definition,
            fontFamily = Fonts.sans,
            fontSize = 16.5.sp,
            lineHeight = 24.sp,
            color = theme.palette.fg,
            modifier = Modifier.padding(horizontal = 26.dp).padding(top = 22.dp),
        )

        if (!word.synonyms.isNullOrEmpty()) {
            FlowRow(modifier = Modifier.padding(horizontal = 26.dp).padding(top = 20.dp)) {
                word.synonyms.forEach { Chip(it) }
            }
        }

        Spacer(Modifier.weight(1f))

        if (!word.example.isNullOrEmpty()) {
            // Full-bleed accent-washed quote block pinned to the bottom.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.accentWash)
                    .padding(horizontal = 26.dp, vertical = 20.dp),
            ) {
                Text(
                    "“",
                    fontFamily = Fonts.serif,
                    fontSize = 46.sp,
                    color = theme.accent,
                    lineHeight = 22.sp,
                )
                Text(
                    word.example,
                    fontFamily = Fonts.serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 17.sp,
                    lineHeight = 25.sp,
                    color = theme.palette.fg,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

// MARK: - Word Detail

@Composable
fun WordDetailScreen(word: Word, onBack: () -> Unit) {
    val theme = LocalTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.palette.bg)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
    ) {
        // Custom nav row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "‹  Today",
                fontFamily = Fonts.mono, fontSize = 12.sp, color = theme.palette.muted,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onBack).padding(4.dp),
            )
            Text("SAVE", fontFamily = Fonts.mono, fontSize = 11.sp, letterSpacing = 1.1.sp, color = theme.accent)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        (word.pos ?: "").uppercase(Locale.getDefault()),
                        fontFamily = Fonts.mono, fontSize = 11.sp, letterSpacing = 1.6.sp, color = theme.accent,
                    )
                    if (!word.ipa.isNullOrEmpty()) {
                        Text(word.ipa, fontFamily = Fonts.mono, fontSize = 12.sp, color = theme.palette.muted)
                    }
                }
                // Decorative "play" button.
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(theme.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayTriangle()
                }
            }

            Text(
                word.word,
                fontFamily = Fonts.serif,
                fontSize = 50.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-1.25).sp,
                color = theme.palette.fg,
                modifier = Modifier.padding(top = 14.dp),
            )

            Divider()

            SectionLabel("DEFINITION")
            Text(
                word.definition,
                fontFamily = Fonts.sans, fontSize = 16.sp, lineHeight = 25.sp,
                color = theme.palette.fg, modifier = Modifier.padding(top = 10.dp),
            )

            if (!word.example.isNullOrEmpty()) {
                SectionLabel("EXAMPLE", modifier = Modifier.padding(top = 24.dp))
                Text(
                    "“${word.example}”",
                    fontFamily = Fonts.serif, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 25.sp,
                    color = theme.palette.fg, modifier = Modifier.padding(top = 10.dp),
                )
            }

            val hasSyn = !word.synonyms.isNullOrEmpty()
            val hasAnt = !word.antonyms.isNullOrEmpty()
            if (hasSyn || hasAnt) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    if (hasSyn) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            SectionLabel("SYNONYMS")
                            FlowRow { word.synonyms!!.forEach { Chip(it) } }
                        }
                    }
                    if (hasAnt) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            SectionLabel("ANTONYMS")
                            FlowRow { word.antonyms!!.forEach { Chip(it, dim = true) } }
                        }
                    }
                }
            }

            if (!word.origin.isNullOrEmpty()) {
                Divider()
                SectionLabel("ORIGIN")
                Text(
                    word.origin,
                    fontFamily = Fonts.sans, fontSize = 14.sp, lineHeight = 22.sp,
                    color = theme.palette.muted, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayTriangle() {
    // Tiny right-pointing triangle approximated with a rotated square corner is fussy;
    // a simple centered glyph reads as a play button and needs no vector asset.
    Text("▶", fontSize = 13.sp, color = Color.White)
}

@Composable
private fun Divider() {
    val theme = LocalTheme.current
    Box(
        Modifier
            .padding(vertical = 22.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(theme.palette.line),
    )
}

// MARK: - Saved (placeholder)

@Composable
fun SavedScreen() {
    val theme = LocalTheme.current
    Column(
        modifier = Modifier.fillMaxSize().background(theme.palette.bg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Saved", fontFamily = Fonts.serif, fontSize = 32.sp, fontWeight = FontWeight.Medium, color = theme.palette.fg)
        Spacer(Modifier.height(10.dp))
        Text("Words you save will appear here.", fontFamily = Fonts.sans, fontSize = 14.sp, color = theme.palette.muted)
    }
}
