@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.market

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.VelnoxApplication
import com.velnox.app.core.catalog.CatalogCategory
import com.velnox.app.core.catalog.CatalogProduct
import com.velnox.app.core.catalog.CatalogQuery
import com.velnox.app.core.catalog.CatalogResult
import com.velnox.app.core.catalog.PriceFormat
import com.velnox.app.feature.home.ProductCard
import com.velnox.app.feature.home.categoryIcon
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.SectionHeader
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MS = 300L
private const val FEATURED_LIMIT = 6

enum class MarketSort(val queryValue: String?, val labelRes: Int) {
    Relevance(null, R.string.market_sort_relevance),
    PriceAsc("price_asc", R.string.market_sort_price_asc),
    PriceDesc("price_desc", R.string.market_sort_price_desc),
    Newest("newest", R.string.market_sort_newest),
    Popular("popular", R.string.market_sort_popular);

    companion object {
        val Saver: Saver<MarketSort, String> = Saver(
            save = { it.name },
            restore = { name -> values().firstOrNull { it.name == name } ?: Relevance },
        )
    }
}

data class MarketFilterState(
    val categorySlug: String? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val inStockOnly: Boolean = false,
    val discountOnly: Boolean = false,
    val shopId: String? = null,
) {
    val hasActiveFilters: Boolean
        get() = categorySlug != null || minPrice != null || maxPrice != null ||
                inStockOnly || discountOnly || shopId != null

    val activeFilterCount: Int
        get() = (if (categorySlug != null) 1 else 0) +
                (if (minPrice != null || maxPrice != null) 1 else 0) +
                (if (inStockOnly) 1 else 0) +
                (if (discountOnly) 1 else 0) +
                (if (shopId != null) 1 else 0)

    fun matches(product: CatalogProduct): Boolean {
        if (categorySlug != null && product.categorySlug != categorySlug) return false
        val p = product.displayPrice
        if (minPrice != null && (p == null || p < minPrice)) return false
        if (maxPrice != null && (p == null || p > maxPrice)) return false
        if (inStockOnly && product.inStock == false) return false
        if (discountOnly) {
            val hasDiscount = (product.compareAtPrice != null && p != null && product.compareAtPrice > p) ||
                    (product.discountPercent != null && product.discountPercent > 0)
            if (!hasDiscount) return false
        }
        if (shopId != null && product.shopId != null && product.shopId != shopId) return false
        return true
    }

    fun toQuery(search: String?, sort: MarketSort, offset: Int = 0, limit: Int = CatalogQuery.PAGE_SIZE): CatalogQuery =
        CatalogQuery(
            search = search?.trim()?.takeIf { it.isNotEmpty() },
            category = categorySlug?.takeIf { it.isNotBlank() },
            minPrice = minPrice,
            maxPrice = maxPrice,
            sortBy = sort.queryValue,
            shopId = shopId?.takeIf { it.isNotBlank() },
            offset = offset,
            limit = limit,
        )

    companion object {
        val Saver: Saver<MarketFilterState, Any> = mapSaver(
            save = { state ->
                buildMap {
                    state.categorySlug?.let { put("categorySlug", it) }
                    state.minPrice?.let { put("minPrice", it) }
                    state.maxPrice?.let { put("maxPrice", it) }
                    put("inStockOnly", state.inStockOnly)
                    put("discountOnly", state.discountOnly)
                    state.shopId?.let { put("shopId", it) }
                }
            },
            restore = { map ->
                MarketFilterState(
                    categorySlug = map["categorySlug"] as? String,
                    minPrice = (map["minPrice"] as? Number)?.toDouble(),
                    maxPrice = (map["maxPrice"] as? Number)?.toDouble(),
                    inStockOnly = map["inStockOnly"] as? Boolean ?: false,
                    discountOnly = map["discountOnly"] as? Boolean ?: false,
                    shopId = map["shopId"] as? String,
                )
            },
        )
    }
}

fun List<CatalogProduct>.sortedByMode(sort: MarketSort): List<CatalogProduct> = when (sort) {
    MarketSort.Relevance -> this
    MarketSort.PriceAsc -> this.sortedWith(compareBy { it.displayPrice ?: Double.MAX_VALUE })
    MarketSort.PriceDesc -> this.sortedWith(compareByDescending { it.displayPrice ?: 0.0 })
    MarketSort.Newest -> this
    MarketSort.Popular -> this.sortedWith(compareByDescending { it.soldCount })
}

@Composable
fun MarketScreen(
    initialCategorySlug: String? = null,
    onOpenProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenCategory: ((String) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val catalog = (LocalContext.current.applicationContext as VelnoxApplication).catalog
    val isThai = LocalConfiguration.current.locales[0].language == "th"

    var query by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var sort by rememberSaveable(stateSaver = MarketSort.Saver) {
        mutableStateOf(MarketSort.Relevance)
    }
    var filterState by rememberSaveable(
        initialCategorySlug,
        stateSaver = MarketFilterState.Saver,
    ) {
        mutableStateOf(MarketFilterState(categorySlug = initialCategorySlug))
    }

    var categories by remember { mutableStateOf<List<CatalogCategory>>(emptyList()) }
    var featuredProducts by remember { mutableStateOf<List<CatalogProduct>>(emptyList()) }

    var products by remember { mutableStateOf<List<CatalogProduct>>(emptyList()) }
    var hasMore by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }

    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Debounce search query
    LaunchedEffect(query) {
        if (query.isEmpty()) {
            debouncedQuery = ""
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = query
    }

    // Load categories once
    LaunchedEffect(Unit) {
        when (val result = catalog.categories()) {
            is CatalogResult.Ok -> {
                categories = result.value.sortedWith(
                    compareByDescending<CatalogCategory> { it.productCount }.thenBy { it.name }
                )
            }
            is CatalogResult.Failed -> {}
        }
    }

    // Load featured products when on default discovery view
    LaunchedEffect(Unit) {
        when (val result = catalog.products(CatalogQuery(limit = FEATURED_LIMIT, sortBy = "popular"))) {
            is CatalogResult.Ok -> featuredProducts = result.value.items
            is CatalogResult.Failed -> {}
        }
    }

    // Primary product list loading
    LaunchedEffect(debouncedQuery, sort, filterState, reloadKey) {
        isLoading = true
        isError = false
        if (listState.firstVisibleItemIndex > 0) {
            listState.scrollToItem(0)
        }

        val requestQuery = filterState.toQuery(
            search = debouncedQuery,
            sort = sort,
            offset = 0,
            limit = CatalogQuery.PAGE_SIZE,
        )

        when (val result = catalog.products(requestQuery)) {
            is CatalogResult.Ok -> {
                val filtered = result.value.items.filter { filterState.matches(it) }
                products = filtered.sortedByMode(sort)
                hasMore = result.value.hasMore
                isLoading = false
            }
            is CatalogResult.Failed -> {
                isError = true
                isLoading = false
            }
        }
    }

    // Infinite scrolling / pagination load more
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            !isLoading && !isLoadingMore && hasMore && totalItems > 0 && lastVisible >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (!shouldLoadMore) return@LaunchedEffect
        isLoadingMore = true
        val nextQuery = filterState.toQuery(
            search = debouncedQuery,
            sort = sort,
            offset = products.size,
            limit = CatalogQuery.PAGE_SIZE,
        )
        when (val result = catalog.products(nextQuery)) {
            is CatalogResult.Ok -> {
                val filtered = result.value.items.filter { filterState.matches(it) }
                products = (products + filtered).distinctBy { it.id }.sortedByMode(sort)
                hasMore = result.value.hasMore
            }
            is CatalogResult.Failed -> {}
        }
        isLoadingMore = false
    }

    val availableShops = remember(products) {
        products.mapNotNull { p ->
            val id = p.shopId ?: p.shopName
            val name = p.shopName
            if (id != null && name != null) id to name else null
        }.distinctBy { it.first }
    }

    val screenTitle = if (onBack != null && filterState.categorySlug != null) {
        categories.firstOrNull { it.slug == filterState.categorySlug }
            ?.let { if (isThai) it.displayName ?: it.name else it.name }
            ?: stringResource(R.string.market_title)
    } else {
        stringResource(R.string.market_title)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(title = screenTitle, onBack = onBack)

        // Search bar
        MarketSearchBar(
            query = query,
            onQueryChange = { query = it },
            onClear = { query = "" },
            modifier = Modifier.padding(horizontal = PageGutter, vertical = VelnoxSpacing.xs),
        )

        // Filter and Sort controls bar
        MarketControlsBar(
            selectedSort = sort,
            activeFilterCount = filterState.activeFilterCount,
            onOpenSort = { showSortMenu = true },
            onOpenFilter = { showFilterSheet = true },
            sortMenuExpanded = showSortMenu,
            onDismissSortMenu = { showSortMenu = false },
            onSelectSort = {
                sort = it
                showSortMenu = false
            },
            modifier = Modifier.padding(horizontal = PageGutter, vertical = VelnoxSpacing.xs),
        )

        // Categories chip row
        if (categories.isNotEmpty()) {
            MarketCategoriesRow(
                categories = categories,
                selectedCategorySlug = filterState.categorySlug,
                isThai = isThai,
                onSelectCategory = { slug ->
                    filterState = filterState.copy(categorySlug = slug)
                    onOpenCategory?.invoke(slug.orEmpty())
                },
                modifier = Modifier.padding(vertical = VelnoxSpacing.xs),
            )
        }

        // Active filter chips (if any)
        if (filterState.hasActiveFilters) {
            ActiveFilterChipsRow(
                filterState = filterState,
                categories = categories,
                isThai = isThai,
                onRemoveCategory = { filterState = filterState.copy(categorySlug = null) },
                onRemovePrice = { filterState = filterState.copy(minPrice = null, maxPrice = null) },
                onRemoveInStock = { filterState = filterState.copy(inStockOnly = false) },
                onRemoveDiscount = { filterState = filterState.copy(discountOnly = false) },
                onRemoveShop = { filterState = filterState.copy(shopId = null) },
                onClearAll = { filterState = MarketFilterState() },
                modifier = Modifier.padding(horizontal = PageGutter, vertical = VelnoxSpacing.xs),
            )
        }

        // Main content
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val columns = when {
                maxWidth < 400.dp -> 2
                maxWidth < 600.dp -> 3
                else -> 4
            }

            when {
                isLoading && products.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }

                isError && products.isEmpty() -> {
                    EmptyState(
                        icon = VelnoxIcons.EyeOff,
                        title = stringResource(R.string.catalog_error_title),
                        body = stringResource(R.string.catalog_error_body),
                        actionLabel = stringResource(R.string.common_retry),
                        onAction = { reloadKey++ },
                    )
                }

                !isLoading && products.isEmpty() -> {
                    EmptyState(
                        icon = VelnoxIcons.Package,
                        title = stringResource(R.string.market_empty_title),
                        body = stringResource(R.string.market_empty_body),
                        actionLabel = if (filterState.hasActiveFilters || query.isNotEmpty()) {
                            stringResource(R.string.market_filter_clear_all)
                        } else null,
                        onAction = {
                            query = ""
                            filterState = MarketFilterState()
                        },
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = VelnoxSpacing.xxl),
                    ) {
                        // Small Featured / Recommended section
                        val showFeatured = debouncedQuery.isBlank() &&
                                !filterState.hasActiveFilters &&
                                featuredProducts.isNotEmpty()
                        if (showFeatured) {
                            item(key = "featured_section") {
                                Column(modifier = Modifier.padding(vertical = VelnoxSpacing.sm)) {
                                    SectionHeader(
                                        title = stringResource(R.string.market_featured_title),
                                        modifier = Modifier.padding(horizontal = PageGutter),
                                    )
                                    Spacer(Modifier.height(VelnoxSpacing.xs))
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = PageGutter),
                                        horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
                                    ) {
                                        items(featuredProducts, key = { "feat_${it.id}" }) { product ->
                                            ProductCard(
                                                product = product,
                                                modifier = Modifier.width(160.dp),
                                                onClick = { onOpenProduct(product.id) },
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(VelnoxSpacing.md))
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(horizontal = PageGutter),
                                    )
                                }
                            }
                        }

                        // Product count header
                        item(key = "product_count_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = PageGutter,
                                        end = PageGutter,
                                        top = VelnoxSpacing.sm,
                                        bottom = VelnoxSpacing.sm,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.market_results_count, products.size),
                                    style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Responsive Product Grid
                        val chunked = products.chunked(columns)
                        items(chunked, key = { row -> row.joinToString("|") { it.id } }) { row ->
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
                                repeat(columns - row.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(VelnoxSpacing.sm))
                        }

                        // Pagination loading footer
                        if (isLoadingMore) {
                            item(key = "loading_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = VelnoxSpacing.md),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
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
        MarketFilterBottomSheet(
            currentState = filterState,
            availableShops = availableShops,
            onDismiss = { showFilterSheet = false },
            onApply = { newState ->
                filterState = newState
                showFilterSheet = false
            },
        )
    }
}

// ------------------------------------------------------------- search bar

@Composable
private fun MarketSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, Squircle)
            .padding(horizontal = VelnoxSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = VelnoxIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(VelnoxSpacing.xs))
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.market_search_placeholder),
                    style = TextXs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextXs.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(VelnoxBrand),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .velnoxClickable(role = Role.Button, onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VelnoxIcons.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------- controls bar

@Composable
private fun MarketControlsBar(
    selectedSort: MarketSort,
    activeFilterCount: Int,
    onOpenSort: () -> Unit,
    onOpenFilter: () -> Unit,
    sortMenuExpanded: Boolean,
    onDismissSortMenu: () -> Unit,
    onSelectSort: (MarketSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sort Button
        Box {
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(SquircleSm)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, SquircleSm)
                    .velnoxClickable(role = Role.Button, onClick = onOpenSort)
                    .padding(horizontal = VelnoxSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(selectedSort.labelRes),
                    style = Text2xs.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = VelnoxIcons.ChevronDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }

            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = onDismissSortMenu,
                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            ) {
                MarketSort.values().forEach { s ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(s.labelRes),
                                style = TextXs.copy(
                                    fontWeight = if (s == selectedSort) FontWeight.Bold else FontWeight.Normal,
                                    color = if (s == selectedSort) VelnoxBrand else MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        },
                        onClick = { onSelectSort(s) },
                        trailingIcon = if (s == selectedSort) {
                            {
                                Icon(
                                    imageVector = VelnoxIcons.Check,
                                    contentDescription = null,
                                    tint = VelnoxBrand,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else null,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Filter Button
        val filterActive = activeFilterCount > 0
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(SquircleSm)
                .background(
                    if (filterActive) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface
                )
                .border(
                    width = 1.dp,
                    color = if (filterActive) VelnoxBrand else MaterialTheme.colorScheme.outline,
                    shape = SquircleSm,
                )
                .velnoxClickable(role = Role.Button, onClick = onOpenFilter)
                .padding(horizontal = VelnoxSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = VelnoxIcons.Sliders,
                contentDescription = null,
                tint = if (filterActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.market_filter_button),
                style = Text2xs.copy(fontWeight = FontWeight.Medium),
                color = if (filterActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            if (activeFilterCount > 0) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(VelnoxBrand),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = activeFilterCount.toString(),
                        style = Text2xs.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------- categories row

@Composable
private fun MarketCategoriesRow(
    categories: List<CatalogCategory>,
    selectedCategorySlug: String?,
    isThai: Boolean,
    onSelectCategory: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = PageGutter),
        horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.xs),
    ) {
        // "All" Chip
        val isAllSelected = selectedCategorySlug == null
        item(key = "all_categories") {
            Row(
                modifier = Modifier
                    .height(34.dp)
                    .clip(Squircle)
                    .background(
                        if (isAllSelected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        width = 1.dp,
                        color = if (isAllSelected) VelnoxBrand else MaterialTheme.colorScheme.outline,
                        shape = Squircle,
                    )
                    .velnoxClickable(role = Role.Button) { onSelectCategory(null) }
                    .semantics { this.selected = isAllSelected }
                    .padding(horizontal = VelnoxSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.market_all_categories),
                    style = Text2xs.copy(fontWeight = FontWeight.Medium),
                    color = if (isAllSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Specific category chips
        items(categories, key = { it.slug }) { category ->
            val isSelected = category.slug == selectedCategorySlug
            Row(
                modifier = Modifier
                    .height(34.dp)
                    .clip(Squircle)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) VelnoxBrand else MaterialTheme.colorScheme.outline,
                        shape = Squircle,
                    )
                    .velnoxClickable(role = Role.Button) {
                        onSelectCategory(if (isSelected) null else category.slug)
                    }
                    .semantics { this.selected = isSelected }
                    .padding(horizontal = VelnoxSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = categoryIcon(category.slug),
                    contentDescription = null,
                    tint = if (isSelected) VelnoxBrand else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isThai) category.displayName ?: category.name else category.name,
                    style = Text2xs.copy(fontWeight = FontWeight.Medium),
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (category.productCount > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "(${category.productCount})",
                        style = Text2xs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------- active filter chips

@Composable
private fun ActiveFilterChipsRow(
    filterState: MarketFilterState,
    categories: List<CatalogCategory>,
    isThai: Boolean,
    onRemoveCategory: () -> Unit,
    onRemovePrice: () -> Unit,
    onRemoveInStock: () -> Unit,
    onRemoveDiscount: () -> Unit,
    onRemoveShop: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Category active chip
        filterState.categorySlug?.let { slug ->
            val name = categories.firstOrNull { it.slug == slug }
                ?.let { if (isThai) it.displayName ?: it.name else it.name }
                ?: slug
            item(key = "active_cat") {
                RemovableChip(label = name, onRemove = onRemoveCategory)
            }
        }

        // Price range chip
        if (filterState.minPrice != null || filterState.maxPrice != null) {
            val label = when {
                filterState.minPrice != null && filterState.maxPrice != null ->
                    "฿${filterState.minPrice.toInt()} - ฿${filterState.maxPrice.toInt()}"
                filterState.minPrice != null -> "≥ ฿${filterState.minPrice.toInt()}"
                else -> "≤ ฿${filterState.maxPrice!!.toInt()}"
            }
            item(key = "active_price") {
                RemovableChip(label = label, onRemove = onRemovePrice)
            }
        }

        // In Stock chip
        if (filterState.inStockOnly) {
            item(key = "active_stock") {
                RemovableChip(
                    label = stringResource(R.string.market_filter_in_stock_only),
                    onRemove = onRemoveInStock,
                )
            }
        }

        // Discount chip
        if (filterState.discountOnly) {
            item(key = "active_discount") {
                RemovableChip(
                    label = stringResource(R.string.market_filter_discount_only),
                    onRemove = onRemoveDiscount,
                )
            }
        }

        // Clear all action
        item(key = "clear_all_chip") {
            Text(
                text = stringResource(R.string.market_filter_clear_all),
                style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                color = VelnoxBrand,
                modifier = Modifier
                    .clip(SquircleXs)
                    .velnoxClickable(role = Role.Button, onClick = onClearAll)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun RemovableChip(
    label: String,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(SquircleXs)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleXs)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = Text2xs.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .velnoxClickable(role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = VelnoxIcons.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

// ------------------------------------------------------------- filter bottom sheet

@Composable
private fun MarketFilterBottomSheet(
    currentState: MarketFilterState,
    availableShops: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onApply: (MarketFilterState) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var minPriceInput by remember {
        mutableStateOf(currentState.minPrice?.toInt()?.toString().orEmpty())
    }
    var maxPriceInput by remember {
        mutableStateOf(currentState.maxPrice?.toInt()?.toString().orEmpty())
    }
    var inStockOnly by remember { mutableStateOf(currentState.inStockOnly) }
    var discountOnly by remember { mutableStateOf(currentState.discountOnly) }
    var selectedShopId by remember { mutableStateOf(currentState.shopId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter)
                .navigationBarsPadding()
                .padding(bottom = VelnoxSpacing.xl),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.market_filter_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.market_filter_reset),
                    style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                    color = VelnoxBrand,
                    modifier = Modifier
                        .clip(SquircleXs)
                        .velnoxClickable(role = Role.Button) {
                            minPriceInput = ""
                            maxPriceInput = ""
                            inStockOnly = false
                            discountOnly = false
                            selectedShopId = null
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(VelnoxSpacing.md))

            // Price range presets
            Text(
                text = stringResource(R.string.market_filter_price_range),
                style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(VelnoxSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.xs),
            ) {
                PricePresetChip(
                    label = stringResource(R.string.market_filter_under_100),
                    selected = minPriceInput.isEmpty() && maxPriceInput == "100",
                    onClick = {
                        minPriceInput = ""
                        maxPriceInput = "100"
                    },
                    modifier = Modifier.weight(1f),
                )
                PricePresetChip(
                    label = stringResource(R.string.market_filter_100_500),
                    selected = minPriceInput == "100" && maxPriceInput == "500",
                    onClick = {
                        minPriceInput = "100"
                        maxPriceInput = "500"
                    },
                    modifier = Modifier.weight(1f),
                )
                PricePresetChip(
                    label = stringResource(R.string.market_filter_over_500),
                    selected = minPriceInput == "500" && maxPriceInput.isEmpty(),
                    onClick = {
                        minPriceInput = "500"
                        maxPriceInput = ""
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(VelnoxSpacing.xs))

            // Custom min / max inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PriceInputField(
                    value = minPriceInput,
                    onValueChange = { minPriceInput = it.filter { c -> c.isDigit() } },
                    placeholder = stringResource(R.string.market_filter_min_price),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "-",
                    style = TextXs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PriceInputField(
                    value = maxPriceInput,
                    onValueChange = { maxPriceInput = it.filter { c -> c.isDigit() } },
                    placeholder = stringResource(R.string.market_filter_max_price),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(VelnoxSpacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(Modifier.height(VelnoxSpacing.md))

            // Availability: In stock only
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .velnoxClickable(role = Role.Switch) { inStockOnly = !inStockOnly }
                    .padding(vertical = VelnoxSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.market_filter_availability),
                        style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.market_filter_in_stock_only),
                        style = Text2xs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = inStockOnly,
                    onCheckedChange = { inStockOnly = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = VelnoxBrand,
                    ),
                )
            }

            Spacer(Modifier.height(VelnoxSpacing.sm))

            // Deals: Discount only
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .velnoxClickable(role = Role.Switch) { discountOnly = !discountOnly }
                    .padding(vertical = VelnoxSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.market_filter_deals),
                        style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.market_filter_discount_only),
                        style = Text2xs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = discountOnly,
                    onCheckedChange = { discountOnly = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = VelnoxBrand,
                    ),
                )
            }

            // Sellers / Shops if available
            if (availableShops.isNotEmpty()) {
                Spacer(Modifier.height(VelnoxSpacing.sm))
                Text(
                    text = stringResource(R.string.market_filter_seller),
                    style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(VelnoxSpacing.xs))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.xs),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item(key = "shop_all") {
                        val isAll = selectedShopId == null
                        PricePresetChip(
                            label = stringResource(R.string.market_filter_all_sellers),
                            selected = isAll,
                            onClick = { selectedShopId = null },
                        )
                    }
                    items(availableShops, key = { it.first }) { (id, name) ->
                        val isSelected = selectedShopId == id
                        PricePresetChip(
                            label = name,
                            selected = isSelected,
                            onClick = { selectedShopId = if (isSelected) null else id },
                        )
                    }
                }
            }

            Spacer(Modifier.height(VelnoxSpacing.lg))

            // Apply button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(Squircle)
                    .background(VelnoxBrand)
                    .velnoxClickable(role = Role.Button) {
                        val min = minPriceInput.toDoubleOrNull()
                        val max = maxPriceInput.toDoubleOrNull()
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            onApply(
                                currentState.copy(
                                    minPrice = min,
                                    maxPrice = max,
                                    inStockOnly = inStockOnly,
                                    discountOnly = discountOnly,
                                    shopId = selectedShopId,
                                )
                            )
                        }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.market_filter_apply),
                    style = TextXs.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun PricePresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(SquircleSm)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = 1.dp,
                color = if (selected) VelnoxBrand else MaterialTheme.colorScheme.outline,
                shape = SquircleSm,
            )
            .velnoxClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = Text2xs.copy(fontWeight = FontWeight.Medium),
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PriceInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(SquircleSm)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleSm)
            .padding(horizontal = VelnoxSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "฿",
            style = TextXs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = TextXs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextXs.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(VelnoxBrand),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
