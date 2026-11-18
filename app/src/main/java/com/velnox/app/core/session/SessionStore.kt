package com.velnox.app.core.session

/**
 * A session as it is persisted between launches.
 *
 * [token] is the opaque session value issued by the Velnox API. It is never
 * logged, never rendered and never written anywhere except the store that
 * produced it.
 */
data class StoredSession(
    val token: String,
)

/**
 * Persistence seam for the session.
 *
 * Part 2 must not implement authentication, so the only implementation here is
 * [NoStoredSessionStore]: it reports that nothing is stored, which is the truth
 * today. Part 3 replaces it with a Keystore-backed store and a real token read;
 * [SessionController] and the navigation gate do not change when that happens.
 *
 * Implementations must fail safe: [read] returning `null` means "guest", never
 * "crash".
 */
interface SessionStore {

    /** The persisted session, or `null` when there is none. */
    suspend fun read(): StoredSession?

    /** Remove the persisted session. Must be idempotent. */
    suspend fun clear()
}

/**
 * The truthful Part 2 implementation: nothing has been persisted yet, because
 * nothing has ever been signed in.
 */
object NoStoredSessionStore : SessionStore {

    override suspend fun read(): StoredSession? = null

    override suspend fun clear() = Unit
}
