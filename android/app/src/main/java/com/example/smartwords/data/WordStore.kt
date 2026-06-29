// Word model + store. Shared by the app and the Glance widget.
// Bundled seed asset is the source of words (831 entries). Selection is a pure
// function of the clock + rotation speed, mirroring ios/Shared/WordStore.swift.

package com.example.smartwords.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

// Lexica design fields (ipa/synonyms/antonyms/origin) are optional — the seed has
// none yet, so detail screens render those sections only when present.
data class Word(
    val word: String,
    val definition: String,
    val short: String? = null,   // 2-3 word gloss for the widget; falls back to definition
    val pos: String? = null,     // part of speech
    val example: String? = null, // usage sentence
    val ipa: String? = null,
    val synonyms: List<String>? = null,
    val antonyms: List<String>? = null,
    val origin: String? = null,
)

object WordStore {
    const val DEFAULT_ROTATION_HOURS = 3

    // Cached so the word list is parsed once per process.
    @Volatile private var cached: List<Word>? = null

    /** Drop the in-process cache so the next read re-parses (after a batch fetch). */
    fun invalidate() { cached = null }

    /** Today's words: the fetched daily batch if cached, else the bundled seed. Never empty. */
    fun words(ctx: Context): List<Word> {
        cached?.let { return it }
        val parsed = cachedBatch(ctx)
            ?: runCatching { parse(readAsset(ctx)) }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: listOf(Word("smartwords", "a word a day, on your home screen.", short = "word a day", pos = "noun"))
        cached = parsed
        return parsed
    }

    /** The daily batch written by [BatchWorker], or null if absent/invalid. */
    private fun cachedBatch(ctx: Context): List<Word>? {
        val f = File(ctx.filesDir, "batch.json")
        if (!f.exists()) return null
        return runCatching { parse(f.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Parse a batch/seed JSON string into words, or empty on any failure. Used by the worker to validate. */
    fun parseOrEmpty(json: String): List<Word> = runCatching { parse(json) }.getOrDefault(emptyList())

    fun batchFile(ctx: Context): File = File(ctx.filesDir, "batch.json")

    /**
     * Index of the word for a given moment — pure function of clock + rotation.
     * Advances one slot every [rotationHours] and a full day's worth per day, so it
     * walks the whole list and never repeats the same day's set.
     */
    fun index(words: List<Word>, rotationHours: Int, cal: Calendar = Calendar.getInstance()): Int {
        if (words.isEmpty()) return 0
        val hours = rotationHours.coerceIn(1, 24)
        val slotsPerDay = 24 / hours
        // dayOfEra: whole days since epoch — equivalent to iOS's ordinality(of:.day,in:.era).
        val dayOfEra = (cal.timeInMillis / TimeUnit.DAYS.toMillis(1)).toInt()
        val slot = cal.get(Calendar.HOUR_OF_DAY) / hours
        val raw = (dayOfEra.toLong() * slotsPerDay + slot) % words.size
        return ((raw + words.size) % words.size).toInt()
    }

    fun word(words: List<Word>, rotationHours: Int, cal: Calendar = Calendar.getInstance()): Word =
        words[index(words, rotationHours, cal)]

    private fun readAsset(ctx: Context): String =
        ctx.assets.open("seed-words.json").bufferedReader().use { it.readText() }

    private fun parse(json: String): List<Word> {
        val arr = JSONObject(json).getJSONArray("words")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Word(
                word = o.getString("word"),
                definition = o.getString("definition"),
                short = o.optString("short").ifEmpty { null },
                pos = o.optString("pos").ifEmpty { null },
                example = o.optString("example").ifEmpty { null },
                ipa = o.optString("ipa").ifEmpty { null },
                synonyms = o.optJSONArray("synonyms")?.let { a -> (0 until a.length()).map { a.getString(it) } },
                antonyms = o.optJSONArray("antonyms")?.let { a -> (0 until a.length()).map { a.getString(it) } },
                origin = o.optString("origin").ifEmpty { null },
            )
        }
    }
}
