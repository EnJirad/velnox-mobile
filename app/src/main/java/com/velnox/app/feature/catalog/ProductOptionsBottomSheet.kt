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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.QuantityStepper
import com.velnox.app.ui.components.VelnoxSpacing
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.SquircleXs
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import kotlin.math.roundToInt

/**
 * Modal Bottom Sheet for selecting product options, variants, and quantity.
 * Supports multi-group options (e.g., Color, Size) as well as direct variant selection.
 * Validates stock, clamps quantity, and updates prices reactively.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProductOptionsBottomSheet(
    product: ProductDetail,
    initialVariantId: String?,
    initialQuantity: Int,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismissRequest: () -> Unit,
    onConfirm: (selectedVariant: ProductVariant?, quantity: Int) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]

    // Multi-group option selections: map of groupId -> optionValue
    val selectedOptions = remember {
        mutableStateMapOf<String, String>().apply {
            // Pre-populate if optionGroups exist
            product.optionGroups.forEach { group ->
                group.values.firstOrNull()?.let { firstVal ->
                    put(group.id, firstVal.value)
                }
            }
        }
    }

    // Direct variant selection when optionGroups are empty but variants exist
    var selectedDirectVariantId by remember {
        val defaultId = initialVariantId ?: product.variants.firstOrNull { (it.stock ?: 1) > 0 }?.id
            ?: product.variants.firstOrNull()?.id
        androidx.compose.runtime.mutableStateOf(defaultId)
    }

    // Resolve the active variant
    val matchedVariant: ProductVariant? = if (product.optionGroups.isNotEmpty()) {
        // Try matching a variant whose title or id contains selected values, or fallback to first
        val selectedValuesList = selectedOptions.values.toList()
        product.variants.firstOrNull { variant ->
            selectedValuesList.all { optVal ->
                variant.title.contains(optVal, ignoreCase = true)
            }
        } ?: product.variants.firstOrNull()
    } else {
        product.variants.firstOrNull { it.id == selectedDirectVariantId }
            ?: product.variants.firstOrNull()
    }

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

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = VelnoxSpacing.md),
        ) {
            // Drag handle visual
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(SquircleXs)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }

            // Top Product Preview Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = VelnoxSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val previewImage = product.images.firstOrNull()?.url
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(SquircleSm)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (previewImage != null) {
                        AsyncImage(
                            model = previewImage,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PriceFormat.format(activePrice, product.currency, locale)?.let { p ->
                            Text(
                                text = p,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = VelnoxBrand,
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
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "-$activeDiscount%",
                                    style = Text2xs.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF059669),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = if (isOutOfStock) {
                            stringResource(R.string.product_out_of_stock)
                        } else if (activeStock != null) {
                            stringResource(R.string.product_stock_left, activeStock)
                        } else {
                            stringResource(R.string.product_in_stock)
                        },
                        style = Text2xs,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Scrollable Options Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PageGutter, vertical = VelnoxSpacing.sm),
            ) {
                // If optionGroups are present, render each group
                if (product.optionGroups.isNotEmpty()) {
                    product.optionGroups.forEach { group ->
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                        )

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
                                            width = if (isSelected) 1.5.dp else 1.dp,
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
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        if (optValue.imageUrl != null) {
                                            AsyncImage(
                                                model = optValue.imageUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(SquircleXs),
                                            )
                                        }
                                        Text(
                                            text = optValue.label.ifBlank { optValue.value },
                                            style = TextXs.copy(
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
                    // Direct variants chips
                    Text(
                        text = stringResource(R.string.product_variants_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
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
                                        width = if (isSelected) 1.5.dp else 1.dp,
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
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = variant.title,
                                    style = TextXs.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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

                Spacer(Modifier.height(VelnoxSpacing.md))

                // Quantity selector row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.product_quantity_label),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )

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
            }

            // Confirm Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = VelnoxSpacing.sm),
            ) {
                Button(
                    onClick = {
                        onConfirm(matchedVariant, currentQuantity)
                    },
                    enabled = !isOutOfStock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = SquircleLg,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VelnoxBrand,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = if (isOutOfStock) {
                            stringResource(R.string.product_out_of_stock)
                        } else {
                            stringResource(R.string.product_confirm)
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}
