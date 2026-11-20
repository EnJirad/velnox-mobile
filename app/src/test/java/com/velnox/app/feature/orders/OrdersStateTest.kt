package com.velnox.app.feature.orders

import com.velnox.app.core.customer.CustomerError
import com.velnox.app.core.customer.CustomerOrder
import com.velnox.app.core.customer.CustomerRepository
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.FakeCustomerApi
import com.velnox.app.core.customer.LoadStatus
import com.velnox.app.core.customer.OrderLine
import com.velnox.app.core.customer.RemoteCart
import com.velnox.app.core.customer.RemoteCartItem
import com.velnox.app.feature.cart.CartState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrdersStateTest {

    private class ServerOrders(initial: List<CustomerOrder>) : FakeCustomerApi() {
        val rows = initial.toMutableList()
        var failNext: CustomerError? = null

        override suspend fun orders(token: String): CustomerResult<List<CustomerOrder>> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it) }
            return CustomerResult.Ok(rows.toList())
        }

        override suspend fun order(token: String, id: String): CustomerResult<CustomerOrder> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it) }
            return rows.firstOrNull { it.id == id }?.let { CustomerResult.Ok(it) }
                ?: CustomerResult.Failed(CustomerError.NotFound)
        }

        override suspend fun cancelOrder(token: String, id: String): CustomerResult<Unit> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it, "Already shipped") }
            val idx = rows.indexOfFirst { it.id == id }
            if (idx < 0) return CustomerResult.Failed(CustomerError.NotFound)
            rows[idx] = rows[idx].copy(status = "cancelled")
            return CustomerResult.Ok(Unit)
        }

        override suspend fun reorder(token: String, orderId: String): CustomerResult<RemoteCart> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it) }
            val order = rows.firstOrNull { it.id == orderId }
                ?: return CustomerResult.Failed(CustomerError.NotFound)
            val cartItems = order.items.mapIndexed { idx, line ->
                RemoteCartItem(
                    id = "re-$idx",
                    productId = line.productId ?: "p-$idx",
                    variantId = null,
                    name = line.name,
                    variantName = line.variantName,
                    shopName = order.shopName,
                    imageUrl = line.imageUrl,
                    price = line.price,
                    currency = order.currency,
                    quantity = line.quantity,
                    maxStock = 10,
                )
            }
            return CustomerResult.Ok(RemoteCart(cartItems))
        }
    }

    private fun sampleOrder(id: String, status: String) = CustomerOrder(
        id = id,
        orderNumber = "202611200000000$id",
        status = status,
        total = 485.0,
        currency = "THB",
        createdAt = 1767323045000L,
        shopName = "Lumen Shop",
        deliveryAddress = "99/1 Sukhumvit Bangkok 10110",
        items = listOf(OrderLine("p1", "Lamp", null, 2, 225.0, "Warm White")),
        subtotal = 450.0,
        shippingFee = 35.0,
        paymentMethod = "PROMPTPAY",
    )

    private fun repo(api: FakeCustomerApi) = CustomerRepository(api, token = { "tok" })

    @Before
    fun setUp() = OrdersState.reset()

    @After
    fun tearDown() = OrdersState.reset()

    @Test
    fun `starts empty with zero demo orders`() {
        assertTrue(OrdersState.orders.isEmpty())
        assertEquals(0, OrdersState.activeCount)
        assertEquals(LoadStatus.Idle, OrdersState.status)
    }

    @Test
    fun `load mirrors server orders, maps statuses, and computes filters and active count`() = runBlocking {
        val server = ServerOrders(
            listOf(
                sampleOrder("1", "pending_payment"),
                sampleOrder("2", "confirmed"),
                sampleOrder("3", "shipped"),
                sampleOrder("4", "delivered"),
                sampleOrder("5", "cancelled"),
                sampleOrder("6", "refunded"),
            ),
        )

        val res = OrdersState.load(repo(server))

        assertTrue(res is CustomerResult.Ok)
        assertEquals(LoadStatus.Ready, OrdersState.status)
        assertEquals(6, OrdersState.orders.size)
        assertEquals(3, OrdersState.activeCount)
        assertEquals(1, OrdersState.forFilter(OrdersFilter.Pending).size)
        assertEquals(1, OrdersState.forFilter(OrdersFilter.Processing).size)
        assertEquals(1, OrdersState.forFilter(OrdersFilter.Shipping).size)
        assertEquals(1, OrdersState.forFilter(OrdersFilter.Completed).size)
        assertEquals(2, OrdersState.forFilter(OrdersFilter.Cancelled).size)

        assertTrue(OrdersState.orders.first { it.id == "1" }.isCancelable)
        assertTrue(OrdersState.orders.first { it.id == "2" }.isCancelable)
        assertFalse(OrdersState.orders.first { it.id == "3" }.isCancelable)
    }

    @Test
    fun `cancelOrder calls server PATCH and reloads updated status`() = runBlocking {
        val server = ServerOrders(listOf(sampleOrder("1", "pending_payment")))
        val r = repo(server)
        OrdersState.load(r)

        val res = OrdersState.cancelOrder(r, "1")

        assertTrue(res is CustomerResult.Ok)
        assertEquals(OrderStatus.Cancelled, OrdersState.orders.single().uiStatus)
        assertEquals(0, OrdersState.activeCount)
    }

    @Test
    fun `reorder populates cartState from server response`() = runBlocking {
        val server = ServerOrders(listOf(sampleOrder("1", "delivered")))
        val r = repo(server)
        val cart = CartState()

        val res = OrdersState.reorder(r, "1", cart)

        assertTrue(res is CustomerResult.Ok)
        assertEquals(1, cart.items.size)
        assertEquals("p1", cart.items.single().productId)
        assertEquals(2, cart.items.single().quantity)
    }

    @Test
    fun `loadDetail fetches full order and updates cached entry`() = runBlocking {
        val initial = sampleOrder("1", "shipped")
        val detailed = initial.copy(trackingNumber = "TH123456", carrier = "Flash Express")
        val server = ServerOrders(listOf(detailed))
        OrdersState.reset(listOf(initial))

        val res = OrdersState.loadDetail(repo(server), "1")

        res as CustomerResult.Ok
        assertEquals("TH123456", res.value.trackingNumber)
        assertEquals("Flash Express", OrdersState.orders.single().carrier)
    }
}
