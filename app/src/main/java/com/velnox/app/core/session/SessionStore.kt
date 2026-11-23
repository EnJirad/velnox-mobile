package com.velnox.app.core.session

/**
 * A session as it is persisted between launches.
 * Supports both modern v2 tokens (accessToken + refreshToken + expiry + backendFlavor)
 * and legacy v1 single token sessions.
 */
data class StoredSession(
    /** The opaque session or access token value issued by the API. Never logged, never rendered. */
    val token: String,
    /** Long-lived refresh token issued by Cloudflare Workers backend. */
    val refreshToken: String? = null,
    /** Expiry timestamp in epoch milliseconds (if provided by API). */
    val expiresAtMillis: Long? = null,
    /** Flavor of the issuing backend: "WORKERS_V1" or "EXPRESS_LEGACY". */
    val backendFlavor: String? = null,
)

/**
 * Persistence seam for the session token.
 *
 * The only production implementation is [SessionVault], which encrypts with a
 * Keystore-held key. Tests use an in-memory fake.
 *
 * Implementations must fail safe: [read] returning `null` means "no session",
 * never "crash", and [clear] must be idempotent so signing out twice is harmless.
 */
interface SessionStore {

    /** The persisted session, or `null` when there is none or it is unreadable. */
    suspend fun read(): StoredSession?

    /** Persist a session. Throwing here means the session could not be stored. */
    suspend fun write(session: StoredSession)

    /** Remove the persisted session. Must be idempotent. */
    suspend fun clear()
}
