package com.velnox.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.velnox.app.core.auth.AuthApi
import com.velnox.app.core.auth.AuthRemoteDataSource
import com.velnox.app.core.auth.AuthRepository
import com.velnox.app.core.auth.GoogleClientIdSource
import com.velnox.app.core.catalog.CatalogRemoteDataSource
import com.velnox.app.core.catalog.CatalogRepository
import com.velnox.app.core.customer.CustomerRemoteDataSource
import com.velnox.app.core.customer.CustomerRepository
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
 * - [catalog] — the public product catalogue, cached in memory across screens.
 * - [customer] — the authenticated customer API (profile, addresses, cart, orders…).
 *
 * Wiring, in one place: `SessionVault` (Android Keystore) → `SessionManager`
 * (single in-memory token + expiry) → `AuthRemoteDataSource` (OkHttp) →
 * `AuthRepository` (rules) → `SessionController` (Compose state).
 */
class VelnoxApplication : Application(), ImageLoaderFactory {

    lateinit var preferences: PreferencesRepository
        private set

    lateinit var session: SessionController
        private set

    /** Shared by the repository and the Google flow, so there is one client. */
    lateinit var authApi: AuthApi
        private set

    lateinit var googleClientId: GoogleClientIdSource
        private set

    /**
     * The public catalogue. One instance for the process, so the product list and
     * its images survive tab switches without re-requesting them, and the app has
     * a single place that knows the API base URL.
     */
    lateinit var catalog: CatalogRepository
        private set

    /** Authenticated customer data; attaches the session token and reacts to a 401. */
    lateinit var customer: CustomerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("VelnoxCrash", "Uncaught exception on thread ${thread.name}", throwable)
            runCatching {
                val crashFile = java.io.File(filesDir, "crash.log")
                crashFile.writeText("${System.currentTimeMillis()}: ${throwable.stackTraceToString()}\n")
            }
        }
        preferences = PreferencesRepository(this)

        val api = AuthRemoteDataSource()
        authApi = api
        googleClientId = GoogleClientIdSource(api)

        catalog = CatalogRepository(CatalogRemoteDataSource())

        val sessions = SessionManager(SessionVault(this))
        session = SessionController(
            repository = AuthRepository(
                api = api,
                sessions = sessions,
            ),
        )

        customer = CustomerRepository(
            api = CustomerRemoteDataSource(),
            token = { sessions.current() },
            onUnauthorized = { session.onUnauthorized() },
        )
    }

    /**
     * Optimized ImageLoader for e-commerce performance:
     * - Strict memory cache cap (15% max available heap) prevents memory pressure and crashes on low-RAM phones.
     * - 50MB LRU disk cache caches product thumbnails and promotional banners across sessions.
     * - allowRgb565(true) uses 2 bytes per pixel instead of 4 for opaque product photos on low memory devices.
     * - respectCacheHeaders(false) ensures catalog images remain cached during browsing sessions.
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .allowRgb565(true)
            .build()
    }
}
