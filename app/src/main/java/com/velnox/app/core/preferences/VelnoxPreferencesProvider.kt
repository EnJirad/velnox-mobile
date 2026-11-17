package com.velnox.app.core.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.velnox.app.VelnoxApplication
import kotlinx.coroutines.launch

/**
 * Preferences access for composables.
 *
 * The repository itself is process-scoped (see [VelnoxApplication]); this class
 * only exposes the current snapshot plus the setters a screen needs, so feature
 * code never touches DataStore directly.
 */
class PreferencesState(
    val current: UserPreferences,
    val setLanguage: (AppLanguage) -> Unit,
    val setThemeMode: (ThemeMode) -> Unit,
    val setNotifyOrderUpdates: (Boolean) -> Unit,
    val setNotifyPromotions: (Boolean) -> Unit,
    val setNotifySecurity: (Boolean) -> Unit,
)

val LocalPreferences = staticCompositionLocalOf<PreferencesState> {
    error("LocalPreferences is not provided. Wrap the tree in VelnoxPreferencesProvider.")
}

@Composable
fun VelnoxPreferencesProvider(content: @Composable () -> Unit) {
    val application = LocalContext.current.applicationContext as VelnoxApplication
    val repository = application.preferences
    val preferences by repository.preferences.collectAsState(initial = UserPreferences())
    val scope = rememberCoroutineScope()

    val state = PreferencesState(
        current = preferences,
        setLanguage = { scope.launch { repository.setLanguage(it) } },
        setThemeMode = { scope.launch { repository.setThemeMode(it) } },
        setNotifyOrderUpdates = { scope.launch { repository.setNotifyOrderUpdates(it) } },
        setNotifyPromotions = { scope.launch { repository.setNotifyPromotions(it) } },
        setNotifySecurity = { scope.launch { repository.setNotifySecurity(it) } },
    )

    CompositionLocalProvider(LocalPreferences provides state) {
        content()
    }
}
