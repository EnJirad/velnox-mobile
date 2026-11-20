package com.velnox.app.feature.wishlist

import com.velnox.app.core.customer.CustomerError
import com.velnox.app.core.customer.CustomerRepository
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.FakeCustomerApi
import com.velnox.app.core.customer.LoadStatus
import com.velnox.app.core.customer.WishlistEntry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WishlistStateTest {

    private class ServerWishlist(initial: List<WishlistEntry> = emptyList()) : FakeCustomerApi() {
        val entries = initial.toMutableList()
        var failNext: CustomerError? = null

        override suspend fun wishlist(token: String): CustomerResult<List<WishlistEntry>> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it) }
            return CustomerResult.Ok(entries.toList())
        }

        override suspend fun toggleWishlist(token: String, productId: String): CustomerResult<Boolean> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it) }
            val existing = entries.indexOfFirst { it.productId == productId }
            return if (existing >= 0) {
                entries.removeAt(existing)
                CustomerResult.Ok(false)
            } else {
                entries.add(0, WishlistEntry(productId, "Prod $productId", 100.0, "THB", null, "Shop", true, 4.9))
                CustomerResult.Ok(true)
            }
        }
    }

    private fun repo(api: FakeCustomerApi) = CustomerRepository(api, token = { "tok" })

    @Before
    fun setUp() = WishlistState.reset()

    @After
    fun tearDown() = WishlistState.reset()

    @Test
    fun `wishlist starts empty with zero mock items`() {
        assertTrue(WishlistState.items.isEmpty())
        assertEquals(LoadStatus.Idle, WishlistState.status)
        assertFalse(WishlistState.isFavorite("prod-1"))
    }

    @Test
    fun `load mirrors server wishlist`() = runBlocking {
        val server = ServerWishlist(
            listOf(WishlistEntry("p1", "Lamp", 390.0, "THB", null, "Lumen", true, 4.8)),
        )

        val res = WishlistState.load(repo(server))

        assertTrue(res is CustomerResult.Ok)
        assertEquals(LoadStatus.Ready, WishlistState.status)
        assertTrue(WishlistState.isFavorite("p1"))
        assertEquals("Lamp", WishlistState.items.single().name)
    }

    @Test
    fun `toggleRemote adds when server returns true and removes when false`() = runBlocking {
        val server = ServerWishlist()
        val r = repo(server)
        val item = WishlistItem("p9", "Keyboard", 1990.0)

        val added = WishlistState.toggleRemote(r, item)
        assertEquals(CustomerResult.Ok(true), added)
        assertTrue(WishlistState.isFavorite("p9"))

        val removed = WishlistState.toggleRemote(r, item)
        assertEquals(CustomerResult.Ok(false), removed)
        assertFalse(WishlistState.isFavorite("p9"))
    }

    @Test
    fun `failed toggleRemote leaves local state unchanged`() = runBlocking {
        val server = ServerWishlist()
        server.failNext = CustomerError.Network

        val res = WishlistState.toggleRemote(repo(server), WishlistItem("p9", "Keyboard", 1990.0))

        assertEquals(CustomerResult.Failed(CustomerError.Network), res)
        assertFalse(WishlistState.isFavorite("p9"))
    }

    @Test
    fun `local add, remove, toggle and clear work for guest fallback`() {
        val item = WishlistItem(productId = "prod-local", name = "Test Product", price = 999.0)
        assertTrue(WishlistState.toggle(item))
        assertTrue(WishlistState.isFavorite("prod-local"))

        WishlistState.add(item)
        assertEquals(1, WishlistState.items.size)

        assertFalse(WishlistState.toggle(item))
        assertFalse(WishlistState.isFavorite("prod-local"))

        WishlistState.add(item)
        WishlistState.clear()
        assertTrue(WishlistState.items.isEmpty())
    }
}
