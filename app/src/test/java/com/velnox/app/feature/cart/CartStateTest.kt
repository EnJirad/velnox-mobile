package com.velnox.app.feature.cart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `variants without variantId distinguish by variantName or imageUrl`() {
        val cart = CartState()
        cart.items.clear()

        cart.add(
            productId = "prod-shoes",
            name = "Running Shoes",
            storeName = "Sport Store",
            imageUrl = "https://example.com/shoes-black.jpg",
            variantId = null,
            variantName = "Black",
            price = 2500.0,
            currency = "THB",
            quantity = 1,
            maxStock = 10,
        )

        cart.add(
            productId = "prod-shoes",
            name = "Running Shoes",
            storeName = "Sport Store",
            imageUrl = "https://example.com/shoes-white.jpg",
            variantId = null,
            variantName = "White",
            price = 2500.0,
            currency = "THB",
            quantity = 1,
            maxStock = 10,
        )

        // Different variant names without variantId should still be distinct lines
        assertEquals(2, cart.items.size)
        assertEquals("prod-shoes_White", cart.items[0].productId)
        assertEquals("prod-shoes_Black", cart.items[1].productId)
        assertEquals("https://example.com/shoes-white.jpg", cart.items[0].imageUrl)
        assertEquals("https://example.com/shoes-black.jpg", cart.items[1].imageUrl)

        // Adding exact same variant again increments quantity
        cart.add(
            productId = "prod-shoes",
            name = "Running Shoes",
            storeName = "Sport Store",
            imageUrl = "https://example.com/shoes-white.jpg",
            variantId = null,
            variantName = "White",
            price = 2500.0,
            currency = "THB",
            quantity = 2,
            maxStock = 10,
        )

        assertEquals(2, cart.items.size)
        assertEquals(3, cart.items[0].quantity)
    }

    @Test
    fun `toggleShopSelection toggles selection of only specified shop items`() {
        val cart = CartState()
        cart.items.clear()

        cart.add(
            productId = "p-shopA-1",
            name = "Shop A Item 1",
            storeName = "Shop A",
            imageUrl = null,
            variantId = "v1",
            variantName = null,
            price = 100.0,
            currency = "THB",
            quantity = 1,
            maxStock = 10,
        )
        cart.add(
            productId = "p-shopA-2",
            name = "Shop A Item 2",
            storeName = "Shop A",
            imageUrl = null,
            variantId = "v2",
            variantName = null,
            price = 200.0,
            currency = "THB",
            quantity = 1,
            maxStock = 10,
        )
        cart.add(
            productId = "p-shopB-1",
            name = "Shop B Item 1",
            storeName = "Shop B",
            imageUrl = null,
            variantId = "v3",
            variantName = null,
            price = 300.0,
            currency = "THB",
            quantity = 1,
            maxStock = 10,
        )

        val shopAItems = cart.items.filter { it.storeName == "Shop A" }
        assertEquals(2, shopAItems.size)

        // Deselect only Shop A items
        cart.toggleShopSelection(shopAItems, target = false)

        val itemA1 = cart.items.first { it.productId == "p-shopA-1_v1" }
        val itemA2 = cart.items.first { it.productId == "p-shopA-2_v2" }
        val itemB1 = cart.items.first { it.productId == "p-shopB-1_v3" }

        assertFalse(itemA1.selected)
        assertFalse(itemA2.selected)
        assertTrue(itemB1.selected) // Shop B unaffected

        // Select Shop A items back
        cart.toggleShopSelection(shopAItems, target = true)
        assertTrue(itemA1.let { cart.items.first { it.productId == "p-shopA-1_v1" }.selected })
        assertTrue(itemA2.let { cart.items.first { it.productId == "p-shopA-2_v2" }.selected })
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

        // Both are selected by default: (150 * 2) + (200 * 1) = 500.0
        assertEquals(500.0, cart.selectedTotalPrice!!, 0.001)

        // When all items are selected, toggleAll() deselects all
        cart.toggleAll()
        assertEquals(0.0, cart.selectedTotalPrice!!, 0.001)

        // toggleAll() again selects all
        cart.toggleAll()
        assertEquals(500.0, cart.selectedTotalPrice!!, 0.001)

        // Unselect Item 2
        cart.toggleSelection("prod-2_v2")
        assertEquals(300.0, cart.selectedTotalPrice!!, 0.001)

        // Unselect Item 1
        cart.toggleSelection("prod-1_v1")
        assertEquals(0.0, cart.selectedTotalPrice!!, 0.001)
    }
}
