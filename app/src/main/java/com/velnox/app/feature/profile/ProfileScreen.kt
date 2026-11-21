@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.profile

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.velnox.app.BuildConfig
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.velnox.app.core.util.ImageOptimizer
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
 * Append a cache-busting version query param to a URL.
 * The database stores the canonical URL without ?v=.
 * Matches the Web contract (bust() in ShopProfile.tsx).
 */
private fun bust(url: String?, version: Long): String? {
    if (url.isNullOrBlank()) return null
    if (url.startsWith("data:")) return url
    val fullUrl = if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("content://") && !url.startsWith("file://")) {
        val cleanKey = url.trimStart('/')
        "${BuildConfig.API_BASE_URL}/api/media/$cleanKey"
    } else {
        url
    }
    if (!fullUrl.startsWith("http://") && !fullUrl.startsWith("https://")) return fullUrl
    val clean = fullUrl.replace(Regex("[?&]v=\\d+"), "").trimEnd('?')
    val sep = if (clean.contains('?')) "&" else "?"
    return "$clean${sep}v=$version"
}

/**
 * Resolves an avatar or cover string to an object that Coil can natively display:
 * - data: URL -> ByteArray (decoded from base64, rendered instantly in-memory)
 * - relative storage path -> absolute API media URL (https://velnox-api.onrender.com/api/media/...)
 * - http/https/content/file URI -> used directly
 */
private fun resolveImageModel(url: String?): Any? {
    if (url.isNullOrBlank()) return null
    if (url.startsWith("data:")) {
        val base64Index = url.indexOf("base64,")
        if (base64Index != -1) {
            val base64Data = url.substring(base64Index + 7)
            return runCatching {
                try {
                    java.util.Base64.getDecoder().decode(base64Data)
                } catch (_: Throwable) {
                    Base64.decode(base64Data, Base64.DEFAULT)
                }
            }.getOrNull() ?: url
        }
        return url
    }
    if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("content://") || url.startsWith("file://")) {
        return url
    }
    val cleanKey = url.trimStart('/')
    return "${BuildConfig.API_BASE_URL}/api/media/$cleanKey"
}

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
    val snackbarHostState = remember { SnackbarHostState() }
    var cacheBustVersion by remember { mutableLongStateOf(System.currentTimeMillis()) }

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
                cacheBustVersion = System.currentTimeMillis()
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
                        val kind = if (target == PhotoTarget.Avatar) "avatar" else "cover"
                        val (maxW, maxH) = if (target == PhotoTarget.Avatar) 800 to 800 else 1920 to 600

                        // 1. Optimize, rotate & compress to WebP format before uploading (parity with Web)
                        val optimized = ImageOptimizer.optimize(
                            context = context,
                            uri = uri,
                            maxWidth = maxW,
                            maxHeight = maxH,
                            quality = 85,
                        )

                        if (optimized == null) {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.profile_image_type_error),
                            )
                            return@launch
                        }

                        // 2. Upload directly to Cloudflare R2 and persist in Neon PostgreSQL
                        val uploadResult = withContext(Dispatchers.IO) {
                            customerRepository.call { api, token ->
                                api.uploadProfileImage(
                                    token = token,
                                    kind = kind,
                                    bytes = optimized.bytes,
                                    mimeType = optimized.mimeType,
                                    filename = optimized.filename,
                                )
                            }
                        }

                        when (uploadResult) {
                            is CustomerResult.Ok -> {
                                val cdnUrl = uploadResult.value
                                if (target == PhotoTarget.Avatar) {
                                    session.updateProfile(avatar = cdnUrl)
                                    preferences.setAvatarUri(cdnUrl)
                                } else {
                                    session.updateProfile(coverUrl = cdnUrl)
                                    preferences.setCoverUri(cdnUrl)
                                }
                                cacheBustVersion = System.currentTimeMillis()

                                // 3. Re-sync latest customer profile from backend to verify DB persistence
                                val refreshResult = customerRepository.call { api, token ->
                                    api.profile(token)
                                }
                                if (refreshResult is CustomerResult.Ok) {
                                    val p = refreshResult.value
                                    session.updateProfile(
                                        name = p.name,
                                        avatar = p.avatarUrl,
                                        coverUrl = p.coverUrl,
                                    )
                                    if (!p.avatarUrl.isNullOrBlank()) preferences.setAvatarUri(p.avatarUrl)
                                    if (!p.coverUrl.isNullOrBlank()) preferences.setCoverUri(p.coverUrl)
                                    cacheBustVersion = System.currentTimeMillis()
                                }

                                snackbarHostState.showSnackbar(
                                    message = if (target == PhotoTarget.Avatar) {
                                        context.getString(R.string.profile_avatar_updated)
                                    } else {
                                        context.getString(R.string.profile_cover_updated)
                                    },
                                )
                            }
                            is CustomerResult.Failed -> {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.profile_image_upload_failed),
                                )
                            }
                        }
                    } catch (_: Exception) {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.profile_image_upload_failed),
                        )
                    } finally {
                        if (target == PhotoTarget.Avatar) isUploadingAvatar = false else isUploadingCover = false
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
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
                    val rawAvatar = preferences.current.avatarUri?.takeIf { it.isNotBlank() } ?: user.avatar
                    val rawCover = preferences.current.coverUri?.takeIf { it.isNotBlank() } ?: user.coverUrl
                    val resolvedAvatar = bust(rawAvatar, cacheBustVersion)
                    val resolvedCover = bust(rawCover, cacheBustVersion)
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
                                    session.updateProfile(avatar = "")
                                } else {
                                    preferences.setCoverUri(null)
                                    session.updateProfile(coverUrl = "")
                                }
                                cacheBustVersion = System.currentTimeMillis()
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

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp),
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
                        model = resolveImageModel(coverUri),
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
                            model = resolveImageModel(avatarUri),
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
