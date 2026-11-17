package com.velnox.app.core.preferences

import androidx.annotation.StringRes
import com.velnox.app.R

/** Appearance modes (§ global theme). SYSTEM delegates to the Android setting. */
enum class ThemeMode(
    val tag: String,
    @StringRes val labelRes: Int,
) {
    LIGHT("light", R.string.appearance_light),
    DARK("dark", R.string.appearance_dark),
    SYSTEM("system", R.string.appearance_system);

    companion object {
        fun fromTag(tag: String?): ThemeMode =
            entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * Local-only preferences. Nothing here is derived from a backend, and no auth
 * state is stored: Phase 3 owns accounts.
 */
data class UserPreferences(
    val language: AppLanguage = AppLanguage.fromDeviceLocale(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notifyOrderUpdates: Boolean = true,
    val notifyPromotions: Boolean = false,
    val notifySecurity: Boolean = true,
)
