@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.checkout

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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.velnox.app.R
import com.velnox.app.core.catalog.PriceFormat
import com.velnox.app.core.customer.CheckoutLine
import com.velnox.app.core.customer.CheckoutRequest
import com.velnox.app.core.customer.CustomerError
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.ShippingQuote
import com.velnox.app.core.customer.rememberCustomerRepository
import com.velnox.app.core.customer.userMessage
import com.velnox.app.feature.cart.CartItem
import com.velnox.app.feature.cart.CartState
import com.velnox.app.feature.home.ProductArtTile
import com.velnox.app.feature.profile.AddressBookState
import com.velnox.app.feature.profile.UserAddress
import com.velnox.app.feature.profile.toInput
import com.velnox.app.ui.components.BrandBadge
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.PrimaryPill
import com.velnox.app.ui.components.StandaloneScreen
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Circle
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import kotlinx.coroutines.launch
import java.util.UUID

/** Payment rails accepted by `POST /api/customer/checkout`. */
enum class PaymentMethod(
    val titleRes: Int,
    val icon: ImageVector,
    val wireValue: String,
) {
    PromptPay(R.string.checkout_payment_promptpay, VelnoxIcons.Sparkles, "PROMPTPAY"),
    CreditCard(R.string.checkout_payment_card, VelnoxIcons.CreditCard, "CARD"),
    CashOnDelivery(R.string.checkout_payment_cod, VelnoxIcons.Truck, "COD"),
}

private data class ShopOrderGroup(
    val shopName: String,
    val items: List<CartItem>,
)

@Composable
fun CheckoutScreen(
    cartState: CartState,
    onBack: () -> Unit,
    onViewOrder: (orderId: String) -> Unit,
    onManageAddresses: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val selectedItems = remember(cartState.items.toList()) {
        cartState.items.filter { it.selected }
    }

    var isOrderSuccess by remember { mutableStateOf(false) }
    var createdOrderId by remember { mutableStateOf("") }
    var createdOrderNumber by remember { mutableStateOf("") }

    if (selectedItems.isEmpty() && !isOrderSuccess) {
        StandaloneScreen(
            title = stringResource(R.string.checkout_title),
            onBack = onBack,
            modifier = modifier,
        ) {
            EmptyState(
                icon = VelnoxIcons.ShoppingBag,
                title = stringResource(R.string.checkout_empty),
                body = stringResource(R.string.catalog_empty_body),
                actionLabel = stringResource(R.string.checkout_continue_shopping),
                onAction = onBack,
            )
        }
        return
    }

    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val repository = rememberCustomerRepository()
    val scope = rememberCoroutineScope()
    val defaultShop = stringResource(R.string.cart_default_shop)

    val shopGroups = remember(selectedItems) {
        val map = LinkedHashMap<String, MutableList<CartItem>>()
        for (item in selectedItems) {
            val name = item.storeName?.takeIf { it.isNotBlank() }
                ?: (if (item.storeRes != 0) context.getString(item.storeRes) else defaultShop)
            map.getOrPut(name) { mutableListOf() }.add(item)
        }
        map.entries.map { ShopOrderGroup(it.key, it.value) }
    }

    var selectedPayment by remember { mutableStateOf(PaymentMethod.PromptPay) }
    var showAddressPicker by remember { mutableStateOf(false) }
    var selectedAddressId by remember { mutableStateOf<String?>(null) }

    var quote by remember { mutableStateOf<ShippingQuote?>(null) }
    var quoteLoading by remember { mutableStateOf(false) }
    var quoteError by remember { mutableStateOf<String?>(null) }
    var quoteRetryTick by remember { mutableIntStateOf(0) }

    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var showPromptPayQr by remember { mutableStateOf(false) }

    val addresses = AddressBookState.addresses
    val activeAddress = remember(addresses.toList(), selectedAddressId) {
        addresses.firstOrNull { it.id == selectedAddressId } ?: AddressBookState.defaultAddress
    }

    // Ensure address book is loaded from server if empty.
    LaunchedEffect(Unit) {
        if (AddressBookState.addresses.isEmpty()) {
            AddressBookState.load(repository)
        }
    }

    val selectedRemoteIds = remember(selectedItems) {
        selectedItems.mapNotNull { it.remoteItemId }
    }

    // Idempotency key scoped to this address + cart selection.
    val requestId = remember(activeAddress?.id, selectedRemoteIds) {
        UUID.randomUUID().toString()
    }

    // Fetch authoritative server shipping quote whenever address or selection changes.
    LaunchedEffect(activeAddress?.id, selectedRemoteIds, quoteRetryTick) {
        val addrId = activeAddress?.id
        if (addrId == null || selectedRemoteIds.isEmpty()) {
            quote = null
            quoteError = null
            quoteLoading = false
            return@LaunchedEffect
        }
        quoteLoading = true
        quoteError = null
        when (val res = repository.call { api, token -> api.shippingQuote(token, addrId, selectedRemoteIds) }) {
            is CustomerResult.Ok -> {
                quote = res.value
                quoteError = null
            }

            is CustomerResult.Failed -> {
                if (res.error == CustomerError.NotFound) {
                    // Endpoint omitted on target Workers API; degrade cleanly to local computation
                    quote = null
                    quoteError = null
                } else {
                    quote = null
                    quoteError = res.userMessage(context)
                }
            }
        }
        quoteLoading = false
    }

    val itemsSubtotal = quote?.subtotal ?: remember(selectedItems) {
        selectedItems.sumOf { (it.price ?: 0.0) * it.quantity }
    }
    val shippingTotal = quote?.shippingTotal
    val discountAmount = quote?.discount ?: 0.0
    val grandTotal = quote?.total ?: itemsSubtotal
    val currency = quote?.currency ?: "THB"

    val formattedGrandTotal = PriceFormat.format(grandTotal, currency, locale)
        ?: "฿${String.format(locale, "%.2f", grandTotal)}"

    StandaloneScreen(
        title = stringResource(R.string.checkout_title),
        onBack = onBack,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Delivery Address Card (real server addresses only)
            item {
                if (activeAddress != null) {
                    AddressCard(
                        address = activeAddress,
                        onChange = { showAddressPicker = true },
                    )
                } else {
                    MissingAddressCard(onAddAddress = onManageAddresses)
                }
            }

            // 2. Purchased Items Grouped by Shop
            items(shopGroups, key = { it.shopName }) { group ->
                val shopFee = quote?.shops?.firstOrNull {
                    it.shopName.equals(group.shopName, ignoreCase = true)
                }?.shippingFee
                ShopItemsSection(
                    group = group,
                    shopShippingFee = shopFee,
                    currency = currency,
                    locale = locale,
                )
            }

            // 3. Payment Method Selector (real server rails)
            item {
                PaymentMethodSection(
                    selectedMethod = selectedPayment,
                    onSelectMethod = {
                        selectedPayment = it
                        submitError = null
                    },
                )
            }

            // 4. Order Financial Summary (server-quoted)
            item {
                OrderSummarySection(
                    itemCount = quote?.itemCount ?: selectedItems.sumOf { it.quantity },
                    itemsSubtotal = itemsSubtotal,
                    shippingTotal = shippingTotal,
                    shippingLoading = quoteLoading,
                    discount = discountAmount,
                    grandTotal = grandTotal,
                    currency = currency,
                    quoteError = quoteError,
                    onRetryQuote = { quoteRetryTick++ },
                    locale = locale,
                )
            }
        }

        // 5. Bottom Sticky Checkout Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .navigationBarsPadding()
                .padding(horizontal = PageGutter, vertical = 12.dp),
        ) {
            if (submitError != null) {
                Text(
                    text = submitError.orEmpty(),
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.checkout_total_payment),
                        style = Text2xs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formattedGrandTotal,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = VelnoxBrand,
                    )
                }
                Spacer(Modifier.width(12.dp))
                PrimaryPill(
                    label = stringResource(
                        if (isSubmitting) R.string.checkout_placing_order else R.string.checkout_place_order,
                    ),
                    onClick = {
                        val address = activeAddress
                        if (isSubmitting) {
                            // Submission already in flight.
                        } else if (address == null) {
                            submitError = context.getString(R.string.checkout_address_required)
                        } else {
                            isSubmitting = true
                            submitError = null
                            scope.launch {
                                val req = CheckoutRequest(
                                    shippingAddressId = address.id,
                                    items = selectedItems.map {
                                        CheckoutLine(
                                            productId = it.productId,
                                            variantId = it.variantId,
                                            quantity = it.quantity,
                                        )
                                    },
                                    paymentMethod = selectedPayment.wireValue,
                                    cartItemIds = selectedRemoteIds.takeIf { it.isNotEmpty() },
                                    shippingQuoteId = quote?.quoteId,
                                    requestId = requestId,
                                    shippingAddress = address.toInput(),
                                )
                                when (val res = repository.call { api, token -> api.checkout(token, req) }) {
                                    is CustomerResult.Ok -> {
                                        createdOrderId = res.value.primaryOrderId
                                        createdOrderNumber = res.value.primaryOrderNumber
                                        cartState.load(repository)
                                        if (selectedPayment == PaymentMethod.PromptPay) {
                                            showPromptPayQr = true
                                        } else {
                                            isOrderSuccess = true
                                        }
                                    }

                                    is CustomerResult.Failed -> {
                                        submitError = res.userMessage(context)
                                    }
                                }
                                isSubmitting = false
                            }
                        }
                    },
                )
            }
        }
    }

    if (showAddressPicker) {
        AlertDialog(
            onDismissRequest = { showAddressPicker = false },
            shape = SquircleLg,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = stringResource(R.string.address_select_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(addresses, key = { it.id }) { addr ->
                        val isSelected = addr.id == activeAddress?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(Squircle)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) VelnoxBrand else MaterialTheme.colorScheme.outlineVariant,
                                    Squircle,
                                )
                                .velnoxClickable(role = Role.RadioButton) {
                                    selectedAddressId = addr.id
                                    showAddressPicker = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = addr.recipientName,
                                        style = TextXs.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = addr.phone,
                                        style = Text2xs,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (addr.isDefault) {
                                        Spacer(Modifier.width(6.dp))
                                        BrandBadge(text = stringResource(R.string.settings_address_default))
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = addr.fullAddressText,
                                    style = Text2xs,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAddressPicker = false
                        onManageAddresses()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.settings_address_add),
                        color = VelnoxBrand,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddressPicker = false }) {
                    Text(
                        text = stringResource(R.string.common_close),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }

    if (showPromptPayQr) {
        PromptPayQrDialog(
            orderNumber = createdOrderNumber,
            amountText = formattedGrandTotal,
            onDismissRequest = {
                showPromptPayQr = false
                isOrderSuccess = true
            },
            onPaymentConfirmed = {
                showPromptPayQr = false
                isOrderSuccess = true
            },
        )
    }

    if (isOrderSuccess) {
        AlertDialog(
            onDismissRequest = { /* force explicit user choice */ },
            shape = SquircleLg,
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(Circle)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VelnoxIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.checkout_success_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.checkout_success_message, createdOrderNumber),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isOrderSuccess = false
                        onViewOrder(createdOrderId)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.checkout_view_order),
                        color = VelnoxBrand,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isOrderSuccess = false
                        onBack()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.checkout_continue_shopping),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@Composable
private fun MissingAddressCard(onAddAddress: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f), SquircleLg)
            .velnoxClickable(role = Role.Button, onClick = onAddAddress)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = VelnoxIcons.MapPin,
                contentDescription = null,
                tint = VelnoxBrand,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.checkout_shipping_address),
                style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.checkout_add_address),
                style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                color = VelnoxBrand,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.checkout_address_required),
            style = TextXs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddressCard(
    address: UserAddress,
    onChange: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = VelnoxIcons.MapPin,
                contentDescription = null,
                tint = VelnoxBrand,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.checkout_shipping_address),
                style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.checkout_change_address),
                style = Text2xs.copy(fontWeight = FontWeight.Medium),
                color = VelnoxBrand,
                modifier = Modifier.velnoxClickable(role = Role.Button, onClick = onChange),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = address.recipientName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = address.phone,
                style = TextXs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (address.isDefault) {
                Spacer(Modifier.width(8.dp))
                BrandBadge(
                    text = stringResource(R.string.settings_address_default),
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = address.fullAddressText,
            style = TextXs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
        )
    }
}

@Composable
private fun ShopItemsSection(
    group: ShopOrderGroup,
    shopShippingFee: Double?,
    currency: String,
    locale: java.util.Locale,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = VelnoxIcons.ShoppingBag,
                contentDescription = null,
                tint = VelnoxBrand,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = group.shopName,
                style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BrandBadge(text = stringResource(R.string.profile_verified_badge))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        group.items.forEachIndexed { index, item ->
            val context = LocalContext.current
            val name = item.name ?: (if (item.nameRes != 0) context.getString(item.nameRes) else item.productId)
            val formattedPrice = item.price?.let { p ->
                PriceFormat.format(p, item.currency, locale) ?: "฿$p"
            } ?: stringResource(R.string.common_value_placeholder)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(SquircleSm)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = TextXs.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!item.variantName.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.variantName,
                            style = Text2xs,
                            color = VelnoxBrand,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formattedPrice,
                            style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "x${item.quantity}",
                            style = Text2xs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (index < group.items.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }

        if (shopShippingFee != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.checkout_standard_delivery),
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (shopShippingFee <= 0.0) {
                        stringResource(R.string.cart_shipping_free)
                    } else {
                        PriceFormat.format(shopShippingFee, currency, locale) ?: "฿$shopShippingFee"
                    },
                    style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PaymentMethodSection(
    selectedMethod: PaymentMethod,
    onSelectMethod: (PaymentMethod) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = VelnoxIcons.CreditCard,
                contentDescription = null,
                tint = VelnoxBrand,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.checkout_payment_method),
                style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(10.dp))

        PaymentMethod.entries.forEach { method ->
            val isSelected = method == selectedMethod
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .velnoxClickable(role = Role.RadioButton) { onSelectMethod(method) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { onSelectMethod(method) },
                    colors = RadioButtonDefaults.colors(selectedColor = VelnoxBrand),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = method.icon,
                    contentDescription = null,
                    tint = if (isSelected) VelnoxBrand else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(method.titleRes),
                    style = TextXs.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun OrderSummarySection(
    itemCount: Int,
    itemsSubtotal: Double,
    shippingTotal: Double?,
    shippingLoading: Boolean,
    discount: Double,
    grandTotal: Double,
    currency: String,
    quoteError: String?,
    onRetryQuote: () -> Unit,
    locale: java.util.Locale,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.checkout_order_summary),
            style = TextXs.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SummaryRow(
            label = stringResource(R.string.checkout_subtotal_items, itemCount),
            value = PriceFormat.format(itemsSubtotal, currency, locale) ?: "฿$itemsSubtotal",
        )

        val shippingLabel = when {
            shippingLoading -> stringResource(R.string.customer_loading)
            shippingTotal == null -> stringResource(R.string.common_value_placeholder)
            shippingTotal <= 0.0 -> stringResource(R.string.cart_shipping_free)
            else -> PriceFormat.format(shippingTotal, currency, locale) ?: "฿$shippingTotal"
        }
        SummaryRow(
            label = stringResource(R.string.checkout_subtotal_shipping),
            value = shippingLabel,
        )

        if (quoteError != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = quoteError,
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.common_retry),
                    style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                    color = VelnoxBrand,
                    modifier = Modifier.velnoxClickable(role = Role.Button, onClick = onRetryQuote),
                )
            }
        }

        if (discount > 0.0) {
            SummaryRow(
                label = stringResource(R.string.checkout_discount_voucher),
                value = "-" + (PriceFormat.format(discount, currency, locale) ?: "฿$discount"),
                valueColor = VelnoxBrand,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.checkout_total_payment),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = PriceFormat.format(grandTotal, currency, locale) ?: "฿$grandTotal",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = VelnoxBrand,
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextXs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = TextXs.copy(fontWeight = FontWeight.Medium),
            color = valueColor,
        )
    }
}
