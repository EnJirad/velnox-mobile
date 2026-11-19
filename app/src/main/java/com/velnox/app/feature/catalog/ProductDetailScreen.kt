package com.velnox.app.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import com.velnox.app.R
import com.velnox.app.VelnoxApplication
import com.velnox.app.core.catalog.CatalogError
import com.velnox.app.core.catalog.CatalogResult
import com.velnox.app.core.catalog.PriceFormat
import com.velnox.app.core.catalog.ProductDetail
import com.velnox.app.feature.home.ProductThumbnail
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.TopBar
import com.velnox.app.ui.components.VelnoxSpacing
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleXs
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand

/**
 * One product, fetched from `GET /api/products/:id`.
 *
 * The id comes from the catalogue card, but the page never trusts the card's
 * snapshot: it asks the API for the product so price, stock, images and
 * description are current, and so an unpublished or deleted product is reported
 * as unavailable instead of rendering a stale copy.
 */
@Composable
fun ProductDetailScreen(
    productId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalog = (LocalContext.current.applicationContext as VelnoxApplication).catalog

    var state by remember(productId) {
        mutableStateOf<ProductState>(ProductState.Loading)
    }
    var reloadKey by remember(productId) { mutableIntStateOf(0) }
    var selectedVariantId by remember(productId) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(productId, reloadKey) {
        state = ProductState.Loading
        state = when (val result = catalog.product(productId, force = reloadKey > 0)) {
            is CatalogResult.Ok -> ProductState.Content(result.value)
            is CatalogResult.Failed -> when (result.error) {
                CatalogError.NotFound -> ProductState.Unavailable
                CatalogError.Network, CatalogError.Server -> ProductState.Error
            }
        }
    }

    val title = (state as? ProductState.Content)?.product?.name.orEmpty()

    // A plain Column, not StandaloneScreen: that scaffold wraps its content in a
    // verticalScroll, and a LazyColumn inside it would be measured with infinite
    // height.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(title = title, onBack = onBack)

        when (val current = state) {
            ProductState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }

            ProductState.Error -> EmptyState(
                icon = VelnoxIcons.EyeOff,
                title = stringResource(R.string.catalog_error_title),
                body = stringResource(R.string.catalog_error_body),
                actionLabel = stringResource(R.string.common_retry),
                onAction = { reloadKey++ },
            )

            ProductState.Unavailable -> EmptyState(
                icon = VelnoxIcons.Package,
                title = stringResource(R.string.product_unavailable_title),
                body = stringResource(R.string.product_unavailable_body),
                actionLabel = stringResource(R.string.common_back),
                onAction = onBack,
            )

            is ProductState.Content -> {
                val selectedVariant = current.product.variants.firstOrNull { it.id == selectedVariantId }
                    ?: current.product.variants.firstOrNull()

                val effectivePrice = selectedVariant?.price ?: current.product.displayPrice
                val effectiveCompareAt = selectedVariant?.compareAtPrice ?: current.product.compareAtPrice
                val effectiveDiscount: Int? = if (effectiveCompareAt != null && effectivePrice != null && effectiveCompareAt > effectivePrice) {
                    (((effectiveCompareAt - effectivePrice) / effectiveCompareAt) * 100).roundToInt()
                } else {
                    current.product.discountPercent
                }
                val effectiveInStock = selectedVariant?.isAvailable ?: current.product.isInStock
                val effectiveStock = selectedVariant?.inventoryQuantity ?: current.product.availableStock

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = VelnoxSpacing.xxl),
                ) {
                    item(key = "gallery", contentType = "gallery") {
                        Gallery(current.product.images.map { it.url })
                    }

                    item(key = "head", contentType = "text") {
                        Column(modifier = Modifier.padding(horizontal = PageGutter)) {
                            Text(
                                text = current.product.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.height(VelnoxSpacing.sm))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
                            ) {
                                PriceFormat.format(
                                    effectivePrice,
                                    current.product.currency,
                                    LocalConfiguration.current.locales[0],
                                )?.let { price ->
                                    Text(
                                        text = price,
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                                if (effectiveCompareAt != null && effectivePrice != null && effectiveCompareAt > effectivePrice) {
                                    PriceFormat.format(
                                        effectiveCompareAt,
                                        current.product.currency,
                                        LocalConfiguration.current.locales[0],
                                    )?.let { comparePrice ->
                                        Text(
                                            text = comparePrice,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textDecoration = TextDecoration.LineThrough,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (effectiveDiscount != null && effectiveDiscount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(SquircleXs)
                                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = "-$effectiveDiscount%",
                                            style = Text2xs.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF059669),
                                        )
                                    }
                                }
                                effectiveInStock?.let { inStock ->
                                    StockBadge(inStock)
                                }
                            }
                            current.product.unit?.let { unit ->
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.product_per_unit, unit),
                                    style = Text2xs,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            effectiveStock?.takeIf { it > 0 }?.let { stock ->
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.product_stock_left, stock),
                                    style = Text2xs,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (current.product.variants.size > 1) {
                        item(key = "variants", contentType = "variants") {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = PageGutter)
                                    .padding(top = VelnoxSpacing.md),
                            ) {
                                Text(
                                    text = stringResource(R.string.product_variants_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Spacer(Modifier.height(VelnoxSpacing.xs))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.xs),
                                ) {
                                    items(
                                        items = current.product.variants,
                                        key = { it.id },
                                    ) { variant ->
                                        val isSelected = variant.id == selectedVariant?.id
                                        Box(
                                            modifier = Modifier
                                                .clip(Squircle)
                                                .background(
                                                    if (isSelected) VelnoxBrand.copy(alpha = 0.12f)
                                                    else MaterialTheme.colorScheme.surface,
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) VelnoxBrand else MaterialTheme.colorScheme.outline,
                                                    shape = Squircle,
                                                )
                                                .velnoxClickable(role = Role.RadioButton) {
                                                    selectedVariantId = variant.id
                                                }
                                                .padding(horizontal = VelnoxSpacing.md, vertical = VelnoxSpacing.sm),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = variant.title,
                                                style = TextXs.copy(
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                ),
                                                color = if (isSelected) VelnoxBrand else MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                current.product.shopName?.let { shop ->
                    item(key = "shop", contentType = "text") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = PageGutter,
                                    top = VelnoxSpacing.lg,
                                    end = PageGutter,
                                )
                                .clip(Squircle)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    Squircle,
                                )
                                .padding(VelnoxSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = VelnoxIcons.ShoppingBag,
                                contentDescription = null,
                                tint = VelnoxBrand,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(VelnoxSpacing.sm))
                            Text(
                                text = shop,
                                style = TextXs.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                if (current.product.soldCount > 0) {
                    item(key = "sold", contentType = "text") {
                        Text(
                            text = stringResource(R.string.product_sold, current.product.soldCount),
                            style = Text2xs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = PageGutter,
                                top = VelnoxSpacing.sm,
                                end = PageGutter,
                            ),
                        )
                    }
                }

                current.product.description?.takeIf { it.isNotBlank() }?.let { description ->
                    item(key = "description", contentType = "text") {
                        Column(modifier = Modifier.padding(horizontal = PageGutter)) {
                            Spacer(Modifier.height(VelnoxSpacing.lg))
                            Text(
                                text = stringResource(R.string.product_description_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.height(VelnoxSpacing.sm))
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun Gallery(urls: List<String>) {
    var selected by remember(urls) { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter)
                .aspectRatio(1f)
                .clip(Squircle)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val url = urls.getOrNull(selected)
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ProductThumbnail(
                    url = null,
                    name = "",
                    outOfStock = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (urls.size > 1) {
            Spacer(Modifier.height(VelnoxSpacing.sm))
            LazyRow(
                contentPadding = PaddingValues(horizontal = PageGutter),
                horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.xs),
            ) {
                itemsIndexed(urls) { index, url ->
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(SquircleXs)
                            .border(
                                width = if (index == selected) 2.dp else 1.dp,
                                color = if (index == selected) {
                                    VelnoxBrand
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = SquircleXs,
                            )
                            .velnoxClickable(role = Role.Button) { selected = index },
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StockBadge(inStock: Boolean) {
    val label = if (inStock) {
        stringResource(R.string.product_in_stock)
    } else {
        stringResource(R.string.product_out_of_stock)
    }
    Text(
        text = label,
        style = Text2xs.copy(fontWeight = FontWeight.Medium),
        color = if (inStock) VelnoxBrand else MaterialTheme.colorScheme.error,
        modifier = Modifier
            .clip(SquircleXs)
            .border(
                1.dp,
                if (inStock) VelnoxBrand else MaterialTheme.colorScheme.error,
                SquircleXs,
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

private sealed interface ProductState {
    data object Loading : ProductState
    data object Error : ProductState
    data object Unavailable : ProductState
    data class Content(val product: ProductDetail) : ProductState
}
