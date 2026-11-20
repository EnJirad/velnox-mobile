@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.checkout

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.velnox.app.R
import com.velnox.app.core.catalog.PriceFormat
import com.velnox.app.feature.cart.CartItem
import com.velnox.app.feature.cart.CartState
import com.velnox.app.feature.home.ProductArtTile
import com.velnox.app.feature.orders.DemoOrder
import com.velnox.app.feature.orders.OrderStatus
import com.velnox.app.feature.orders.OrdersDemo
import com.velnox.app.feature.profile.AddressBookState
import com.velnox.app.feature.profile.UserAddress
import com.velnox.app.ui.components.BrandBadge
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.PrimaryPill
import com.velnox.app.ui.components.StandaloneScreen
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Circle
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand

enum class ShippingOption(
    val titleRes: Int,
    val price: Double,
    val daysLabel: String,
) {
    Standard(R.string.checkout_standard_delivery, 35.0, "2-3 วัน"),
    Express(R.string.checkout_express_delivery, 65.0, "1-2 วัน"),
    Economy(R.string.checkout_eco_delivery, 20.0, "4-5 วัน"),
}

enum class PaymentMethod(
    val titleRes: Int,
    val icon: ImageVector,
) {
    PromptPay(R.string.checkout_payment_promptpay, VelnoxIcons.Sparkles),
    CreditCard(R.string.checkout_payment_card, VelnoxIcons.CreditCard),
    TrueMoney(R.string.checkout_payment_truemoney, VelnoxIcons.ShieldCheck),
    CashOnDelivery(R.string.checkout_payment_cod, VelnoxIcons.Truck),
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
    modifier: Modifier = Modifier,
) {
    val selectedItems = remember(cartState.items.toList()) {
        cartState.items.filter { it.selected }
    }

    if (selectedItems.isEmpty()) {
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
    val focusManager = LocalFocusManager.current
    val defaultShop = stringResource(R.string.cart_default_shop)

    // Group items by shop
    val shopGroups = remember(selectedItems) {
        val map = LinkedHashMap<String, MutableList<CartItem>>()
        for (item in selectedItems) {
            val name = item.storeName?.takeIf { it.isNotBlank() }
                ?: (if (item.storeRes != 0) context.getString(item.storeRes) else defaultShop)
            map.getOrPut(name) { mutableListOf() }.add(item)
        }
        map.entries.map { ShopOrderGroup(it.key, it.value) }
    }

    // Shipping selection per shop (defaults to Standard)
    val shopShipping = remember {
        mutableStateMapOf<String, ShippingOption>().apply {
            shopGroups.forEach { put(it.shopName, ShippingOption.Standard) }
        }
    }

    var selectedPayment by remember { mutableStateOf(PaymentMethod.PromptPay) }
    var voucherCodeInput by remember { mutableStateOf("") }
    var appliedVoucherDiscount by remember { mutableDoubleStateOf(0.0) }
    var voucherNotice by remember { mutableStateOf<String?>(null) }
    var isOrderSuccess by remember { mutableStateOf(false) }
    var createdOrderId by remember { mutableStateOf("") }

    var showAddressPicker by remember { mutableStateOf(false) }
    var selectedAddressId by remember { mutableStateOf<String?>(null) }
    val addresses = AddressBookState.addresses
    val activeAddress = remember(addresses.toList(), selectedAddressId) {
        addresses.firstOrNull { it.id == selectedAddressId } ?: AddressBookState.defaultAddress
    }

    val itemsSubtotal = remember(selectedItems) {
        selectedItems.sumOf { (it.price ?: 450.0) * it.quantity }
    }

    val totalShippingFee = remember(shopShipping.values.toList()) {
        shopGroups.sumOf { shopShipping[it.shopName]?.price ?: 35.0 }
    }

    val grandTotal = (itemsSubtotal + totalShippingFee - appliedVoucherDiscount).coerceAtLeast(0.0)

    val formattedGrandTotal = PriceFormat.format(grandTotal, "THB", locale) ?: "฿${String.format(locale, "%.2f", grandTotal)}"

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
            // 1. Delivery Address Card
            item {
                AddressCard(
                    recipient = activeAddress?.recipientName ?: "สมชาย ใจดี",
                    phone = activeAddress?.phone ?: "081-234-5678",
                    address = activeAddress?.fullAddressText ?: "123/45 ถนนสุขุมวิท แขวงคลองเตย เขตคลองเตย กรุงเทพมหานคร 10110",
                    isDefault = activeAddress?.isDefault ?: true,
                    onChange = { showAddressPicker = true },
                )
            }

            // 2. Purchased Items Grouped by Shop
            items(shopGroups, key = { it.shopName }) { group ->
                ShopItemsSection(
                    group = group,
                    selectedShipping = shopShipping[group.shopName] ?: ShippingOption.Standard,
                    onSelectShipping = { option -> shopShipping[group.shopName] = option },
                    locale = locale,
                )
            }

            // 3. Vouchers & Coupons
            item {
                VoucherCard(
                    voucherCode = voucherCodeInput,
                    onCodeChange = { voucherCodeInput = it; voucherNotice = null },
                    onApplyCode = {
                        focusManager.clearFocus()
                        if (voucherCodeInput.trim().equals("VELNOX50", ignoreCase = true)) {
                            appliedVoucherDiscount = 50.0
                            voucherNotice = context.getString(R.string.checkout_voucher_applied)
                        } else if (voucherCodeInput.trim().equals("FREESHIP", ignoreCase = true)) {
                            appliedVoucherDiscount = totalShippingFee
                            voucherNotice = context.getString(R.string.checkout_voucher_applied)
                        } else {
                            voucherNotice = context.getString(R.string.checkout_voucher_invalid)
                        }
                    },
                    appliedDiscount = appliedVoucherDiscount,
                    onQuickSelectVoucher = { discount ->
                        appliedVoucherDiscount = discount
                        voucherNotice = context.getString(R.string.checkout_voucher_applied)
                    },
                    notice = voucherNotice,
                    totalShipping = totalShippingFee,
                )
            }

            // 4. Payment Method Selector
            item {
                PaymentMethodSection(
                    selectedMethod = selectedPayment,
                    onSelectMethod = { selectedPayment = it },
                )
            }

            // 5. Order Financial Summary
            item {
                OrderSummarySection(
                    itemCount = selectedItems.sumOf { it.quantity },
                    itemsSubtotal = itemsSubtotal,
                    shippingTotal = totalShippingFee,
                    voucherDiscount = appliedVoucherDiscount,
                    grandTotal = grandTotal,
                    locale = locale,
                )
            }
        }

        // 6. Bottom Sticky Checkout Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .navigationBarsPadding()
                .padding(horizontal = PageGutter, vertical = 12.dp),
        ) {
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
                    label = stringResource(R.string.checkout_place_order),
                    onClick = {
                        val newOrderId = "VLX-${(1000..9999).random()}"
                        createdOrderId = newOrderId

                        // Add new active order to OrdersDemo
                        val firstItem = selectedItems.first()
                        val orderTitle = firstItem.name
                            ?: if (firstItem.nameRes != 0) context.getString(firstItem.nameRes) else "Velnox Order"

                        OrdersDemo.addOrder(
                            DemoOrder(
                                id = newOrderId,
                                status = OrderStatus.Processing,
                                productNameRes = firstItem.nameRes,
                                productArt = firstItem.art,
                                itemCount = selectedItems.sumOf { it.quantity },
                                daysAgo = 0,
                                customTitle = orderTitle,
                                customStore = firstItem.storeName,
                                totalPrice = grandTotal,
                                imageUrl = firstItem.imageUrl,
                                deliveryAddress = activeAddress?.shortSummary ?: activeAddress?.fullAddressText,
                            ),
                        )

                        // Clear purchased items from Cart
                        cartState.removeSelected()
                        isOrderSuccess = true
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
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
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
                TextButton(onClick = { showAddressPicker = false }) {
                    Text(
                        text = stringResource(R.string.common_close),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
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
                    text = stringResource(R.string.checkout_success_message, createdOrderId),
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
private fun AddressCard(
    recipient: String,
    phone: String,
    address: String,
    isDefault: Boolean,
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
                text = recipient,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = phone,
                style = TextXs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isDefault) {
                Spacer(Modifier.width(8.dp))
                BrandBadge(
                    text = "ค่าเริ่มต้น",
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = address,
            style = TextXs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
        )
    }
}

@Composable
private fun ShopItemsSection(
    group: ShopOrderGroup,
    selectedShipping: ShippingOption,
    onSelectShipping: (ShippingOption) -> Unit,
    locale: java.util.Locale,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg),
    ) {
        // Shop header
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
            BrandBadge(text = "Verified")
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Item lines
        group.items.forEachIndexed { index, item ->
            val context = LocalContext.current
            val name = item.name ?: (if (item.nameRes != 0) context.getString(item.nameRes) else item.productId)
            val price = item.price ?: 450.0
            val formattedPrice = PriceFormat.format(price, item.currency, locale) ?: "฿$price"

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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Shipping selection
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.checkout_shipping_option),
                style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))

            ShippingOption.entries.forEach { option ->
                val selected = option == selectedShipping
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .velnoxClickable(role = Role.RadioButton) { onSelectShipping(option) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onSelectShipping(option) },
                        colors = RadioButtonDefaults.colors(selectedColor = VelnoxBrand),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(option.titleRes),
                            style = Text2xs.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.checkout_estimated_delivery, option.daysLabel),
                            style = Text2xs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "฿${option.price.toInt()}",
                        style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoucherCard(
    voucherCode: String,
    onCodeChange: (String) -> Unit,
    onApplyCode: () -> Unit,
    appliedDiscount: Double,
    onQuickSelectVoucher: (Double) -> Unit,
    notice: String?,
    totalShipping: Double,
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
                imageVector = VelnoxIcons.Tag,
                contentDescription = null,
                tint = VelnoxBrand,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.checkout_voucher_title),
                style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (appliedDiscount > 0) {
                Text(
                    text = "-฿${appliedDiscount.toInt()}",
                    style = Text2xs.copy(fontWeight = FontWeight.Bold),
                    color = VelnoxBrand,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Code input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(SquircleSm)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (voucherCode.isEmpty()) {
                    Text(
                        text = stringResource(R.string.checkout_voucher_code_hint),
                        style = TextXs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = voucherCode,
                    onValueChange = onCodeChange,
                    singleLine = true,
                    textStyle = TextXs.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(VelnoxBrand),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onApplyCode() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(SquircleSm)
                    .background(VelnoxBrand)
                    .velnoxClickable(role = Role.Button, onClick = onApplyCode)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.checkout_voucher_apply),
                    style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        if (notice != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = notice,
                style = Text2xs,
                color = if (appliedDiscount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(10.dp))

        // Quick vouchers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickVoucherChip(
                label = stringResource(R.string.checkout_voucher_free_shipping),
                selected = appliedDiscount == totalShipping && totalShipping > 0,
                onClick = { onQuickSelectVoucher(totalShipping) },
                modifier = Modifier.weight(1f),
            )
            QuickVoucherChip(
                label = stringResource(R.string.checkout_voucher_discount_50),
                selected = appliedDiscount == 50.0,
                onClick = { onQuickSelectVoucher(50.0) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickVoucherChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(SquircleSm)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                SquircleSm,
            )
            .velnoxClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = Text2xs.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    shippingTotal: Double,
    voucherDiscount: Double,
    grandTotal: Double,
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
            value = PriceFormat.format(itemsSubtotal, "THB", locale) ?: "฿$itemsSubtotal",
        )

        SummaryRow(
            label = stringResource(R.string.checkout_subtotal_shipping),
            value = PriceFormat.format(shippingTotal, "THB", locale) ?: "฿$shippingTotal",
        )

        if (voucherDiscount > 0) {
            SummaryRow(
                label = stringResource(R.string.checkout_discount_voucher),
                value = "-฿${voucherDiscount.toInt()}",
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
                text = PriceFormat.format(grandTotal, "THB", locale) ?: "฿$grandTotal",
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
