@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.feature.home.ProductArtTile
import com.velnox.app.ui.components.BrandBadge
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.PreviewNotice
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
import com.velnox.app.ui.theme.VelnoxPrimary
import com.velnox.app.ui.theme.VelnoxPrimaryForeground
import com.velnox.app.ui.theme.VelnoxTheme

/**
 * Cart tab: selectable lines, quantity steppers, removal, and a summary whose
 * amounts stay placeholders until a real commerce backend exists.
 */
@Composable
fun CartScreen(
    state: CartState,
    onCheckout: () -> Unit,
    onBrowseProducts: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            EmptyState(
                icon = VelnoxIcons.ShoppingCart,
                title = stringResource(R.string.cart_empty_title),
                body = stringResource(R.string.cart_empty_body),
                actionLabel = stringResource(R.string.cart_empty_action),
                onAction = onBrowseProducts,
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = PageGutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                PreviewNotice(text = stringResource(R.string.cart_demo_notice))
                Spacer(Modifier.height(4.dp))
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectionBox(checked = state.allSelected, onClick = { state.toggleAll() })
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.cart_select_all),
                        style = TextXs.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(SquircleXs)
                            .clickable(role = Role.Checkbox) { state.toggleAll() }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.cart_selected_count, state.selectedCount),
                        style = Text2xs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.items, key = { it.productId }) { item ->
                CartLine(
                    item = item,
                    onToggle = { state.toggleSelection(item.productId) },
                    onIncrease = { state.increase(item.productId) },
                    onDecrease = { state.decrease(item.productId) },
                    onRemove = { state.remove(item.productId) },
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                SectionCard(title = stringResource(R.string.orders_detail_summary)) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        SummaryRow(
                            stringResource(R.string.cart_subtotal),
                            stringResource(R.string.common_value_placeholder),
                        )
                        SummaryRow(stringResource(R.string.cart_shipping), stringResource(R.string.cart_shipping_free))
                        SummaryRow(
                            stringResource(R.string.cart_total),
                            stringResource(R.string.common_value_placeholder),
                            strong = true,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.cart_price_preview),
                            style = Text2xs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        CheckoutBar(
            selectedCount = state.selectedCount,
            onCheckout = onCheckout,
        )
    }
}

@Composable
private fun CartLine(
    item: CartItem,
    onToggle: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
            .padding(10.dp),
    ) {
        Column {
            SelectionBox(checked = item.selected, onClick = onToggle)
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.size(72.dp)) {
                ProductArtTile(art = item.art, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(item.nameRes),
                style = TextXs.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(item.storeRes),
                style = Text2xs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            StatusChip(
                text = stringResource(R.string.common_value_placeholder),
                tone = StatusTone.Neutral,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuantityStepper(
                    value = item.quantity,
                    onDecrease = onDecrease,
                    onIncrease = onIncrease,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(SquircleSm)
                        .clickable(role = Role.Button, onClick = onRemove)
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
    val shape = SquircleXs
    Box(
        modifier = Modifier
            .size(48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() }),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(shape)
                .background(if (checked) VelnoxPrimary else MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = if (checked) VelnoxPrimary else MaterialTheme.colorScheme.outline,
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = VelnoxIcons.Check,
                    contentDescription = null,
                    tint = VelnoxPrimaryForeground,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, strong: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (strong) MaterialTheme.typography.titleSmall else TextXs,
            color = if (strong) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (strong) MaterialTheme.typography.titleSmall else TextXs,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CheckoutBar(selectedCount: Int, onCheckout: () -> Unit) {
    val canCheckout = selectedCount > 0
    val buttonBackground = if (canCheckout) VelnoxPrimary else MaterialTheme.colorScheme.surfaceVariant
    val buttonForeground = if (canCheckout) {
        VelnoxPrimaryForeground
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = PageGutter, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.cart_total),
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.common_value_placeholder),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .clip(Squircle)
                    .background(buttonBackground)
                    .clickable(enabled = canCheckout, role = Role.Button, onClick = onCheckout)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.cart_checkout),
                    style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                    color = buttonForeground,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = VelnoxIcons.ArrowRight,
                    contentDescription = null,
                    tint = buttonForeground,
                    modifier = Modifier.size(14.dp),
                )
            }
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

@Preview(name = "Cart 320dp", showBackground = true, widthDp = 320, heightDp = 720)
@Composable
private fun CartCompactPreview() {
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
