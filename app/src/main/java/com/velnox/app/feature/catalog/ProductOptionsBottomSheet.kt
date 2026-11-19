package com.velnox.app.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.velnox.app.R
import com.velnox.app.core.catalog.PriceFormat
import com.velnox.app.core.catalog.ProductDetail
import com.velnox.app.core.catalog.ProductVariant
import com.velnox.app.feature.home.ProductThumbnail
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.QuantityStepper
import com.velnox.app.ui.components.VelnoxSpacing
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.SquircleXs
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import kotlin.math.roundToInt

/**
 * The intent or context with which the Product Options bottom sheet was opened.
 */
enum class ProductSelectionIntent {
    SELECT_OPTIONS,
    ADD_TO_CART,
    BUY_NOW,
}

/**
 * Large Product Selection Popup / Bottom Sheet (approx. 80% screen height).
 *
 * Implements:
 * - Approximately 80% screen height with rounded top corners and smooth entrance
 * - Clear close button at top right
 * - Upper product preview with large image (responsive to selected variant image),
 *   prominent current price, genuine original price strikethrough, discount badge, and stock info
 * - Visual option image size controls (A- / A+) affecting only option thumbnails
 * - Simplified, highly readable option group chips and quantity stepper
 * - Context-aware bottom actions:
 *     - ADD_TO_CART: Shows ONLY the Add to Cart confirmation button
 *     - BUY_NOW: Shows ONLY the Buy Now confirmation button
 *     - SELECT_OPTIONS: Shows both Add to Cart and Buy Now options
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProductOptionsBottomSheet(
    product: ProductDetail,
    initialVariantId: String?,
    initialQuantity: Int,
    intent: ProductSelectionIntent = ProductSelectionIntent.SELECT_OPTIONS,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismissRequest: () -> Unit,
    onAddToCart: ((selectedVariant: ProductVariant?, quantity: Int) -> Unit)? = null,
    onBuyNow: ((selectedVariant: ProductVariant?, quantity: Int) -> Unit)? = null,
    onConfirmSelection: ((selectedVariant: ProductVariant?, quantity: Int) -> Unit)? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val targetSheetHeight = (screenHeight * 0.82f).coerceAtLeast(460.dp)

    // Option thumbnail size preference (persisted while interacting with options)
    var thumbnailSizeDp by rememberSaveable { mutableFloatStateOf(48f) }

    // Multi-group option selections: map of groupId -> optionValue
    val initialVariant = product.variants.firstOrNull { it.id == initialVariantId }
    val selectedOptions = remember {
        mutableStateMapOf<String, String>().apply {
            product.optionGroups.forEach { group ->
                val matchingVal = if (initialVariant != null) {
                    group.values.firstOrNull { v ->
                        initialVariant.title.contains(v.value, ignoreCase = true) ||
                            initialVariant.title.contains(v.label, ignoreCase = true)
                    } ?: group.values.firstOrNull()
                } else {
                    group.values.firstOrNull()
                }
                if (matchingVal != null) {
                    put(group.id, matchingVal.value)
                }
            }
        }
    }

    // Direct variant selection when optionGroups are empty but variants exist
    var selectedDirectVariantId by remember {
        val defaultId = initialVariantId ?: product.variants.firstOrNull { (it.stock ?: 1) > 0 }?.id
            ?: product.variants.firstOrNull()?.id
        mutableStateOf(defaultId)
    }

    // Resolve the active variant based on current selections
    val matchedVariant: ProductVariant? = if (product.optionGroups.isNotEmpty()) {
        val selectedValuesList = selectedOptions.values.filter { it.isNotBlank() }
        product.variants.firstOrNull { variant ->
            selectedValuesList.all { optVal ->
                variant.title.contains(optVal, ignoreCase = true)
            }
        } ?: product.variants.firstOrNull()
    } else {
        product.variants.firstOrNull { it.id == selectedDirectVariantId }
            ?: product.variants.firstOrNull()
    }

    // Preview image: prioritize option image if an option value with imageUrl is selected
    val selectedOptionImage = product.optionGroups
        .flatMap { it.values }
        .firstOrNull { optVal -> selectedOptions.values.contains(optVal.value) && !optVal.imageUrl.isNullOrBlank() }
        ?.imageUrl
    val previewImage = selectedOptionImage ?: product.images.firstOrNull()?.url

    val activePrice = matchedVariant?.price ?: product.displayPrice
    val activeCompareAt = matchedVariant?.compareAtPrice ?: product.compareAtPrice
    val activeDiscount: Int? = if (activeCompareAt != null && activePrice != null && activeCompareAt > activePrice) {
        (((activeCompareAt - activePrice) / activeCompareAt) * 100).roundToInt()
    } else {
        product.discountPercent
    }
    val activeStock = matchedVariant?.stock ?: product.availableStock
    val isOutOfStock = activeStock != null && activeStock <= 0

    var currentQuantity by remember {
        val maxStock = activeStock ?: 99
        mutableIntStateOf(initialQuantity.coerceIn(1, maxStock.coerceAtLeast(1)))
    }

    // Check if any group contains image options
    val hasImageOptions = remember(product.optionGroups) {
        product.optionGroups.any { group -> group.values.any { !it.imageUrl.isNullOrBlank() } }
    }

    ModalBottomSheet(
        onDismissRequest = {
            onConfirmSelection?.invoke(matchedVariant, currentQuantity)
            onDismissRequest()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(targetSheetHeight)
                .navigationBarsPadding(),
        ) {
            // Header: Drag Handle & Close Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = 8.dp),
            ) {
                // Centered handle
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(SquircleXs)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .align(Alignment.Center),
                )

                // Top-right Close Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .velnoxClickable(
                            role = Role.Button,
                            onClick = {
                                onConfirmSelection?.invoke(matchedVariant, currentQuantity)
                                onDismissRequest()
                            },
                        )
                        .align(Alignment.CenterEnd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VelnoxIcons.Close,
                        contentDescription = stringResource(R.string.product_close),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Top Product Preview Card (Large, Clear Layout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = VelnoxSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Left: Large Product Image
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(SquircleLg)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SquircleLg),
                    contentAlignment = Alignment.Center,
                ) {
                    if (previewImage != null) {
                        AsyncImage(
                            model = previewImage,
                            contentDescription = product.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    } else {
                        ProductThumbnail(
                            url = null,
                            name = product.name,
                            outOfStock = isOutOfStock,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                }

                // Right: Essential info (Price, Compare-at, Discount, Stock, Selected summary)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Price line
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PriceFormat.format(activePrice, product.currency, locale)?.let { p ->
                            Text(
                                text = p,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFE11D48),
                            )
                        }

                        if (activeCompareAt != null && activePrice != null && activeCompareAt > activePrice) {
                            PriceFormat.format(activeCompareAt, product.currency, locale)?.let { cp ->
                                Text(
                                    text = cp,
                                    style = TextXs,
                                    textDecoration = TextDecoration.LineThrough,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (activeDiscount != null && activeDiscount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(SquircleXs)
                                    .background(Color(0xFFE11D48).copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "-$activeDiscount%",
                                    style = Text2xs.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFE11D48),
                                )
                            }
                        }
                    }

                    // Stock status
                    Text(
                        text = if (isOutOfStock) {
                            stringResource(R.string.product_out_of_stock)
                        } else if (activeStock != null) {
                            stringResource(R.string.product_stock_left, activeStock)
                        } else {
                            stringResource(R.string.product_in_stock)
                        },
                        style = Text2xs.copy(fontWeight = FontWeight.Medium),
                        color = if (isOutOfStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Selected options summary
                    val selectionSummary = if (product.optionGroups.isNotEmpty()) {
                        selectedOptions.values.filter { it.isNotBlank() }.joinToString(", ")
                    } else {
                        matchedVariant?.title.orEmpty()
                    }
                    if (selectionSummary.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.product_options_selected, selectionSummary),
                            style = Text2xs,
                            color = VelnoxBrand,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(VelnoxSpacing.xs))

            // Scrollable Content Area: Options & Quantity Stepper
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PageGutter, vertical = VelnoxSpacing.xs),
            ) {
                // 1. Multi-group Option Selectors
                if (product.optionGroups.isNotEmpty()) {
                    product.optionGroups.forEachIndexed { groupIndex, group ->
                        val hasImagesInThisGroup = group.values.any { !it.imageUrl.isNullOrBlank() }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (groupIndex == 0) 6.dp else 14.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onBackground,
                            )

                            // Thumbnail size control for options with image swatches
                            if (hasImagesInThisGroup) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.product_thumbnail_size),
                                        style = Text2xs,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(SquircleSm)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(0.5.dp, MaterialTheme.colorScheme.outline, SquircleSm)
                                            .velnoxClickable(
                                                enabled = thumbnailSizeDp > 36f,
                                                role = Role.Button,
                                                onClick = {
                                                    thumbnailSizeDp = (thumbnailSizeDp - 8f).coerceAtLeast(36f)
                                                },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "A-",
                                            style = Text2xs.copy(fontWeight = FontWeight.Bold),
                                            color = if (thumbnailSizeDp > 36f) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            },
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(SquircleSm)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(0.5.dp, MaterialTheme.colorScheme.outline, SquircleSm)
                                            .velnoxClickable(
                                                enabled = thumbnailSizeDp < 64f,
                                                role = Role.Button,
                                                onClick = {
                                                    thumbnailSizeDp = (thumbnailSizeDp + 8f).coerceAtMost(64f)
                                                },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "A+",
                                            style = Text2xs.copy(fontWeight = FontWeight.Bold),
                                            color = if (thumbnailSizeDp < 64f) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            group.values.forEach { optValue ->
                                val isSelected = selectedOptions[group.id] == optValue.value
                                val optOutOfStock = optValue.stock != null && optValue.stock <= 0

                                Box(
                                    modifier = Modifier
                                        .clip(Squircle)
                                        .background(
                                            when {
                                                isSelected -> VelnoxBrand.copy(alpha = 0.12f)
                                                optOutOfStock -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                else -> MaterialTheme.colorScheme.surface
                                            },
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = when {
                                                isSelected -> VelnoxBrand
                                                else -> MaterialTheme.colorScheme.outline
                                            },
                                            shape = Squircle,
                                        )
                                        .velnoxClickable(
                                            enabled = !optOutOfStock,
                                            role = Role.RadioButton,
                                        ) {
                                            selectedOptions[group.id] = optValue.value
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (optValue.imageUrl != null) {
                                            AsyncImage(
                                                model = optValue.imageUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(thumbnailSizeDp.dp)
                                                    .clip(SquircleXs),
                                            )
                                        }
                                        Text(
                                            text = optValue.label.ifBlank { optValue.value },
                                            style = TextXs.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            ),
                                            color = when {
                                                optOutOfStock -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                isSelected -> VelnoxBrand
                                                else -> MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (product.variants.size > 1) {
                    // Direct variants chips when optionGroups is empty
                    Text(
                        text = stringResource(R.string.product_variants_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        product.variants.forEach { variant ->
                            val isSelected = variant.id == matchedVariant?.id
                            val vOutOfStock = variant.stock != null && variant.stock <= 0

                            Box(
                                modifier = Modifier
                                    .clip(Squircle)
                                    .background(
                                        when {
                                            isSelected -> VelnoxBrand.copy(alpha = 0.12f)
                                            vOutOfStock -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            else -> MaterialTheme.colorScheme.surface
                                        },
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = when {
                                            isSelected -> VelnoxBrand
                                            else -> MaterialTheme.colorScheme.outline
                                        },
                                        shape = Squircle,
                                    )
                                    .velnoxClickable(
                                        enabled = !vOutOfStock,
                                        role = Role.RadioButton,
                                    ) {
                                        selectedDirectVariantId = variant.id
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = variant.title,
                                    style = TextXs.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = when {
                                        vOutOfStock -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        isSelected -> VelnoxBrand
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(VelnoxSpacing.lg))

                // Quantity Stepper Section (Accessible and Easy to Use)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.product_quantity_label),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (activeStock != null) {
                            Text(
                                text = stringResource(R.string.product_stock_left, activeStock),
                                style = Text2xs,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    QuantityStepper(
                        value = currentQuantity,
                        onDecrease = {
                            if (currentQuantity > 1) currentQuantity--
                        },
                        onIncrease = {
                            val maxLimit = activeStock ?: 99
                            if (currentQuantity < maxLimit) currentQuantity++
                        },
                        decreaseEnabled = currentQuantity > 1 && !isOutOfStock,
                        increaseEnabled = !isOutOfStock && currentQuantity < (activeStock ?: 99),
                    )
                }

                Spacer(Modifier.height(VelnoxSpacing.md))
            }

            // Fixed/Sticky Bottom Action Area (Context-Aware)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    .padding(horizontal = PageGutter, vertical = 12.dp),
            ) {
                when (intent) {
                    ProductSelectionIntent.ADD_TO_CART -> {
                        // User opened with Add to Cart intent -> ONLY Add to Cart button
                        Button(
                            onClick = {
                                onAddToCart?.invoke(matchedVariant, currentQuantity)
                                onDismissRequest()
                            },
                            enabled = !isOutOfStock,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = SquircleLg,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VelnoxBrand,
                                contentColor = Color.White,
                            ),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = VelnoxIcons.ShoppingCart,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isOutOfStock) {
                                        stringResource(R.string.product_out_of_stock)
                                    } else {
                                        stringResource(R.string.product_add_to_cart)
                                    },
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        }
                    }

                    ProductSelectionIntent.BUY_NOW -> {
                        // User opened with Buy Now intent -> ONLY Buy Now button
                        Button(
                            onClick = {
                                onBuyNow?.invoke(matchedVariant, currentQuantity)
                                onDismissRequest()
                            },
                            enabled = !isOutOfStock,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = SquircleLg,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                text = if (isOutOfStock) {
                                    stringResource(R.string.product_out_of_stock)
                                } else {
                                    stringResource(R.string.product_buy_now)
                                },
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }

                    ProductSelectionIntent.SELECT_OPTIONS -> {
                        // Direct options selection intent -> show BOTH Add to Cart and Buy Now
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Add to Cart
                            OutlinedButton(
                                onClick = {
                                    onAddToCart?.invoke(matchedVariant, currentQuantity)
                                    onDismissRequest()
                                },
                                enabled = !isOutOfStock,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = SquircleLg,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = VelnoxBrand,
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(
                                        if (!isOutOfStock) VelnoxBrand else MaterialTheme.colorScheme.outline,
                                    ),
                                ),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = VelnoxIcons.ShoppingCart,
                                        contentDescription = null,
                                        tint = if (!isOutOfStock) VelnoxBrand else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.product_add_to_cart),
                                        style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                                    )
                                }
                            }

                            // Buy Now
                            Button(
                                onClick = {
                                    onBuyNow?.invoke(matchedVariant, currentQuantity)
                                    onDismissRequest()
                                },
                                enabled = !isOutOfStock,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = SquircleLg,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Text(
                                    text = if (isOutOfStock) {
                                        stringResource(R.string.product_out_of_stock)
                                    } else {
                                        stringResource(R.string.product_buy_now)
                                    },
                                    style = TextXs.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
