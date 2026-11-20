@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.profile

import com.velnox.app.ui.components.velnoxClickable

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.core.auth.AuthUser
import com.velnox.app.feature.orders.OrdersFilter
import com.velnox.app.feature.orders.OrdersState
import com.velnox.app.feature.orders.OrderStatus
import com.velnox.app.feature.orders.uiStatus
import com.velnox.app.feature.wishlist.WishlistState
import com.velnox.app.ui.components.BrandBadge
import com.velnox.app.ui.components.CardDivider
import com.velnox.app.ui.components.DangerRow
import com.velnox.app.ui.components.InitialsAvatar
import com.velnox.app.ui.components.IconActionButton
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.SectionCard
import com.velnox.app.ui.components.SettingRow
import com.velnox.app.ui.components.SoftIconTile
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxTheme

/**
 * Profile tab — the account hub.
 *
 * Settings lives behind the icon in the top-right corner. Identity is clearly
 * labelled as a demo account, and no address, payment or order value is real.
 */
@Composable
fun ProfileScreen(
    user: AuthUser,
    onOpenSettings: () -> Unit,
    onOpenEditProfile: () -> Unit,
    onOpenPersonalInfo: () -> Unit,
    onOpenAddresses: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenWishlist: () -> Unit,
    onOpenMessages: () -> Unit,
    modifier: Modifier = Modifier,
    onSignOut: () -> Unit = {},
) {
    var showSignOut by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(start = PageGutter, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconActionButton(
                icon = VelnoxIcons.Cog,
                contentDescription = stringResource(R.string.profile_settings),
                onClick = onOpenSettings,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = PageGutter, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ProfileHeaderCard(user = user, onOpenEditProfile = onOpenEditProfile) }

            item {
                StatsRow(
                    onOpenOrders = onOpenOrders,
                    onOpenWishlist = onOpenWishlist,
                    onOpenMessages = onOpenMessages,
                )
            }

            item {
                OrderShortcutsCard(onOpenOrders = onOpenOrders)
            }

            item {
                SectionCard(title = stringResource(R.string.profile_section_account)) {
                    AccountRow(
                        icon = VelnoxIcons.Pencil,
                        title = stringResource(R.string.profile_edit),
                        onClick = onOpenEditProfile,
                    )
                    CardDivider()
                    AccountRow(
                        icon = VelnoxIcons.User,
                        title = stringResource(R.string.profile_personal_info),
                        onClick = onOpenPersonalInfo,
                    )
                    CardDivider()
                    AccountRow(
                        icon = VelnoxIcons.MapPin,
                        title = stringResource(R.string.profile_addresses),
                        onClick = onOpenAddresses,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.profile_section_shortcuts)) {
                    SettingRow(
                        icon = VelnoxIcons.Cog,
                        title = stringResource(R.string.profile_settings),
                        onClick = onOpenSettings,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.profile_section_addresses)) {
                    val addresses = AddressBookState.addresses
                    if (addresses.isEmpty()) {
                        Text(
                            text = stringResource(R.string.address_empty),
                            style = TextXs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    } else {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            addresses.forEachIndexed { index, addr ->
                                AddressLine(
                                    label = addr.recipientName + if (addr.isDefault) " (${stringResource(R.string.profile_address_default)})" else "",
                                    detail = addr.fullAddressText,
                                    isDefault = addr.isDefault,
                                )
                                if (index < addresses.lastIndex) {
                                    Spacer(Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                    CardDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .velnoxClickable(role = Role.Button, onClick = onOpenAddresses)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = VelnoxIcons.Plus,
                            contentDescription = null,
                            tint = VelnoxBrand,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.profile_add_address),
                            style = TextXs.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            item {
                SectionCard {
                    DangerRow(
                        icon = VelnoxIcons.LogOut,
                        title = stringResource(R.string.profile_sign_out),
                        onClick = { showSignOut = true },
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.common_preview_notice),
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }
    }

    if (showSignOut) {
        AlertDialog(
            onDismissRequest = { showSignOut = false },
            shape = Squircle,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOut = false
                        onSignOut()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.settings_sign_out_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOut = false }) {
                    Text(
                        text = stringResource(R.string.common_close),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )
    }
}

@Composable
private fun ProfileHeaderCard(user: AuthUser, onOpenEditProfile: () -> Unit) {
    // Initials from the real account, never a hardcoded pair.
    val initials = remember(user.name, user.email) { initialsOf(user.name, user.email) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(initials = initials, size = 56, brand = true)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.name.ifBlank { user.email.substringBefore('@') },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    BrandBadge(text = stringResource(R.string.profile_verified_badge))
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(SquircleSm)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.profile_membership_tier),
                            style = Text2xs.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.profile_demo_handle),
                        style = Text2xs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.profile_role_label, user.role),
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(SquircleSm)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .velnoxClickable(role = Role.Button, onClick = onOpenEditProfile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VelnoxIcons.Pencil,
                    contentDescription = stringResource(R.string.profile_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun StatsRow(
    onOpenOrders: () -> Unit,
    onOpenWishlist: () -> Unit,
    onOpenMessages: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            value = OrdersState.orders.size.toString(),
            label = stringResource(R.string.profile_stats_orders),
            modifier = Modifier.weight(1f),
            onClick = onOpenOrders,
        )
        StatTile(
            value = WishlistState.items.size.toString(),
            label = stringResource(R.string.profile_stats_wishlist),
            modifier = Modifier.weight(1f),
            onClick = onOpenWishlist,
        )
        StatTile(
            value = "3",
            label = stringResource(R.string.profile_stats_messages),
            modifier = Modifier.weight(1f),
            onClick = onOpenMessages,
        )
        StatTile(
            value = "5",
            label = stringResource(R.string.profile_stats_vouchers),
            modifier = Modifier.weight(1f),
            onClick = {},
        )
    }
}

@Composable
private fun OrderShortcutsCard(onOpenOrders: () -> Unit) {
    val toPayCount = OrdersState.forFilter(OrdersFilter.Pending).size
    val toShipCount = OrdersState.forFilter(OrdersFilter.Processing).size
    val toReceiveCount = OrdersState.forFilter(OrdersFilter.Shipping).size
    val toReviewCount = OrdersState.forFilter(OrdersFilter.Completed).size
    val returnsCount = OrdersState.orders.count { it.uiStatus == OrderStatus.Returned }

    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.profile_orders),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier.velnoxClickable(role = Role.Button, onClick = onOpenOrders),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.profile_orders_view_all),
                    style = TextXs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = VelnoxIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        CardDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            OrderShortcutItem(
                icon = VelnoxIcons.Wallet,
                label = stringResource(R.string.profile_shortcut_to_pay),
                badge = toPayCount,
                onClick = onOpenOrders,
                modifier = Modifier.weight(1f),
            )
            OrderShortcutItem(
                icon = VelnoxIcons.Package,
                label = stringResource(R.string.profile_shortcut_to_ship),
                badge = toShipCount,
                onClick = onOpenOrders,
                modifier = Modifier.weight(1f),
            )
            OrderShortcutItem(
                icon = VelnoxIcons.Truck,
                label = stringResource(R.string.profile_shortcut_to_receive),
                badge = toReceiveCount,
                onClick = onOpenOrders,
                modifier = Modifier.weight(1f),
            )
            OrderShortcutItem(
                icon = VelnoxIcons.Star,
                label = stringResource(R.string.profile_shortcut_to_review),
                badge = toReviewCount,
                onClick = onOpenOrders,
                modifier = Modifier.weight(1f),
            )
            OrderShortcutItem(
                icon = VelnoxIcons.RotateCcw,
                label = stringResource(R.string.profile_shortcut_returns),
                badge = returnsCount,
                onClick = onOpenOrders,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OrderShortcutItem(
    icon: ImageVector,
    label: String,
    badge: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .velnoxClickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(SquircleSm)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(SquircleSm)
                        .background(MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badge.toString(),
                        style = Text2xs.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = Text2xs,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, Squircle)
            .velnoxClickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = Text2xs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun AccountRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    SettingRow(icon = icon, title = title, onClick = onClick)
}

@Composable
private fun AddressLine(label: String, detail: String, isDefault: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        SoftIconTile(icon = VelnoxIcons.MapPin, size = 28)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                style = TextXs.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = Text2xs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        if (isDefault) {
            Box(
                modifier = Modifier
                    .clip(SquircleSm)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_address_default),
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}


/**
 * Two initials for the avatar.
 *
 * Falls back to the email's first letters when the account has no display name,
 * so the avatar is never blank and never invented.
 */
private fun initialsOf(name: String, email: String): String {
    val source = name.trim().ifBlank { email.substringBefore('@') }
    val parts = source.split(' ', '.', '_', '-').filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        source.isNotEmpty() -> source.take(2).uppercase()
        else -> "?"
    }
}

/** Only for @Preview: a fixed account so the layout can be inspected. */
private val PreviewUser = AuthUser(
    id = "preview",
    email = "ann@example.com",
    name = "Ann Suriya",
    role = "customer",
)

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun ProfilePreview() {
    VelnoxTheme(darkTheme = false) {
        ProfileScreen(
            user = PreviewUser,
            onOpenSettings = {},
            onOpenEditProfile = {},
            onOpenPersonalInfo = {},
            onOpenAddresses = {},
            onOpenOrders = {},
            onOpenWishlist = {},
            onOpenMessages = {},
        )
    }
}

@Preview(name = "Profile 320dp", showBackground = true, widthDp = 320, heightDp = 760)
@Composable
private fun ProfileCompactPreview() {
    VelnoxTheme(darkTheme = false) {
        ProfileScreen(
            user = PreviewUser,
            onOpenSettings = {},
            onOpenEditProfile = {},
            onOpenPersonalInfo = {},
            onOpenAddresses = {},
            onOpenOrders = {},
            onOpenWishlist = {},
            onOpenMessages = {},
        )
    }
}

@Preview(name = "Profile dark", showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun ProfileDarkPreview() {
    VelnoxTheme(darkTheme = true) {
        ProfileScreen(
            user = PreviewUser,
            onOpenSettings = {},
            onOpenEditProfile = {},
            onOpenPersonalInfo = {},
            onOpenAddresses = {},
            onOpenOrders = {},
            onOpenWishlist = {},
            onOpenMessages = {},
        )
    }
}
