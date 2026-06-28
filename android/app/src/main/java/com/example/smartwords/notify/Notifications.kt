// Daily word notification. Mirrors the iOS Notifier: one reminder a day at 9 AM
// showing the word that will be current then. Uses AlarmManager (framework, no
// extra dependency) with an inexact daily repeat — exact timing isn't needed for
// a word-of-the-day nudge, so we avoid the SCHEDULE_EXACT_ALARM permission.

package com.example.smartwords.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import com.example.smartwords.MainActivity
import com.example.smartwords.R
import com.example.smartwords.data.SettingsRepository
import com.example.smartwords.data.WordStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

private const val CHANNEL_ID = "daily-word"
private const val NOTIF_ID = 1
private const val REQUEST_CODE = 100
private const val HOUR_OF_DAY = 9

object Notifications {

    /** Enable (schedule daily) or disable (cancel) the reminder. */
    fun apply(ctx: Context, enabled: Boolean) {
        if (enabled) schedule(ctx) else cancel(ctx)
    }

    private fun schedule(ctx: Context) {
        ensureChannel(ctx)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next9amMillis(),
            AlarmManager.INTERVAL_DAY,
            pendingIntent(ctx),
        )
    }

    private fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(ctx))
    }

    private fun next9amMillis(): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HOUR_OF_DAY)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    private fun pendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, WordAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            ctx, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Word of the day", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /** Post the reminder for whatever word is current right now. Suspends on the DataStore read. */
    suspend fun post(ctx: Context) {
        ensureChannel(ctx)
        val rotation = SettingsRepository.read(ctx).rotationHours
        val word = WordStore.word(WordStore.words(ctx), rotation)

        val tap = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(word.word)
            .setContentText(word.short ?: word.definition)
            .setStyle(NotificationCompat.BigTextStyle().bigText(word.definition))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()

        // POST_NOTIFICATIONS is requested in the UI; if revoked, notify() is a no-op.
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, notif) }
    }
}

class WordAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try { Notifications.post(app) } finally { pending.finish() }
        }
    }
}
