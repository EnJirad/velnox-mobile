@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.orders

import com.velnox.app.ui.components.velnoxClickable

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.feature.home.ProductArt
import com.velnox.app.feature.home.ProductArtTile
import com.velnox.app.ui.components.CardDivider
import com.velnox.app.ui.components.DetailLine
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.PreviewNotice
import com.velnox.app.ui.components.SectionCard
import com.velnox.app.ui.components.StandaloneScreen
import com.velnox.app.ui.components.StatusChip
import com.velnox.app.ui.components.StatusTone
import com.velnox.app.ui.components.TopBar
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxTheme

enum class OrderStatus(@StringRes val labelRes: Int, val tone: StatusTone) {
    Pending(R.string.orders_status_pending, StatusTone.Warning),
    Processing(R.string.orders_status_processing, StatusTone.Brand),
    Shipping(R.string.orders_status_shipping, StatusTone.Brand),
    Completed(R.string.orders_status_completed, StatusTone.Success),
    Cancelled(R.string.orders_status_cancelled, StatusTone.Neutral),
    Returned(R.string.orders_status_returned, StatusTone.Danger),
}

/** Filter tabs in the order in which the brief lists them. */
enum class OrdersFilter(@StringRes val labelRes: Int) {
    All(R.string.orders_tab_all),
    Pending(R.string.orders_tab_pending),
    Processing(R.string.orders_tab_processing),
    Shipping(R.string.orders_tab_shipping),
    Completed(R.string.orders_tab_completed),
    Cancelled(R.string.orders_tab_cancelled),
}

/**
 * Sample order. No prices and no totals: Phase 2 must not fabricate financial
 * data, so cards show identity, status and item counts only.
 */
data class DemoOrder(
    val id: String,
    val status: OrderStatus,
    @StringRes val productNameRes: Int = 0,
    val productArt: ProductArt = ProductArt.Headphones,
    val itemCount: Int = 1,
    val daysAgo: Int = 0,
    val customTitle: String? = null,
    val customStore: String? = null,
    val totalPrice: Double? = null,
    val imageUrl: String? = null,
)

object OrdersDemo {
    val orders = androidx.compose.runtime.mutableStateListOf(
        DemoOrder("VLX-2401", OrderStatus.Shipping, R.string.product_2_name, ProductArt.Headphones, 1, 1),
        DemoOrder("VLX-2402", OrderStatus.Processing, R.string.product_3_name, ProductArt.Tote, 2, 2),
        DemoOrder("VLX-2403", OrderStatus.Pending, R.string.product_6_name, ProductArt.Lamp, 1, 3),
        DemoOrder("VLX-2404", OrderStatus.Completed, R.string.product_5_name, ProductArt.Mug, 3, 9),
        DemoOrder("VLX-2405", OrderStatus.Returned, R.string.product_1_name, ProductArt.Jacket, 1, 14),
    )

    fun addOrder(order: DemoOrder) {
        orders.add(0, order)
    }

    fun forFilter(filter: OrdersFilter): List<DemoOrder> = when (filter) {
        OrdersFilter.All -> orders
        OrdersFilter.Pending -> orders.filter { it.status == OrderStatus.Pending }
        OrdersFilter.Processing -> orders.filter { it.status == OrderStatus.Processing }
        OrdersFilter.Shipping -> orders.filter { it.status == OrderStatus.Shipping }
        OrdersFilter.Completed -> orders.filter { it.status == OrderStatus.Completed }
        OrdersFilter.Cancelled -> orders.filter {
            it.status == OrderStatus.Cancelled || it.status == OrderStatus.Returned
        }
    }
}

@Composable
fun OrdersScreen(
    onOpenOrder: (String) -> Unit,
    onBrowseProducts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(OrdersFilter.All.name) }
    val activeFilter = OrdersFilter.entries.firstOrNull { it.name == filter } ?: OrdersFilter.All
    val visibleOrders = OrdersDemo.forFilter(activeFilter)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(title = stringResource(R.string.orders_title))

        LazyRow(
            contentPadding = PaddingValues(horizontal = PageGutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            items(OrdersFilter.entries.toList()) { item ->
                val count = if (item == OrdersFilter.All) null else OrdersDemo.forFilter(item).size
                FilterPill(
                    label = stringResource(item.labelRes),
                    count = count,
                    selected = item == activeFilter,
                    onClick = { filter = item.name },
                )
            }
        }

        if (visibleOrders.isEmpty()) {
            EmptyState(
                icon = VelnoxIcons.Package,
                title = stringResource(R.string.orders_empty_title),
                body = stringResource(R.string.orders_empty_body),
                actionLabel = stringResource(R.string.orders_empty_action),
                onAction = onBrowseProducts,
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = PageGutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { PreviewNotice(text = stringResource(R.string.orders_demo_notice)) }
            items(visibleOrders, key = { it.id }) { order ->
                OrderCard(order = order, onClick = { onOpenOrder(order.id) })
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(Squircle)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .border(
                width = 1.dp,
                color = if (selected) VelnoxBrand else MaterialTheme.colorScheme.outline,
                shape = Squircle,
            )
            .velnoxClickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                style = Text2xs.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
            )
            if (count != null && count > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "($count)",
                    style = Text2xs,
                    color = if (selected) VelnoxBrand else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OrderCard(order: DemoOrder, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
            .velnoxClickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = order.id,
                style = Text2xs.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            StatusChip(text = stringResource(order.status.labelRes), tone = order.status.tone)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).clip(SquircleSm)) {
                if (order.imageUrl != null) {
                    coil.compose.AsyncImage(
                        model = order.imageUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ProductArtTile(art = order.productArt, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val orderTitle = order.customTitle ?: if (order.productNameRes != 0) stringResource(order.productNameRes) else order.id
                Text(
                    text = orderTitle,
                    style = TextXs.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.orders_item_count, order.itemCount),
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.Icon(
                imageVector = VelnoxIcons.ChevronRight,
                contentDescription = stringResource(R.string.orders_detail_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun OrderDetailScreen(
    orderId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val order = OrdersDemo.orders.firstOrNull { it.id == orderId } ?: OrdersDemo.orders.first()

    StandaloneScreen(
        title = stringResource(R.string.orders_detail_title),
        subtitle = order.id,
        onBack = onBack,
        modifier = modifier,
    ) {
        PreviewNotice(text = stringResource(R.string.orders_demo_notice))
        Spacer(Modifier.height(12.dp))

        SectionCard(title = stringResource(R.string.orders_detail_items)) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(56.dp).clip(SquircleSm)) {
                    if (order.imageUrl != null) {
                        coil.compose.AsyncImage(
                            model = order.imageUrl,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ProductArtTile(art = order.productArt, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val detailTitle = order.customTitle ?: if (order.productNameRes != 0) stringResource(order.productNameRes) else order.id
                    Text(
                        text = detailTitle,
                        style = TextXs.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.orders_item_count, order.itemCount),
                        style = Text2xs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(text = stringResource(order.status.labelRes), tone = order.status.tone)
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = stringResource(R.string.orders_detail_timeline)) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                OrderTimeline(status = order.status)
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = stringResource(R.string.orders_detail_summary)) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                DetailLine(
                    stringResource(R.string.cart_subtotal),
                    stringResource(R.string.common_value_placeholder),
                )
                CardDivider()
                DetailLine(stringResource(R.string.cart_shipping), stringResource(R.string.cart_shipping_free))
                CardDivider()
                DetailLine(
                    stringResource(R.string.cart_total),
                    stringResource(R.string.common_value_placeholder),
                    strong = true,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = stringResource(R.string.orders_detail_shipping)) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                DetailLine(stringResource(R.string.orders_detail_shipping), stringResource(R.string.orders_demo_address))
                CardDivider()
                DetailLine(stringResource(R.string.orders_detail_payment), stringResource(R.string.orders_demo_payment_method))
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OrderTimeline(status: OrderStatus) {
    val steps = listOf(
        R.string.orders_status_pending,
        R.string.orders_status_processing,
        R.string.orders_status_shipping,
        R.string.orders_status_completed,
    )
    // How far along the four-step timeline this status sits.
    val reached = when (status) {
        OrderStatus.Pending -> 1
        OrderStatus.Processing -> 2
        OrderStatus.Shipping -> 3
        OrderStatus.Completed -> 4
        OrderStatus.Cancelled -> 1
        OrderStatus.Returned -> 4
    }
    Column {
        steps.forEachIndexed { index, stepRes ->
            val active = index < reached
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (active) VelnoxBrand else MaterialTheme.colorScheme.outline),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(stepRes),
                    style = TextXs,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .width(2.dp)
                        .height(12.dp)
                        .background(if (index < reached - 1) VelnoxBrand else MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun OrdersPreview() {
    VelnoxTheme(darkTheme = false) {
        OrdersScreen(onOpenOrder = {}, onBrowseProducts = {})
    }
}

@Preview(name = "Orders 320dp", showBackground = true, widthDp = 320, heightDp = 760)
@Composable
private fun OrdersCompactPreview() {
    VelnoxTheme(darkTheme = false) {
        OrdersScreen(onOpenOrder = {}, onBrowseProducts = {})
    }
}

@Preview(name = "Orders dark", showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun OrdersDarkPreview() {
    VelnoxTheme(darkTheme = true) {
        OrdersScreen(onOpenOrder = {}, onBrowseProducts = {})
    }
}
