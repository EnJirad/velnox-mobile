package com.velnox.app.navigation

import com.velnox.app.core.auth.AuthUser
import com.velnox.app.core.session.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The protected-navigation policy.
 *
 * These tests are the reason a new screen cannot quietly become public: an
 * unclassified route must fall back to "sign-in required", and only the
 * authenticated state may pass.
 */
class AccessPolicyTest {

    private val user = AuthUser("u-1", "a@b.co", "Ann", "customer")

    @Test
    fun `catalogue destinations are public`() {
        assertNull(AccessPolicy.reasonForOverlay("category"))
        assertNull(AccessPolicy.reasonForOverlay("category:fashion"))
        assertNull(AccessPolicy.reasonForOverlay("product:p1"))
        assertNull(AccessPolicy.reasonForOverlay("search"))
    }

    @Test
    fun `account destinations are protected`() {
        assertEquals(LoginReason.Checkout, AccessPolicy.reasonForOverlay("checkout"))
        assertEquals(LoginReason.Orders, AccessPolicy.reasonForOverlay("order:o-1"))
        assertEquals(LoginReason.Messages, AccessPolicy.reasonForOverlay("thread:t-1"))
        assertEquals(LoginReason.Account, AccessPolicy.reasonForOverlay("settings"))
        assertEquals(LoginReason.Account, AccessPolicy.reasonForOverlay("settings/password"))
        assertEquals(LoginReason.Account, AccessPolicy.reasonForOverlay("wishlist"))
    }

    @Test
    fun `an unclassified route fails closed`() {
        assertEquals(LoginReason.Account, AccessPolicy.reasonForOverlay("some-future-screen"))
        assertEquals(LoginReason.Account, AccessPolicy.reasonForOverlay(""))
    }

    @Test
    fun `only the authenticated state is allowed`() {
        assertFalse(AccessPolicy.isAllowed(SessionState.Loading))
        assertFalse(AccessPolicy.isAllowed(SessionState.Guest))
        assertFalse(AccessPolicy.isAllowed(SessionState.ExpiredSession))
        assertTrue(AccessPolicy.isAllowed(SessionState.Authenticated(user)))
    }

    @Test
    fun `an expired session explains itself instead of looking like a first visit`() {
        assertEquals(
            LoginReason.Expired,
            AccessPolicy.reasonFor(LoginReason.Cart, SessionState.ExpiredSession),
        )
        assertEquals(
            LoginReason.Cart,
            AccessPolicy.reasonFor(LoginReason.Cart, SessionState.Guest),
        )
        assertEquals(
            LoginReason.Cart,
            AccessPolicy.reasonFor(LoginReason.Cart, SessionState.Loading),
        )
    }
}
