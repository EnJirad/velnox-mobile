package com.velnox.app

/**
 * Application-level authentication state.
 *
 * Guest — no stored session; Home and browsing are visible, protected
 * actions (Orders, Cart, Profile, Messages, Add to Cart, Checkout)
 * redirect to Login.
 *
 * Authenticated — a valid session exists; full access.
 *
 * Persistence is managed in the nav host, not by a server round-trip
 * in this phase. Phase 4 replaces this with a real session token check.
 */
enum class AuthState {
    Guest,
    Authenticated,
}
