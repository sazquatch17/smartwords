// Lexica — word-of-the-day app. Hosts the Today / Word Detail / Settings flow with
// live theming. Theme + accent + rotation are persisted in DataStore and shared
// with the Glance widget; changing them recolors the app live and refreshes the
// widget. Deep link smartwords://word/<index> opens that word's detail.

package com.example.smartwords

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.smartwords.data.AppSettingsState
import com.example.smartwords.data.SettingsRepository
import com.example.smartwords.data.ThemeMode
import com.example.smartwords.data.WordStore
import com.example.smartwords.notify.Notifications
import com.example.smartwords.ui.BottomBar
import com.example.smartwords.ui.LexicaTheme
import com.example.smartwords.ui.ResolvedTheme
import com.example.smartwords.ui.SavedScreen
import com.example.smartwords.ui.SearchScreen
import com.example.smartwords.ui.SettingsScreen
import com.example.smartwords.ui.Tab
import com.example.smartwords.ui.TodayScreen
import com.example.smartwords.ui.Accents
import com.example.smartwords.ui.resolvePalette
import com.example.smartwords.widget.SmartWordsWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Word index to open from a deep link, or -1 for none.
    private var deepLinkIndex by mutableIntStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkIndex = parseDeepLink(intent)
        setContent { RootApp(deepLinkIndex) { deepLinkIndex = -1 } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkIndex = parseDeepLink(intent)
    }

    private fun parseDeepLink(intent: Intent?): Int {
        val uri = intent?.data ?: return -1
        if (uri.scheme != "smartwords" || uri.host != "word") return -1
        return uri.lastPathSegment?.toIntOrNull() ?: -1
    }
}

@Composable
private fun RootApp(deepLinkIndex: Int, onDeepLinkConsumed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by SettingsRepository.flow(context)
        .collectAsState(initial = AppSettingsState())

    val palette = resolvePalette(settings.mode)
    val theme = ResolvedTheme(palette = palette, accent = Accents.by(settings.accentId).color)

    // Words + current word are derived from the persisted rotation speed.
    val words = remember { WordStore.words(context) }
    val currentIndex = remember(settings.rotationHours) {
        WordStore.index(words, settings.rotationHours)
    }
    val currentWord = words[currentIndex]

    var tab by remember { mutableStateOf(Tab.TODAY) }
    var searchOpen by remember { mutableStateOf(false) }
    var browseIndex by remember { mutableStateOf<Int?>(null) }

    // Deep link smartwords://word/<index> just opens Today (the current word).
    LaunchedEffect(deepLinkIndex) {
        if (deepLinkIndex >= 0) {
            tab = Tab.TODAY
            onDeepLinkConsumed()
        }
    }

    // Daily reminder: schedule/cancel with the toggle, asking for the Android 13+
    // notification permission the first time it's turned on.
    val needsPerm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> Notifications.apply(context, granted) }
    LaunchedEffect(settings.notifications) {
        if (!settings.notifications) {
            Notifications.apply(context, false)
        } else if (!needsPerm || ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Notifications.apply(context, true)
        } else {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LexicaTheme(theme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.palette.bg),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                val bi = browseIndex
                when {
                    bi != null && bi in words.indices -> TodayScreen(
                        word = words[bi],
                        isSaved = settings.savedIds.contains(bi),
                        onToggleSave = { scope.launch { SettingsRepository.toggleSaved(context, bi) } },
                        isToday = false,
                        onBack = { browseIndex = null },
                    )
                    searchOpen -> SearchScreen(
                        words = words,
                        onPick = { browseIndex = it },
                        onClose = { searchOpen = false },
                    )
                    else -> when (tab) {
                    Tab.TODAY -> TodayScreen(
                        word = currentWord,
                        isSaved = settings.savedIds.contains(currentIndex),
                        onToggleSave = {
                            scope.launch { SettingsRepository.toggleSaved(context, currentIndex) }
                        },
                        onSearch = { searchOpen = true },
                    )
                    Tab.SAVED -> SavedScreen(
                        saved = settings.savedIds.filter { it in words.indices }.map { it to words[it] },
                        onRemove = { i -> scope.launch { SettingsRepository.toggleSaved(context, i) } },
                    )
                    Tab.SETTINGS -> SettingsScreen(
                        state = settings,
                        previewWord = currentWord,
                        onMode = { m ->
                            scope.launch {
                                SettingsRepository.setMode(context, m)
                                SmartWordsWidget().updateAll(context)
                            }
                        },
                        onAccent = { id ->
                            scope.launch {
                                SettingsRepository.setAccent(context, id)
                                SmartWordsWidget().updateAll(context)
                            }
                        },
                        onNotifications = { on ->
                            scope.launch { SettingsRepository.setNotifications(context, on) }
                        },
                        onRotation = { h ->
                            scope.launch {
                                SettingsRepository.setRotationHours(context, h)
                                SmartWordsWidget().updateAll(context)
                            }
                        },
                    )
                    }
                }
            }
            BottomBar(current = tab, onSelect = { selected ->
                searchOpen = false
                browseIndex = null
                tab = selected
            })
        }
    }
}
