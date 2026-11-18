package com.velnox.app

import android.app.Application
import com.velnox.app.core.auth.AuthApi
import com.velnox.app.core.auth.AuthRemoteDataSource
import com.velnox.app.core.auth.AuthRepository
import com.velnox.app.core.auth.GoogleClientIdSource
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
 * - [googleClientId] — resolves the public Web OAuth client id for Google
 *   Sign-In, from the build or from the API's config probe.
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

    /** Shared by the repository and the Google flow, so there is one client. */
    lateinit var authApi: AuthApi
        private set

    lateinit var googleClientId: GoogleClientIdSource
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = PreferencesRepository(this)

        val api = AuthRemoteDataSource()
        authApi = api
        googleClientId = GoogleClientIdSource(api)

        val sessions = SessionManager(SessionVault(this))
        session = SessionController(
            repository = AuthRepository(
                api = api,
                sessions = sessions,
            ),
        )
    }
}
