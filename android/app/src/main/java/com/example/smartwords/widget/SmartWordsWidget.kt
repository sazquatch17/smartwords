// SmartWords — redesigned Jetpack Glance home-screen widget (Lexica design).
// SMALL  : "WORD" label + accent dot, big serif word, pos (accent), short gloss.
// MEDIUM+: "WORD OF THE DAY" + date, serif word + IPA, accent vertical bar +
//          definition + italic example.
// Theme + accent + rotation are read from the shared DataStore. Tapping opens the
// app at that word via the smartwords://word/<index> deep link.

package com.example.smartwords.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.smartwords.MainActivity
import com.example.smartwords.data.SettingsRepository
import com.example.smartwords.data.ThemeMode
import com.example.smartwords.data.Word
import com.example.smartwords.data.WordStore
import com.example.smartwords.ui.Accents
import com.example.smartwords.ui.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmartWordsWidget : GlanceAppWidget() {

    // Exact reports the widget's real current size, so SMALL vs MEDIUM is chosen
    // live as the user resizes the widget on the home screen.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = SettingsRepository.read(context)
        val words = WordStore.words(context)
        val index = WordStore.index(words, settings.rotationHours)
        val word = words[index]
        val accent = Accents.by(settings.accentId).color
        val mode = settings.mode

        provideContent {
            GlanceTheme {
                WidgetContent(word = word, index = index, accent = accent, mode = mode)
            }
        }
    }
}

@Composable
private fun WidgetContent(word: Word, index: Int, accent: Color, mode: ThemeMode) {
    val context = LocalContext.current
    val size = LocalSize.current
    val wide = size.width >= 220.dp

    // Open the app at this word: smartwords://word/<index>.
    val openIntent = Intent(
        Intent.ACTION_VIEW,
        "smartwords://word/$index".toUri(),
        context,
        MainActivity::class.java,
    )
    val openAction = actionStartActivity(openIntent)

    val accentProvider = ColorProvider(accent, accent)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(themed(mode, Palette.Light.surface, Palette.Dark.surface))
            .cornerRadius(26.dp)
            .padding(if (wide) 16.dp else 14.dp)
            .clickable(openAction),
    ) {
        if (wide) MediumLayout(word, accentProvider, mode)
        else SmallLayout(word, accentProvider, mode)
    }
}

@Composable
private fun SmallLayout(word: Word, accent: ColorProvider, mode: ThemeMode) {
    val fg = themed(mode, Palette.Light.fg, Palette.Dark.fg)
    val muted = themed(mode, Palette.Light.muted, Palette.Dark.muted)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "WORD",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace, fontSize = 8.sp,
                    fontWeight = FontWeight.Medium, color = muted,
                ),
            )
            Spacer(GlanceModifier.defaultWeight())
            Box(modifier = GlanceModifier.size(7.dp).cornerRadius(4.dp).background(accent)) {}
        }
        Spacer(GlanceModifier.height(12.dp))
        Text(
            word.word,
            maxLines = 1,
            style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = fg),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            (word.pos ?: "WORD").uppercase(Locale.getDefault()),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = accent),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            word.short ?: word.definition,
            maxLines = 3,
            style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 10.sp, color = muted),
        )
    }
}

@Composable
private fun MediumLayout(word: Word, accent: ColorProvider, mode: ThemeMode) {
    val fg = themed(mode, Palette.Light.fg, Palette.Dark.fg)
    val muted = themed(mode, Palette.Light.muted, Palette.Dark.muted)
    val date = SimpleDateFormat("MMM d", Locale.getDefault())
        .format(Date()).uppercase(Locale.getDefault())

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("WORD OF THE DAY", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, color = muted))
            Spacer(GlanceModifier.defaultWeight())
            Text(date, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, color = muted))
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                word.word,
                maxLines = 1,
                style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = fg),
            )
            if (!word.ipa.isNullOrEmpty()) {
                Spacer(GlanceModifier.width(10.dp))
                Text(word.ipa, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = muted))
            }
        }
        Spacer(GlanceModifier.height(9.dp))
        Row {
            Box(modifier = GlanceModifier.width(2.dp).height(40.dp).cornerRadius(2.dp).background(accent)) {}
            Spacer(GlanceModifier.width(8.dp))
            Column {
                Text(
                    word.definition,
                    maxLines = 2,
                    style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.5.sp, color = fg),
                )
                if (!word.example.isNullOrEmpty()) {
                    Spacer(GlanceModifier.height(5.dp))
                    Text(
                        "“${word.example}”",
                        maxLines = 1,
                        style = TextStyle(
                            fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                            fontSize = 11.sp, color = muted,
                        ),
                    )
                }
            }
        }
    }
}

// day/night ColorProvider honoring an explicit theme mode. AUTO provides distinct
// day/night colors (system decides); LIGHT/DARK pin both sides.
private fun themed(mode: ThemeMode, lightColor: Color, darkColor: Color): ColorProvider =
    when (mode) {
        ThemeMode.LIGHT -> ColorProvider(lightColor, lightColor)
        ThemeMode.DARK -> ColorProvider(darkColor, darkColor)
        ThemeMode.AUTO -> ColorProvider(day = lightColor, night = darkColor)
    }

class SmartWordsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmartWordsWidget()
}
