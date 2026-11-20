package com.velnox.app.feature.notifications

import androidx.compose.runtime.mutableStateListOf

/**
 * Categories of user notifications.
 */
enum class NotificationCategory {
    ALL,
    ORDERS,
    PROMOTIONS,
    SYSTEM,
}

/**
 * An individual notification item displayed in the Notification Center.
 */
data class AppNotification(
    val id: String,
    val category: NotificationCategory,
    val title: String,
    val body: String,
    val timeAgo: String,
    val unread: Boolean = true,
    val targetRoute: String? = null,
)

/**
 * Observable in-memory state holder for notifications with category filtering and unread management.
 */
class NotificationsState(
    initialItems: List<AppNotification> = defaultNotifications(),
) {
    private val _items = mutableStateListOf<AppNotification>().apply {
        addAll(initialItems)
    }

    val items: List<AppNotification>
        get() = _items

    val unreadCount: Int
        get() = _items.count { it.unread }

    fun unreadCountForCategory(category: NotificationCategory): Int {
        return if (category == NotificationCategory.ALL) {
            unreadCount
        } else {
            _items.count { it.category == category && it.unread }
        }
    }

    fun markAsRead(id: String) {
        val index = _items.indexOfFirst { it.id == id }
        if (index >= 0) {
            val item = _items[index]
            if (item.unread) {
                _items[index] = item.copy(unread = false)
            }
        }
    }

    fun markAllAsRead() {
        for (i in _items.indices) {
            if (_items[i].unread) {
                _items[i] = _items[i].copy(unread = false)
            }
        }
    }

    fun deleteNotification(id: String) {
        _items.removeAll { it.id == id }
    }

    fun clearAll() {
        _items.clear()
    }

    fun addNotification(notification: AppNotification) {
        _items.add(0, notification)
    }

    fun filterByCategory(category: NotificationCategory): List<AppNotification> {
        return if (category == NotificationCategory.ALL) {
            _items.toList()
        } else {
            _items.filter { it.category == category }
        }
    }

    companion object {
        fun defaultNotifications(): List<AppNotification> = listOf(
            AppNotification(
                id = "notif_ord_1",
                category = NotificationCategory.ORDERS,
                title = "Order Shipped / พัสดุถูกจัดส่งแล้ว",
                body = "Order #VLX-2401 is on its way with Flash Express. Tracking: TH01928374.",
                timeAgo = "10m ago",
                unread = true,
                targetRoute = "order:VLX-2401",
            ),
            AppNotification(
                id = "notif_promo_1",
                category = NotificationCategory.PROMOTIONS,
                title = "Flash Sale Drop! 🔥",
                body = "Save up to 50% on Premium Electronics & Audio accessories. Limited quantities!",
                timeAgo = "1h ago",
                unread = true,
                targetRoute = "category:electronics",
            ),
            AppNotification(
                id = "notif_ord_2",
                category = NotificationCategory.ORDERS,
                title = "Order Confirmed / ยืนยันคำสั่งซื้อ",
                body = "Order #VLX-8820 is packed and ready for carrier pickup.",
                timeAgo = "3h ago",
                unread = true,
                targetRoute = "order:VLX-8820",
            ),
            AppNotification(
                id = "notif_promo_2",
                category = NotificationCategory.PROMOTIONS,
                title = "Exclusive Voucher: ฿150 Off",
                body = "Claim your weekend shopping voucher now. Valid across all verified stores.",
                timeAgo = "1d ago",
                unread = false,
                targetRoute = "category",
            ),
            AppNotification(
                id = "notif_sys_1",
                category = NotificationCategory.SYSTEM,
                title = "Security Alert: Active Session",
                body = "New login from Pixel 8 Pro (Bangkok, TH). Tap to review your authorized devices.",
                timeAgo = "2d ago",
                unread = false,
                targetRoute = "settings/security",
            ),
            AppNotification(
                id = "notif_sys_2",
                category = NotificationCategory.SYSTEM,
                title = "Velnox App Updated to v2.4.0",
                body = "Ultra-fast image engine, optimized memory cache, and redesigned checkout are live.",
                timeAgo = "3d ago",
                unread = false,
                targetRoute = null,
            ),
        )
    }
}
