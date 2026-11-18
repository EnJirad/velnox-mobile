package com.velnox.app.core.session

/**
 * Who the app currently believes it is acting for.
 *
 * This is the single source of truth for the guest/authenticated decision. No
 * screen may invent its own flag: navigation, the bottom bar and any protected
 * action all read this value through [SessionController] and [AccessPolicy].
 *
 * Part 2 ships two reachable states — [Restoring] resolves to [Guest] because no
 * session is persisted yet — and [Authenticated] is what Part 3 produces once a
 * real credential exchange exists. [Authenticated] deliberately carries no
 * account: there is no profile source until the API layer lands, and inventing a
 * fake identity here would be worse than having none.
 */
sealed interface SessionState {

    /** A stored session is being resolved. Not yet a guest, not yet signed in. */
    data object Restoring : SessionState

    /** No session. Browsing is allowed; account surfaces are not. */
    data object Guest : SessionState

    /** A session exists. Account surfaces are allowed. */
    data object Authenticated : SessionState

    val isAuthenticated: Boolean get() = this is Authenticated

    val isGuest: Boolean get() = this is Guest
}
