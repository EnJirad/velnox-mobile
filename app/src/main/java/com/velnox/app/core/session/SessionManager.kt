package com.velnox.app.core.session

import com.velnox.app.core.auth.AuthSession
import com.velnox.app.core.auth.Jwt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the session token and refresh tokens: where they are stored, when they are stale,
 * and how they are destroyed.
 *
 * Everything that needs a token asks this class, so there is exactly one copy in
 * memory and one on disk. The token is never exposed to the UI layer.
 */
class SessionManager(
    private val store: SessionStore,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val mutex = Mutex()
    private var cached: StoredSession? = null
    private var loaded = false

    /** The current token string, loading from secure storage on first use. */
    suspend fun current(): String? = mutex.withLock {
        ensureLoaded()
        cached?.token
    }

    /** The full current session object (including refresh token and metadata). */
    suspend fun currentSession(): StoredSession? = mutex.withLock {
        ensureLoaded()
        cached
    }

    private suspend fun ensureLoaded() {
        if (!loaded) {
            cached = try {
                store.read()
            } catch (_: Exception) {
                null
            }
            loaded = true
        }
    }

    /** Persist a freshly issued token string (backwards compatibility). */
    suspend fun save(token: String) = mutex.withLock {
        val session = StoredSession(token = token)
        store.write(session)
        cached = session
        loaded = true
    }

    /** Persist a full AuthSession with refresh token and expiry metadata. */
    suspend fun save(session: AuthSession) = mutex.withLock {
        val expiresAt = session.expiresInSeconds?.let { now() + it * 1000L }
        val stored = StoredSession(
            token = session.token,
            refreshToken = session.refreshToken,
            expiresAtMillis = expiresAt,
            backendFlavor = session.backendFlavor,
        )
        store.write(stored)
        cached = stored
        loaded = true
    }

    /** Updates access and refresh tokens after a token rotation. */
    suspend fun updateTokens(
        accessToken: String,
        refreshToken: String?,
        expiresAtMillis: Long?,
    ) = mutex.withLock {
        ensureLoaded()
        val current = cached
        val updated = StoredSession(
            token = accessToken,
            refreshToken = refreshToken ?: current?.refreshToken,
            expiresAtMillis = expiresAtMillis ?: current?.expiresAtMillis,
            backendFlavor = current?.backendFlavor,
        )
        store.write(updated)
        cached = updated
        loaded = true
    }

    /** Forget the token everywhere. Safe to call when there is none. */
    suspend fun clear() = mutex.withLock {
        cached = null
        loaded = true
        try {
            store.clear()
        } catch (_: Exception) {
        }
    }

    /**
     * `true` when the access token is near or past expiry.
     */
    fun shouldRefresh(session: StoredSession? = null, skewMillis: Long = 60_000L): Boolean {
        val s = session ?: cached ?: return false
        val expFromJwt = Jwt.expiresAtMillis(s.token)
        val exp = expFromJwt ?: s.expiresAtMillis ?: return false
        return now() + skewMillis >= exp
    }

    /**
     * `true` when the token's own `exp` says it is past.
     */
    fun isExpired(token: String): Boolean = Jwt.isExpired(token, now())
}
