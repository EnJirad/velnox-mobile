package com.velnox.app.navigation

import androidx.annotation.StringRes
import com.velnox.app.R
import com.velnox.app.core.session.SessionState

/**
 * Why the Login screen was opened.
 *
 * Carried through the navigation gate so the screen can explain the requirement
 * instead of appearing out of nowhere. Every entry maps to a localized sentence.
 */
enum class LoginReason(
    val key: String,
    @StringRes val messageRes: Int,
) {
    Cart("cart", R.string.auth_reason_cart),
    Orders("orders", R.string.auth_reason_orders),
    Profile("profile", R.string.auth_reason_profile),
    Messages("messages", R.string.auth_reason_messages),
    Checkout("checkout", R.string.auth_reason_checkout),
    Account("account", R.string.auth_reason_account),

    /** A session existed and stopped being valid; the token has been cleared. */
    Expired("expired", R.string.auth_reason_expired),
    ;

    companion object {
        fun fromKey(key: String?): LoginReason? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Which authentication screen the gate is showing.
 *
 * The gate is one destination with several steps, so the pending destination and
 * the reason survive moving between sign-in, sign-up and recovery.
 */
enum class AuthStep(val key: String) {
    Login("login"),
    Register("register"),
    Forgot("forgot"),
    Reset("reset"),
    ;

    companion object {
        fun fromKey(key: String?): AuthStep? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The one place that decides what a guest may reach.
 *
 * Both navigation funnels (`requestTab` and `requestOverlay` in `VelnoxNavHost`)
 * ask this object, and nothing else is allowed to branch on the session. Screens
 * keep rendering whatever they are given; they never decide access themselves.
 *
 * **Protected actions are destinations.** Checkout, wishlist, order detail and
 * messages are routes, so they pass through the same gate as the bottom bar.
 * Part 4's "Add to cart" must be added here as a route for the same reason.
 *
 * This is the *client* policy and it exists for UX and defence in depth. It is
 * not the security boundary: the Velnox API enforces authentication on every
 * account surface with `requireAuth` (`/api/customer/cart*`, `/checkout`,
 * `/orders*`, `/profile`, `/addresses`), and that server check remains the only
 * authority on what a guest can actually read or change.
 */
object AccessPolicy {

    /**
     * `true` when the session currently allows account surfaces.
     *
     * Only [SessionState.Authenticated] passes: `Loading` is unknown,
     * `ExpiredSession` is gone and `Guest` never had one. A boolean stored on the
     * client can therefore never satisfy this on its own — the state is only
     * produced by a `200` from `/api/auth/me` or a successful sign-in.
     */
    fun isAllowed(state: SessionState): Boolean = state is SessionState.Authenticated

    /** Public: Home, Market. Everything else on the bottom bar is account-specific. */
    fun reasonForTab(tab: AppTab): LoginReason? = when (tab) {
        AppTab.Home -> null
        AppTab.Market -> null
        AppTab.Orders -> LoginReason.Orders
        AppTab.Cart -> LoginReason.Cart
        AppTab.Account -> LoginReason.Account
    }

    /**
     * Public overlays are the catalogue surface: the category hub, a category
     * listing, product detail and search. Everything else needs an account.
     *
     * Unknown routes fall through to [LoginReason.Account] on purpose — a new
     * destination that nobody classified must fail closed rather than silently
     * expose itself to guests.
     */
    fun reasonForOverlay(key: String): LoginReason? = when {
        key == "category" || key.startsWith("category:") -> null
        key.startsWith("product:") -> null
        key == "search" -> null

        key == "checkout" -> LoginReason.Checkout
        key.startsWith("order:") -> LoginReason.Orders
        key == "messages" || key.startsWith("thread:") -> LoginReason.Messages
        key == "settings" || key.startsWith("settings/") -> LoginReason.Account
        key == "wishlist" -> LoginReason.Account

        else -> LoginReason.Account
    }

    /**
     * The reason to show for a blocked destination.
     *
     * An expired session says so, because "you were signed in and need to again"
     * is a different situation from "you never signed in" — and only the former
     * means the user's previous session was cleared.
     */
    fun reasonFor(base: LoginReason, state: SessionState): LoginReason =
        if (state is SessionState.ExpiredSession) LoginReason.Expired else base
}
