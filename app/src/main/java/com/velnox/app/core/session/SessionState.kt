package com.velnox.app.core.session

import com.velnox.app.core.auth.AuthUser

/**
 * The app's single authentication state.
 *
 * Exactly four states, and every screen reads them from one place — no screen
 * keeps its own signed-in flag.
 *
 * - [Loading] — a stored session is being resolved. The shell is already on
 *   screen; protected destinations are not opened yet because the answer is not
 *   known.
 * - [Guest] — nobody is signed in. Browsing is allowed, account surfaces are not.
 * - [Authenticated] — the API confirmed the token and returned the account.
 * - [ExpiredSession] — a session existed and is no longer valid (expired or
 *   rejected). The token has been cleared; signing in again is required.
 *
 * [Guest] and [ExpiredSession] both block account surfaces, but they are not the
 * same thing to the user: one is "you never signed in", the other is "you were
 * signed in and need to again", which the Login gate says out loud.
 */
sealed interface SessionState {

    data object Loading : SessionState

    data object Guest : SessionState

    data class Authenticated(override val user: AuthUser) : SessionState

    data object ExpiredSession : SessionState

    val isAuthenticated: Boolean get() = this is Authenticated

    /** The signed-in account, or `null` for every other state. */
    val user: AuthUser? get() = (this as? Authenticated)?.user
}

/**
 * Something the user should know about how the session resolved, without
 * pretending to be a fifth state.
 *
 * [NetworkUnavailable] means a token is stored but could not be verified. The
 * state is [SessionState.Guest] — the app must not act authenticated on an
 * unproven token — and the token stays on the device so the next launch can try
 * again.
 */
enum class SessionNotice {
    NetworkUnavailable,

    /** The signed-in account now also accepts Google sign-in. */
    GoogleLinked,

    /**
     * Linking was attempted and refused or failed. The account is untouched — the
     * user still signs in with the password they just used.
     */
    GoogleLinkFailed,
}
