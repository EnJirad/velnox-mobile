@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.market

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetState
import androidx.compose.material3.ModalBottomSheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velnox.app.R
import com.velnox.app.core.catalog.CatalogCategory
import com.velnox.app.core.catalog.CatalogPage
import com.velnox.app.core.catalog.CatalogProduct
import com.velnox.app.core.catalog.CatalogQuery
import com.velnox.app.core.catalog.CatalogResult
import com.velnox.app.core.catalog.PriceFormat
import com.velnox.app.ui.components.BrandMark
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.SectionHeader
import com.velnox.app.ui.components.TrustRow
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
import com.velnox.app.ui.theme.VelnoxTheme
import com.velnox.app.VelnoxApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Velnox Market — full catalogue with search, filter, category navigation.
 * Public screen (no auth gate). Mirrors web /products page.
 */
@Composable
fun MarketScreen(
    onOpenProduct: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalog = (LocalContext.current.applicationContext as VelnoxApplication).catalog
    val isThai = LocalConfiguration.current.locales[0].language == "th"
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollState = rememberScrollState()

    // Search state
    var query by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    private const val SEARCH_DEBOUNCE_MS = 300L

    // Category state
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<CatalogCategory>>(emptyList()) }

    // Feed state
    var feed by remember { mutableStateOf<MarketFeed>(MarketFeed.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }

    // Filter state
    var sortOption by rememberSaveable { mutableStateOf(SortOption.Newest) }
    var priceMin by rememberSaveable { mutableStateOf("") }
    var priceMax by rememberSaveable { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Load categories once
    LaunchedEffect(Unit) {
        if (categories.isEmpty()) {
            val loaded = catalog.categories()
            if (loaded is CatalogResult.Ok) {
                categories = loaded.value
            }
        }
    }

    // Debounce search
    LaunchedEffect(query) {
        if (debouncedQuery == query) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = query
    }

    // Fetch products
    LaunchedEffect(debouncedQuery, selectedCategory, sortOption, reloadKey) {
        feed = MarketFeed.Loading
        if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)

        feed = catalog.products(
            CatalogQuery(
                search = debouncedQuery.takeIf { it.isNotBlank() },
                category = selectedCategory,
                offset = 0,
            ),
        ).toMarketFeed()
    }

    fun loadMore() {
        val current = feed as? MarketFeed.Content ?: return
        if (!current.hasMore || current.loadingMore) return
        feed = current.copy(loadingMore = true)
        scope.launch {
            val next = catalog.products(
                CatalogQuery(
                    search = debouncedQuery.takeIf { it.isNotBlank() },
                    category = selectedCategory,
                    offset = current.items.size,
                ),
            )
            feed = when (next) {
                is CatalogResult.Ok -> current.copy(
                    items = current.items + next.value.items,
                    hasMore = next.value.hasMore,
                    loadingMore = false,
                )
                is CatalogResult.Failed -> current.copy(loadingMore = false, hasMore = false)
            }
        }
    }

    fun clearFilters() {
        priceMin = ""
        priceMax = ""
        sortOption = SortOption.Newest
        showFilterSheet = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Top Bar with Emerald background
        MarketTopBar(
            query = query,
            onQueryChange = { query = it },
            onClearQuery = { query = "" },
            onFilterClick = { showFilterSheet = true },
            onSortClick = { showSortMenu = true },
            sortOption = sortOption,
            onSortSelect = { sortOption = it; showSortMenu = false },
        )

        // Sticky Category Row
        if (categories.isNotEmpty() && !query.isNotBlank()) {
            MarketCategoryRow(
                categories = categories,
                selectedCategory = selectedCategory,
                onSelectCategory = { selectedCategory = it },
                onOpenAllCategories = { onOpenCategory("all") },
                isThai = isThai,
            )
        }

        // Product Grid
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val columns = when {
                maxWidth < 400.dp -> 2
                maxWidth < 600.dp -> 3
                else -> 4
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = com.velnox.app.ui.components.ContentMaxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentPadding = PaddingValues(bottom = VelnoxSpacing.xxl),
            ) {
                when (val current = feed) {
                    MarketFeed.Loading -> {
                        item(key = "loading") {
                            LoadingRow()
                        }
                    }
                    MarketFeed.Error -> {
                        item(key = "error") {
                            EmptyState(
                                icon = VelnoxIcons.WifiOff,
                                title = stringResource(R.string.catalog_error_title),
                                body = stringResource(R.string.catalog_error_body),
                                actionLabel = stringResource(R.string.common_retry),
                                onAction = { retry() },
                            )
                        }
                    }
                    MarketFeed.Empty -> {
                        item(key = "empty") {
                            EmptyState(
                                icon = VelnoxIcons.Package,
                                title = if (query.isNotBlank()) {
                                    stringResource(R.string.home_search_no_results_title)
                                } else {
                                    stringResource(R.string.catalog_empty_title)
                                },
                                body = if (query.isNotBlank()) {
                                    stringResource(R.string.home_search_no_results_body)
                                } else {
                                    stringResource(R.string.catalog_empty_body)
                                },
                                actionLabel = if (query.isNotBlank()) {
                                    stringResource(R.string.home_search_clear)
                                } else {
                                    null
                                },
                                onAction = if (query.isNotBlank()) {
                                    { query = "" }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    is MarketFeed.Content -> {
                        if (current.items.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(
                                    icon = VelnoxIcons.Package,
                                    title = stringResource(R.string.catalog_empty_title),
                                    body = stringResource(R.string.catalog_empty_body),
                                )
                            }
                        } else {
                            items(
                                items = current.items.chunked(columns),
                                key = { row -> row.joinToString("|") { it.id } },
                                contentType = { "productRow" },
                            ) { row ->
                                MarketProductRow(
                                    row = row,
                                    onOpenProduct = onOpenProduct,
                                )
                            }

                            if (current.hasMore) {
                                item(key = "load-more") {
                                    LoadMoreRow(
                                        loading = current.loadingMore,
                                        onLoadMore = { loadMore() },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        FilterBottomSheet(
            onDismiss = { showFilterSheet = false },
            onApply = { showFilterSheet = false },
            onClear = { clearFilters() },
            priceMin = priceMin,
            onPriceMinChange = { priceMin = it },
            priceMax = priceMax,
            onPriceMaxChange = { priceMax = it },
            sortOption = sortOption,
            onSortChange = { sortOption = it },
        )
    }

    // Sort Dropdown Menu
    if (showSortMenu) {
        SortDropdownMenu(
            onDismiss = { showSortMenu = false },
            currentOption = sortOption,
            onSelect = { sortOption = it; showSortMenu = false },
        )
    }
}

private fun CatalogResult<CatalogPage>.toMarketFeed(): MarketFeed = when (this) {
    is CatalogResult.Failed -> MarketFeed.Error
    is CatalogResult.Ok -> when {
        value.items.isEmpty() -> MarketFeed.Empty
        else -> MarketFeed.Content(items = value.items, hasMore = value.hasMore)
    }
}

// ───────────────────────────────────────────────────────────── Feed State

sealed interface MarketFeed {
    data object Loading : MarketFeed
    data object Empty : MarketFeed
    data object Error : MarketFeed
    data class Content(
        val items: List<CatalogProduct>,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
    ) : MarketFeed
}

// ───────────────────────────────────────────────────────────── Top Bar

@Composable
private fun MarketTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onFilterClick: () -> Unit,
    onSortClick: () -> Unit,
    sortOption: SortOption,
    onSortSelect: (SortOption) -> Unit,
) {
    val focusManager = androidx.compose.foundation.text.LocalFocusManager.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) VelnoxBrand else MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .widthIn(max = com.velnox.app.ui.components.ContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = VelnoxSpacing.md, vertical = VelnoxSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Brand mark (white on primary)
                BrandMark(size = 36, tile = com.velnox.app.ui.components.TileStyle.Brand)
                Spacer(Modifier.width(VelnoxSpacing.sm))

                // Search field
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .clip(com.velnox.app.ui.theme.SquircleSm)
                        .padding(horizontal = VelnoxSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = VelnoxIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(VelnoxSpacing.sm))
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.Transparent),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.home_search_placeholder),
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            )
                        },
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            innerTextField()
                            if (query.isNotBlank()) {
                                Icon(
                                    imageVector = VelnoxIcons.Close,
                                    contentDescription = stringResource(R.string.home_search_clear),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .velnoxClickable(role = Role.Button, onClick = onClearQuery)
                                        .padding(end = 4.dp),
                                )
                            }
                        },
                    )
                }
                Spacer(Modifier.width(VelnoxSpacing.sm))

                // Filter button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(com.velnox.app.ui.theme.Circle)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.12f))
                        .velnoxClickable(role = Role.Button, onClick = onFilterClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VelnoxIcons.SlidersHorizontal,
                        contentDescription = stringResource(R.string.home_filter),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(VelnoxSpacing.xs))

                // Sort dropdown
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(com.velnox.app.ui.theme.SquircleSm)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.12f))
                        .velnoxClickable(role = Role.Button, onClick = onSortClick)
                        .padding(horizontal = VelnoxSpacing.md),
                    contentAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = sortOption.label,
                            style = TextXs.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = VelnoxIcons.ChevronDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            // Search results hint when querying
            if (query.isNotBlank()) {
                Text(
                    text = stringResource(R.string.home_search_results_title),
                    style = TextXs,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VelnoxSpacing.md, bottom = VelnoxSpacing.xs),
                )
            }
        }
    }
}

// ───────────────────────────────────────────────────────────── Category Row

@Composable
private fun MarketCategoryRow(
    categories: List<CatalogCategory>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    onOpenAllCategories: () -> Unit,
    isThai: Boolean,
) {
    val visibleCategories = categories.filter { it.productCount > 0 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp)
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyRow(
            contentPadding = com.velnox.app.foundation.PaddingValues(horizontal = PageGutter),
            horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
            modifier = Modifier.padding(bottom = VelnoxSpacing.sm),
        ) {
            // All category
            MarketCategoryChip(
                icon = VelnoxIcons.Grid,
                label = stringResource(R.string.home_category_all),
                selected = selectedCategory == null,
                onClick = { onSelectCategory(null) },
            )

            // Categories from API
            items(visibleCategories, key = { it.slug }, contentType = { "category" }) { category ->
                MarketCategoryChip(
                    icon = marketCategoryIcon(category.slug),
                    label = if (isThai) category.displayName ?: category.name else category.name,
                    selected = category.slug == selectedCategory,
                    onClick = { onSelectCategory(category.slug) },
                )
            }

            // See all categories
            MarketCategoryChip(
                icon = VelnoxIcons.ChevronRight,
                label = stringResource(R.string.home_category_open),
                selected = false,
                onClick = onOpenAllCategories,
            )
        }
        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = PageGutter),
        )
    }
}

@Composable
private fun MarketCategoryChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) VelnoxBrand else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(com.velnox.app.ui.theme.SquircleSm)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (selected) VelnoxBrand else MaterialTheme.colorScheme.outline,
                shape = com.velnox.app.ui.theme.SquircleSm,
            )
            .velnoxClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = VelnoxSpacing.md),
        contentAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = TextXs.copy(fontWeight = FontWeight.Medium),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ───────────────────────────────────────────────────────────── Product Row

@Composable
private fun MarketProductRow(
    row: List<CatalogProduct>,
    onOpenProduct: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter),
        horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
    ) {
        row.forEach { product ->
            MarketProductCard(
                product = product,
                modifier = Modifier.weight(1f),
                onClick = { onOpenProduct(product.id) },
            )
        }
        repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
    }
    Spacer(Modifier.height(VelnoxSpacing.sm))
}

@Composable
private fun MarketProductCard(
    product: CatalogProduct,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = modifier
            .clip(com.velnox.app.ui.theme.SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, com.velnox.app.ui.theme.SquircleLg)
            .velnoxClickable(role = Role.Button, onClick = onClick)
            .padding(VelnoxSpacing.sm),
    ) {
        MarketProductThumbnail(
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PriceFormat.format(product.price, product.currency, locale)?.let { price ->
                Text(
                    text = price,
                    style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            if (product.soldCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = VelnoxSpacing.sm),
                ) {
                    Icon(
                        imageVector = VelnoxIcons.TrendingUp,
                        contentDescription = null,
                        tint = VelnoxBrand,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = stringResource(R.string.product_sold, product.soldCount),
                        style = Text2xs.copy(fontWeight = FontWeight.Medium),
                        color = VelnoxBrand,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketProductThumbnail(
    url: String?,
    name: String,
    outOfStock: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(com.velnox.app.ui.theme.SquircleLg)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (url != null && url.isNotBlank()) {
            coil.compose.AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = {
                    MarketProductPlaceholder()
                },
                error = {
                    MarketProductPlaceholder()
                },
            )
        } else {
            MarketProductPlaceholder()
        }
        if (outOfStock) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clip(com.velnox.app.ui.theme.SquircleLg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.product_out_of_stock),
                    style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = VelnoxSpacing.sm, vertical = VelnoxSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun MarketProductPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = VelnoxIcons.Package,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.size(32.dp),
        )
    }
}

// ───────────────────────────────────────────────────────────── Loading / Load More

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
                    .clip(com.velnox.app.ui.theme.Squircle)
                    .velnoxClickable(role = Role.Button, onClick = onLoadMore)
                    .padding(horizontal = VelnoxSpacing.lg, vertical = VelnoxSpacing.sm),
            )
        }
    }
}

// ───────────────────────────────────────────────────────────── Filter Bottom Sheet

enum class SortOption(
    @StringRes val labelRes: Int,
) {
    Newest(R.string.market_sort_newest),
    Popular(R.string.market_sort_popular),
    PriceLow(R.string.market_sort_price_low),
    PriceHigh(R.string.market_sort_price_high),
    Rating(R.string.market_sort_rating),
    
    val label: String
        @Composable
        get() = stringResource(labelRes),
}

@Composable
private fun FilterBottomSheet(
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    priceMin: String,
    onPriceMinChange: (String) -> Unit,
    priceMax: String,
    onPriceMaxChange: (String) -> Unit,
    sortOption: SortOption,
    onSortChange: (SortOption) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        confirmStateChange = { it != ModalBottomSheetValue.Hidden },
    )
    ModalBottomSheet(
        sheetState = sheetState,
        sheetShape = com.velnox.app.ui.theme.SquircleLg,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(VelnoxSpacing.lg),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.market_filter_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = VelnoxIcons.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .velnoxClickable(role = Role.Button, onClick = onDismiss),
                    )
                }
                Spacer(Modifier.height(VelnoxSpacing.lg))

                // Price range
                Text(
                    text = stringResource(R.string.market_filter_price_range),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(VelnoxSpacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.md),
                ) {
                    FilterPriceInput(
                        label = stringResource(R.string.market_filter_min_price),
                        value = priceMin,
                        onValueChange = onPriceMinChange,
                    )
                    FilterPriceInput(
                        label = stringResource(R.string.market_filter_max_price),
                        value = priceMax,
                        onValueChange = onPriceMaxChange,
                    )
                }
                Spacer(Modifier.height(VelnoxSpacing.xl))

                // Sort
                Text(
                    text = stringResource(R.string.market_filter_sort_by),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(VelnoxSpacing.md))
                Column {
                    SortOption.entries.forEach { option ->
                        FilterSortRow(
                            label = stringResource(option.labelRes),
                            selected = sortOption == option,
                            onClick = { onSortChange(option) },
                        )
                    }
                }
                Spacer(Modifier.height(VelnoxSpacing.xxxl))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.md),
                ) {
                    com.velnox.app.ui.components.PrimaryPill(
                        label = stringResource(R.string.market_filter_clear),
                        onClick = { onClear(); onApply() },
                        modifier = Modifier.weight(1f),
                    )
                    com.velnox.app.ui.components.PrimaryPill(
                        label = stringResource(R.string.market_filter_apply),
                        onClick = onApply,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(VelnoxSpacing.xl))
            }
        },
    )
}

@Composable
private fun FilterPriceInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = label,
            style = TextXs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(VelnoxSpacing.xs))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.surface)
                .clip(com.velnox.app.ui.theme.SquircleSm)
                .padding(horizontal = VelnoxSpacing.md)
                .border(1.dp, MaterialTheme.colorScheme.outline, com.velnox.app.ui.theme.SquircleSm),
            singleLine = true,
            keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FilterSortRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .velnoxClickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = VelnoxSpacing.md, vertical = VelnoxSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(com.velnox.app.ui.theme.Circle)
                .border(
                    width = if (selected) 6.dp else 1.dp,
                    color = if (selected) VelnoxBrand else MaterialTheme.colorScheme.outline,
                    shape = com.velnox.app.ui.theme.Circle,
                ),
        )
    }
}

// ───────────────────────────────────────────────────────────── Sort Dropdown Menu

@Composable
private fun SortDropdownMenu(
    onDismiss: () -> Unit,
    currentOption: SortOption,
    onSelect: (SortOption) -> Unit,
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 180.dp),
        shape = com.velnox.app.ui.theme.SquircleSm,
        properties = androidx.compose.material3.DropdownMenuProperties(
            animationSpec = androidx.compose.animation.core.tween(150),
        ),
    ) {
        SortOption.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(text = stringResource(option.labelRes), style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = {
                    if (currentOption == option) {
                        Icon(
                            imageVector = VelnoxIcons.Check,
                            contentDescription = null,
                            tint = VelnoxBrand,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                onClick = { onSelect(option) },
            )
        }
    }
}

// ───────────────────────────────────────────────────────────── Category Icon Map

private fun marketCategoryIcon(slug: String): androidx.compose.ui.graphics.vector.ImageVector = when {
    slug.contains("charger") || slug.contains("cable") || slug.contains("phone") -> VelnoxIcons.Headphones
    slug.contains("clothing") || slug.contains("shirt") || slug.contains("fashion") -> VelnoxIcons.Shirt
    slug.contains("electronic") || slug.contains("computer") -> VelnoxIcons.Laptop
    slug.contains("kitchen") || slug.contains("food") || slug.contains("cook") -> VelnoxIcons.Coffee
    slug.contains("beauty") || slug.contains("health") -> VelnoxIcons.Sparkles
    else -> VelnoxIcons.Tag
}
