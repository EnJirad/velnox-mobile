package com.velnox.app.feature.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsStateTest {

    @Test
    fun `initial state contains default notifications with unread items`() {
        val state = NotificationsState()
        assertTrue(state.items.isNotEmpty())
        assertTrue(state.unreadCount > 0)
        assertEquals(state.items.count { it.unread }, state.unreadCount)
    }

    @Test
    fun `filtering by category returns correct subsets`() {
        val state = NotificationsState(
            initialItems = listOf(
                AppNotification("1", NotificationCategory.ORDERS, "Order 1", "Body", "1m", unread = true),
                AppNotification("2", NotificationCategory.PROMOTIONS, "Promo 1", "Body", "2m", unread = true),
                AppNotification("3", NotificationCategory.SYSTEM, "Sys 1", "Body", "3m", unread = false),
                AppNotification("4", NotificationCategory.ORDERS, "Order 2", "Body", "4m", unread = false),
            )
        )

        val allItems = state.filterByCategory(NotificationCategory.ALL)
        assertEquals(4, allItems.size)

        val orderItems = state.filterByCategory(NotificationCategory.ORDERS)
        assertEquals(2, orderItems.size)
        assertTrue(orderItems.all { it.category == NotificationCategory.ORDERS })

        val promoItems = state.filterByCategory(NotificationCategory.PROMOTIONS)
        assertEquals(1, promoItems.size)
        assertEquals("2", promoItems.first().id)

        val sysItems = state.filterByCategory(NotificationCategory.SYSTEM)
        assertEquals(1, sysItems.size)
        assertEquals("3", sysItems.first().id)
    }

    @Test
    fun `unread count by category counts only unread items in that category`() {
        val state = NotificationsState(
            initialItems = listOf(
                AppNotification("1", NotificationCategory.ORDERS, "Order 1", "Body", "1m", unread = true),
                AppNotification("2", NotificationCategory.ORDERS, "Order 2", "Body", "2m", unread = false),
                AppNotification("3", NotificationCategory.PROMOTIONS, "Promo 1", "Body", "3m", unread = true),
            )
        )

        assertEquals(2, state.unreadCount)
        assertEquals(2, state.unreadCountForCategory(NotificationCategory.ALL))
        assertEquals(1, state.unreadCountForCategory(NotificationCategory.ORDERS))
        assertEquals(1, state.unreadCountForCategory(NotificationCategory.PROMOTIONS))
        assertEquals(0, state.unreadCountForCategory(NotificationCategory.SYSTEM))
    }

    @Test
    fun `markAsRead updates item unread status and decrements unreadCount`() {
        val notif = AppNotification("target", NotificationCategory.ORDERS, "Order 1", "Body", "1m", unread = true)
        val state = NotificationsState(initialItems = listOf(notif))

        assertEquals(1, state.unreadCount)
        assertTrue(state.items.first().unread)

        state.markAsRead("target")

        assertEquals(0, state.unreadCount)
        assertFalse(state.items.first().unread)
    }

    @Test
    fun `markAllAsRead sets all notifications to read`() {
        val state = NotificationsState(
            initialItems = listOf(
                AppNotification("1", NotificationCategory.ORDERS, "Order 1", "Body", "1m", unread = true),
                AppNotification("2", NotificationCategory.PROMOTIONS, "Promo 1", "Body", "2m", unread = true),
                AppNotification("3", NotificationCategory.SYSTEM, "Sys 1", "Body", "3m", unread = true),
            )
        )

        assertEquals(3, state.unreadCount)

        state.markAllAsRead()

        assertEquals(0, state.unreadCount)
        assertTrue(state.items.none { it.unread })
    }

    @Test
    fun `deleteNotification removes the notification from list`() {
        val state = NotificationsState(
            initialItems = listOf(
                AppNotification("1", NotificationCategory.ORDERS, "Order 1", "Body", "1m", unread = true),
                AppNotification("2", NotificationCategory.PROMOTIONS, "Promo 1", "Body", "2m", unread = true),
            )
        )

        state.deleteNotification("1")

        assertEquals(1, state.items.size)
        assertEquals("2", state.items.first().id)
        assertEquals(1, state.unreadCount)
    }

    @Test
    fun `clearAll empties notification list`() {
        val state = NotificationsState(
            initialItems = listOf(
                AppNotification("1", NotificationCategory.ORDERS, "Order 1", "Body", "1m", unread = true),
            )
        )

        state.clearAll()

        assertTrue(state.items.isEmpty())
        assertEquals(0, state.unreadCount)
    }

    @Test
    fun `addNotification adds new item to the top`() {
        val state = NotificationsState(initialItems = emptyList())
        val newNotif = AppNotification("new_1", NotificationCategory.ORDERS, "New Order", "Body", "Just now", unread = true)

        state.addNotification(newNotif)

        assertEquals(1, state.items.size)
        assertEquals("new_1", state.items.first().id)
        assertEquals(1, state.unreadCount)
    }
}
