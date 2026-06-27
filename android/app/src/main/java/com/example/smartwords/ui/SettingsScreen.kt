// Settings — appearance segmented control, accent swatches, and the DAILY WORD
// section (notification toggle, rotation control, widget preview card).
// Mirrors the SettingsView in ios/App/SmartWordsApp.swift.

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartwords.data.AppSettingsState
import com.example.smartwords.data.ThemeMode
import com.example.smartwords.data.Word
import java.util.Locale

@Composable
fun SettingsScreen(
    state: AppSettingsState,
    previewWord: Word,
    onMode: (ThemeMode) -> Unit,
    onAccent: (String) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onRotation: (Int) -> Unit,
) {
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
            "Settings",
            fontFamily = Fonts.serif, fontSize = 32.sp, fontWeight = FontWeight.Medium,
            letterSpacing = (-0.6).sp, color = theme.palette.fg,
            modifier = Modifier.padding(top = 6.dp),
        )

        SectionLabel("APPEARANCE", modifier = Modifier.padding(top = 30.dp))
        Segmented(state.mode, onMode)

        SectionLabel("ACCENT COLOR", modifier = Modifier.padding(top = 30.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Selected", fontFamily = Fonts.sans, fontSize = 14.sp, color = theme.palette.muted)
            Text(
                Accents.by(state.accentId).name,
                fontFamily = Fonts.sans, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = theme.accent,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Accents.all.forEach { accent -> Swatch(accent, selected = accent.id == state.accentId, onClick = { onAccent(accent.id) }) }
        }

        SectionLabel("DAILY WORD", modifier = Modifier.padding(top = 30.dp))
        NotifRow(on = state.notifications, onChange = onNotifications)
        ThinLine()
        RotationRow(hours = state.rotationHours, onSelect = onRotation)
        ThinLine()
        WidgetRow(previewWord)
    }
}

@Composable
private fun Segmented(mode: ThemeMode, onMode: (ThemeMode) -> Unit) {
    val theme = LocalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(theme.palette.line)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        seg("Light", ThemeMode.LIGHT, mode, onMode)
        seg("Dark", ThemeMode.DARK, mode, onMode)
        seg("Auto", ThemeMode.AUTO, mode, onMode)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.seg(
    label: String,
    value: ThemeMode,
    current: ThemeMode,
    onMode: (ThemeMode) -> Unit,
) {
    val theme = LocalTheme.current
    val on = current == value
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) theme.palette.surface else Color.Transparent)
            .clickable { onMode(value) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = Fonts.sans, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = if (on) theme.palette.fg else theme.palette.muted,
        )
    }
}

@Composable
private fun Swatch(accent: Accent, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalTheme.current
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.color)
                .then(
                    if (selected) Modifier
                        .border(2.5.dp, theme.palette.bg, CircleShape)
                    else Modifier
                )
                .clickable(onClick = onClick),
        )
        if (selected) {
            // Outer accent ring.
            Box(
                Modifier
                    .size(46.dp)
                    .border(2.dp, accent.color, CircleShape)
            )
        }
    }
}

@Composable
private fun NotifRow(on: Boolean, onChange: (Boolean) -> Unit) {
    val theme = LocalTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text("Daily notification", fontFamily = Fonts.sans, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = theme.palette.fg)
            Text("Delivered at 9:00 AM", fontFamily = Fonts.sans, fontSize = 12.5.sp, color = theme.palette.muted)
        }
        ToggleSwitch(on = on, onChange = onChange)
    }
}

@Composable
private fun RotationRow(hours: Int, onSelect: (Int) -> Unit) {
    val theme = LocalTheme.current
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text("New word", fontFamily = Fonts.sans, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = theme.palette.fg)
            Text("How often the widget advances", fontFamily = Fonts.sans, fontSize = 12.5.sp, color = theme.palette.muted)
        }
        Box {
            Text(
                label(hours),
                fontFamily = Fonts.sans, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = theme.accent,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { expanded = true }.padding(6.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf(1, 3, 6, 12, 24).forEach { h ->
                    DropdownMenuItem(
                        text = { Text(label(h)) },
                        onClick = { expanded = false; onSelect(h) },
                    )
                }
            }
        }
    }
}

private fun label(hours: Int): String = if (hours >= 24) "Daily" else "${hours}h"

@Composable
private fun WidgetRow(word: Word) {
    val theme = LocalTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text("Widgets", fontFamily = Fonts.sans, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = theme.palette.fg)
            Text("Small & Medium · on Home", fontFamily = Fonts.sans, fontSize = 12.5.sp, color = theme.palette.muted)
        }
        // Mini word card preview.
        Column(
            modifier = Modifier
                .size(width = 78.dp, height = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.accent.copy(alpha = 0.10f).compositeOverSurface(theme.palette.surface))
                .border(1.dp, theme.palette.line, RoundedCornerShape(12.dp))
                .padding(horizontal = 11.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                word.word,
                fontFamily = Fonts.serif, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = theme.palette.fg,
                maxLines = 1,
            )
            Text(
                (word.pos ?: "word").uppercase(Locale.getDefault()),
                fontFamily = Fonts.mono, fontSize = 7.sp, letterSpacing = 0.8.sp, color = theme.accent,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun Color.compositeOverSurface(surface: Color): Color {
    val a = alpha
    return Color(
        red = red * a + surface.red * (1 - a),
        green = green * a + surface.green * (1 - a),
        blue = blue * a + surface.blue * (1 - a),
        alpha = 1f,
    )
}

@Composable
private fun ThinLine() {
    val theme = LocalTheme.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(theme.palette.line))
}
