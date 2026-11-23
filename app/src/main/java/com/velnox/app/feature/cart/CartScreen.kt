@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.cart

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.velnox.app.R
import com.velnox.app.VelnoxApplication
import com.velnox.app.core.catalog.PriceFormat
import com.velnox.app.core.customer.CustomerRepository
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.LoadStatus
import com.velnox.app.core.customer.userMessage
import com.velnox.app.feature.home.ProductArtTile
import com.velnox.app.ui.components.BrandBadge
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.QuantityStepper
import com.velnox.app.ui.components.SectionCard
import com.velnox.app.ui.components.StatusChip
import com.velnox.app.ui.components.StatusTone
import com.velnox.app.ui.components.TopBar
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.SquircleXs
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxTheme
import kotlinx.coroutines.launch

data class ShopCartGroup(
    val shopName: String,
    val items: List<CartItem>,
)

/**
 * Cart tab: selectable lines grouped by seller/shop, quantity steppers, removal,
 * and a summary whose amounts reflect actual selected items from the server.
 */
@Composable
fun CartScreen(
    state: CartState,
    onCheckout: () -> Unit,
    onBrowseProducts: () -> Unit,
    onOpenProduct: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository: CustomerRepository? = remember(context) {
        (context.applicationContext as? VelnoxApplication)?.customer
    }
    val scope = rememberCoroutineScope()
    var actionError by remember { mutableStateOf<String?>(null) }
    var itemPendingDeleteId by remember { mutableStateOf<String?>(null) }

    // Refresh cart from server on screen entry so prices and stock are current.
    LaunchedEffect(repository) {
        if (repository != null) {
            state.load(repository)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(
            title = stringResource(R.string.cart_title),
            actions = {
                if (state.items.isNotEmpty()) {
                    BrandBadge(text = state.items.size.toString())
                    Spacer(Modifier.width(6.dp))
                }
            },
        )

        if (state.items.isEmpty()) {
            when (state.status) {
                LoadStatus.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.customer_loading),
                            style = TextXs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                LoadStatus.Failed -> {
                    EmptyState(
                        icon = VelnoxIcons.ShoppingCart,
                        title = stringResource(R.string.cart_load_failed),
                        body = stringResource(R.string.customer_error_network),
                        actionLabel = stringResource(R.string.common_retry),
                        onAction = {
                            if (repository != null) {
                                scope.launch { state.load(repository) }
                            }
                        },
                    )
                }

                LoadStatus.Idle, LoadStatus.Ready -> {
                    EmptyState(
                        icon = VelnoxIcons.ShoppingCart,
                        title = stringResource(R.string.cart_empty_title),
                        body = stringResource(R.string.cart_empty_body),
                        actionLabel = stringResource(R.string.cart_empty_action),
                        onAction = onBrowseProducts,
                    )
                }
            }
            return@Column
        }

        val locale = LocalConfiguration.current.locales[0]
        val defaultShop = stringResource(R.string.cart_default_shop)

        val shopGroups = remember(state.items.toList()) {
            val map = LinkedHashMap<String, MutableList<CartItem>>()
            for (item in state.items) {
                val sName = item.storeName?.takeIf { it.isNotBlank() }
                    ?: (if (item.storeRes != 0) context.getString(item.storeRes) else defaultShop)
                map.getOrPut(sName) { mutableListOf() }.add(item)
            }
            map.entries.map { ShopCartGroup(it.key, it.value) }
        }

        val totalPrice = state.selectedTotalPrice
        val formattedTotal = if (totalPrice != null) {
            PriceFormat.format(totalPrice, "THB", locale) ?: "฿${String.format(locale, "%.2f", totalPrice)}"
        } else {
            stringResource(R.string.common_value_placeholder)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = PageGutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (actionError != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Squircle)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = actionError.orEmpty(),
                            style = Text2xs,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SquircleLg)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
                        .velnoxClickable(role = Role.Checkbox) { state.toggleAll() }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectionBox(checked = state.allSelected, onClick = { state.toggleAll() })
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.cart_select_all),
                        style = TextXs.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.cart_selected_count, state.selectedCount),
                        style = Text2xs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Grouped by Shop / Seller
            items(shopGroups, key = { it.shopName }) { group ->
                ShopCartSection(
                    group = group,
                    state = state,
                    onIncrease = { lineId ->
                        actionError = null
                        if (repository != null) {
                            scope.launch {
                                val res = state.increaseRemote(repository, lineId)
                                if (res is CustomerResult.Failed) {
                                    actionError = res.userMessage(context)
                                }
                            }
                        } else {
                            state.increase(lineId)
                        }
                    },
                    onDecrease = { lineId ->
                        actionError = null
                        if (repository != null) {
                            scope.launch {
                                val res = state.decreaseRemote(repository, lineId)
                                if (res is CustomerResult.Failed) {
                                    actionError = res.userMessage(context)
                                }
                            }
                        } else {
                            state.decrease(lineId)
                        }
                    },
                    onRemove = { lineId ->
                        itemPendingDeleteId = lineId
                    },
                    onOpenProduct = onOpenProduct,
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                SectionCard(title = stringResource(R.string.orders_detail_summary)) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        SummaryRow(
                            stringResource(R.string.cart_subtotal),
                            formattedTotal,
                        )
                        SummaryRow(
                            stringResource(R.string.cart_shipping),
                            stringResource(R.string.cart_shipping_at_checkout),
                        )
                        SummaryRow(
                            stringResource(R.string.cart_total),
                            formattedTotal,
                            strong = true,
                        )
                    }
                }
            }
        }

        CheckoutBar(
            selectedCount = state.selectedCount,
            totalPriceText = formattedTotal,
            onCheckout = onCheckout,
        )

        if (itemPendingDeleteId != null) {
            val targetId = itemPendingDeleteId!!
            AlertDialog(
                onDismissRequest = { itemPendingDeleteId = null },
                shape = SquircleLg,
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = stringResource(R.string.cart_delete_confirm_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.cart_delete_confirm_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    Text(
                        text = stringResource(R.string.cart_remove),
                        style = TextXs.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .velnoxClickable(role = Role.Button) {
                                itemPendingDeleteId = null
                                actionError = null
                                if (repository != null) {
                                    scope.launch {
                                        val res = state.removeRemote(repository, targetId)
                                        if (res is CustomerResult.Failed) {
                                            actionError = res.userMessage(context)
                                        }
                                    }
                                } else {
                                    state.remove(targetId)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                },
                dismissButton = {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        style = TextXs.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .velnoxClickable(role = Role.Button) {
                                itemPendingDeleteId = null
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun ShopCartSection(
    group: ShopCartGroup,
    state: CartState,
    onIncrease: (String) -> Unit,
    onDecrease: (String) -> Unit,
    onRemove: (String) -> Unit,
    onOpenProduct: ((String) -> Unit)? = null,
) {
    val isShopAllSelected = group.items.isNotEmpty() && group.items.all { it.selected }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg),
    ) {
        // Shop Header with Shop-level Selection
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionBox(
                checked = isShopAllSelected,
                onClick = { state.toggleShopSelection(group.items, !isShopAllSelected) },
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = VelnoxIcons.ShoppingBag,
                contentDescription = null,
                tint = VelnoxBrand,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = group.shopName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BrandBadge(text = stringResource(R.string.profile_verified_badge))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Shop Items
        group.items.forEachIndexed { index, item ->
            CartLine(
                item = item,
                onToggle = { state.toggleSelection(item.id) },
                onIncrease = { onIncrease(item.id) },
                onDecrease = { onDecrease(item.id) },
                onRemove = { onRemove(item.id) },
                onOpenProduct = onOpenProduct,
            )
            if (index < group.items.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun CartLine(
    item: CartItem,
    onToggle: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
    onOpenProduct: ((String) -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionBox(checked = item.selected, onClick = onToggle)
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(SquircleSm)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (onOpenProduct != null && item.productId.isNotBlank()) {
                        Modifier.velnoxClickable(role = Role.Button) {
                            onOpenProduct.invoke(item.productId)
                        }
                    } else Modifier
                ),
        ) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ProductArtTile(art = item.art, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onOpenProduct != null && item.productId.isNotBlank()) {
                        Modifier.velnoxClickable(role = Role.Button) {
                            onOpenProduct.invoke(item.productId)
                        }
                    } else Modifier
                ),
        ) {
            val itemName = item.name ?: if (item.nameRes != 0) stringResource(item.nameRes) else ""
            Text(
                text = itemName,
                style = TextXs.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.variantName.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(SquircleXs)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = item.variantName,
                        style = Text2xs.copy(fontWeight = FontWeight.Medium),
                        color = VelnoxBrand,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            if (item.price != null) {
                PriceFormat.format(item.price, item.currency, LocalConfiguration.current.locales[0])?.let { p ->
                    Text(
                        text = p,
                        style = TextXs.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                StatusChip(
                    text = stringResource(R.string.common_value_placeholder),
                    tone = StatusTone.Neutral,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuantityStepper(
                    value = item.quantity,
                    onDecrease = onDecrease,
                    onIncrease = onIncrease,
                    increaseEnabled = item.quantity < (item.maxStock ?: CartState.MAX_QTY),
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(SquircleSm)
                        .velnoxClickable(role = Role.Button, onClick = onRemove)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = VelnoxIcons.Trash,
                        contentDescription = stringResource(R.string.cart_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.cart_remove),
                        style = Text2xs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Square check control; the box itself is a squircle, its states are explicit. */
@Composable
private fun SelectionBox(checked: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(SquircleXs)
            .background(if (checked) VelnoxBrand else Color.Transparent)
            .border(
                1.5.dp,
                if (checked) VelnoxBrand else MaterialTheme.colorScheme.outlineVariant,
                SquircleXs,
            )
            .velnoxClickable(role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = VelnoxIcons.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, strong: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (strong) TextXs.copy(fontWeight = FontWeight.SemiBold) else TextXs,
            color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (strong) TextXs.copy(fontWeight = FontWeight.Bold) else TextXs,
            color = if (strong) VelnoxBrand else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CheckoutBar(
    selectedCount: Int,
    totalPriceText: String,
    onCheckout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .navigationBarsPadding()
            .padding(horizontal = PageGutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.cart_total),
                style = Text2xs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = totalPriceText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = VelnoxBrand,
            )
        }
        Spacer(Modifier.width(12.dp))
        val enabled = selectedCount > 0
        Box(
            modifier = Modifier
                .height(48.dp)
                .clip(Squircle)
                .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .velnoxClickable(role = Role.Button, enabled = enabled, onClick = onCheckout)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.cart_checkout),
                style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CartPreview() {
    VelnoxTheme(darkTheme = false) {
        CartScreen(state = CartState(), onCheckout = {}, onBrowseProducts = {})
    }
}

@Preview(name = "Cart dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CartDarkPreview() {
    VelnoxTheme(darkTheme = true) {
        CartScreen(state = CartState(), onCheckout = {}, onBrowseProducts = {})
    }
}
