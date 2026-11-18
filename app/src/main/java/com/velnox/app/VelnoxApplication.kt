package com.velnox.app

import android.app.Application
import com.velnox.app.core.auth.AuthRemoteDataSource
import com.velnox.app.core.auth.AuthRepository
import com.velnox.app.core.preferences.PreferencesRepository
import com.velnox.app.core.session.SessionController
import com.velnox.app.core.session.SessionManager
import com.velnox.app.core.session.SessionVault

/**
 * Application entry point. Owns the process-scoped singletons so they outlive a
 * composition and a configuration change:
 *
 * - [preferences] — language, appearance and notification switches (DataStore).
 * - [session] — authentication state, resolved once at startup.
 *
 * Wiring, in one place: `SessionVault` (Android Keystore) → `SessionManager`
 * (single in-memory token + expiry) → `AuthRemoteDataSource` (OkHttp) →
 * `AuthRepository` (rules) → `SessionController` (Compose state).
 */
class VelnoxApplication : Application() {

    lateinit var preferences: PreferencesRepository
        private set

    lateinit var session: SessionController
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = PreferencesRepository(this)

        val sessions = SessionManager(SessionVault(this))
        session = SessionController(
            repository = AuthRepository(
                api = AuthRemoteDataSource(),
                sessions = sessions,
            ),
        )
    }
}
