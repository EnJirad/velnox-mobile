package com.velnox.app.feature.orders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrdersStateTest {

    @Test
    fun `adding order places it at front of orders list`() {
        val initialCount = OrdersDemo.orders.size
        val newOrder = DemoOrder(
            id = "VLX-9999",
            status = OrderStatus.Processing,
            customTitle = "Mechanical Keyboard",
            totalPrice = 1890.0,
            itemCount = 1,
            daysAgo = 0,
        )

        OrdersDemo.addOrder(newOrder)

        assertEquals(initialCount + 1, OrdersDemo.orders.size)
        assertEquals("VLX-9999", OrdersDemo.orders.first().id)
        assertEquals(OrderStatus.Processing, OrdersDemo.orders.first().status)
        assertEquals(1890.0, OrdersDemo.orders.first().totalPrice!!, 0.001)
    }

    @Test
    fun `cancelling order updates its status to Cancelled`() {
        val testOrder = DemoOrder(
            id = "VLX-CANCEL-TEST",
            status = OrderStatus.Pending,
            customTitle = "Test Item To Cancel",
            totalPrice = 500.0,
            itemCount = 1,
            daysAgo = 0,
        )
        OrdersDemo.addOrder(testOrder)

        assertEquals(OrderStatus.Pending, OrdersDemo.orders.first { it.id == "VLX-CANCEL-TEST" }.status)

        OrdersDemo.cancelOrder("VLX-CANCEL-TEST")

        val cancelled = OrdersDemo.orders.first { it.id == "VLX-CANCEL-TEST" }
        assertEquals(OrderStatus.Cancelled, cancelled.status)

        // Cancelled orders appear under Cancelled filter
        val cancelledList = OrdersDemo.forFilter(OrdersFilter.Cancelled)
        assertTrue(cancelledList.any { it.id == "VLX-CANCEL-TEST" })
    }

    @Test
    fun `forFilter returns correct subsets`() {
        val all = OrdersDemo.forFilter(OrdersFilter.All)
        assertEquals(OrdersDemo.orders.size, all.size)

        val processing = OrdersDemo.forFilter(OrdersFilter.Processing)
        assertTrue(processing.all { it.status == OrderStatus.Processing })

        val completed = OrdersDemo.forFilter(OrdersFilter.Completed)
        assertTrue(completed.all { it.status == OrderStatus.Completed })
    }
}
