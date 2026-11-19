@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.velnox.app.feature.home

internal const val ALL_CATEGORY = "all"

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.ui.components.BrandBadge
import com.velnox.app.ui.components.BrandMark
import com.velnox.app.ui.components.ContentMaxWidth
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.SectionHeader
import com.velnox.app.ui.components.TrustRow
import com.velnox.app.ui.components.VelnoxSpacing
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Circle
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.SquircleXs
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxDestructive
import com.velnox.app.ui.theme.VelnoxTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import coil.compose.AsyncImage
import com.velnox.app.VelnoxApplication
import com.velnox.app.core.catalog.CatalogCategory
import com.velnox.app.core.catalog.CatalogPage
import com.velnox.app.core.catalog.CatalogProduct
import com.velnox.app.core.catalog.CatalogQuery
import com.velnox.app.core.catalog.CatalogResult
import com.velnox.app.core.catalog.PriceFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long typing must pause before the query is sent to the server. */
private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * Recommended section model for the Home screen.
 */
data class RecommendedSection(
    val titleRes: Int,
    val products: List<CatalogProduct>,
    val sortKey: String,
)

/**
 * Velnox Home — a fixed toolbar over a curated feed of recommended products.
 *
 * Shows: seasonal banner → recommended category sections (ยอดฮิต, สินค้าขายดี, เป็นที่ต้องการ, etc.)
 * → trust indicators → quick link to full Market.
 * The catalogue comes from the deployed Velnox API.
 */
@Composable
fun HomeScreen(
    onOpenCategoryHub: () -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalog = (LocalContext.current.applicationContext as VelnoxApplication).catalog
    val isThai = LocalConfiguration.current.locales[0].language == "th"

    var query by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf<List<CatalogCategory>>(emptyList()) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Recommended category sections state
    var recommendedSections by remember { mutableStateOf<List<RecommendedSection>>(emptyList()) }
    var sectionsLoading by remember { mutableStateOf(true) }
    var sectionsError by remember { mutableStateOf(false) }

    // Typing must not fire one request per keystroke.
    LaunchedEffect(query) {
        if (debouncedQuery == query) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = query
    }

    // Load categories for the category hub
    LaunchedEffect(debouncedQuery, reloadKey) {
        if (categories.isEmpty()) {
            val loaded = catalog.categories()
            if (loaded is CatalogResult.Ok) categories = loaded.value
        }
    }

    // Load recommended sections
    LaunchedEffect(reloadKey) {
        sectionsLoading = true
        sectionsError = false
        scope.launch {
            val popular = catalog.products(
                CatalogQuery(
                    search = null,
                    category = null,
                    sort = "popular",
                    limit = 6,
                ),
            )
            val bestSelling = catalog.products(
                CatalogQuery(
                    search = null,
                    category = null,
                    sort = "best_selling",
                    limit = 6,
                ),
            )
            val mostWanted = catalog.products(
                CatalogQuery(
                    search = null,
                    category = null,
                    sort = "most_wanted",
                    limit = 6,
                ),
            )
            val trending = catalog.products(
                CatalogQuery(
                    search = null,
                    category = null,
                    sort = "trending",
                    limit = 6,
                ),
            )
            val newArrivals = catalog.products(
                CatalogQuery(
                    search = null,
                    category = null,
                    sort = "newest",
                    limit = 6,
                ),
            )
            val recommended = catalog.products(
                CatalogQuery(
                    search = null,
                    category = null,
                    sort = "recommended",
                    limit = 6,
                ),
            )

            val results = listOf(
                popular to R.string.market_category_popular,
                bestSelling to R.string.market_category_best_selling,
                mostWanted to R.string.market_category_most_wanted,
                trending to R.string.market_category_trending,
                newArrivals to R.string.market_category_new_arrivals,
                recommended to R.string.market_category_recommended,
            )

            val sections = results.mapNotNull { (result, titleRes) ->
                when (result) {
                    is CatalogResult.Ok -> {
                        if (result.value.items.isNotEmpty()) {
                            RecommendedSection(
                                titleRes = titleRes,
                                products = result.value.items,
                                sortKey = titleRes.toString(),
                            )
                        } else null
                    }
                    else -> null
                }
            }

            recommendedSections = sections
            sectionsLoading = false
        }
    }

    fun retry() {
        scope.launch {
            catalog.invalidate()
            categories = emptyList()
            reloadKey++
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HomeToolbar(
            query = query,
            onQueryChange = { query = it },
            onClearQuery = { query = "" },
            notifications = HomeNotifications.items,
            onOpenMessages = onOpenMessages,
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentPadding = PaddingValues(bottom = VelnoxSpacing.xxl),
            ) {
                if (debouncedQuery.isBlank()) {
                    item(key = "seasonal", contentType = "seasonal") {
                        SeasonalBanner(
                            onClick = { onOpenCategoryHub() },
                            modifier = Modifier.padding(
                                start = PageGutter,
                                top = VelnoxSpacing.md,
                                end = PageGutter,
                                bottom = VelnoxSpacing.md,
                            ),
                        )
                    }

                    // Recommended sections
                    items(recommendedSections, key = { it.titleRes }) { section ->
                        RecommendedSectionUI(
                            section = section,
                            onOpenProduct = onOpenProduct,
                            onSeeAll = { onOpenCategoryHub() },
                            isThai = isThai,
                        )
                    }

                    // Trust indicators
                    item(key = "trust", contentType = "trust") {
                        Box(
                            Modifier.padding(
                                horizontal = PageGutter,
                                vertical = VelnoxSpacing.md,
                            ),
                        ) {
                            TrustRow()
                        }
                    }

                    // Quick access to Market
                    item(key = "market-link", contentType = "market-link") {
                        MarketLinkCard(
                            onClick = { onOpenCategoryHub() },
                        )
                    }

                } else {
                    // Search results - show hint to go to Market for full search
                    item(key = "search-header", contentType = "section") {
                        Text(
                            text = stringResource(R.string.home_search_results_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(
                                start = PageGutter,
                                top = VelnoxSpacing.lg,
                                end = PageGutter,
                                bottom = VelnoxSpacing.sm,
                            ),
                        )
                    }
                    item(key = "search-hint", contentType = "search-hint") {
                        SearchHintCard(
                            query = debouncedQuery,
                            onClear = { query = ""; debouncedQuery = "" },
                        )
                    }
                }
            }
        }
    }
}

/**
 * UI for a recommended section with horizontal product rail.
 */
@Composable
private fun RecommendedSectionUI(
    section: RecommendedSection,
    onOpenProduct: (String) -> Unit,
    onSeeAll: () -> Unit,
    isThai: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(section.titleRes),
            action = stringResource(R.string.market_see_all),
            onAction = onSeeAll,
            modifier = Modifier.padding(horizontal = PageGutter),
        )
        Spacer(Modifier.height(VelnoxSpacing.md))
        LazyRow(
            contentPadding = PaddingValues(horizontal = PageGutter),
            horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
        ) {
            items(section.products, key = { it.id }, contentType = { "recommendedProduct" }) { product ->
                ProductCard(
                    product = product,
                    modifier = Modifier.width(152.dp),
                    onClick = { onOpenProduct(product.id) },
                )
            }
        }
        Spacer(Modifier.height(VelnoxSpacing.lg))
    }
}

/**
 * Card linking to the full Market tab.
 */
@Composable
private fun MarketLinkCard(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter, vertical = VelnoxSpacing.md)
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.primary)
            .velnoxClickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(VelnoxSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(Circle)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VelnoxIcons.ShoppingBag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(VelnoxSpacing.md))
            Column {
                Text(
                    text = stringResource(R.string.market_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = stringResource(R.string.market_categories),
                    style = TextXs,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.74f),
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = VelnoxIcons.ArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Hint card when searching from Home - directs to Market for full search.
 */
@Composable
private fun SearchHintCard(
    query: String,
    onClear: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter, vertical = VelnoxSpacing.md)
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(VelnoxSpacing.lg),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = VelnoxIcons.Search,
                    contentDescription = null,
                    tint = VelnoxBrand,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(VelnoxSpacing.sm))
                Text(
                    text = stringResource(R.string.home_search_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(Circle)
                            .velnoxClickable(role = Role.Button, onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = VelnoxIcons.Close,
                            contentDescription = stringResource(R.string.home_search_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(VelnoxSpacing.md))
            Text(
                text = stringResource(R.string.market_search_placeholder),
                style = TextXs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- seasonal

@Composable
private fun SeasonalBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.primary),
    ) {
        val onPrimary = MaterialTheme.colorScheme.onPrimary
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = VelnoxBrand.copy(alpha = 0.22f),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.92f, size.height * 0.10f),
            )
            drawCircle(
                color = onPrimary.copy(alpha = 0.08f),
                radius = size.minDimension * 0.36f,
                center = Offset(size.width * 0.10f, size.height * 0.95f),
            )
        }
        Column(
            modifier = Modifier.padding(VelnoxSpacing.xl),
        ) {
            BrandBadge(text = stringResource(R.string.home_hero_badge))
            Spacer(Modifier.height(VelnoxSpacing.md))
            Text(
                text = stringResource(R.string.home_hero_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.widthIn(max = 240.dp),
            )
            Spacer(Modifier.height(VelnoxSpacing.xs))
            Text(
                text = stringResource(R.string.home_hero_body),
                style = TextXs,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.74f),
                modifier = Modifier.widthIn(max = 260.dp),
            )
            Spacer(Modifier.height(VelnoxSpacing.lg))
            Row(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(SquircleSm)
                    .background(MaterialTheme.colorScheme.secondary)
                    .velnoxClickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = VelnoxSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_hero_cta),
                    style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSecondary,
                )
                Icon(
                    imageVector = VelnoxIcons.ArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier
                        .padding(start = VelnoxSpacing.xs)
                        .size(14.dp),
                )
            }
        }
    }
}

// ----------------------------------------------------------------- featured

// --------------------------------------------------------------- featured

@Composable
private fun FeaturedSection(
    products: List<CatalogProduct>,
    onOpenProduct: (String) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.home_featured_title),
            action = stringResource(R.string.home_see_all),
            onAction = onSeeAll,
            modifier = Modifier.padding(horizontal = PageGutter),
        )
        Spacer(Modifier.height(VelnoxSpacing.md))
        LazyRow(
            contentPadding = PaddingValues(horizontal = PageGutter),
            horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
        ) {
            items(products, key = { it.id }, contentType = { "featuredProduct" }) { product ->
                ProductCard(
                    product = product,
                    modifier = Modifier.width(152.dp),
                    onClick = { onOpenProduct(product.id) },
                )
            }
        }
    }
}

// --------------------------------------------------------------- product grid

private fun LazyListScope.productRows(
    products: List<CatalogProduct>,
    columns: Int,
    onOpenProduct: (String) -> Unit,
) {
    items(
        items = products.chunked(columns),
        key = { row -> row.joinToString("|") { it.id } },
        contentType = { "productRow" },
    ) { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter),
            horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
        ) {
            row.forEach { product ->
                ProductCard(
                    product = product,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenProduct(product.id) },
                )
            }
            // Keeps the last row aligned with the grid above it.
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(VelnoxSpacing.sm))
    }
}

/**
 * A real catalogue product: the seller's own image, name, shop and price.
 *
 * Nothing is substituted when a field is missing. A product without an image
 * renders the neutral tile, a product without a price renders no price line, and
 * only a known out-of-stock flag adds the badge.
 */
@Composable
internal fun ProductCard(
    product: CatalogProduct,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = modifier
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
            .velnoxClickable(role = Role.Button, onClick = onClick)
            .padding(VelnoxSpacing.sm),
    ) {
        ProductThumbnail(
            url = product.imageUrl,
            name = product.name,
            outOfStock = product.inStock == false,
            modifier = Modifier.aspectRatio(1f),
        )
        Spacer(Modifier.height(VelnoxSpacing.sm))
        Text(
            text = product.name,
            style = TextXs.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        product.shopName?.let { shop ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = shop,
                style = Text2xs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PriceFormat.format(product.price, product.currency, locale)?.let { price ->
                Text(
                    text = price,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            if (product.inStock == false) {
                Text(
                    text = stringResource(R.string.product_out_of_stock),
                    style = Text2xs.copy(fontWeight = FontWeight.Medium),
                    color = VelnoxDestructive,
                )
            }
        }
    }
}

@Composable
private fun ProductThumbnail(
    url: String?,
    name: String,
    outOfStock: Boolean,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (url != null && url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = {
                    ProductArtFallback(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    )
                },
                error = {
                    ProductArtFallback(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    )
                },
            )
        } else {
            ProductArtFallback(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )
        }
        if (outOfStock) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.44f))
                    .clip(Squircle),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.product_out_of_stock),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = Color.White,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- toolbar

@Composable
private fun HomeToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    notifications: List<HomeNotification>,
    onOpenMessages: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = VelnoxSpacing.md, vertical = VelnoxSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(size = 40)
            Spacer(Modifier.width(VelnoxSpacing.sm))
            HomeSearchField(
                query = query,
                onQueryChange = onQueryChange,
                onClearQuery = onClearQuery,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(VelnoxSpacing.sm))
            NotificationBell(notifications = notifications)
            Spacer(Modifier.width(VelnoxSpacing.sm))
            MessageButton(onOpenMessages = onOpenMessages)
        }
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun HomeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) VelnoxBrand else MaterialTheme.colorScheme.outline

    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, borderColor, Squircle)
            .padding(start = VelnoxSpacing.md, end = VelnoxSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = VelnoxIcons.Search,
            contentDescription = null,
            tint = VelnoxBrand,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(VelnoxSpacing.sm))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_search_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(VelnoxBrand),
                interactionSource = interactionSource,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = VelnoxSpacing.md),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(Circle)
                    .velnoxClickable(role = Role.Button, onClick = onClearQuery),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VelnoxIcons.Close,
                    contentDescription = stringResource(R.string.home_search_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun NotificationBell(notifications: List<HomeNotification>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val hasUnread = notifications.any { it.unread }

    Box {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(Circle)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .velnoxClickable(role = Role.Button, onClick = { expanded = true }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = VelnoxIcons.Bell,
                contentDescription = stringResource(R.string.home_notifications),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(19.dp),
            )
            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 9.dp, end = 9.dp)
                        .size(7.dp)
                        .clip(Circle)
                        .background(VelnoxBrand),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 280.dp, max = 340.dp),
            shape = SquircleLg,
            containerColor = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(VelnoxSpacing.lg)) {
                Text(
                    text = stringResource(R.string.home_notifications),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(VelnoxSpacing.md))
                if (notifications.isEmpty()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(SquircleSm)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = VelnoxIcons.Bell,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        Spacer(Modifier.width(VelnoxSpacing.md))
                        Column {
                            Text(
                                text = stringResource(R.string.notifications_empty_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(VelnoxSpacing.xs))
                            Text(
                                text = stringResource(R.string.notifications_empty_body),
                                style = TextXs,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    notifications.forEach { item ->
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = item.body,
                            style = TextXs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        item.time?.let { time ->
                            Text(
                                text = time,
                                style = Text2xs,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(VelnoxSpacing.md))
                    }
                }
            }
        }
    }
}

/** Messages icon button in the home toolbar header. */
@Composable
private fun MessageButton(onOpenMessages: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(Circle)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .velnoxClickable(role = Role.Button, onClick = onOpenMessages),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = VelnoxIcons.MessageCircle,
            contentDescription = stringResource(R.string.nav_messages),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(19.dp),
        )
    }
}

// --------------------------------------------------------------- loading / error

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun LoadMoreRow(loading: Boolean, onLoadMore: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = VelnoxSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Text(
                text = stringResource(R.string.catalog_load_more),
                style = TextXs.copy(fontWeight = FontWeight.Medium),
                color = VelnoxBrand,
                modifier = Modifier
                    .clip(Squircle)
                    .velnoxClickable(role = Role.Button, onClick = onLoadMore)
                    .padding(horizontal = VelnoxSpacing.lg, vertical = VelnoxSpacing.sm),
            )
        }
    }
}
