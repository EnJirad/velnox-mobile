package com.velnox.app.feature.wishlist

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WishlistStateTest {

    @Before
    fun setUp() {
        WishlistState.reset()
    }

    @After
    fun tearDown() {
        WishlistState.reset()
    }

    @Test
    fun initialWishlist_containsDefaultItems() {
        assertTrue(WishlistState.items.isNotEmpty())
        assertTrue(WishlistState.isFavorite("prod-1"))
        assertTrue(WishlistState.isFavorite("prod-2"))
        assertFalse(WishlistState.isFavorite("prod-unknown"))
    }

    @Test
    fun add_addsNewItem_andAvoidsDuplicates() {
        val newItem = WishlistItem(
            productId = "prod-test",
            name = "Test Product",
            price = 999.0,
        )
        WishlistState.add(newItem)

        assertTrue(WishlistState.isFavorite("prod-test"))
        val count = WishlistState.items.size

        // Adding again should not duplicate
        WishlistState.add(newItem)
        assertEquals(count, WishlistState.items.size)
    }

    @Test
    fun remove_removesExistingItem() {
        assertTrue(WishlistState.isFavorite("prod-1"))
        val removed = WishlistState.remove("prod-1")

        assertTrue(removed)
        assertFalse(WishlistState.isFavorite("prod-1"))
    }

    @Test
    fun toggle_addsWhenMissing_removesWhenPresent() {
        val testItem = WishlistItem(
            productId = "prod-toggle",
            name = "Toggle Item",
            price = 100.0,
        )

        assertFalse(WishlistState.isFavorite("prod-toggle"))

        // First toggle: should add
        val added = WishlistState.toggle(testItem)
        assertTrue(added)
        assertTrue(WishlistState.isFavorite("prod-toggle"))

        // Second toggle: should remove
        val toggledOff = WishlistState.toggle(testItem)
        assertFalse(toggledOff)
        assertFalse(WishlistState.isFavorite("prod-toggle"))
    }

    @Test
    fun clear_emptiesWishlist() {
        assertTrue(WishlistState.items.isNotEmpty())
        WishlistState.clear()
        assertTrue(WishlistState.items.isEmpty())
    }
}
