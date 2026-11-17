package com.velnox.app

import android.app.Application
import com.velnox.app.core.preferences.PreferencesRepository

/**
 * Application entry point. Owns the single preferences repository so the
 * DataStore instance lives for the whole process, not per composition.
 */
class VelnoxApplication : Application() {

    lateinit var preferences: PreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = PreferencesRepository(this)
    }
}
