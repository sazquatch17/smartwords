// DataStore-backed settings shared between the app and the widget.
// Theme (light/dark/auto), accent, daily-notification toggle, and rotation hours.
// Changing theme/accent/rotation triggers a widget refresh from the caller.

package com.example.smartwords.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { LIGHT, DARK, AUTO }

// One DataStore for the whole app + widget process.
val Context.dataStore by preferencesDataStore("smartwords")

data class AppSettingsState(
    val mode: ThemeMode = ThemeMode.AUTO,
    val accentId: String = "amber",
    val notifications: Boolean = true,
    val rotationHours: Int = WordStore.DEFAULT_ROTATION_HOURS,
    val savedIds: List<Int> = emptyList(),   // word indices, most-recent first
)

object SettingsRepository {
    private val KEY_THEME = stringPreferencesKey("theme")
    private val KEY_ACCENT = stringPreferencesKey("accent")
    private val KEY_NOTIF = booleanPreferencesKey("notif")
    private val KEY_ROTATION = intPreferencesKey("rotationHours")
    private val KEY_SAVED = stringPreferencesKey("saved")   // CSV of indices, order preserved

    fun flow(ctx: Context): Flow<AppSettingsState> = ctx.dataStore.data.map { p ->
        AppSettingsState(
            mode = runCatching { ThemeMode.valueOf((p[KEY_THEME] ?: "AUTO").uppercase()) }
                .getOrDefault(ThemeMode.AUTO),
            accentId = p[KEY_ACCENT] ?: "amber",
            notifications = p[KEY_NOTIF] ?: true,
            rotationHours = (p[KEY_ROTATION] ?: WordStore.DEFAULT_ROTATION_HOURS)
                .takeIf { it > 0 } ?: WordStore.DEFAULT_ROTATION_HOURS,
            savedIds = (p[KEY_SAVED] ?: "").split(",").mapNotNull { it.toIntOrNull() },
        )
    }

    /** One-shot read for non-Compose callers (e.g. the widget). */
    suspend fun read(ctx: Context): AppSettingsState = flow(ctx).first()

    suspend fun setMode(ctx: Context, mode: ThemeMode) =
        ctx.dataStore.edit { it[KEY_THEME] = mode.name }

    suspend fun setAccent(ctx: Context, id: String) =
        ctx.dataStore.edit { it[KEY_ACCENT] = id }

    suspend fun setNotifications(ctx: Context, on: Boolean) =
        ctx.dataStore.edit { it[KEY_NOTIF] = on }

    suspend fun setRotationHours(ctx: Context, hours: Int) =
        ctx.dataStore.edit { it[KEY_ROTATION] = hours }

    suspend fun toggleSaved(ctx: Context, index: Int) {
        ctx.dataStore.edit { p ->
            val cur = (p[KEY_SAVED] ?: "").split(",").mapNotNull { it.toIntOrNull() }.toMutableList()
            if (cur.contains(index)) cur.remove(index) else cur.add(0, index)
            p[KEY_SAVED] = cur.joinToString(",")
        }
    }
}
