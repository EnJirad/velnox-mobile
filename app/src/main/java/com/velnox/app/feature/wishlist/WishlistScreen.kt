@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.wishlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.velnox.app.R
import com.velnox.app.core.catalog.PriceFormat
import com.velnox.app.feature.cart.CartState
import com.velnox.app.ui.components.BrandBadge
import com.velnox.app.ui.components.CardDivider
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.SectionCard
import com.velnox.app.ui.components.StandaloneScreen
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dedicated Wishlist screen displaying bookmarked products with immediate Move to Cart,
 * stock status indicator, and quick removal with undo capability.
 */
@Composable
fun WishlistScreen(
    cartState: CartState,
    onBack: () -> Unit,
    onOpenProduct: (productId: String) -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = WishlistState.items
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()

    var recentlyRemoved by remember { mutableStateOf<WishlistItem?>(null) }
    var actionNotice by remember { mutableStateOf<String?>(null) }

    StandaloneScreen(
        title = stringResource(R.string.wishlist_title),
        onBack = onBack,
        modifier = modifier.fillMaxSize(),
    ) {
        if (items.isEmpty()) {
            EmptyState(
                icon = VelnoxIcons.Heart,
                title = stringResource(R.string.wishlist_empty_title),
                body = stringResource(R.string.wishlist_empty_body),
                actionLabel = stringResource(R.string.wishlist_explore),
                onAction = onExplore,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = PageGutter, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.wishlist_count, items.size),
                        style = Text2xs.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }

                items(items, key = { it.productId }) { item ->
                    WishlistItemCard(
                        item = item,
                        locale = locale,
                        onClick = { onOpenProduct(item.productId) },
                        onMoveToCart = {
                            cartState.add(
                                productId = item.productId,
                                name = item.name,
                                storeName = item.shopName,
                                imageUrl = item.imageUrl,
                                variantId = null,
                                variantName = null,
                                price = item.price,
                                currency = item.currency,
                                quantity = 1,
                                maxStock = null,
                            )
                            actionNotice = "เพิ่ม ${item.name} ลงตะกร้าแล้ว"
                            scope.launch {
                                delay(3000)
                                if (actionNotice?.contains(item.name) == true) {
                                    actionNotice = null
                                }
                            }
                        },
                        onRemove = {
                            recentlyRemoved = item
                            WishlistState.remove(item.productId)
                            actionNotice = "นำออกจากรายการโปรดแล้ว"
                            scope.launch {
                                delay(4000)
                                if (recentlyRemoved == item) {
                                    recentlyRemoved = null
                                    actionNotice = null
                                }
                            }
                        },
                    )
                }
            }
        }

        // Action / Undo Banner
        AnimatedVisibility(
            visible = actionNotice != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = 8.dp)
                    .clip(Squircle)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = actionNotice.orEmpty(),
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (recentlyRemoved != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.wishlist_undo),
                        style = TextXs.copy(fontWeight = FontWeight.Bold),
                        color = VelnoxBrand,
                        modifier = Modifier
                            .velnoxClickable(role = Role.Button) {
                                recentlyRemoved?.let { WishlistState.add(it) }
                                recentlyRemoved = null
                                actionNotice = null
                            }
                            .padding(4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WishlistItemCard(
    item: WishlistItem,
    locale: java.util.Locale,
    onClick: () -> Unit,
    onMoveToCart: () -> Unit,
    onRemove: () -> Unit,
) {
    val priceFormatted = item.price?.let { PriceFormat.format(it, item.currency, locale) } ?: "฿${item.price ?: 0.0}"

    SectionCard(
        modifier = Modifier
            .fillMaxWidth()
            .velnoxClickable(role = Role.Button, onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Product Thumbnail
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(SquircleSm)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SquircleSm),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!item.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = VelnoxIcons.ShoppingBag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Details
                Column(modifier = Modifier.weight(1f)) {
                    if (!item.shopName.isNullOrBlank()) {
                        Text(
                            text = item.shopName,
                            style = Text2xs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                    }

                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = priceFormatted,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = VelnoxBrand,
                        )

                        if (item.inStock) {
                            BrandBadge(text = stringResource(R.string.wishlist_in_stock))
                        } else {
                            Text(
                                text = stringResource(R.string.wishlist_out_of_stock),
                                style = Text2xs,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            CardDivider()
            Spacer(Modifier.height(8.dp))

            // Actions row: Move to Cart & Remove
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Remove button
                Row(
                    modifier = Modifier
                        .clip(Squircle)
                        .velnoxClickable(role = Role.Button, onClick = onRemove)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = VelnoxIcons.Trash,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.common_delete),
                        style = Text2xs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Move to Cart button
                Row(
                    modifier = Modifier
                        .clip(Squircle)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .velnoxClickable(role = Role.Button, onClick = onMoveToCart)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = VelnoxIcons.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.wishlist_move_to_cart),
                        style = Text2xs.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
