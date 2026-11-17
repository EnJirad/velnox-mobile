package com.velnox.app.feature.home

/**
 * Notification row model for the Home bell popover.
 *
 * There is no notification service in this phase, so [HomeNotifications.items] is
 * empty and the popover shows its honest empty state. A future repository can
 * fill this list without changing the popover UI.
 */
data class HomeNotification(
    val id: String,
    val title: String,
    val body: String,
    val time: String? = null,
    val unread: Boolean = false,
)

object HomeNotifications {
    val items: List<HomeNotification> = emptyList()
}
