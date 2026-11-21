package com.velnox.app.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteIntegrityTest {

    @Test
    fun `all core e-commerce overlays are recognized and classified by AccessPolicy`() {
        val publicOverlays = listOf(
            "category",
            "category:fashion",
            "category:electronics",
            "product:prod_1",
            "shop:shop_1",
            "search",
            "notifications",
        )

        for (route in publicOverlays) {
            val reason = AccessPolicy.reasonForOverlay(route)
            // Public routes must return null (accessible without sign-in gate)
            assertTrue("Expected $route to be public", reason == null)
        }

        val protectedOverlays = listOf(
            "checkout" to LoginReason.Checkout,
            "order:VLX-2401" to LoginReason.Orders,
            "messages" to LoginReason.Messages,
            "thread:t-1" to LoginReason.Messages,
            "settings" to LoginReason.Account,
            "wishlist" to LoginReason.Account,
        )

        for ((route, expectedReason) in protectedOverlays) {
            val reason = AccessPolicy.reasonForOverlay(route)
            assertTrue("Expected $route to require $expectedReason", reason == expectedReason)
        }
    }

    @Test
    fun `unregistered route fails closed to Account login reason`() {
        val unknownReason = AccessPolicy.reasonForOverlay("nonexistent_route_xyz")
        assertTrue(unknownReason == LoginReason.Account)
    }

    @Test
    fun `product route prefixing ensures public access`() {
        val rawId = "prod_999"
        val normalized = if (rawId.startsWith("product:")) rawId else "product:$rawId"
        assertTrue(AccessPolicy.reasonForOverlay(normalized) == null)

        val alreadyPrefixed = "product:prod_888"
        val normalizedPrefixed = if (alreadyPrefixed.startsWith("product:")) alreadyPrefixed else "product:$alreadyPrefixed"
        assertTrue(AccessPolicy.reasonForOverlay(normalizedPrefixed) == null)
    }
}
