// Lexica — word-of-the-day app. Hosts the Today / Word Detail / Settings flow with
// live theming. Theme + accent + rotation are persisted in DataStore and shared
// with the Glance widget; changing them recolors the app live and refreshes the
// widget. Deep link smartwords://word/<index> opens that word's detail.

package com.example.smartwords

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.example.smartwords.ui.BottomBar
import com.example.smartwords.ui.LexicaTheme
import com.example.smartwords.ui.ResolvedTheme
import com.example.smartwords.ui.SavedScreen
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
    val currentWord = remember(settings.rotationHours) {
        WordStore.word(words, settings.rotationHours)
    }

    var tab by remember { mutableStateOf(Tab.TODAY) }

    // Deep link smartwords://word/<index> just opens Today (the current word).
    LaunchedEffect(deepLinkIndex) {
        if (deepLinkIndex >= 0) {
            tab = Tab.TODAY
            onDeepLinkConsumed()
        }
    }

    LexicaTheme(theme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.palette.bg),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    Tab.TODAY -> TodayScreen(word = currentWord)
                    Tab.SAVED -> SavedScreen()
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
            BottomBar(current = tab, onSelect = { selected -> tab = selected })
        }
    }
}
