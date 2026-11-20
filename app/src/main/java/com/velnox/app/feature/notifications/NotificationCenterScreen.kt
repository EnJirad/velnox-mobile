package com.velnox.app.feature.notifications

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velnox.app.R
import com.velnox.app.ui.components.BrandBadge
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Circle
import com.velnox.app.ui.theme.ContentMaxWidth
import com.velnox.app.ui.theme.PageGutter
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxSpacing

@Composable
fun NotificationCenterScreen(
    state: NotificationsState,
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by remember { mutableStateOf(NotificationCategory.ALL) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val filteredList = state.filterByCategory(selectedCategory)

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.notifications_clear_all),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.notifications_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.clearAll()
                        showClearConfirmDialog = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
            shape = SquircleLg,
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NotificationCenterHeader(
            unreadCount = state.unreadCount,
            hasItems = state.items.isNotEmpty(),
            onBack = onBack,
            onMarkAllAsRead = { state.markAllAsRead() },
            onClearAll = { showClearConfirmDialog = true },
        )

        NotificationCategoryTabs(
            selectedCategory = selectedCategory,
            onSelectCategory = { selectedCategory = it },
            state = state,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Crossfade(
            targetState = selectedCategory,
            animationSpec = tween(durationMillis = 200),
            label = "notification_tab_transition",
            modifier = Modifier.weight(1f),
        ) { category ->
            val itemsForCategory = state.filterByCategory(category)

            if (itemsForCategory.isEmpty()) {
                NotificationEmptyState(category = category)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = PageGutter,
                        end = PageGutter,
                        top = VelnoxSpacing.md,
                        bottom = VelnoxSpacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
                ) {
                    items(itemsForCategory, key = { it.id }) { notification ->
                        NotificationItemCard(
                            notification = notification,
                            onClick = {
                                state.markAsRead(notification.id)
                                notification.targetRoute?.let { onOpenRoute(it) }
                            },
                            onDelete = { state.deleteNotification(notification.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCenterHeader(
    unreadCount: Int,
    hasItems: Boolean,
    onBack: () -> Unit,
    onMarkAllAsRead: () -> Unit,
    onClearAll: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = VelnoxSpacing.sm, vertical = VelnoxSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = VelnoxIcons.ChevronLeft,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.width(VelnoxSpacing.xs))

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.notifications_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (unreadCount > 0) {
                    Spacer(Modifier.width(VelnoxSpacing.sm))
                    Box(
                        modifier = Modifier
                            .clip(Circle)
                            .background(VelnoxBrand)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            style = Text2xs,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (unreadCount > 0) {
                TextButton(
                    onClick = onMarkAllAsRead,
                    contentPadding = PaddingValues(horizontal = VelnoxSpacing.sm),
                ) {
                    Icon(
                        imageVector = VelnoxIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = VelnoxBrand,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.notifications_mark_all_read),
                        style = TextXs,
                        fontWeight = FontWeight.SemiBold,
                        color = VelnoxBrand,
                    )
                }
            }

            if (hasItems) {
                IconButton(onClick = onClearAll) {
                    Icon(
                        imageVector = VelnoxIcons.Trash,
                        contentDescription = stringResource(R.string.notifications_clear_all),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCategoryTabs(
    selectedCategory: NotificationCategory,
    onSelectCategory: (NotificationCategory) -> Unit,
    state: NotificationsState,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter, vertical = VelnoxSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
        ) {
            val categories = NotificationCategory.values()
            items(categories) { category ->
                val isSelected = category == selectedCategory
                val unreadCount = state.unreadCountForCategory(category)
                val labelRes = when (category) {
                    NotificationCategory.ALL -> R.string.notifications_tab_all
                    NotificationCategory.ORDERS -> R.string.notifications_tab_orders
                    NotificationCategory.PROMOTIONS -> R.string.notifications_tab_promotions
                    NotificationCategory.SYSTEM -> R.string.notifications_tab_system
                }

                val background = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Row(
                    modifier = Modifier
                        .clip(Squircle)
                        .background(background)
                        .velnoxClickable(role = Role.Tab, onClick = { onSelectCategory(category) })
                        .padding(horizontal = VelnoxSpacing.md, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = TextXs,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor,
                    )

                    if (unreadCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(Circle)
                                .background(VelnoxBrand),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItemCard(
    notification: AppNotification,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val (icon, iconTint, iconBg) = when (notification.category) {
        NotificationCategory.ORDERS -> Triple(
            VelnoxIcons.Truck,
            VelnoxBrand,
            VelnoxBrand.copy(alpha = 0.12f),
        )
        NotificationCategory.PROMOTIONS -> Triple(
            VelnoxIcons.Sparkles,
            Color(0xFFD97706),
            Color(0xFFFEF3C7),
        )
        NotificationCategory.SYSTEM -> Triple(
            VelnoxIcons.ShieldCheck,
            Color(0xFF4F46E5),
            Color(0xFFEEF2FF),
        )
        NotificationCategory.ALL -> Triple(
            VelnoxIcons.Bell,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer,
        )
    }

    val cardBackground = if (notification.unread) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleLg)
            .background(cardBackground)
            .border(
                width = 1.dp,
                color = if (notification.unread) {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                },
                shape = SquircleLg,
            )
            .velnoxClickable(role = Role.Button, onClick = onClick)
            .padding(VelnoxSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(SquircleSm)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(VelnoxSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (notification.unread) FontWeight.Bold else FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (notification.unread) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(Circle)
                                .background(VelnoxBrand),
                        )
                    }
                }

                Spacer(Modifier.width(VelnoxSpacing.xs))
                Text(
                    text = notification.timeAgo,
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = notification.body,
                style = TextXs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )

            Spacer(Modifier.height(VelnoxSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tagText = when (notification.category) {
                    NotificationCategory.ORDERS -> stringResource(R.string.notifications_tab_orders)
                    NotificationCategory.PROMOTIONS -> stringResource(R.string.notifications_tab_promotions)
                    NotificationCategory.SYSTEM -> stringResource(R.string.notifications_tab_system)
                    NotificationCategory.ALL -> stringResource(R.string.notifications_tab_all)
                }

                BrandBadge(text = tagText)

                if (notification.targetRoute != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = VelnoxIcons.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationEmptyState(category: NotificationCategory) {
    val messageRes = if (category == NotificationCategory.ALL) {
        R.string.notifications_empty_body
    } else {
        R.string.notifications_empty_filter
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(PageGutter),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(Circle)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VelnoxIcons.Bell,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp),
                )
            }

            Spacer(Modifier.height(VelnoxSpacing.md))

            Text(
                text = stringResource(R.string.notifications_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(VelnoxSpacing.xs))

            Text(
                text = stringResource(messageRes),
                style = TextXs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )
        }
    }
}
