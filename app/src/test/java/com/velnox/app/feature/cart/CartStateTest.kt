package com.velnox.app.feature.cart

import com.velnox.app.core.customer.CustomerError
import com.velnox.app.core.customer.CustomerRepository
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.FakeCustomerApi
import com.velnox.app.core.customer.LoadStatus
import com.velnox.app.core.customer.RemoteCart
import com.velnox.app.core.customer.RemoteCartItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CartStateTest {

    private class ServerCart(initial: List<RemoteCartItem> = emptyList()) : FakeCustomerApi() {
        val rows = initial.toMutableList()
        var failNext: CustomerError? = null
        var addedCalls = mutableListOf<Triple<String, String?, Int>>()
        private var nextId = 1

        override suspend fun cart(token: String): CustomerResult<RemoteCart> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it) }
            return CustomerResult.Ok(RemoteCart(rows.toList()))
        }

        override suspend fun addToCart(
            token: String,
            productId: String,
            variantId: String?,
            quantity: Int,
        ): CustomerResult<RemoteCart> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it, "Out of stock") }
            addedCalls += Triple(productId, variantId, quantity)
            val idx = rows.indexOfFirst { it.productId == productId && it.variantId == variantId }
            if (idx >= 0) {
                val cur = rows[idx]
                rows[idx] = cur.copy(quantity = (cur.quantity + quantity).coerceAtMost(cur.maxStock ?: 100))
            } else {
                rows.add(
                    0,
                    RemoteCartItem(
                        id = "ci-${nextId++}",
                        productId = productId,
                        variantId = variantId,
                        name = "Product $productId",
                        variantName = variantId?.let { "Var $it" },
                        shopName = "Server Shop",
                        imageUrl = null,
                        price = 199.0,
                        currency = "THB",
                        quantity = quantity,
                        maxStock = 10,
                    ),
                )
            }
            return CustomerResult.Ok(RemoteCart(rows.toList()))
        }

        override suspend fun updateCartItem(
            token: String,
            itemId: String,
            quantity: Int,
        ): CustomerResult<RemoteCart> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it) }
            val idx = rows.indexOfFirst { it.id == itemId }
            if (idx >= 0) rows[idx] = rows[idx].copy(quantity = quantity)
            return CustomerResult.Ok(RemoteCart(rows.toList()))
        }

        override suspend fun removeCartItem(token: String, itemId: String): CustomerResult<RemoteCart> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it) }
            rows.removeAll { it.id == itemId }
            return CustomerResult.Ok(RemoteCart(rows.toList()))
        }
    }

    private fun repo(api: FakeCustomerApi) = CustomerRepository(api, token = { "tok" })

    private fun remoteRow(
        id: String,
        productId: String,
        variantId: String? = null,
        qty: Int = 1,
        price: Double = 100.0,
        maxStock: Int = 10,
    ) = RemoteCartItem(
        id = id,
        productId = productId,
        variantId = variantId,
        name = "Item $productId",
        variantName = variantId,
        shopName = "Shop A",
        imageUrl = null,
        price = price,
        currency = "THB",
        quantity = qty,
        maxStock = maxStock,
    )

    @Test
    fun `cart starts empty with zero mock items`() {
        val cart = CartState()
        assertTrue(cart.items.isEmpty())
        assertEquals(LoadStatus.Idle, cart.status)
        assertEquals(0, cart.selectedCount)
        assertEquals(0.0, cart.selectedTotalPrice!!, 0.001)
    }

    @Test
    fun `load mirrors server cart and preserves prior selection state`() = runBlocking {
        val server = ServerCart(listOf(remoteRow("ci-1", "p1", qty = 2, price = 150.0), remoteRow("ci-2", "p2", qty = 1, price = 200.0)))
        val cart = CartState()

        val first = cart.load(repo(server))
        assertTrue(first is CustomerResult.Ok)
        assertEquals(LoadStatus.Ready, cart.status)
        assertEquals(2, cart.items.size)
        assertEquals("ci-1", cart.items[0].remoteItemId)
        assertEquals("p1", cart.items[0].productId)
        assertEquals(500.0, cart.selectedTotalPrice!!, 0.001)

        // Deselect ci-2, reload from server: selection stays false
        cart.toggleSelection("ci-2")
        assertEquals(300.0, cart.selectedTotalPrice!!, 0.001)

        cart.load(repo(server))
        assertTrue(cart.items.first { it.id == "ci-1" }.selected)
        assertFalse(cart.items.first { it.id == "ci-2" }.selected)
    }

    @Test
    fun `remote add, increase, decrease and remove mirror server responses`() = runBlocking {
        val server = ServerCart()
        val cart = CartState()
        val r = repo(server)

        assertTrue(cart.addRemote(r, productId = "p1", variantId = "v1", quantity = 2) is CustomerResult.Ok)
        assertEquals(1, cart.items.size)
        val lineId = cart.items.single().id
        assertEquals("ci-1", lineId)
        assertEquals(2, cart.items.single().quantity)

        assertTrue(cart.increaseRemote(r, lineId) is CustomerResult.Ok)
        assertEquals(3, cart.items.single().quantity)

        assertTrue(cart.decreaseRemote(r, lineId) is CustomerResult.Ok)
        assertEquals(2, cart.items.single().quantity)

        assertTrue(cart.removeRemote(r, lineId) is CustomerResult.Ok)
        assertTrue(cart.items.isEmpty())
    }

    @Test
    fun `syncLocalAndLoad pushes guest items to server then loads authoritative cart`() = runBlocking {
        val server = ServerCart(listOf(remoteRow("ci-existing", "p-server", qty = 1)))
        val cart = CartState()
        cart.add(
            productId = "p-guest",
            name = "Guest Item",
            storeName = "Guest Shop",
            imageUrl = null,
            variantId = "v-g",
            variantName = "M",
            price = 50.0,
            quantity = 2,
        )
        assertNull(cart.items.single().remoteItemId)

        val result = cart.syncLocalAndLoad(repo(server))

        assertTrue(result is CustomerResult.Ok)
        assertEquals(listOf(Triple("p-guest", "v-g", 2)), server.addedCalls)
        assertEquals(2, cart.items.size)
        assertTrue(cart.items.all { it.remoteItemId != null })
    }

    @Test
    fun `failed remote load marks status Failed without discarding existing lines`() = runBlocking {
        val server = ServerCart(listOf(remoteRow("ci-1", "p1")))
        val cart = CartState()
        cart.load(repo(server))
        server.failNext = CustomerError.Network

        val res = cart.load(repo(server))

        assertEquals(CustomerResult.Failed(CustomerError.Network), res)
        assertEquals(LoadStatus.Failed, cart.status)
        assertEquals(1, cart.items.size)
    }

    @Test
    fun `guest fallback adds variants locally, clamps to maxStock, and supports shop selection`() {
        val cart = CartState()

        cart.add(
            productId = "prod-1",
            name = "Test Cable",
            storeName = "Shop A",
            imageUrl = null,
            variantId = "var-red",
            variantName = "Red",
            price = 100.0,
            quantity = 3,
            maxStock = 5,
        )
        cart.add(
            productId = "prod-1",
            name = "Test Cable",
            storeName = "Shop A",
            imageUrl = null,
            variantId = "var-red",
            variantName = "Red",
            price = 100.0,
            quantity = 4,
            maxStock = 5,
        )
        cart.add(
            productId = "prod-2",
            name = "Charger",
            storeName = "Shop B",
            imageUrl = null,
            variantId = null,
            variantName = null,
            price = 200.0,
            quantity = 1,
            maxStock = 10,
        )

        assertEquals(2, cart.items.size)
        val red = cart.items.first { it.variantId == "var-red" }
        assertEquals("prod-1", red.productId)
        assertEquals(5, red.quantity)
        assertEquals(700.0, cart.selectedTotalPrice!!, 0.001)

        val shopAItems = cart.items.filter { it.storeName == "Shop A" }
        cart.toggleShopSelection(shopAItems, target = false)
        assertFalse(cart.items.first { it.variantId == "var-red" }.selected)
        assertTrue(cart.items.first { it.productId == "prod-2" }.selected)
        assertEquals(200.0, cart.selectedTotalPrice!!, 0.001)

        val removed = cart.removeSelected()
        assertEquals(1, removed.size)
        assertEquals("prod-2", removed.single().productId)
        assertEquals(1, cart.items.size)
    }
}
