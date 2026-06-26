// SmartWords — Android widget (reference implementation).
// Jetpack Glance homescreen widget. One periodic worker fetches the day's batch
// (once) and nudges the widget at each rotation slot; the widget only reads the
// cached batch and picks the current word. No third-party deps: HttpURLConnection
// + org.json (both bundled with Android).

package com.example.smartwords

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.net.URL
import java.util.Calendar

// MARK: - Model + cache

// pos/example optional: not every source provides them (the seed has no pos).
data class Word(
    val word: String,
    val definition: String,
    val pos: String? = null,
    val example: String? = null,
)

private val Context.dataStore by preferencesDataStore("smartwords")
private val BATCH = stringPreferencesKey("batch")

object WordStore {
    const val ROTATION_HOURS = 3
    // TODO: set to your real host (hosting deferred for the proof of concept).
    private const val HOST = "https://example.com/words"

    /** Today's words: cached batch if present, else bundled seed asset. Never empty. */
    suspend fun words(ctx: Context): List<Word> {
        val cached = ctx.dataStore.data.first()[BATCH]
        return cached?.let { parse(it) }?.takeIf { it.isNotEmpty() } ?: seed(ctx)
    }

    /** Word for a moment — pure function of clock + batch. */
    fun word(words: List<Word>, cal: Calendar = Calendar.getInstance()): Word {
        val slot = cal.get(Calendar.HOUR_OF_DAY) / ROTATION_HOURS
        return words[slot % words.size]
    }

    /** Fetch today's batch and cache it. Safe to call repeatedly; failures are swallowed. */
    suspend fun refresh(ctx: Context, date: String) {
        runCatching {
            val json = URL("$HOST/$date.json").readText()      // ponytail: stdlib GET, no OkHttp
            ctx.dataStore.edit { it[BATCH] = json }
        }
    }

    private fun seed(ctx: Context): List<Word> =
        runCatching { parse(ctx.assets.open("seed-words.json").bufferedReader().readText()) }
            .getOrElse { listOf(Word("smartwords", "a word a day, on your home screen.")) }

    private fun parse(json: String): List<Word> {
        val arr = JSONObject(json).getJSONArray("words")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Word(
                word = o.getString("word"),
                definition = o.getString("definition"),
                pos = o.optString("pos").ifEmpty { null },
                example = o.optString("example").ifEmpty { null },
            )
        }
    }
}

// MARK: - Widget (editorial / serif, native Noto Serif)

private fun serif(size: Int, weight: FontWeight = FontWeight.Normal,
                  style: FontStyle = FontStyle.Normal) = TextStyle(
    fontFamily = FontFamily.Serif, fontSize = size.sp, fontWeight = weight, fontStyle = style,
    color = ColorProvider(day = Color.Black, night = Color.White),
)

class SmartWordsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val w = WordStore.word(WordStore.words(context))
        provideContent {
            Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
                Text(w.word, style = serif(22, FontWeight.Medium))
                w.pos?.let { Text(it, style = serif(12, style = FontStyle.Italic)) }
                Text(w.definition, style = serif(13))
                w.example?.let { Text("“$it”", style = serif(12, style = FontStyle.Italic)) }
            }
        }
    }
}

class SmartWordsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = SmartWordsWidget()
}

// MARK: - Worker (daily fetch + rotation nudge)

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val cal = Calendar.getInstance()
        val date = "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        WordStore.refresh(applicationContext, date)
        SmartWordsWidget().updateAll(applicationContext)
        return Result.success()
    }
}

// Schedule from the app once (e.g. in onCreate):
//   PeriodicWorkRequestBuilder<UpdateWorker>(WordStore.ROTATION_HOURS.toLong(), TimeUnit.HOURS)
//   then WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(...)
// WorkManager floors periodic intervals at 15 min; 3h sits well above it.
