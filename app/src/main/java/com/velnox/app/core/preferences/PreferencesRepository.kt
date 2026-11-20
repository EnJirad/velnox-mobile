package com.velnox.app.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val STORE_NAME = "velnox_preferences"

private val Context.velnoxDataStore: DataStore<Preferences> by preferencesDataStore(name = STORE_NAME)

/**
 * Single source of truth for language, appearance and notification switches.
 *
 * DataStore writes are atomic and survive process death, so the selected
 * language and theme are restored on the next cold start.
 */
class PreferencesRepository(context: Context) {

    private val store = context.applicationContext.velnoxDataStore

    private object Keys {
        val language = stringPreferencesKey("language")
        val theme = stringPreferencesKey("theme_mode")
        val notifyOrders = booleanPreferencesKey("notify_order_updates")
        val notifyPromotions = booleanPreferencesKey("notify_promotions")
        val notifySecurity = booleanPreferencesKey("notify_security")
        val avatarUri = stringPreferencesKey("profile_avatar_uri")
        val coverUri = stringPreferencesKey("profile_cover_uri")
    }

    val preferences: Flow<UserPreferences> = store.data
        .catch { error ->
            // A corrupted file must not crash the app; fall back to defaults.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { values ->
            UserPreferences(
                language = AppLanguage.fromTag(values[Keys.language]),
                themeMode = ThemeMode.fromTag(values[Keys.theme]),
                notifyOrderUpdates = values[Keys.notifyOrders] ?: true,
                notifyPromotions = values[Keys.notifyPromotions] ?: false,
                notifySecurity = values[Keys.notifySecurity] ?: true,
                avatarUri = values[Keys.avatarUri]?.takeIf { it.isNotBlank() },
                coverUri = values[Keys.coverUri]?.takeIf { it.isNotBlank() },
            )
        }

    suspend fun setLanguage(language: AppLanguage) {
        store.edit { it[Keys.language] = language.tag }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.theme] = mode.tag }
    }

    suspend fun setNotifyOrderUpdates(enabled: Boolean) {
        store.edit { it[Keys.notifyOrders] = enabled }
    }

    suspend fun setNotifyPromotions(enabled: Boolean) {
        store.edit { it[Keys.notifyPromotions] = enabled }
    }

    suspend fun setNotifySecurity(enabled: Boolean) {
        store.edit { it[Keys.notifySecurity] = enabled }
    }

    suspend fun setAvatarUri(uri: String?) {
        store.edit {
            if (uri.isNullOrBlank()) it.remove(Keys.avatarUri) else it[Keys.avatarUri] = uri
        }
    }

    suspend fun setCoverUri(uri: String?) {
        store.edit {
            if (uri.isNullOrBlank()) it.remove(Keys.coverUri) else it[Keys.coverUri] = uri
        }
    }
}
