package com.velnox.app.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Owns the current [SessionState] for the whole process.
 *
 * Created once in `VelnoxApplication` so a configuration change cannot lose the
 * resolved session, and so a rotation cannot re-run the restore round trip.
 *
 * The restore path is deliberately asynchronous and starts in
 * [SessionState.Restoring]: the app must paint Home immediately and must never
 * show a full-screen Login while a session is being resolved.
 */
class SessionController(
    private val store: SessionStore = NoStoredSessionStore,
) {

    var state by mutableStateOf<SessionState>(SessionState.Restoring)
        private set

    private var restoreStarted = false

    /**
     * Resolve the persisted session exactly once per process.
     *
     * Repeated calls (activity recreation, recomposition) are no-ops, so a
     * signed-in state cannot be knocked back to [SessionState.Restoring] by a
     * rotation. A storage failure resolves to guest instead of throwing: the
     * worst outcome of a broken store must be "please sign in again".
     */
    suspend fun restoreOnce() {
        if (restoreStarted) return
        restoreStarted = true

        val stored = try {
            store.read()
        } catch (_: Exception) {
            null
        }

        state = if (stored == null) SessionState.Guest else SessionState.Authenticated
    }

    /**
     * DEMO ONLY — the pre-existing simulated sign-in on the Login screen calls
     * this so the navigation gate and the pending-destination restore can be
     * proven end to end in Part 2.
     *
     * It performs no credential check, mints no token and persists nothing, so a
     * relaunch returns to [SessionState.Guest]. Part 3 replaces this call site
     * with a real `AuthRepository` sign-in and deletes this function.
     */
    fun completeDemoSignIn() {
        state = SessionState.Authenticated
    }

    /** Drop the session locally, then clear whatever was persisted. */
    suspend fun signOut() {
        try {
            store.clear()
        } catch (_: Exception) {
            // A failed clear must not keep the user signed in on this device.
        }
        state = SessionState.Guest
    }
}
