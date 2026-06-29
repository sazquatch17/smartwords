// Daily batch pipeline (Android side). A WorkManager CoroutineWorker fetches that
// day's word batch from a static host, validates it, and writes it to the app's
// files dir; the app + Glance widget then read it via WordStore.words(), falling
// back to the bundled seed. The widget itself never makes a network call.
//
// Hosting is deferred (see SPEC.md): HOST is empty, so the worker no-ops and the
// app runs entirely on the seed. Point HOST at a static base URL to enable.

package com.example.smartwords.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import com.example.smartwords.widget.SmartWordsWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object BatchConfig {
    // e.g. "https://example.com" -> fetches "<HOST>/words/2026-06-24.json".
    const val HOST = ""
    const val UNIQUE_WORK = "daily-batch"

    /** Enqueue the once-a-day fetch (kept if already scheduled). */
    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<BatchWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class BatchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (BatchConfig.HOST.isEmpty()) return@withContext Result.success()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        try {
            val conn = (URL("${BatchConfig.HOST}/words/$date.json").openConnection() as HttpURLConnection)
                .apply { connectTimeout = 10_000; readTimeout = 10_000 }
            try {
                if (conn.responseCode != 200) return@withContext Result.retry()
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                // Validate before writing so a bad payload never replaces good data.
                if (WordStore.parseOrEmpty(body).isEmpty()) return@withContext Result.retry()
                WordStore.batchFile(applicationContext).writeText(body)
                WordStore.invalidate()
                SmartWordsWidget().updateAll(applicationContext)
                Result.success()
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result.retry()   // keep the previous cache / seed; try again later
        }
    }
}
