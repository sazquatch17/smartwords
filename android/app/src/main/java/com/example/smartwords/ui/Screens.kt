// The three Lexica screens (Today / Word Detail / Settings) plus the saved
// placeholder, the bottom navigation, and the root container that wires theme
// state, deep links, and live widget updates. Mirrors ios/App/SmartWordsApp.swift.

package com.example.smartwords.ui

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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

// The single word page: Today's highlights (accent bar, bottom example panel)
// merged with the full detail content (definition, synonyms/antonyms, origin).
// Content scrolls in the upper region; the example panel is pinned at the bottom.
// A TextToSpeech bound to the composition; shut down on dispose.
@Composable
private fun rememberTts(): TextToSpeech {
    val context = LocalContext.current
    val tts = remember { TextToSpeech(context.applicationContext) {} }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    return tts
}

@Composable
fun TodayScreen(word: Word, isSaved: Boolean, onToggleSave: () -> Unit) {
    val theme = LocalTheme.current
    val tts = rememberTts()

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
        // Scrollable content fills the space above the pinned example panel.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(dateLine, fontFamily = Fonts.mono, fontSize = 10.5.sp, letterSpacing = 1.2.sp, color = theme.palette.muted)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dayCount, fontFamily = Fonts.mono, fontSize = 10.5.sp, letterSpacing = 1.2.sp, color = theme.accent)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onToggleSave)
                            .padding(4.dp),
                    ) {
                        BookmarkIcon(filled = isSaved, tint = if (isSaved) theme.accent else theme.palette.muted)
                    }
                }
            }

            // Hero word + pronounce + accent bar + pos/ipa (Today highlight)
            Column(modifier = Modifier.padding(horizontal = 26.dp).padding(top = 26.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = word.word,
                        fontFamily = Fonts.serif,
                        fontSize = 60.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-1.5).sp,
                        color = theme.palette.fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable { tts.speak(word.word, TextToSpeech.QUEUE_FLUSH, null, "word") }
                            .background(theme.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("▶", fontSize = 13.sp, color = Color.White)
                    }
                }
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

            // Definition (detail content)
            Text(
                text = word.definition,
                fontFamily = Fonts.sans,
                fontSize = 16.5.sp,
                lineHeight = 24.sp,
                color = theme.palette.fg,
                modifier = Modifier.padding(horizontal = 26.dp).padding(top = 22.dp),
            )

            // Synonyms / antonyms
            val hasSyn = !word.synonyms.isNullOrEmpty()
            val hasAnt = !word.antonyms.isNullOrEmpty()
            if (hasSyn || hasAnt) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(top = 26.dp),
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

            // Origin
            if (!word.origin.isNullOrEmpty()) {
                Box(
                    Modifier
                        .padding(horizontal = 26.dp).padding(vertical = 22.dp)
                        .fillMaxWidth().height(1.dp).background(theme.palette.line),
                )
                SectionLabel("ORIGIN", modifier = Modifier.padding(horizontal = 26.dp))
                Text(
                    word.origin,
                    fontFamily = Fonts.sans, fontSize = 14.sp, lineHeight = 22.sp,
                    color = theme.palette.muted,
                    modifier = Modifier.padding(horizontal = 26.dp).padding(top = 10.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // Full-bleed accent-washed example panel pinned to the bottom (Today highlight).
        if (!word.example.isNullOrEmpty()) {
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

// Bookmark glyph drawn with Canvas so no icon dependency is needed.
@Composable
private fun BookmarkIcon(filled: Boolean, tint: Color) {
    Canvas(modifier = Modifier.size(width = 13.dp, height = 17.dp)) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(size.width / 2f, size.height * 0.62f)
            lineTo(0f, size.height)
            close()
        }
        if (filled) drawPath(path, tint)
        else drawPath(path, tint, style = Stroke(width = 1.6.dp.toPx()))
    }
}

// MARK: - Saved

@Composable
fun SavedScreen(saved: List<Pair<Int, Word>>, onRemove: (Int) -> Unit) {
    val theme = LocalTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.palette.bg)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp)
            .padding(bottom = 20.dp),
    ) {
        Text(
            "Saved", fontFamily = Fonts.serif, fontSize = 32.sp, fontWeight = FontWeight.Medium,
            letterSpacing = (-0.6).sp, color = theme.palette.fg,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        )
        if (saved.isEmpty()) {
            Text(
                "Words you save will appear here.",
                fontFamily = Fonts.sans, fontSize = 14.sp, color = theme.palette.muted,
                modifier = Modifier.padding(top = 14.dp),
            )
        } else {
            saved.forEach { (i, w) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(w.word, fontFamily = Fonts.serif, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = theme.palette.fg)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!w.pos.isNullOrEmpty()) {
                                Text(w.pos.uppercase(Locale.getDefault()), fontFamily = Fonts.mono, fontSize = 9.5.sp, letterSpacing = 1.2.sp, color = theme.accent)
                            }
                            if (!w.short.isNullOrEmpty()) {
                                Text(w.short, fontFamily = Fonts.sans, fontSize = 13.sp, color = theme.palette.muted)
                            }
                        }
                    }
                    Text(
                        "✕", fontSize = 14.sp, color = theme.palette.muted,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onRemove(i) }.padding(4.dp),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(theme.palette.line))
            }
        }
    }
}
