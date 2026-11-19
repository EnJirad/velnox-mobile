package com.velnox.app.feature.catalog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.velnox.app.R
import com.velnox.app.VelnoxApplication
import com.velnox.app.core.catalog.CatalogError
import com.velnox.app.core.catalog.CatalogResult
import com.velnox.app.core.catalog.PriceFormat
import com.velnox.app.core.catalog.ProductDetail
import com.velnox.app.core.catalog.ProductVariant
import com.velnox.app.feature.home.ProductThumbnail
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.QuantityStepper
import com.velnox.app.ui.components.TopBar
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Premium Product Detail Screen.
 *
 * Implements:
 * - Responsive gallery with large main image on the left and vertical thumbnail strip on the right
 * - Horizontal swipe gestures and tap-to-select for gallery thumbnails
 * - Full-screen image zoom viewer (pinch-to-zoom, pan, double-tap to reset)
 * - Real backend prices, compare-at prices, discount badges, and stock levels
 * - Variant selection with real backend attributes, availability checks, and price/stock reactivity
 * - Quantity selector validated against available stock
 * - Integrated Add to Cart and Buy Now purchase actions
 */
@Composable
fun ProductDetailScreen(
    productId: String,
    onBack: () -> Unit,
    onAddToCart: ((ProductDetail, ProductVariant?, Int) -> Unit)? = null,
    onBuyNow: ((ProductDetail, ProductVariant?, Int) -> Unit)? = null,
    onOpenCart: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val catalog = (LocalContext.current.applicationContext as VelnoxApplication).catalog

    var state by remember(productId) {
        mutableStateOf<ProductState>(ProductState.Loading)
    }
    var reloadKey by remember(productId) { mutableIntStateOf(0) }
    var selectedVariantId by remember(productId) { mutableStateOf<String?>(null) }
    var quantity by remember(productId) { mutableIntStateOf(1) }
    var addedFeedbackMessage by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(productId, reloadKey) {
        state = ProductState.Loading
        state = when (val result = catalog.product(productId, force = reloadKey > 0)) {
            is CatalogResult.Ok -> {
                val detail = result.value
                // Automatically select first in-stock variant if variants exist
                if (detail.variants.isNotEmpty() && selectedVariantId == null) {
                    selectedVariantId = detail.variants.firstOrNull { (it.stock ?: 1) > 0 }?.id
                        ?: detail.variants.firstOrNull()?.id
                }
                ProductState.Content(detail)
            }
            is CatalogResult.Failed -> when (result.error) {
                CatalogError.NotFound -> ProductState.Unavailable
                CatalogError.Network, CatalogError.Server -> ProductState.Error
            }
        }
    }

    val title = (state as? ProductState.Content)?.product?.name.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(
            title = title,
            onBack = onBack,
            actions = {
                if (onOpenCart != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .velnoxClickable(role = Role.Button, onClick = onOpenCart),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = VelnoxIcons.ShoppingCart,
                            contentDescription = stringResource(R.string.nav_cart),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
        )

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
                val product = current.product
                val selectedVariant = product.variants.firstOrNull { it.id == selectedVariantId }
                    ?: product.variants.firstOrNull()

                val effectivePrice = selectedVariant?.price ?: product.displayPrice
                val effectiveCompareAt = selectedVariant?.compareAtPrice ?: product.compareAtPrice
                val effectiveDiscount: Int? = if (effectiveCompareAt != null && effectivePrice != null && effectiveCompareAt > effectivePrice) {
                    (((effectiveCompareAt - effectivePrice) / effectiveCompareAt) * 100).roundToInt()
                } else {
                    product.discountPercent
                }
                val effectiveStock = selectedVariant?.stock ?: product.availableStock
                val isOutOfStock = effectiveStock != null && effectiveStock <= 0
                val canPurchase = !isOutOfStock

                // Clamp quantity whenever selected variant's stock changes
                LaunchedEffect(effectiveStock) {
                    val maxLimit = effectiveStock ?: 99
                    if (maxLimit > 0 && quantity > maxLimit) {
                        quantity = maxLimit
                    } else if (quantity < 1) {
                        quantity = 1
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        // 1. Gallery
                        item(key = "gallery", contentType = "gallery") {
                            ProductGallery(
                                urls = product.images.map { it.url },
                                modifier = Modifier.padding(bottom = VelnoxSpacing.md),
                            )
                        }

                        // 2. Pricing & Stock overview
                        item(key = "pricing_overview", contentType = "overview") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = PageGutter),
                            ) {
                                // Price line
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
                                ) {
                                    PriceFormat.format(
                                        effectivePrice,
                                        product.currency,
                                        LocalConfiguration.current.locales[0],
                                    )?.let { price ->
                                        Text(
                                            text = price,
                                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                    }

                                    if (effectiveCompareAt != null && effectivePrice != null && effectiveCompareAt > effectivePrice) {
                                        PriceFormat.format(
                                            effectiveCompareAt,
                                            product.currency,
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

                                    Spacer(Modifier.weight(1f))

                                    StockBadge(inStock = !isOutOfStock)
                                }

                                // Unit & remaining stock info
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    product.unit?.let { unit ->
                                        Text(
                                            text = stringResource(R.string.product_per_unit, unit),
                                            style = Text2xs,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    if (effectiveStock != null && effectiveStock > 0) {
                                        Text(
                                            text = stringResource(R.string.product_stock_left, effectiveStock),
                                            style = Text2xs,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Spacer(Modifier.height(VelnoxSpacing.sm))

                                // Product Title
                                Text(
                                    text = product.name,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onBackground,
                                )

                                // Category & Sold Count
                                Row(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    product.categorySlug?.let { category ->
                                        Box(
                                            modifier = Modifier
                                                .clip(SquircleXs)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                        ) {
                                            Text(
                                                text = category,
                                                style = Text2xs.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }

                                    if (product.soldCount > 0) {
                                        Text(
                                            text = stringResource(R.string.product_sold, product.soldCount),
                                            style = Text2xs,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Variant Selection (if product has real variants)
                        if (product.variants.size > 1) {
                            item(key = "variants_section", contentType = "variants") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = PageGutter)
                                        .padding(top = VelnoxSpacing.lg),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.product_variants_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )

                                        selectedVariant?.let { v ->
                                            Text(
                                                text = v.title,
                                                style = Text2xs.copy(fontWeight = FontWeight.Medium),
                                                color = VelnoxBrand,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(VelnoxSpacing.sm))

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(
                                            items = product.variants,
                                            key = { it.id },
                                        ) { variant ->
                                            val isSelected = variant.id == selectedVariant?.id
                                            val variantOutOfStock = variant.stock != null && variant.stock <= 0

                                            Box(
                                                modifier = Modifier
                                                    .clip(Squircle)
                                                    .background(
                                                        when {
                                                            isSelected -> VelnoxBrand.copy(alpha = 0.12f)
                                                            variantOutOfStock -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                                                        enabled = !variantOutOfStock,
                                                        role = Role.RadioButton,
                                                    ) {
                                                        selectedVariantId = variant.id
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = variant.title,
                                                        style = TextXs.copy(
                                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                        ),
                                                        color = when {
                                                            variantOutOfStock -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                            isSelected -> VelnoxBrand
                                                            else -> MaterialTheme.colorScheme.onSurface
                                                        },
                                                    )

                                                    variant.price?.let { vPrice ->
                                                        PriceFormat.format(
                                                            vPrice,
                                                            product.currency,
                                                            LocalConfiguration.current.locales[0],
                                                        )?.let { formattedPrice ->
                                                            Text(
                                                                text = formattedPrice,
                                                                style = Text2xs,
                                                                color = if (variantOutOfStock) {
                                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                                } else {
                                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                                },
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Quantity Selector Section
                        item(key = "quantity_section", contentType = "quantity") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = PageGutter)
                                    .padding(top = VelnoxSpacing.lg),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.product_quantity_label),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                        effectiveStock?.let { stock ->
                                            Text(
                                                text = stringResource(R.string.product_stock_left, stock),
                                                style = Text2xs,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }

                                    QuantityStepper(
                                        value = quantity,
                                        onDecrease = {
                                            if (quantity > 1) quantity--
                                        },
                                        onIncrease = {
                                            val maxLimit = effectiveStock ?: 99
                                            if (quantity < maxLimit) quantity++
                                        },
                                        decreaseEnabled = quantity > 1 && !isOutOfStock,
                                        increaseEnabled = !isOutOfStock && quantity < (effectiveStock ?: 99),
                                    )
                                }
                            }
                        }

                        // 5. Seller Information Card
                        product.shopName?.let { shop ->
                            item(key = "shop_section", contentType = "shop") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = PageGutter)
                                        .padding(top = VelnoxSpacing.lg),
                                ) {
                                    Text(
                                        text = stringResource(R.string.product_seller_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Spacer(Modifier.height(VelnoxSpacing.xs))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(SquircleLg)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
                                            .padding(VelnoxSpacing.md),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(VelnoxBrand.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = VelnoxIcons.ShoppingBag,
                                                contentDescription = null,
                                                tint = VelnoxBrand,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(VelnoxSpacing.md))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = shop,
                                                style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            product.shopSlug?.let { slug ->
                                                Text(
                                                    text = "@$slug",
                                                    style = Text2xs,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 6. Product Description
                        product.description?.takeIf { it.isNotBlank() }?.let { description ->
                            item(key = "description_section", contentType = "description") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = PageGutter)
                                        .padding(top = VelnoxSpacing.lg),
                                ) {
                                    Text(
                                        text = stringResource(R.string.product_description_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Spacer(Modifier.height(VelnoxSpacing.xs))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(SquircleLg)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
                                            .padding(VelnoxSpacing.md),
                                    ) {
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

                    // Added feedback banner
                    AnimatedVisibility(
                        visible = addedFeedbackMessage != null,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp, start = PageGutter, end = PageGutter),
                    ) {
                        addedFeedbackMessage?.let { msg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(Squircle)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, VelnoxBrand, Squircle)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = VelnoxIcons.Check,
                                    contentDescription = null,
                                    tint = VelnoxBrand,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    style = TextXs.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (onOpenCart != null) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.product_view_cart),
                                        style = TextXs.copy(fontWeight = FontWeight.Bold),
                                        color = VelnoxBrand,
                                        modifier = Modifier.velnoxClickable(role = Role.Button, onClick = onOpenCart),
                                    )
                                }
                            }
                        }
                    }
                }

                // 8. Bottom Action Area
                BottomPurchaseArea(
                    canPurchase = canPurchase,
                    isOutOfStock = isOutOfStock,
                    onAddToCart = {
                        if (canPurchase) {
                            onAddToCart?.invoke(product, selectedVariant, quantity)
                            addedFeedbackMessage = product.name
                            scope.launch {
                                delay(2800)
                                addedFeedbackMessage = null
                            }
                        }
                    },
                    onBuyNow = {
                        if (canPurchase) {
                            onBuyNow?.invoke(product, selectedVariant, quantity)
                        }
                    },
                )
            }
        }
    }
}

/**
 * Responsive Product Gallery:
 * - Large main image on the LEFT
 * - Vertical thumbnail strip on the RIGHT
 * - Horizontal swipe gestures for switching images
 * - Full-screen pinch-to-zoom viewer dialog
 * - Graceful handling of 0, 1, or multiple images
 */
@Composable
private fun ProductGallery(
    urls: List<String>,
    modifier: Modifier = Modifier,
) {
    var selected by remember(urls) { mutableIntStateOf(0) }
    var showFullScreen by remember { mutableStateOf(false) }
    val thumbListState = rememberLazyListState()

    val validSelected = if (urls.isEmpty()) 0 else selected.coerceIn(0, urls.lastIndex)

    LaunchedEffect(validSelected) {
        if (urls.size > 1) {
            thumbListState.animateScrollToItem(validSelected)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter),
    ) {
        val totalWidth = maxWidth
        val thumbWidth = if (totalWidth < 340.dp) 48.dp else 60.dp
        val galleryHeight = (totalWidth * 0.88f).coerceIn(260.dp, 360.dp)

        if (urls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(galleryHeight)
                    .clip(SquircleLg)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                ProductThumbnail(
                    url = null,
                    name = "",
                    outOfStock = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else if (urls.size == 1) {
            // Single image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(galleryHeight)
                    .clip(SquircleLg)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .velnoxClickable(role = Role.Image) { showFullScreen = true },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = urls[0],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VelnoxIcons.Maximize,
                        contentDescription = stringResource(R.string.product_full_screen_zoom),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        } else {
            // Multi-image layout: Main image on the LEFT, vertical thumbnail strip on the RIGHT
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(galleryHeight),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Main image container
                var dragTotalX by remember { mutableFloatStateOf(0f) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(SquircleLg)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(urls.size) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragTotalX = 0f },
                                onDragEnd = {
                                    if (dragTotalX < -40f && validSelected < urls.lastIndex) {
                                        selected = validSelected + 1
                                    } else if (dragTotalX > 40f && validSelected > 0) {
                                        selected = validSelected - 1
                                    }
                                    dragTotalX = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    dragTotalX += dragAmount
                                },
                            )
                        }
                        .velnoxClickable(role = Role.Image) { showFullScreen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    val currentUrl = urls.getOrNull(validSelected)
                    if (currentUrl != null) {
                        AsyncImage(
                            model = currentUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Index indicator badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .clip(SquircleXs)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "${validSelected + 1}/${urls.size}",
                            style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                    }

                    // Maximize / zoom icon
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = VelnoxIcons.Maximize,
                            contentDescription = stringResource(R.string.product_full_screen_zoom),
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                // Vertical thumbnail strip on the RIGHT
                LazyColumn(
                    state = thumbListState,
                    modifier = Modifier
                        .width(thumbWidth)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(urls) { index, url ->
                        val isSelected = index == validSelected
                        Box(
                            modifier = Modifier
                                .size(thumbWidth)
                                .clip(SquircleSm)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) VelnoxBrand else MaterialTheme.colorScheme.outline,
                                    shape = SquircleSm,
                                )
                                .velnoxClickable(role = Role.Button) {
                                    selected = index
                                },
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

    // Full-screen image zoom dialog
    if (showFullScreen && urls.isNotEmpty()) {
        FullScreenImageDialog(
            urls = urls,
            initialIndex = validSelected,
            onDismiss = { showFullScreen = false },
        )
    }
}

/**
 * Full-screen image viewer supporting pinch-to-zoom, panning, and reset.
 */
@Composable
private fun FullScreenImageDialog(
    urls: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val currentUrl = urls.getOrNull(currentIndex)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentIndex) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (currentUrl != null) {
                    AsyncImage(
                        model = currentUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y,
                            ),
                    )
                }
            }

            // Top action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = PageGutter, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .velnoxClickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VelnoxIcons.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }

                if (urls.size > 1) {
                    Text(
                        text = "${currentIndex + 1} / ${urls.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                } else {
                    Spacer(Modifier.size(36.dp))
                }

                if (scale > 1f) {
                    Box(
                        modifier = Modifier
                            .clip(SquircleXs)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .velnoxClickable(role = Role.Button) {
                                scale = 1f
                                offset = Offset.Zero
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Reset",
                            style = Text2xs,
                            color = Color.White,
                        )
                    }
                } else {
                    Spacer(Modifier.size(36.dp))
                }
            }

            // Navigation arrows when unzoomed
            if (urls.size > 1 && scale == 1f) {
                if (currentIndex > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .velnoxClickable(role = Role.Button) {
                                currentIndex--
                                scale = 1f
                                offset = Offset.Zero
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = VelnoxIcons.ChevronLeft,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (currentIndex < urls.lastIndex) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .velnoxClickable(role = Role.Button) {
                                currentIndex++
                                scale = 1f
                                offset = Offset.Zero
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = VelnoxIcons.ChevronRight,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Mobile purchase action bar pinned at the bottom of the screen.
 * Respects Android safe areas and navigation bar insets.
 */
@Composable
private fun BottomPurchaseArea(
    canPurchase: Boolean,
    isOutOfStock: Boolean,
    onAddToCart: () -> Unit,
    onBuyNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
            .navigationBarsPadding()
            .padding(horizontal = PageGutter, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Add to Cart (Secondary style)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(Squircle)
                    .background(
                        if (canPurchase) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    .border(
                        width = 1.dp,
                        color = if (canPurchase) VelnoxBrand else MaterialTheme.colorScheme.outline,
                        shape = Squircle,
                    )
                    .velnoxClickable(
                        enabled = canPurchase,
                        role = Role.Button,
                        onClick = onAddToCart,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = VelnoxIcons.ShoppingCart,
                        contentDescription = null,
                        tint = if (canPurchase) VelnoxBrand else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.product_add_to_cart),
                        style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                        color = if (canPurchase) VelnoxBrand else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Buy Now (Primary solid style)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(Squircle)
                    .background(
                        if (canPurchase) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .velnoxClickable(
                        enabled = canPurchase,
                        role = Role.Button,
                        onClick = onBuyNow,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isOutOfStock) {
                        stringResource(R.string.product_out_of_stock)
                    } else {
                        stringResource(R.string.product_buy_now)
                    },
                    style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                    color = if (canPurchase) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
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
