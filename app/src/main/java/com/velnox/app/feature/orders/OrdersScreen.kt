@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.orders

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.velnox.app.R
import com.velnox.app.VelnoxApplication
import com.velnox.app.core.catalog.PriceFormat
import com.velnox.app.core.customer.CustomerOrder
import com.velnox.app.core.customer.CustomerRepository
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.LoadStatus
import com.velnox.app.core.customer.userMessage
import com.velnox.app.feature.cart.CartState
import com.velnox.app.feature.checkout.PromptPayQrDialog
import com.velnox.app.feature.home.ProductArt
import com.velnox.app.feature.home.ProductArtTile
import com.velnox.app.ui.components.CardDivider
import com.velnox.app.ui.components.DetailLine
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.PrimaryPill
import com.velnox.app.ui.components.SectionCard
import com.velnox.app.ui.components.StandaloneScreen
import com.velnox.app.ui.components.StatusChip
import com.velnox.app.ui.components.TopBar
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxTheme
import kotlinx.coroutines.launch

@Composable
fun OrdersScreen(
    onOpenOrder: (String) -> Unit,
    onBrowseProducts: () -> Unit,
    initialFilter: OrdersFilter = OrdersFilter.All,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository: CustomerRepository? = remember(context) {
        (context.applicationContext as? VelnoxApplication)?.customer
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository) {
        if (repository != null) {
            OrdersState.load(repository)
        }
    }

    var filter by rememberSaveable(initialFilter) { mutableStateOf(initialFilter.name) }
    LaunchedEffect(initialFilter) {
        filter = initialFilter.name
    }
    val activeFilter = OrdersFilter.entries.firstOrNull { it.name == filter } ?: OrdersFilter.All
    val visibleOrders = OrdersState.forFilter(activeFilter)

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
                val count = if (item == OrdersFilter.All) null else OrdersState.forFilter(item).size
                FilterPill(
                    label = stringResource(item.labelRes),
                    count = count,
                    selected = item == activeFilter,
                    onClick = { filter = item.name },
                )
            }
        }

        if (visibleOrders.isEmpty()) {
            when {
                OrdersState.orders.isEmpty() && OrdersState.status == LoadStatus.Loading -> {
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

                OrdersState.orders.isEmpty() && OrdersState.status == LoadStatus.Failed -> {
                    EmptyState(
                        icon = VelnoxIcons.Package,
                        title = stringResource(R.string.orders_load_failed),
                        body = stringResource(R.string.customer_error_network),
                        actionLabel = stringResource(R.string.common_retry),
                        onAction = {
                            if (repository != null) {
                                scope.launch { OrdersState.load(repository) }
                            }
                        },
                    )
                }

                else -> {
                    EmptyState(
                        icon = VelnoxIcons.Package,
                        title = stringResource(R.string.orders_empty_title),
                        body = stringResource(R.string.orders_empty_body),
                        actionLabel = stringResource(R.string.orders_empty_action),
                        onAction = onBrowseProducts,
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = PageGutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
private fun OrderCard(order: CustomerOrder, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val firstItem = order.items.firstOrNull()
    val uiStatus = order.uiStatus

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
                text = order.shopName?.takeIf { it.isNotBlank() } ?: order.orderNumber,
                style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            StatusChip(text = stringResource(uiStatus.labelRes), tone = uiStatus.tone)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).clip(SquircleSm)) {
                val imageUrl = firstItem?.imageUrl
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ProductArtTile(art = ProductArt.Headphones, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = firstItem?.name ?: order.orderNumber,
                    style = TextXs.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.orders_item_count, order.itemCount),
                        style = Text2xs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    order.total?.let { total ->
                        val formatted = PriceFormat.format(total, order.currency, locale) ?: "฿$total"
                        Text(
                            text = " · $formatted",
                            style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                            color = VelnoxBrand,
                        )
                    }
                }
            }
            Icon(
                imageVector = VelnoxIcons.ChevronRight,
                contentDescription = stringResource(R.string.orders_detail_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        if (uiStatus == OrderStatus.Pending) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SquircleSm)
                    .background(Color(0xFFFEF3C7))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.checkout_payment_promptpay),
                    style = Text2xs.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFF92400E),
                )
                Text(
                    text = stringResource(R.string.orders_pay_now),
                    style = Text2xs.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFB45309),
                )
            }
        }
    }
}

@Composable
fun OrderDetailScreen(
    orderId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    cartState: CartState? = null,
    onOpenCart: () -> Unit = {},
    onContactSeller: ((shopId: String, productId: String?) -> Unit)? = null,
    onOpenProduct: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val repository: CustomerRepository? = remember(context) {
        (context.applicationContext as? VelnoxApplication)?.customer
    }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val locale = LocalConfiguration.current.locales[0]

    var order by remember(orderId) {
        mutableStateOf(OrdersState.orders.firstOrNull { it.id == orderId || it.orderNumber == orderId })
    }
    var loading by remember(orderId) { mutableStateOf(order == null) }
    var loadError by remember(orderId) { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }

    var showCancelDialog by remember { mutableStateOf(false) }
    var showPromptPayDialog by remember { mutableStateOf(false) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var copiedNotice by remember { mutableStateOf(false) }

    LaunchedEffect(orderId, repository, reloadTick) {
        if (repository == null) {
            loading = false
            return@LaunchedEffect
        }
        loading = order == null
        loadError = null
        when (val res = OrdersState.loadDetail(repository, orderId)) {
            is CustomerResult.Ok -> {
                order = res.value
                loadError = null
            }

            is CustomerResult.Failed -> {
                if (order == null) {
                    loadError = res.userMessage(context)
                }
            }
        }
        loading = false
    }

    val activeOrder = order
    val placeholder = stringResource(R.string.common_value_placeholder)

    fun fmt(amount: Double?): String = amount?.let {
        PriceFormat.format(it, activeOrder?.currency ?: "THB", locale) ?: "฿$it"
    } ?: placeholder

    StandaloneScreen(
        title = stringResource(R.string.orders_detail_title),
        subtitle = activeOrder?.orderNumber ?: orderId,
        onBack = onBack,
        modifier = modifier,
    ) {
        if (activeOrder == null) {
            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.customer_loading),
                            style = TextXs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    EmptyState(
                        icon = VelnoxIcons.Package,
                        title = stringResource(R.string.orders_not_found),
                        body = loadError ?: stringResource(R.string.customer_error_not_found),
                        actionLabel = stringResource(R.string.common_retry),
                        onAction = { reloadTick++ },
                    )
                }
            }
            return@StandaloneScreen
        }

        val uiStatus = activeOrder.uiStatus

        SectionCard(title = stringResource(R.string.orders_detail_items)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = activeOrder.shopName?.takeIf { it.isNotBlank() } ?: activeOrder.orderNumber,
                        style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (onContactSeller != null && !activeOrder.shopId.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.product_chat_seller),
                            style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                            color = VelnoxBrand,
                            modifier = Modifier.velnoxClickable(role = Role.Button) {
                                onContactSeller.invoke(activeOrder.shopId, activeOrder.items.firstOrNull()?.productId)
                            },
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    StatusChip(text = stringResource(uiStatus.labelRes), tone = uiStatus.tone)
                }

                activeOrder.items.forEachIndexed { idx, line ->
                    if (idx > 0) CardDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (onOpenProduct != null && !line.productId.isNullOrBlank()) {
                                    Modifier.clip(SquircleSm).velnoxClickable(role = Role.Button) {
                                        onOpenProduct.invoke(line.productId)
                                    }
                                } else Modifier
                            ),
                    ) {
                        Box(modifier = Modifier.size(52.dp).clip(SquircleSm)) {
                            if (line.imageUrl != null) {
                                AsyncImage(
                                    model = line.imageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                ProductArtTile(art = ProductArt.Headphones, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = line.name ?: activeOrder.orderNumber,
                                style = TextXs.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!line.variantName.isNullOrBlank()) {
                                Text(
                                    text = line.variantName,
                                    style = Text2xs,
                                    color = VelnoxBrand,
                                )
                            }
                            Text(
                                text = stringResource(R.string.orders_quantity, line.quantity),
                                style = Text2xs,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (line.price != null) {
                            Text(
                                text = fmt(line.price),
                                style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = stringResource(R.string.orders_detail_timeline)) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                OrderTimeline(status = uiStatus)
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = stringResource(R.string.orders_detail_summary)) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                DetailLine(stringResource(R.string.cart_subtotal), fmt(activeOrder.subtotal))
                CardDivider()
                val shippingText = activeOrder.shippingFee?.let { fee ->
                    if (fee <= 0.0) stringResource(R.string.cart_shipping_free) else fmt(fee)
                } ?: placeholder
                DetailLine(stringResource(R.string.cart_shipping), shippingText)
                if ((activeOrder.discount ?: 0.0) > 0.0) {
                    CardDivider()
                    DetailLine(stringResource(R.string.checkout_discount_voucher), "-${fmt(activeOrder.discount)}")
                }
                CardDivider()
                DetailLine(
                    stringResource(R.string.cart_total),
                    fmt(activeOrder.total),
                    strong = true,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = stringResource(R.string.orders_detail_shipping)) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                DetailLine(
                    stringResource(R.string.orders_detail_shipping),
                    activeOrder.deliveryAddress ?: placeholder,
                )
                CardDivider()
                DetailLine(
                    stringResource(R.string.orders_detail_payment),
                    formatPaymentMethod(activeOrder.paymentMethod) ?: placeholder,
                )
                if (!activeOrder.carrier.isNullOrBlank()) {
                    CardDivider()
                    DetailLine(stringResource(R.string.orders_tracking_courier), activeOrder.carrier)
                }
                if (!activeOrder.trackingNumber.isNullOrBlank()) {
                    val tracking = activeOrder.trackingNumber
                    CardDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.orders_tracking_number),
                            style = TextXs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tracking,
                                style = TextXs.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (copiedNotice) {
                                    stringResource(R.string.orders_tracking_copied)
                                } else {
                                    stringResource(R.string.orders_tracking_copy)
                                },
                                style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                                color = VelnoxBrand,
                                modifier = Modifier.velnoxClickable(role = Role.Button) {
                                    clipboardManager.setText(AnnotatedString(tracking))
                                    copiedNotice = true
                                },
                            )
                        }
                    }
                }
            }
        }

        if (actionError != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = actionError.orEmpty(),
                style = Text2xs,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (activeOrder.uiStatus == OrderStatus.Pending) {
            Spacer(Modifier.height(14.dp))
            PrimaryPill(
                label = stringResource(R.string.orders_pay_now),
                onClick = { showPromptPayDialog = true },
            )
        }

        if (cartState != null && repository != null) {
            Spacer(Modifier.height(14.dp))
            PrimaryPill(
                label = stringResource(
                    if (actionBusy) R.string.orders_reordering else R.string.orders_buy_again,
                ),
                onClick = {
                    if (!actionBusy) {
                        actionBusy = true
                        actionError = null
                        scope.launch {
                            when (val res = OrdersState.reorder(repository, activeOrder.id, cartState)) {
                                is CustomerResult.Ok -> onOpenCart()
                                is CustomerResult.Failed -> actionError = res.userMessage(context)
                            }
                            actionBusy = false
                        }
                    }
                },
            )
        }

        val reviewedProductId = activeOrder.items.firstOrNull()?.productId
        if (activeOrder.uiStatus == OrderStatus.Completed && onOpenProduct != null && !reviewedProductId.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(Squircle)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Squircle)
                    .velnoxClickable(role = Role.Button) {
                        onOpenProduct(reviewedProductId)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = VelnoxIcons.Star,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.orders_review_item),
                    style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (activeOrder.isCancelable && repository != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(Squircle)
                    .border(1.dp, MaterialTheme.colorScheme.error, Squircle)
                    .velnoxClickable(role = Role.Button, enabled = !actionBusy) {
                        showCancelDialog = true
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(
                        if (actionBusy) R.string.orders_cancelling else R.string.orders_cancel_order,
                    ),
                    style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showCancelDialog && activeOrder != null && repository != null) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            shape = SquircleLg,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.orders_cancel_order)) },
            text = { Text(stringResource(R.string.orders_cancel_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        actionBusy = true
                        actionError = null
                        scope.launch {
                            when (val res = OrdersState.cancelOrder(repository, activeOrder.id)) {
                                is CustomerResult.Ok -> {
                                    reloadTick++
                                }

                                is CustomerResult.Failed -> {
                                    actionError = res.userMessage(context)
                                }
                            }
                            actionBusy = false
                        }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.orders_cancel_order),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }

    if (showPromptPayDialog && activeOrder != null) {
        PromptPayQrDialog(
            orderNumber = activeOrder.orderNumber,
            amountText = fmt(activeOrder.total),
            onDismissRequest = { showPromptPayDialog = false },
            onPaymentConfirmed = {
                showPromptPayDialog = false
                reloadTick++
                if (repository != null) {
                    scope.launch { OrdersState.load(repository) }
                }
            },
        )
    }
}

@Composable
private fun formatPaymentMethod(raw: String?): String? = when (raw?.uppercase()) {
    "PROMPTPAY" -> stringResource(R.string.checkout_payment_promptpay)
    "CARD", "CREDIT_CARD" -> stringResource(R.string.checkout_payment_card)
    "COD", "CASH_ON_DELIVERY" -> stringResource(R.string.checkout_payment_cod)
    else -> raw?.takeIf { it.isNotBlank() }
}

@Composable
private fun OrderTimeline(status: OrderStatus) {
    val steps = listOf(
        R.string.orders_timeline_placed,
        R.string.orders_timeline_paid,
        R.string.orders_timeline_processing,
        R.string.orders_timeline_shipped,
        R.string.orders_timeline_delivered,
    )
    val reached = when (status) {
        OrderStatus.Pending -> 1
        OrderStatus.Processing -> 3
        OrderStatus.Shipping -> 4
        OrderStatus.Completed -> 5
        OrderStatus.Cancelled -> 1
        OrderStatus.Returned -> 5
    }
    Column {
        steps.forEachIndexed { index, stepRes ->
            val active = index < reached
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
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

@Preview(name = "Orders dark", showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun OrdersDarkPreview() {
    VelnoxTheme(darkTheme = true) {
        OrdersScreen(onOpenOrder = {}, onBrowseProducts = {})
    }
}
