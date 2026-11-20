package com.velnox.app.feature.cart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CartStateTest {

    @Test
    fun `adding real product adds item to cart with variant and price`() {
        val cart = CartState()
        val initialSize = cart.items.size

        cart.add(
            productId = "prod-1",
            name = "Test Cable",
            storeName = "Gadget Shop",
            imageUrl = "https://example.com/cable.jpg",
            variantId = "var-1",
            variantName = "Red / 1m",
            price = 144.53,
            currency = "THB",
            quantity = 2,
            maxStock = 10,
        )

        assertEquals(initialSize + 1, cart.items.size)
        val added = cart.items.first()
        assertEquals("prod-1_var-1", added.productId)
        assertEquals("Test Cable", added.name)
        assertEquals("Gadget Shop", added.storeName)
        assertEquals("https://example.com/cable.jpg", added.imageUrl)
        assertEquals("var-1", added.variantId)
        assertEquals("Red / 1m", added.variantName)
        assertEquals(144.53, added.price!!, 0.001)
        assertEquals("THB", added.currency)
        assertEquals(2, added.quantity)
        assertTrue(added.selected)
    }

    @Test
    fun `adding same variant increases quantity up to maxStock`() {
        val cart = CartState()

        cart.add(
            productId = "prod-1",
            name = "Test Cable",
            storeName = "Gadget Shop",
            imageUrl = null,
            variantId = "var-1",
            variantName = "Red / 1m",
            price = 100.0,
            currency = "THB",
            quantity = 3,
            maxStock = 5,
        )

        assertEquals(3, cart.items.first().quantity)

        // Adding 4 more should clamp to maxStock (5)
        cart.add(
            productId = "prod-1",
            name = "Test Cable",
            storeName = "Gadget Shop",
            imageUrl = null,
            variantId = "var-1",
            variantName = "Red / 1m",
            price = 100.0,
            currency = "THB",
            quantity = 4,
            maxStock = 5,
        )

        assertEquals(5, cart.items.first().quantity)
    }

    @Test
    fun `different variants of same product are separate cart items`() {
        val cart = CartState()

        cart.add(
            productId = "prod-1",
            name = "Test Cable",
            storeName = "Gadget Shop",
            imageUrl = null,
            variantId = "var-red",
            variantName = "Red",
            price = 100.0,
            currency = "THB",
            quantity = 1,
            maxStock = 10,
        )

        cart.add(
            productId = "prod-1",
            name = "Test Cable",
            storeName = "Gadget Shop",
            imageUrl = null,
            variantId = "var-blue",
            variantName = "Blue",
            price = 120.0,
            currency = "THB",
            quantity = 2,
            maxStock = 10,
        )

        assertEquals(5, cart.items.size) // 3 demo items + 2 new variants
        assertEquals("prod-1_var-blue", cart.items[0].productId)
        assertEquals("prod-1_var-red", cart.items[1].productId)
    }

    @Test
    fun `selectedTotalPrice computes correct sum of selected items with prices`() {
        val cart = CartState()
        // clear demo items without prices
        cart.items.clear()

        cart.add(
            productId = "prod-1",
            name = "Item 1",
            storeName = "Store 1",
            imageUrl = null,
            variantId = "v1",
            variantName = null,
            price = 150.0,
            currency = "THB",
            quantity = 2,
            maxStock = 10,
        )

        cart.add(
            productId = "prod-2",
            name = "Item 2",
            storeName = "Store 2",
            imageUrl = null,
            variantId = "v2",
            variantName = null,
            price = 200.0,
            currency = "THB",
            quantity = 1,
            maxStock = 10,
        )

        // Both are selected by default
        assertEquals(500.0, cart.selectedTotalPrice!!, 0.001)

        // Unselect Item 2
        cart.toggleSelection("prod-2_v2")
        assertEquals(300.0, cart.selectedTotalPrice!!, 0.001)

        // Toggle all off
        cart.toggleAll()
        assertEquals(0.0, cart.selectedTotalPrice!!, 0.001)
    }
}
