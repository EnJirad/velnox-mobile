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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.velnox.app.R
import com.velnox.app.VelnoxApplication
import com.velnox.app.core.session.LocalSession
import com.velnox.app.core.catalog.CatalogError
import com.velnox.app.core.catalog.CatalogProduct
import com.velnox.app.core.catalog.CatalogQuery
import com.velnox.app.core.catalog.CatalogResult
import com.velnox.app.core.catalog.DescriptionBlock
import com.velnox.app.core.catalog.PriceFormat
import com.velnox.app.core.catalog.ProductDetail
import com.velnox.app.core.catalog.ProductReviewsData
import com.velnox.app.core.catalog.ProductVariant
import com.velnox.app.feature.home.ProductCard
import com.velnox.app.feature.home.ProductThumbnail
import com.velnox.app.feature.wishlist.WishlistItem
import com.velnox.app.feature.wishlist.WishlistState
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
 * - Isolated thumbnail nested scroll connection preventing parent page movement
 * - Horizontal swipe gestures and tap-to-select for gallery thumbnails
 * - Full-screen image zoom viewer (pinch-to-zoom, pan, double-tap to reset)
 * - Real backend prices, compare-at prices, discount badges, and stock levels
 * - Variant selection and Product Options Bottom Sheet popup
 * - Quantity selector validated against available stock
 * - Seller card with navigation to Seller Shop screen
 * - Three-tab layout: Recommendations (real catalog query), Details (specs & description), Reviews (real reviews API)
 * - Integrated Add to Cart and Buy Now purchase actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productId: String,
    onBack: () -> Unit,
    onAddToCart: ((ProductDetail, ProductVariant?, Int, String?) -> Unit)? = null,
    onBuyNow: ((ProductDetail, ProductVariant?, Int, String?) -> Unit)? = null,
    onOpenCart: (() -> Unit)? = null,
    onOpenShop: ((String) -> Unit)? = null,
    onOpenProduct: ((String) -> Unit)? = null,
    onContactSeller: ((String, String?) -> Unit)? = null,
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
    var showOptionsSheet by remember(productId) { mutableStateOf(false) }
    var optionsPopupIntent by remember(productId) {
        mutableStateOf(ProductSelectionIntent.SELECT_OPTIONS)
    }
    var selectedTab by remember(productId) { mutableIntStateOf(0) } // 0 = Recs, 1 = Details, 2 = Reviews

    // Recommendations and Reviews state
    var recProducts by remember(productId) { mutableStateOf<List<CatalogProduct>>(emptyList()) }
    var recLoading by remember(productId) { mutableStateOf(false) }
    var reviewsData by remember(productId) { mutableStateOf<ProductReviewsData?>(null) }
    var reviewsLoading by remember(productId) { mutableStateOf(false) }

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

    // Load recommendations and reviews when product loads
    LaunchedEffect(productId, state) {
        val detail = (state as? ProductState.Content)?.product
        if (detail != null) {
            // Load recommendations
            recLoading = true
            val recResult = catalog.products(
                CatalogQuery(
                    category = detail.categorySlug,
                    limit = 8,
                ),
            )
            if (recResult is CatalogResult.Ok) {
                recProducts = recResult.value.items.filter { it.id != productId }.take(6)
            }
            recLoading = false

            // Load reviews
            reviewsLoading = true
            val revResult = catalog.reviews(productId)
            if (revResult is CatalogResult.Ok) {
                reviewsData = revResult.value
            }
            reviewsLoading = false
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
                val isFav = WishlistState.isFavorite(productId)
                val detail = (state as? ProductState.Content)?.product
                val session = LocalSession.current
                val customerRepo = (LocalContext.current.applicationContext as? VelnoxApplication)?.customer
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .velnoxClickable(role = Role.Button) {
                            if (detail != null) {
                                val wishItem = WishlistItem(
                                    productId = detail.id,
                                    name = detail.name,
                                    price = detail.displayPrice,
                                    currency = detail.currency,
                                    imageUrl = detail.images.firstOrNull()?.url,
                                    shopName = detail.shopName,
                                    inStock = detail.isInStock ?: true,
                                    rating = detail.rating,
                                )
                                if (session.state.isAuthenticated && customerRepo != null) {
                                    scope.launch { WishlistState.toggleRemote(customerRepo, wishItem) }
                                } else {
                                    WishlistState.toggle(wishItem)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isFav) VelnoxIcons.HeartFill else VelnoxIcons.Heart,
                        contentDescription = stringResource(R.string.profile_wishlist),
                        tint = if (isFav) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                val currentProduct = (state as? ProductState.Content)?.product
                if (currentProduct != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .velnoxClickable(role = Role.Button) {
                                val shareText = "${currentProduct.name}\n${currentProduct.shopName.orEmpty()}\nhttps://velnox.app/product/${currentProduct.id}"
                                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(sendIntent, null))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = VelnoxIcons.Share2,
                            contentDescription = stringResource(R.string.common_share),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
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
                var detailZoomImageUrl by remember { mutableStateOf<String?>(null) }

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

                        // 2. Product Title & Trust row (Rating, Review count, Sold count)
                        item(key = "title_section", contentType = "title") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = PageGutter),
                            ) {
                                // Product Title
                                Text(
                                    text = product.name,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onBackground,
                                )

                                // Category & Trust row (Rating, Reviews, Sold count)
                                val hasRating = product.rating != null && product.rating > 0.0
                                val hasReviews = product.reviewCount > 0
                                val hasSoldCount = product.soldCount > 0
                                val hasCategory = !product.categorySlug.isNullOrBlank()

                                if (hasRating || hasReviews || hasSoldCount || hasCategory) {
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

                                        if (hasRating) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            ) {
                                                Icon(
                                                    imageVector = VelnoxIcons.Star,
                                                    contentDescription = null,
                                                    tint = Color(0xFFF59E0B),
                                                    modifier = Modifier.size(13.dp),
                                                )
                                                Text(
                                                    text = String.format(java.util.Locale.US, "%.1f", product.rating),
                                                    style = TextXs.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                if (hasReviews) {
                                                    Text(
                                                        text = "(${product.reviewCount})",
                                                        style = Text2xs,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        } else if (hasReviews) {
                                            Text(
                                                text = stringResource(R.string.product_reviews_count, product.reviewCount),
                                                style = Text2xs,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        if ((hasRating || hasReviews) && hasSoldCount) {
                                            Text(
                                                text = "•",
                                                style = Text2xs,
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        }

                                        if (hasSoldCount) {
                                            Text(
                                                text = stringResource(R.string.product_sold, product.soldCount),
                                                style = Text2xs,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Pricing, Discount badge, and Stock overview
                        item(key = "pricing_overview", contentType = "overview") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = PageGutter)
                                    .padding(top = VelnoxSpacing.md),
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
                                            color = Color(0xFFE11D48),
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
                                                .background(Color(0xFFE11D48).copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = "-$effectiveDiscount%",
                                                style = Text2xs.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFFE11D48),
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
                            }
                        }

                        // 4. Product Options Selection Entry Point (Visual variant card, replaces horizontal chips)
                        if (product.optionGroups.isNotEmpty() || product.variants.size > 1) {
                            item(key = "options_entry", contentType = "options") {
                                val selectedOptionThumbnail = remember(selectedVariant, product.optionGroups, product.images) {
                                    val optionImg = selectedVariant?.options?.values?.firstNotNullOfOrNull { optVal ->
                                        product.optionGroups.flatMap { it.values }.firstOrNull { it.value == optVal && !it.imageUrl.isNullOrBlank() }?.imageUrl
                                    }
                                    optionImg ?: product.images.firstOrNull()?.url
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = PageGutter)
                                        .padding(top = VelnoxSpacing.md),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(SquircleLg)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
                                            .velnoxClickable(role = Role.Button) {
                                                optionsPopupIntent = ProductSelectionIntent.SELECT_OPTIONS
                                                showOptionsSheet = true
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        // Visual variant thumbnail
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(SquircleSm)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, SquircleSm),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (!selectedOptionThumbnail.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = selectedOptionThumbnail,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = VelnoxIcons.ProductVariants,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        }

                                        // Summary and title
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Icon(
                                                    imageVector = VelnoxIcons.ProductVariants,
                                                    contentDescription = null,
                                                    tint = VelnoxBrand,
                                                    modifier = Modifier.size(15.dp),
                                                )
                                                Text(
                                                    text = stringResource(R.string.product_select_options),
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            val summaryText = selectedVariant?.title
                                                ?: product.optionGroups.joinToString(", ") { it.name }
                                            if (summaryText.isNotBlank()) {
                                                Text(
                                                    text = stringResource(R.string.product_options_selected, summaryText),
                                                    style = TextXs.copy(fontWeight = FontWeight.Medium),
                                                    color = VelnoxBrand,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            } else {
                                                Text(
                                                    text = stringResource(R.string.product_options_none_selected),
                                                    style = TextXs,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = VelnoxIcons.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }

                        // 6. Seller Information Card with Visit Shop button
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
                                    val shopTarget = product.shopSlug ?: product.shopId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(SquircleLg)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
                                            .velnoxClickable(
                                                role = Role.Button,
                                                enabled = !shopTarget.isNullOrBlank() && onOpenShop != null,
                                            ) {
                                                shopTarget?.let { onOpenShop?.invoke(it) }
                                            }
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
                                        }
                                        if (!shopTarget.isNullOrBlank() && onOpenShop != null) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(SquircleXs)
                                                    .border(1.dp, VelnoxBrand, SquircleXs)
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.product_shop_visit),
                                                    style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                                                    color = VelnoxBrand,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 7. Three-Tab Section: Recommendations, Details, Reviews
                        item(key = "tabs_header", contentType = "tabs_header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = PageGutter)
                                    .padding(top = VelnoxSpacing.xl, bottom = VelnoxSpacing.sm),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(Squircle)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    val tabs = listOf(
                                        stringResource(R.string.product_tab_recommendations),
                                        stringResource(R.string.product_tab_details),
                                        stringResource(R.string.product_tab_reviews),
                                    )
                                    tabs.forEachIndexed { index, tabTitle ->
                                        val isSelected = selectedTab == index
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(SquircleSm)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.surface
                                                    else Color.Transparent,
                                                )
                                                .velnoxClickable(role = Role.Tab) {
                                                    selectedTab = index
                                                }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = tabTitle,
                                                style = TextXs.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                ),
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Tab 0: Recommendations
                        if (selectedTab == 0) {
                            if (recLoading) {
                                item(key = "rec_loading") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            } else if (recProducts.isEmpty()) {
                                item(key = "rec_empty") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = PageGutter, vertical = 24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.product_recommendations_empty),
                                            style = TextXs,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                val pairs = recProducts.chunked(2)
                                itemsIndexed(items = pairs, key = { index, _ -> "rec_row_$index" }) { _, rowPair ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = PageGutter, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        rowPair.forEach { recProd ->
                                            ProductCard(
                                                product = recProd,
                                                modifier = Modifier.weight(1f),
                                                onClick = { onOpenProduct?.invoke(recProd.id) },
                                            )
                                        }
                                        if (rowPair.size == 1) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        // Tab 1: Product Details (Specifications & Description)
                        if (selectedTab == 1) {
                            item(key = "details_content") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = PageGutter, vertical = VelnoxSpacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(VelnoxSpacing.md),
                                ) {
                                    // Specifications Table
                                    if (product.attributes.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(SquircleLg)
                                                .background(MaterialTheme.colorScheme.surface)
                                                .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
                                                .padding(VelnoxSpacing.md),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.product_specifications_title),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onBackground,
                                            )
                                            Spacer(Modifier.height(VelnoxSpacing.sm))
                                            product.attributes.forEachIndexed { index, attr ->
                                                if (index > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(1.dp)
                                                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    Text(
                                                        text = attr.name,
                                                        style = TextXs,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.weight(0.4f),
                                                    )
                                                    Text(
                                                        text = attr.value,
                                                        style = TextXs.copy(fontWeight = FontWeight.Medium),
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.weight(0.6f),
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 2. Seller-uploaded Description Blocks (Text & Images preserved in seller's order)
                                    if (product.descriptionBlocks.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
                                        ) {
                                            product.descriptionBlocks.forEach { block ->
                                                when (block) {
                                                    is DescriptionBlock.Text -> {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(SquircleLg)
                                                                .background(MaterialTheme.colorScheme.surface)
                                                                .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
                                                                .padding(VelnoxSpacing.md),
                                                        ) {
                                                            Text(
                                                                text = block.text,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                            )
                                                        }
                                                    }
                                                    is DescriptionBlock.Image -> {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(SquircleLg)
                                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                                .velnoxClickable(role = Role.Image) {
                                                                    detailZoomImageUrl = block.imageUrl
                                                                },
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            AsyncImage(
                                                                model = block.imageUrl,
                                                                contentDescription = null,
                                                                contentScale = ContentScale.FillWidth,
                                                                modifier = Modifier.fillMaxWidth(),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else if (!product.description.isNullOrBlank()) {
                                        // Fallback to plain description if no structured description blocks
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(SquircleLg)
                                                .background(MaterialTheme.colorScheme.surface)
                                                .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
                                                .padding(VelnoxSpacing.md),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.product_description_title),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onBackground,
                                            )
                                            Spacer(Modifier.height(VelnoxSpacing.xs))
                                            Text(
                                                text = product.description,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }

                                    // 3. Additional Seller Detail Images (detailImages not already in descriptionBlocks or main gallery)
                                    val blockImageUrls = remember(product.descriptionBlocks) {
                                        product.descriptionBlocks.filterIsInstance<DescriptionBlock.Image>().map { it.imageUrl }.toSet()
                                    }
                                    val galleryImageUrls = remember(product.images) {
                                        product.images.map { it.url }.toSet()
                                    }
                                    val extraDetailImages = remember(product.detailImages, blockImageUrls, galleryImageUrls) {
                                        product.detailImages.filter { it !in blockImageUrls && it !in galleryImageUrls }
                                    }
                                    if (extraDetailImages.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
                                        ) {
                                            extraDetailImages.forEach { imgUrl ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(SquircleLg)
                                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                                        .velnoxClickable(role = Role.Image) {
                                                            detailZoomImageUrl = imgUrl
                                                        },
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    AsyncImage(
                                                        model = imgUrl,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.FillWidth,
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Tab 2: Reviews
                        if (selectedTab == 2) {
                            if (reviewsLoading) {
                                item(key = "rev_loading") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            } else {
                                val revData = reviewsData
                                if (revData == null || revData.items.isEmpty()) {
                                    item(key = "rev_empty") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = PageGutter, vertical = 24.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = stringResource(R.string.product_reviews_empty),
                                                style = TextXs,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                } else {
                                    // Aggregate Rating Card
                                    item(key = "rev_summary") {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = PageGutter, vertical = VelnoxSpacing.xs),
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(SquircleLg)
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
                                                    .padding(VelnoxSpacing.md),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = "%.1f".format(revData.avgRating),
                                                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onBackground,
                                                    )
                                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        repeat(5) { starIdx ->
                                                            val isFilled = starIdx < revData.avgRating.roundToInt()
                                                            Icon(
                                                                imageVector = VelnoxIcons.Star,
                                                                contentDescription = null,
                                                                tint = if (isFilled) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline,
                                                                modifier = Modifier.size(14.dp),
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        text = stringResource(R.string.product_reviews_count, revData.total),
                                                        style = Text2xs,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }

                                                // Distribution bars
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                                ) {
                                                    (5 downTo 1).forEach { stars ->
                                                        val count = revData.distribution[stars.toString()] ?: 0
                                                        val fraction = if (revData.total > 0) count.toFloat() / revData.total else 0f
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        ) {
                                                            Text(
                                                                text = "$stars★",
                                                                style = Text2xs,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                            Box(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .height(6.dp)
                                                                    .clip(CircleShape)
                                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxHeight()
                                                                        .fillMaxWidth(fraction)
                                                                        .clip(CircleShape)
                                                                        .background(Color(0xFFF59E0B)),
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Reviews list
                                    itemsIndexed(items = revData.items, key = { idx, item -> item.id.ifBlank { "rev_$idx" } }) { _, review ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = PageGutter, vertical = 4.dp)
                                                .clip(SquircleLg)
                                                .background(MaterialTheme.colorScheme.surface)
                                                .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
                                                .padding(VelnoxSpacing.md),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = review.authorName.orEmpty().ifBlank { "User" },
                                                    style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    repeat(5) { starIdx ->
                                                        Icon(
                                                            imageVector = VelnoxIcons.Star,
                                                            contentDescription = null,
                                                            tint = if (starIdx < review.rating) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline,
                                                            modifier = Modifier.size(12.dp),
                                                        )
                                                    }
                                                }
                                            }

                                            review.comment?.takeIf { it.isNotBlank() }?.let { comment ->
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    text = comment,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }

                                            if (review.images.isNotEmpty()) {
                                                Spacer(Modifier.height(8.dp))
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    items(review.images) { imgUrl ->
                                                        AsyncImage(
                                                            model = imgUrl,
                                                            contentDescription = null,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier
                                                                .size(48.dp)
                                                                .clip(SquircleXs)
                                                                .background(MaterialTheme.colorScheme.surfaceVariant),
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

                    // Added feedback banner
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp, start = PageGutter, end = PageGutter),
                    ) {
                        AnimatedVisibility(
                            visible = addedFeedbackMessage != null,
                            enter = fadeIn() + slideInVertically { -it },
                            exit = fadeOut() + slideOutVertically { -it },
                        ) {
                            val msg = addedFeedbackMessage
                            if (msg != null) {
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
                }

                // 8. Large Product Options Bottom Sheet Popup (Context-Aware)
                if (showOptionsSheet) {
                    ProductOptionsBottomSheet(
                        product = product,
                        initialVariantId = selectedVariantId,
                        initialQuantity = quantity,
                        intent = optionsPopupIntent,
                        onDismissRequest = { showOptionsSheet = false },
                        onAddToCart = { variant, newQuantity, variantImageUrl ->
                            selectedVariantId = variant?.id
                            quantity = newQuantity
                            showOptionsSheet = false
                            if (canPurchase) {
                                onAddToCart?.invoke(product, variant, newQuantity, variantImageUrl)
                                addedFeedbackMessage = product.name
                                scope.launch {
                                    delay(2800)
                                    addedFeedbackMessage = null
                                }
                            }
                        },
                        onBuyNow = { variant, newQuantity, variantImageUrl ->
                            selectedVariantId = variant?.id
                            quantity = newQuantity
                            showOptionsSheet = false
                            if (canPurchase) {
                                onBuyNow?.invoke(product, variant, newQuantity, variantImageUrl)
                            }
                        },
                        onConfirmSelection = { variant, newQuantity ->
                            selectedVariantId = variant?.id
                            quantity = newQuantity
                            showOptionsSheet = false
                        },
                    )
                }

                // 9. Bottom Action Area (Context-Aware Add to Cart & Buy Now)
                val resolvedDirectVariantImage = selectedVariant?.options?.values?.firstNotNullOfOrNull { optVal ->
                    product.optionGroups.flatMap { it.values }.firstOrNull { it.value == optVal && !it.imageUrl.isNullOrBlank() }?.imageUrl
                } ?: product.images.firstOrNull()?.url

                BottomPurchaseArea(
                    canPurchase = canPurchase,
                    isOutOfStock = isOutOfStock,
                    onAddToCart = {
                        if (canPurchase) {
                            if (product.variants.size > 1 || product.optionGroups.isNotEmpty()) {
                                optionsPopupIntent = ProductSelectionIntent.ADD_TO_CART
                                showOptionsSheet = true
                            } else {
                                onAddToCart?.invoke(product, selectedVariant, quantity, resolvedDirectVariantImage)
                                addedFeedbackMessage = product.name
                                scope.launch {
                                    delay(2800)
                                    addedFeedbackMessage = null
                                }
                            }
                        }
                    },
                    onBuyNow = {
                        if (canPurchase) {
                            if (product.variants.size > 1 || product.optionGroups.isNotEmpty()) {
                                optionsPopupIntent = ProductSelectionIntent.BUY_NOW
                                showOptionsSheet = true
                            } else {
                                onBuyNow?.invoke(product, selectedVariant, quantity, resolvedDirectVariantImage)
                            }
                        }
                    },
                    onChatSeller = if (onContactSeller != null && (!product.shopId.isNullOrBlank() || !product.shopSlug.isNullOrBlank())) {
                        {
                            val target = product.shopId?.takeIf { it.isNotBlank() } ?: product.shopSlug.orEmpty()
                            if (target.isNotBlank()) {
                                onContactSeller.invoke(target, product.id)
                            }
                        }
                    } else null,
                )

                // Full-screen zoom viewer for seller detail images
                detailZoomImageUrl?.let { zoomUrl ->
                    FullScreenImageDialog(
                        urls = listOf(zoomUrl),
                        initialIndex = 0,
                        onDismiss = { detailZoomImageUrl = null },
                    )
                }
            }
        }
    }
}

/**
 * Responsive Product Gallery:
 * - Large main image on the LEFT
 * - Vertical thumbnail strip on the RIGHT with isolated nested scroll connection
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

    // NestedScrollConnection consuming unconsumed vertical scroll and fling
    // to prevent the thumbnail strip from scrolling the parent Product Detail page.
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Trap any remaining scroll delta so it never bubbles up to the parent LazyColumn
                return available
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                // Trap any remaining fling velocity so the parent page doesn't fling
                return available
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter),
    ) {
        val totalWidth = maxWidth
        val thumbWidth = if (totalWidth < 340.dp) 52.dp else 64.dp
        val galleryHeight = (totalWidth * 1.05f).coerceIn(340.dp, 440.dp)

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
                    contentScale = ContentScale.Fit,
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
                            contentScale = ContentScale.Fit,
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

                // Vertical thumbnail strip on the RIGHT with nested scroll isolated
                LazyColumn(
                    state = thumbListState,
                    modifier = Modifier
                        .width(thumbWidth)
                        .fillMaxHeight()
                        .nestedScroll(nestedScrollConnection),
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

            // Top Bar with back/close button and image counter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .velnoxClickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VelnoxIcons.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }

                if (urls.size > 1) {
                    Box(
                        modifier = Modifier
                            .clip(SquircleXs)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "${currentIndex + 1} / ${urls.size}",
                            style = TextXs.copy(fontWeight = FontWeight.SemiBold),
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
    onChatSeller: (() -> Unit)? = null,
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
            if (onChatSeller != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(Squircle)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, Squircle)
                        .velnoxClickable(role = Role.Button, onClick = onChatSeller),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VelnoxIcons.MessageCircle,
                        contentDescription = stringResource(R.string.product_chat_seller),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

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
