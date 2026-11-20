package com.velnox.app.feature.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.velnox.app.core.customer.CustomerNotification
import com.velnox.app.core.customer.CustomerRepository
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.LoadStatus

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
 * Observable state holder for notifications mirrored from `/api/customer/notifications`.
 *
 * Starts empty (zero mock notifications).
 */
class NotificationsState(
    initialItems: List<AppNotification> = emptyList(),
) {
    private val _items = mutableStateListOf<AppNotification>().apply {
        addAll(initialItems)
    }

    var status by mutableStateOf(if (initialItems.isEmpty()) LoadStatus.Idle else LoadStatus.Ready)
        private set

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

    /** Fetch notifications from `GET /api/customer/notifications`. */
    suspend fun load(
        repository: CustomerRepository,
        nowMillis: Long = System.currentTimeMillis(),
    ): CustomerResult<Unit> {
        status = LoadStatus.Loading
        return when (val result = repository.call { api, token -> api.notifications(token) }) {
            is CustomerResult.Ok -> {
                _items.clear()
                _items.addAll(result.value.map { it.toAppNotification(nowMillis) })
                status = LoadStatus.Ready
                CustomerResult.Ok(Unit)
            }

            is CustomerResult.Failed -> {
                status = LoadStatus.Failed
                result
            }
        }
    }

    /** Mark one notification read via `PATCH /api/customer/notifications/:id/read`. */
    suspend fun markAsReadRemote(
        repository: CustomerRepository,
        id: String,
    ): CustomerResult<Unit> {
        val current = _items.firstOrNull { it.id == id }
        if (current != null && !current.unread) return CustomerResult.Ok(Unit)
        val result = repository.call { api, token -> api.markNotificationRead(token, id) }
        if (result is CustomerResult.Ok) {
            markAsRead(id)
        }
        return result
    }

    /** Mark all notifications read via `PUT /api/customer/notifications/read-all`. */
    suspend fun markAllAsReadRemote(repository: CustomerRepository): CustomerResult<Unit> {
        if (unreadCount == 0) return CustomerResult.Ok(Unit)
        val result = repository.call { api, token -> api.markAllNotificationsRead(token) }
        if (result is CustomerResult.Ok) {
            markAllAsRead()
        }
        return result
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
        status = LoadStatus.Idle
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
        fun categoryFor(rawType: String): NotificationCategory = when {
            rawType.contains("order") ||
                rawType.contains("ship") ||
                rawType.contains("payment") ||
                rawType.contains("deliver") -> NotificationCategory.ORDERS
            rawType.contains("promo") ||
                rawType.contains("voucher") ||
                rawType.contains("deal") ||
                rawType.contains("sale") -> NotificationCategory.PROMOTIONS
            else -> NotificationCategory.SYSTEM
        }

        fun formatTimeAgo(createdAtMillis: Long?, nowMillis: Long): String {
            if (createdAtMillis == null || createdAtMillis <= 0L) return ""
            val diffSec = ((nowMillis - createdAtMillis) / 1000L).coerceAtLeast(0L)
            return when {
                diffSec < 60L -> "now"
                diffSec < 3600L -> "${diffSec / 60L}m"
                diffSec < 86400L -> "${diffSec / 3600L}h"
                else -> "${diffSec / 86400L}d"
            }
        }
    }
}

private fun CustomerNotification.toAppNotification(nowMillis: Long): AppNotification = AppNotification(
    id = id,
    category = NotificationsState.categoryFor(type),
    title = title,
    body = body,
    timeAgo = NotificationsState.formatTimeAgo(createdAt, nowMillis),
    unread = !read,
    targetRoute = link,
)
