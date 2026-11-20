package com.velnox.app.feature.notifications

import com.velnox.app.core.customer.CustomerError
import com.velnox.app.core.customer.CustomerNotification
import com.velnox.app.core.customer.CustomerRepository
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.FakeCustomerApi
import com.velnox.app.core.customer.LoadStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsStateTest {

    private class ServerNotifications(initial: List<CustomerNotification>) : FakeCustomerApi() {
        val rows = initial.toMutableList()
        var failNext: CustomerError? = null
        var markedIds = mutableListOf<String>()
        var markedAll = 0

        override suspend fun notifications(token: String): CustomerResult<List<CustomerNotification>> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it) }
            return CustomerResult.Ok(rows.toList())
        }

        override suspend fun markNotificationRead(token: String, id: String): CustomerResult<Unit> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it) }
            markedIds += id
            val idx = rows.indexOfFirst { it.id == id }
            if (idx >= 0) rows[idx] = rows[idx].copy(read = true)
            return CustomerResult.Ok(Unit)
        }

        override suspend fun markAllNotificationsRead(token: String): CustomerResult<Unit> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it) }
            markedAll++
            rows.replaceAll { it.copy(read = true) }
            return CustomerResult.Ok(Unit)
        }
    }

    private fun repo(api: FakeCustomerApi) = CustomerRepository(api, token = { "tok" })

    @Test
    fun `initial state starts empty with zero mock notifications`() {
        val state = NotificationsState()
        assertTrue(state.items.isEmpty())
        assertEquals(0, state.unreadCount)
        assertEquals(LoadStatus.Idle, state.status)
    }

    @Test
    fun `load mirrors server notifications, maps categories, and formats time`() = runBlocking {
        val now = 1_767_323_600_000L
        val server = ServerNotifications(
            listOf(
                CustomerNotification("n1", "order_shipped", "Shipped", "On the way", now - 600_000L, false, "order:o1"),
                CustomerNotification("n2", "promotion", "Sale", "-20%", now - 7_200_000L, false, null),
                CustomerNotification("n3", "security", "Login", "New device", now - 172_800_000L, true, null),
            ),
        )
        val state = NotificationsState()

        val res = state.load(repo(server), nowMillis = now)

        assertTrue(res is CustomerResult.Ok)
        assertEquals(LoadStatus.Ready, state.status)
        assertEquals(3, state.items.size)
        assertEquals(2, state.unreadCount)
        assertEquals(1, state.unreadCountForCategory(NotificationCategory.ORDERS))
        assertEquals(1, state.unreadCountForCategory(NotificationCategory.PROMOTIONS))
        assertEquals(0, state.unreadCountForCategory(NotificationCategory.SYSTEM))
        assertEquals("10m", state.items[0].timeAgo)
        assertEquals("2h", state.items[1].timeAgo)
        assertEquals("2d", state.items[2].timeAgo)
        assertEquals("order:o1", state.items[0].targetRoute)
    }

    @Test
    fun `markAsReadRemote and markAllAsReadRemote persist on server and update unread count`() = runBlocking {
        val server = ServerNotifications(
            listOf(
                CustomerNotification("n1", "order", "Order 1", "Body", 1L, false, null),
                CustomerNotification("n2", "promo", "Promo 1", "Body", 1L, false, null),
            ),
        )
        val state = NotificationsState()
        val r = repo(server)
        state.load(r)

        assertTrue(state.markAsReadRemote(r, "n1") is CustomerResult.Ok)
        assertEquals(listOf("n1"), server.markedIds)
        assertEquals(1, state.unreadCount)
        assertFalse(state.items.first { it.id == "n1" }.unread)

        assertTrue(state.markAllAsReadRemote(r) is CustomerResult.Ok)
        assertEquals(1, server.markedAll)
        assertEquals(0, state.unreadCount)
        assertTrue(state.items.none { it.unread })
    }

    @Test
    fun `failed load marks status Failed`() = runBlocking {
        val server = ServerNotifications(emptyList())
        server.failNext = CustomerError.Network
        val state = NotificationsState()

        val res = state.load(repo(server))

        assertEquals(CustomerResult.Failed(CustomerError.Network), res)
        assertEquals(LoadStatus.Failed, state.status)
    }

    @Test
    fun `filtering by category returns correct subsets`() {
        val state = NotificationsState(
            initialItems = listOf(
                AppNotification("1", NotificationCategory.ORDERS, "Order 1", "Body", "1m", unread = true),
                AppNotification("2", NotificationCategory.PROMOTIONS, "Promo 1", "Body", "2m", unread = true),
                AppNotification("3", NotificationCategory.SYSTEM, "Sys 1", "Body", "3m", unread = false),
                AppNotification("4", NotificationCategory.ORDERS, "Order 2", "Body", "4m", unread = false),
            ),
        )

        assertEquals(4, state.filterByCategory(NotificationCategory.ALL).size)
        assertEquals(2, state.filterByCategory(NotificationCategory.ORDERS).size)
        assertEquals(1, state.filterByCategory(NotificationCategory.PROMOTIONS).size)
        assertEquals(1, state.filterByCategory(NotificationCategory.SYSTEM).size)
    }
}
