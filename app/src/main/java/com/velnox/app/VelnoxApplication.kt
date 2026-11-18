package com.velnox.app

import android.app.Application
import com.velnox.app.core.preferences.PreferencesRepository
import com.velnox.app.core.session.SessionController

/**
 * Application entry point. Owns the process-scoped singletons so they outlive a
 * single composition and a configuration change:
 *
 * - [preferences] — language, appearance and notification switches (DataStore).
 * - [session] — guest/authenticated state resolved once at startup.
 */
class VelnoxApplication : Application() {

    lateinit var preferences: PreferencesRepository
        private set

    lateinit var session: SessionController
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = PreferencesRepository(this)
        // Part 3 injects the real, Keystore-backed SessionStore here.
        session = SessionController()
    }
}
