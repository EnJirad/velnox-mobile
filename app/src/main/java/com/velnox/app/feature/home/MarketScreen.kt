@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.velnox.app.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.stickyHeader
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velnox.app.R
import com.velnox.app.ui.components.BrandMark
import com.velnox.app.ui.components.ContentMaxWidth
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.SectionHeader
import com.velnox.app.ui.components.VelnoxSpacing
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Circle
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.VelnoxDestructive
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.SquircleXs
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
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

/** Maximum price for slider (will be adjusted based on data). */
private const val MAX_PRICE_DEFAULT = 50000

/**
 * Velnox Market — full marketplace with categories, filters, search, and product grid.
 *
 * Features:
 * - Search bar with clear
 * - Category chips (horizontal scroll)
 * - Filter button (opens filter bottom sheet)
 * - Sort dropdown
 * - Product grid with lazy loading
 */
@Composable
fun MarketScreen(
    onOpenCategory: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalog = (LocalContext.current.applicationContext as VelnoxApplication).catalog
    val isThai = LocalConfiguration.current.locales[0].language == "th"

    // Search state
    var query by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    
    // Category state
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<CatalogCategory>>(emptyList()) }
    
    // Filter state
    var filterExpanded by remember { mutableStateOf(false) }
    var sortBy by rememberSaveable { mutableStateOf("popular") }
    var minPrice by rememberSaveable { mutableStateOf(0) }
    var maxPrice by rememberSaveable { mutableStateOf(MAX_PRICE_DEFAULT) }
    var priceRangeMin by remember { mutableStateOf(0) }
    var priceRangeMax by remember { mutableStateOf(MAX_PRICE_DEFAULT) }
    
    // Product feed state
    var feed by remember { mutableStateOf<MarketFeed>(MarketFeed.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Load categories
    LaunchedEffect(reloadKey) {
        if (categories.isEmpty()) {
            val loaded = catalog.categories()
            if (loaded is CatalogResult.Ok) categories = loaded.value
        }
    }

    // Debounce search
    LaunchedEffect(query) {
        if (debouncedQuery == query) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = query
    }

    // Load products when filters change
    LaunchedEffect(debouncedQuery, selectedCategory, sortBy, minPrice, maxPrice, reloadKey) {
        feed = MarketFeed.Loading
        if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)

        val query = CatalogQuery(
            search = debouncedQuery.takeIf { it.isNotBlank() },
            category = selectedCategory,
            sort = sortBy,
            minPrice = if (minPrice > 0) minPrice else null,
            maxPrice = if (maxPrice < MAX_PRICE_DEFAULT) maxPrice else null,
        )

        feed = catalog.products(query).toMarketFeed()
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
                    sort = sortBy,
                    minPrice = if (minPrice > 0) minPrice else null,
                    maxPrice = if (maxPrice < MAX_PRICE_DEFAULT) maxPrice else null,
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

    fun retry() {
        scope.launch {
            catalog.invalidate()
            categories = emptyList()
            reloadKey++
        }
    }

    fun clearFilters() {
        selectedCategory = null
        sortBy = "popular"
        minPrice = 0
        maxPrice = MAX_PRICE_DEFAULT
        priceRangeMin = 0
        priceRangeMax = MAX_PRICE_DEFAULT
    }

    val hasActiveFilters = selectedCategory != null || sortBy != "popular" || minPrice > 0 || maxPrice < MAX_PRICE_DEFAULT

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Top bar with title and chat
        MarketTopBar(
            query = query,
            onQueryChange = { query = it },
            onClearQuery = { query = ""; debouncedQuery = "" },
            onFilterClick = { filterExpanded = true },
            hasActiveFilters = hasActiveFilters,
        )

        // Category chips
        if (categories.isNotEmpty()) {
            CategoryChips(
                categories = categories,
                selectedCategory = selectedCategory,
                onSelectCategory = { selectedCategory = it },
                isThai = isThai,
            )
        }

        // Product feed
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
                    .widthIn(max = ContentMaxWidth)
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
                                icon = VelnoxIcons.EyeOff,
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
                                icon = VelnoxIcons.Search,
                                title = stringResource(R.string.market_no_results_title),
                                body = stringResource(R.string.market_no_results_body),
                                actionLabel = if (hasActiveFilters) stringResource(R.string.market_clear_filter) else null,
                                onAction = if (hasActiveFilters) { clearFilters() } else null,
                            )
                        }
                    }
                    is MarketFeed.Content -> {
                        // Sort dropdown header
                        item(key = "sort-header") {
                            SortHeader(
                                currentSort = sortBy,
                                onSortChange = { sortBy = it },
                                productCount = current.items.size,
                            )
                        }

                        // Product grid
                        items(current.items.chunked(columns), key = { row -> row.joinToString("|") { it.id } }) { row ->
                            item(key = "row-${row.joinToString(",") { it.id }}") {
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
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                                Spacer(Modifier.height(VelnoxSpacing.sm))
                            }
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

        // Filter bottom sheet
        if (filterExpanded) {
            FilterBottomSheet(
                categories = categories,
                selectedCategory = selectedCategory,
                onCategorySelect = { selectedCategory = it },
                sortBy = sortBy,
                onSortChange = { sortBy = it },
                minPrice = minPrice,
                maxPrice = maxPrice,
                priceRangeMin = priceRangeMin,
                priceRangeMax = priceRangeMax,
                onPriceChange = { min, max ->
                    priceRangeMin = min
                    priceRangeMax = max
                },
                onApply = {
                    minPrice = priceRangeMin
                    maxPrice = priceRangeMax
                    filterExpanded = false
                },
                onClear = { clearFilters() },
                onDismiss = { filterExpanded = false },
                isThai = isThai,
            )
        }
    }
}

// ---------------------------------------------------------------- Top Bar

@Composable
private fun MarketTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onFilterClick: () -> Unit,
    hasActiveFilters: Boolean,
) {
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
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = VelnoxSpacing.md, vertical = VelnoxSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark(size = 36)
                Spacer(Modifier.width(VelnoxSpacing.sm))
                Text(
                    text = stringResource(R.string.market_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.weight(1f))
                // Chat icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(Circle)
                        .background(MaterialTheme.colorScheme.secondary)
                        .velnoxClickable(role = Role.Button, onClick = { /* TODO: open chat */ }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VelnoxIcons.MessageCircle,
                        contentDescription = stringResource(R.string.chat_title),
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(VelnoxSpacing.sm))
                // Filter button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(Circle)
                        .background(
                            if (hasActiveFilters) VelnoxBrand.copy(alpha = 0.16f) 
                            else MaterialTheme.colorScheme.secondary
                        )
                        .velnoxClickable(role = Role.Button, onClick = onFilterClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VelnoxIcons.Filter,
                        contentDescription = stringResource(R.string.market_filter),
                        tint = if (hasActiveFilters) VelnoxBrand else MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    if (hasActiveFilters) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 6.dp)
                                .size(7.dp)
                                .clip(Circle)
                                .background(VelnoxBrand),
                        )
                    }
                }
            }
            // Search field
            Row(
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = VelnoxSpacing.md, bottom = VelnoxSpacing.md),
            ) {
                MarketSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onClearQuery = onClearQuery,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MarketSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, if (focused) VelnoxBrand else MaterialTheme.colorScheme.outline, Squircle)
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
                    text = stringResource(R.string.market_search_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextField(
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
                colors = TextFieldDefaults.textFieldColors(
                    containerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
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

// ---------------------------------------------------------------- Category Chips

@Composable
private fun CategoryChips(
    categories: List<CatalogCategory>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    isThai: Boolean,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = PageGutter),
        horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = VelnoxSpacing.sm)
            .background(MaterialTheme.colorScheme.background),
    ) {
        // All category
        item(key = "all") {
            CategoryChip(
                icon = VelnoxIcons.Grid,
                label = stringResource(R.string.market_all_categories),
                selected = selectedCategory == null,
                onClick = { onSelectCategory(null) },
            )
        }
        items(categories, key = { it.slug }) { category ->
            CategoryChip(
                icon = categoryIcon(category.slug),
                label = if (isThai) category.displayName ?: category.name else category.name,
                selected = category.slug == selectedCategory,
                onClick = { onSelectCategory(category.slug) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        VelnoxBrand.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val content = if (selected) {
        VelnoxBrand
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(Squircle)
            .background(background)
            .border(
                width = 1.dp,
                color = if (selected) VelnoxBrand else MaterialTheme.colorScheme.outline,
                shape = Squircle,
            )
            .velnoxClickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = VelnoxSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = TextXs.copy(fontWeight = FontWeight.Medium),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------- Sort Header

@Composable
private fun SortHeader(
    currentSort: String,
    onSortChange: (String) -> Unit,
    productCount: Int,
) {
    val sortOptions = mapOf(
        "popular" to R.string.market_sort_popular,
        "best_selling" to R.string.market_sort_best_selling,
        "newest" to R.string.market_sort_newest,
        "price_low" to R.string.market_sort_price_low,
        "price_high" to R.string.market_sort_price_high,
    )
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter, vertical = VelnoxSpacing.sm)
            .background(MaterialTheme.colorScheme.background),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.market_sort),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Box(
            modifier = Modifier
                .padding(start = VelnoxSpacing.md)
                .clip(SquircleSm)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, SquircleSm)
                .velnoxClickable(role = Role.Button, onClick = { expanded = !expanded }),
            contentAlignment = Alignment.Center,
            propagateMinConstraints = false,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = VelnoxSpacing.md, vertical = VelnoxSpacing.xs),
            ) {
                Text(
                    text = stringResource(sortOptions[currentSort] ?: R.string.market_sort_popular),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(VelnoxSpacing.xs))
                Icon(
                    imageVector = if (expanded) VelnoxIcons.ChevronUp else VelnoxIcons.ChevronDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.widthIn(min = 200.dp),
        shape = SquircleLg,
        containerColor = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(VelnoxSpacing.sm)) {
            sortOptions.forEach { (key, labelRes) ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(labelRes), style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        onSortChange(key)
                        expanded = false
                    },
                    leadingIcon = {
                        if (currentSort == key) {
                            Icon(
                                imageVector = VelnoxIcons.Check,
                                contentDescription = null,
                                tint = VelnoxBrand,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Filter Bottom Sheet

@Composable
private fun FilterBottomSheet(
    categories: List<CatalogCategory>,
    selectedCategory: String?,
    onCategorySelect: (String?) -> Unit,
    sortBy: String,
    onSortChange: (String) -> Unit,
    minPrice: Int,
    maxPrice: Int,
    priceRangeMin: Int,
    priceRangeMax: Int,
    onPriceChange: (Int, Int) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    isThai: Boolean,
) {
    val sortOptions = mapOf(
        "popular" to R.string.market_sort_popular,
        "best_selling" to R.string.market_sort_best_selling,
        "newest" to R.string.market_sort_newest,
        "price_low" to R.string.market_sort_price_low,
        "price_high" to R.string.market_sort_price_high,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .velnoxClickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .background(MaterialTheme.colorScheme.surface)
                .clip(SquircleLg.copy(topStart = 24.dp, topEnd = 24.dp))
                .shadow(8.dp)
                .align(Alignment.BottomCenter),
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = VelnoxSpacing.md)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(4.dp)
                        .clip(SquircleSm)
                        .background(MaterialTheme.colorScheme.outline),
                )
            }

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(VelnoxSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.market_filter),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.market_clear_filter),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = VelnoxBrand,
                    modifier = Modifier
                        .velnoxClickable(role = Role.Button, onClick = onClear)
                        .padding(VelnoxSpacing.sm),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Content
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(VelnoxSpacing.md)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            ) {
                // Category filter
                Text(
                    text = stringResource(R.string.market_categories),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = VelnoxSpacing.sm),
                )
                LazyColumn(
                    contentPadding = PaddingValues(0),
                    verticalArrangement = Arrangement.spacedBy(VelnoxSpacing.xs),
                ) {
                    item(key = "all") {
                        FilterRadioOption(
                            label = stringResource(R.string.market_all_categories),
                            selected = selectedCategory == null,
                            onClick = { onCategorySelect(null) },
                        )
                    }
                    items(categories, key = { it.slug }) { category ->
                        FilterRadioOption(
                            label = if (isThai) category.displayName ?: category.name else category.name,
                            selected = category.slug == selectedCategory,
                            onClick = { onCategorySelect(category.slug) },
                        )
                    }
                }

                Spacer(Modifier.height(VelnoxSpacing.lg))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(VelnoxSpacing.lg))

                // Sort filter
                Text(
                    text = stringResource(R.string.market_sort),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = VelnoxSpacing.sm),
                )
                LazyColumn(
                    contentPadding = PaddingValues(0),
                    verticalArrangement = Arrangement.spacedBy(VelnoxSpacing.xs),
                ) {
                    items(sortOptions.entries, key = { it.key }) { (key, labelRes) ->
                        FilterRadioOption(
                            label = stringResource(labelRes),
                            selected = sortBy == key,
                            onClick = { onSortChange(key) },
                        )
                    }
                }

                Spacer(Modifier.height(VelnoxSpacing.lg))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(VelnoxSpacing.lg))

                // Price range filter
                Text(
                    text = stringResource(R.string.market_price_range),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = VelnoxSpacing.md),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = PriceFormat.format(priceRangeMin, "THB", java.util.Locale.getDefault()) ?: "0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = PriceFormat.format(priceRangeMax, "THB", java.util.Locale.getDefault()) ?: "50,000",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(VelnoxSpacing.sm))
                RangeSlider(
                    min = 0,
                    max = MAX_PRICE_DEFAULT.toFloat(),
                    rangeStart = priceRangeMin.toFloat(),
                    rangeEnd = priceRangeMax.toFloat(),
                    onRangeChange = { range ->
                        onPriceChange(range.start.roundToInt(), range.endInclusive.roundToInt())
                    },
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = VelnoxBrand,
                        activeTrackColor = VelnoxBrand,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline,
                    ),
                )

                Spacer(Modifier.height(VelnoxSpacing.xl))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.md),
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onClear,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            outlinedBorder = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ),
                    ) {
                        Text(text = stringResource(R.string.market_clear_filter))
                    }
                    androidx.compose.material3.FilledButton(
                        onClick = onApply,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.filledButtonColors(
                            containerColor = VelnoxBrand,
                            contentColor = VelnoxBrandForeground,
                        ),
                    ) {
                        Text(text = stringResource(R.string.market_apply_filter))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(SquircleSm)
            .background(
                if (selected) VelnoxBrand.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 1.dp,
                color = if (selected) VelnoxBrand else MaterialTheme.colorScheme.outline,
                shape = SquircleSm,
            )
            .velnoxClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = VelnoxSpacing.md, vertical = VelnoxSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) VelnoxBrand else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            Icon(
                imageVector = VelnoxIcons.Check,
                contentDescription = null,
                tint = VelnoxBrand,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Range Slider (simplified)

@Composable
private fun RangeSlider(
    min: Float,
    max: Float,
    rangeStart: Float,
    rangeEnd: Float,
    onRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    colors: androidx.compose.material3.SliderColors = androidx.compose.material3.SliderDefaults.colors(),
) {
    androidx.compose.material3.RangeSlider(
        value = rangeStart..rangeEnd,
        onValueChange = onRangeChange,
        valueRange = min..max,
        steps = (max - min).roundToInt(),
        colors = colors,
        modifier = Modifier.fillMaxWidth().padding(horizontal = VelnoxSpacing.xs),
    )
}

// ---------------------------------------------------------------- Product Card (reuse from HomeScreen)

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

// ---------------------------------------------------------------- Category Icon Helper

internal fun categoryIcon(slug: String): ImageVector = when {
    slug.contains("charger") || slug.contains("cable") || slug.contains("phone") -> VelnoxIcons.Headphones
    slug.contains("clothing") || slug.contains("shirt") || slug.contains("fashion") -> VelnoxIcons.Shirt
    slug.contains("electronic") || slug.contains("computer") -> VelnoxIcons.Laptop
    slug.contains("kitchen") || slug.contains("food") || slug.contains("cook") -> VelnoxIcons.Coffee
    slug.contains("beauty") || slug.contains("health") -> VelnoxIcons.Sparkles
    else -> VelnoxIcons.Tag
}

/**
 * Market feed state.
 */
private sealed interface MarketFeed {
    data object Loading : MarketFeed
    data object Empty : MarketFeed
    data object Error : MarketFeed
    data class Content(
        val items: List<CatalogProduct>,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
    ) : MarketFeed
}

private fun CatalogResult<CatalogPage>.toMarketFeed(): MarketFeed = when (this) {
    is CatalogResult.Ok -> {
        if (value.items.isEmpty()) MarketFeed.Empty
        else MarketFeed.Content(
            items = value.items,
            hasMore = value.hasMore,
        )
    }
    is CatalogResult.Failed -> MarketFeed.Error
}

// ---------------------------------------------------------------- Product Art

enum class ProductArt {
    Box, Shirt, Headphones, Laptop, Coffee, Sparkles, Sneaker, Serum
}

@Composable
private fun ProductArtComposable(
    art: ProductArt,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val s = 1f
    val stroke = 1.5f * s
    val p = { x: Float, y: Float -> Offset(x * 24f * s, y * 24f * s) }
    androidx.compose.foundation.Canvas(modifier = modifier) {
        when (art) {
            ProductArt.Box -> {
                val box = Path().apply {
                    moveTo(p(3f, 3f).x, p(3f, 3f).y)
                    lineTo(p(21f, 3f).x, p(21f, 3f).y)
                    lineTo(p(21f, 21f).x, p(21f, 21f).y)
                    lineTo(p(3f, 21f).x, p(3f, 21f).y)
                    close()
                }
                drawPath(box, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawLine(color, p(3f, 9f), p(21f, 9f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, p(12f, 3f), p(12f, 21f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            ProductArt.Shirt -> {
                val shirt = Path().apply {
                    moveTo(p(12f, 4f).x, p(12f, 4f).y)
                    lineTo(p(4f, 4f).x, p(4f, 4f).y)
                    lineTo(p(4f, 18f).x, p(4f, 18f).y)
                    lineTo(p(10f, 18f).x, p(10f, 18f).y)
                    lineTo(p(10f, 22f).x, p(10f, 22f).y)
                    lineTo(p(14f, 22f).x, p(14f, 22f).y)
                    lineTo(p(14f, 18f).x, p(14f, 18f).y)
                    lineTo(p(20f, 18f).x, p(20f, 18f).y)
                    lineTo(p(20f, 4f).x, p(20f, 4f).y)
                    close()
                }
                drawPath(shirt, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawLine(color, p(8f, 10f), p(16f, 10f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            ProductArt.Headphones -> {
                drawPath(
                    Path().apply {
                        moveTo(p(4f, 12f).x, p(4f, 12f).y)
                        lineTo(p(4f, 8f).x, p(4f, 8f).y)
                        cubicTo(p(4f, 4f).x, p(4f, 4f).y, p(8f, 4f).x, p(8f, 4f).y, p(12f, 8f).x, p(12f, 8f).y)
                        lineTo(p(12f, 4f).x, p(12f, 4f).y)
                    },
                    color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round)
                )
                drawPath(
                    Path().apply {
                        moveTo(p(20f, 12f).x, p(20f, 12f).y)
                        lineTo(p(20f, 8f).x, p(20f, 8f).y)
                        cubicTo(p(20f, 4f).x, p(20f, 4f).y, p(16f, 4f).x, p(16f, 4f).y, p(12f, 8f).x, p(12f, 8f).y)
                        lineTo(p(12f, 4f).x, p(12f, 4f).y)
                    },
                    color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round)
                )
                drawLine(color, p(12f, 12f), p(12f, 16f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawCircle(color, p(12f, 16f), 4f * s, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            }
            ProductArt.Laptop -> {
                val laptop = Path().apply {
                    moveTo(p(3f, 4f).x, p(3f, 4f).y)
                    lineTo(p(21f, 4f).x, p(21f, 4f).y)
                    lineTo(p(21f, 16f).x, p(21f, 16f).y)
                    lineTo(p(3f, 16f).x, p(3f, 16f).y)
                    close()
                }
                drawPath(laptop, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                val base = Path().apply {
                    moveTo(p(7f, 16f).x, p(7f, 16f).y)
                    lineTo(p(17f, 16f).x, p(17f, 16f).y)
                    lineTo(p(17f, 18f).x, p(17f, 18f).y)
                    lineTo(p(7f, 18f).x, p(7f, 18f).y)
                    close()
                }
                drawPath(base, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            ProductArt.Coffee -> {
                val cup = Path().apply {
                    moveTo(p(6f, 8f).x, p(6f, 8f).y)
                    lineTo(p(6f, 18f).x, p(6f, 18f).y)
                    lineTo(p(18f, 18f).x, p(18f, 18f).y)
                    lineTo(p(18f, 8f).x, p(18f, 8f).y)
                    close()
                }
                drawPath(cup, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawPath(
                    Path().apply {
                        moveTo(p(20f, 8f).x, p(20f, 8f).y)
                        cubicTo(p(22f, 8f).x, p(22f, 8f).y, p(22f, 14f).x, p(22f, 14f).y, p(20f, 14f).x, p(20f, 14f).y)
                    },
                    color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round)
                )
                drawLine(color, p(8f, 8f), p(16f, 8f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            ProductArt.Sparkles -> {
                val s1 = 2f * s
                drawPath(
                    Path().apply {
                        moveTo(p(12f, 4f).x, p(12f, 4f).y)
                        lineTo(p(12f, 20f).x, p(12f, 20f).y)
                        moveTo(p(4f, 12f).x, p(4f, 12f).y)
                        lineTo(p(20f, 12f).x, p(20f, 12f).y)
                        moveTo(p(7f, 7f).x, p(7f, 7f).y)
                        lineTo(p(17f, 17f).x, p(17f, 17f).y)
                        moveTo(p(17f, 7f).x, p(17f, 7f).y)
                        lineTo(p(7f, 17f).x, p(7f, 17f).y)
                    },
                    color, style = androidx.compose.ui.graphics.drawscope.Stroke(s1, cap = StrokeCap.Round)
                )
            }
            ProductArt.Sneaker -> {
                val shoe = Path().apply {
                    moveTo(p(4f, 16f).x, p(4f, 16f).y)
                    lineTo(p(4f, 9f).x, p(4f, 9f).y)
                    lineTo(p(9f, 9f).x, p(9f, 9f).y)
                    lineTo(p(13f, 13f).x, p(13f, 13f).y)
                    lineTo(p(20f, 15f).x, p(20f, 15f).y)
                    lineTo(p(21f, 19f).x, p(21f, 19f).y)
                    lineTo(p(4f, 19f).x, p(4f, 19f).y)
                    close()
                }
                drawPath(shoe, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawLine(color, p(4f, 19f), p(21f, 19f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            ProductArt.Serum -> {
                drawRoundRect(
                    color = color,
                    topLeft = p(8f, 9f),
                    size = androidx.compose.ui.geometry.Size(8f * s, 11f * s),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * s),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
                drawRoundRect(
                    color = color,
                    topLeft = p(10f, 3f),
                    size = androidx.compose.ui.geometry.Size(4f * s, 5f * s),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f * s),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
                drawLine(color, p(12f, 14f), p(12f, 17f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
    }
}

// ---------------------------------------------------------------- Loading / Error / Product Art (reuse)

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

@Composable
private fun ProductArtFallback(
    modifier: Modifier,
    color: Color,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        ProductArtComposable(
            art = ProductArt.Box,
            color = color,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Preview
@Composable
private fun MarketScreenPreview() {
    VelnoxTheme {
        MarketScreen(
            onOpenCategory = {},
            onOpenProduct = {},
        )
    }
}
