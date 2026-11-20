@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.profile

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.velnox.app.R
import com.velnox.app.core.auth.AuthUser
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.rememberCustomerRepository
import com.velnox.app.core.preferences.LocalPreferences
import com.velnox.app.core.session.LocalSession
import com.velnox.app.feature.messages.MessagesState
import com.velnox.app.feature.orders.OrderStatus
import com.velnox.app.feature.orders.OrdersFilter
import com.velnox.app.feature.orders.OrdersState
import com.velnox.app.feature.orders.uiStatus
import com.velnox.app.feature.wishlist.WishlistState
import com.velnox.app.ui.components.BrandBadge
import com.velnox.app.ui.components.CardDivider
import com.velnox.app.ui.components.IconActionButton
import com.velnox.app.ui.components.InitialsAvatar
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.SectionCard
import com.velnox.app.ui.components.SettingRow
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Circle
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class PhotoTarget { Avatar, Cover }

/**
 * Profile tab — clean, focused account hub.
 *
 * Sign out lives exclusively in SettingsScreen (reached via the top-right Cog).
 * Supports customizable Cover Banner & Avatar photo selection persisted locally,
 * summary stats, order status shortcuts, and essential services (Addresses & Help).
 */
@Composable
fun ProfileScreen(
    user: AuthUser,
    onOpenSettings: () -> Unit,
    onOpenEditProfile: () -> Unit,
    onOpenAddresses: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenWishlist: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenHelpCenter: () -> Unit,
    modifier: Modifier = Modifier,
    onPickImage: (((Uri?) -> Unit) -> Unit)? = null,
    onOpenOrdersWithFilter: ((OrdersFilter) -> Unit)? = null,
) {
    val context = LocalContext.current
    val preferences = LocalPreferences.current
    val session = LocalSession.current
    val customerRepository = rememberCustomerRepository()
    val scope = rememberCoroutineScope()

    var showPhotoOptionsFor by remember { mutableStateOf<PhotoTarget?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var isUploadingCover by remember { mutableStateOf(false) }

    // Synchronize latest profile from backend so web updates appear instantly on mobile
    LaunchedEffect(user.id) {
        when (val res = customerRepository.call { api, token -> api.profile(token) }) {
            is CustomerResult.Ok -> {
                val p = res.value
                session.updateProfile(
                    name = p.name,
                    avatar = p.avatarUrl,
                    coverUrl = p.coverUrl,
                )
            }
            else -> Unit
        }
    }

    fun launchGallery(target: PhotoTarget) {
        if (onPickImage == null) return
        onPickImage { uri ->
            if (uri != null) {
                scope.launch {
                    if (target == PhotoTarget.Avatar) isUploadingAvatar = true else isUploadingCover = true
                    try {
                        // 1. Instant local persistence for immediate offline feedback
                        val savedPath = persistPickedImage(
                            context = context,
                            sourceUri = uri,
                            prefix = if (target == PhotoTarget.Avatar) "profile_avatar" else "profile_cover",
                        )
                        if (savedPath != null) {
                            if (target == PhotoTarget.Avatar) {
                                preferences.setAvatarUri(savedPath)
                            } else {
                                preferences.setCoverUri(savedPath)
                            }
                        }

                        // 2. Read binary data and upload directly to Cloudflare R2
                        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val bytes = withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            }.getOrNull()
                        }
                        if (bytes != null) {
                            val kind = if (target == PhotoTarget.Avatar) "avatar" else "cover"
                            val uploadResult = customerRepository.call { api, token ->
                                api.uploadProfileImage(
                                    token = token,
                                    kind = kind,
                                    bytes = bytes,
                                    mimeType = mimeType,
                                )
                            }
                            if (uploadResult is CustomerResult.Ok) {
                                val cdnUrl = uploadResult.value
                                if (target == PhotoTarget.Avatar) {
                                    session.updateProfile(avatar = cdnUrl)
                                    preferences.setAvatarUri(cdnUrl)
                                } else {
                                    session.updateProfile(coverUrl = cdnUrl)
                                    preferences.setCoverUri(cdnUrl)
                                }
                            }
                        }
                    } finally {
                        if (target == PhotoTarget.Avatar) isUploadingAvatar = false else isUploadingCover = false
                    }
                }
            }
        }
    }

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
            item {
                val resolvedAvatar = preferences.current.avatarUri?.takeIf { it.isNotBlank() } ?: user.avatar
                val resolvedCover = preferences.current.coverUri?.takeIf { it.isNotBlank() } ?: user.coverUrl
                ProfileHeaderCard(
                    user = user,
                    avatarUri = resolvedAvatar,
                    coverUri = resolvedCover,
                    isUploadingAvatar = isUploadingAvatar,
                    isUploadingCover = isUploadingCover,
                    onEditAvatar = {
                        if (resolvedAvatar != null) {
                            showPhotoOptionsFor = PhotoTarget.Avatar
                        } else {
                            launchGallery(PhotoTarget.Avatar)
                        }
                    },
                    onEditCover = {
                        if (resolvedCover != null) {
                            showPhotoOptionsFor = PhotoTarget.Cover
                        } else {
                            launchGallery(PhotoTarget.Cover)
                        }
                    },
                    onOpenEditProfile = onOpenEditProfile,
                )
            }

            item {
                StatsRow(
                    onOpenOrders = onOpenOrders,
                    onOpenWishlist = onOpenWishlist,
                    onOpenMessages = onOpenMessages,
                )
            }

            item {
                OrderShortcutsCard(
                    onOpenOrders = onOpenOrders,
                    onOpenOrdersWithFilter = onOpenOrdersWithFilter,
                )
            }

            item {
                val addressCount = AddressBookState.addresses.size
                SectionCard(title = stringResource(R.string.profile_section_services)) {
                    SettingRow(
                        icon = VelnoxIcons.MapPin,
                        title = stringResource(R.string.profile_addresses),
                        subtitle = stringResource(R.string.profile_addresses_subtitle),
                        value = if (addressCount > 0) addressCount.toString() else null,
                        onClick = onOpenAddresses,
                    )
                    CardDivider()
                    SettingRow(
                        icon = VelnoxIcons.HelpCircle,
                        title = stringResource(R.string.profile_help_center),
                        subtitle = stringResource(R.string.profile_help_center_subtitle),
                        onClick = onOpenHelpCenter,
                    )
                }
            }
        }
    }

    val targetForOptions = showPhotoOptionsFor
    if (targetForOptions != null) {
        AlertDialog(
            onDismissRequest = { showPhotoOptionsFor = null },
            shape = SquircleLg,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = if (targetForOptions == PhotoTarget.Avatar) {
                        stringResource(R.string.profile_change_avatar)
                    } else {
                        stringResource(R.string.profile_change_cover)
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Squircle)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .velnoxClickable(role = Role.Button) {
                                showPhotoOptionsFor = null
                                launchGallery(targetForOptions)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = VelnoxIcons.Image,
                            contentDescription = null,
                            tint = VelnoxBrand,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.profile_photo_choose_gallery),
                            style = TextXs.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Squircle)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f))
                            .velnoxClickable(role = Role.Button) {
                                showPhotoOptionsFor = null
                                if (targetForOptions == PhotoTarget.Avatar) {
                                    preferences.setAvatarUri(null)
                                } else {
                                    preferences.setCoverUri(null)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = VelnoxIcons.RotateCcw,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.profile_photo_reset),
                            style = TextXs.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhotoOptionsFor = null }) {
                    Text(
                        text = stringResource(R.string.common_close),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@Composable
private fun ProfileHeaderCard(
    user: AuthUser,
    avatarUri: String?,
    coverUri: String?,
    isUploadingAvatar: Boolean = false,
    isUploadingCover: Boolean = false,
    onEditAvatar: () -> Unit,
    onEditCover: () -> Unit,
    onOpenEditProfile: () -> Unit,
) {
    val initials = remember(user.name, user.email) { initialsOf(user.name, user.email) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg),
    ) {
        // Cover Banner + Overlapping Avatar container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp),
        ) {
            // Cover Banner (top 116dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp)
                    .velnoxClickable(role = Role.Button, onClick = onEditCover),
            ) {
                if (!coverUri.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUri,
                        contentDescription = stringResource(R.string.profile_change_cover),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Subtle top-to-bottom scrim for legibility of cover edit chip
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.28f)),
                                ),
                            ),
                    )
                } else {
                    DefaultCoverBackdrop(modifier = Modifier.fillMaxSize())
                }

                // Change Cover Pill
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(Circle)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .velnoxClickable(role = Role.Button, onClick = onEditCover)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isUploadingCover) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                    } else {
                        Icon(
                            imageVector = VelnoxIcons.Camera,
                            contentDescription = stringResource(R.string.profile_change_cover),
                            tint = Color.White,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        text = stringResource(R.string.profile_change_cover),
                        style = Text2xs.copy(fontWeight = FontWeight.Medium),
                        color = Color.White,
                    )
                }
            }

            // Overlapping Circular Avatar with Camera Badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(4.dp, Circle)
                        .clip(Circle)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(3.dp)
                        .clip(Circle)
                        .velnoxClickable(role = Role.Button, onClick = onEditAvatar),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isUploadingAvatar) {
                        CircularProgressIndicator(
                            color = VelnoxBrand,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp),
                        )
                    } else if (!avatarUri.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUri,
                            contentDescription = stringResource(R.string.profile_change_avatar),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        InitialsAvatar(initials = initials, size = 70, brand = true)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(Circle)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                        .clip(Circle)
                        .background(VelnoxBrand)
                        .velnoxClickable(role = Role.Button, onClick = onEditAvatar),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VelnoxIcons.Camera,
                        contentDescription = stringResource(R.string.profile_change_avatar),
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            // Quick Edit Profile Action aligned to bottom-end of header overlap zone
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 2.dp)
                    .size(36.dp)
                    .clip(SquircleSm)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, SquircleSm)
                    .velnoxClickable(role = Role.Button, onClick = onOpenEditProfile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VelnoxIcons.Pencil,
                    contentDescription = stringResource(R.string.profile_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Identity & Tier Info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.name.ifBlank { user.email.substringBefore('@') },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                BrandBadge(text = stringResource(R.string.profile_verified_badge))
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(SquircleSm)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = stringResource(R.string.profile_membership_tier),
                        style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = user.email,
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DefaultCoverBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0F172A),
                    Color(0xFF064E3B),
                    VelnoxBrand,
                ),
            ),
        ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = size.height * 0.85f,
                center = Offset(size.width * 0.88f, size.height * 0.15f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = size.height * 0.6f,
                center = Offset(size.width * 0.12f, size.height * 0.95f),
            )
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
            value = MessagesState.conversations.size.toString(),
            label = stringResource(R.string.profile_stats_messages),
            modifier = Modifier.weight(1f),
            onClick = onOpenMessages,
        )
    }
}

@Composable
private fun OrderShortcutsCard(
    onOpenOrders: () -> Unit,
    onOpenOrdersWithFilter: ((OrdersFilter) -> Unit)? = null,
) {
    val toPayCount = OrdersState.forFilter(OrdersFilter.Pending).size
    val toShipCount = OrdersState.forFilter(OrdersFilter.Processing).size
    val toReceiveCount = OrdersState.forFilter(OrdersFilter.Shipping).size
    val toReviewCount = OrdersState.forFilter(OrdersFilter.Completed).size
    val returnsCount = OrdersState.orders.count { it.uiStatus == OrderStatus.Returned }

    val openFilter: (OrdersFilter) -> Unit = { filter ->
        if (onOpenOrdersWithFilter != null) {
            onOpenOrdersWithFilter(filter)
        } else {
            onOpenOrders()
        }
    }

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
                onClick = { openFilter(OrdersFilter.Pending) },
                modifier = Modifier.weight(1f),
            )
            OrderShortcutItem(
                icon = VelnoxIcons.Package,
                label = stringResource(R.string.profile_shortcut_to_ship),
                badge = toShipCount,
                onClick = { openFilter(OrdersFilter.Processing) },
                modifier = Modifier.weight(1f),
            )
            OrderShortcutItem(
                icon = VelnoxIcons.Truck,
                label = stringResource(R.string.profile_shortcut_to_receive),
                badge = toReceiveCount,
                onClick = { openFilter(OrdersFilter.Shipping) },
                modifier = Modifier.weight(1f),
            )
            OrderShortcutItem(
                icon = VelnoxIcons.Star,
                label = stringResource(R.string.profile_shortcut_to_review),
                badge = toReviewCount,
                onClick = { openFilter(OrdersFilter.Completed) },
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

/**
 * Copies a user-picked image into app-private storage so the avatar/cover stays
 * readable across restarts without depending on transient Photo Picker URI grants.
 */
private suspend fun persistPickedImage(
    context: Context,
    sourceUri: Uri,
    prefix: String,
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(context.filesDir, "profile_media").apply { mkdirs() }
        // Remove previous files for this slot to keep storage tidy.
        dir.listFiles()?.filter { it.name.startsWith("${prefix}_") }?.forEach { it.delete() }
        val dest = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return@withContext null
        Uri.fromFile(dest).toString()
    }.getOrNull()
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
            onOpenAddresses = {},
            onOpenOrders = {},
            onOpenWishlist = {},
            onOpenMessages = {},
            onOpenHelpCenter = {},
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
            onOpenAddresses = {},
            onOpenOrders = {},
            onOpenWishlist = {},
            onOpenMessages = {},
            onOpenHelpCenter = {},
        )
    }
}
