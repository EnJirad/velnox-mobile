package com.velnox.app.core.session

import com.velnox.app.core.auth.Jwt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the session token: where it is stored, when it is stale, and how it is
 * destroyed.
 *
 * Everything that needs a token asks this class, so there is exactly one copy in
 * memory and one on disk. The token is never exposed to the UI layer.
 */
class SessionManager(
    private val store: SessionStore,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val mutex = Mutex()
    private var cached: String? = null
    private var loaded = false

    /** The current token, loading it from secure storage on first use. */
    suspend fun current(): String? = mutex.withLock {
        if (!loaded) {
            cached = try {
                store.read()?.token
            } catch (_: Exception) {
                null
            }
            loaded = true
        }
        cached
    }

    /** Persist a freshly issued token and keep it in memory. */
    suspend fun save(token: String) = mutex.withLock {
        store.write(StoredSession(token))
        cached = token
        loaded = true
    }

    /** Forget the token everywhere. Safe to call when there is none. */
    suspend fun clear() = mutex.withLock {
        cached = null
        loaded = true
        try {
            store.clear()
        } catch (_: Exception) {
            // The in-memory copy is already gone, and the store's own read path
            // discards anything it cannot decrypt.
        }
    }

    /**
     * `true` when the token's own `exp` says it is past.
     *
     * Only used to avoid a guaranteed-401 round trip; the API remains the
     * authority on whether a session is valid.
     */
    fun isExpired(token: String): Boolean = Jwt.isExpired(token, now())
}
