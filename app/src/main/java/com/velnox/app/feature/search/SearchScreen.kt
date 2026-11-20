@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.velnox.app.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velnox.app.R
import com.velnox.app.VelnoxApplication
import com.velnox.app.core.catalog.CatalogCategory
import com.velnox.app.core.catalog.CatalogProduct
import com.velnox.app.core.catalog.CatalogQuery
import com.velnox.app.core.catalog.CatalogResult
import com.velnox.app.feature.home.ProductCard
import com.velnox.app.ui.components.BrandBadge
import com.velnox.app.ui.components.ContentMaxWidth
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.VelnoxSpacing
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Circle
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    searchState: SearchState = remember { SearchState() },
) {
    val catalog = (LocalContext.current.applicationContext as VelnoxApplication).catalog
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var submittedQuery by rememberSaveable { mutableStateOf(initialQuery) }
    var filter by remember { mutableStateOf(SearchFilter()) }
    var showFilterSheet by remember { mutableStateOf(false) }

    var categories by remember { mutableStateOf<List<CatalogCategory>>(emptyList()) }
    var results by remember { mutableStateOf<List<CatalogProduct>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by rememberSaveable { mutableStateOf(initialQuery.isNotBlank()) }

    // Fetch categories for filtering
    LaunchedEffect(Unit) {
        val catResult = catalog.categories()
        if (catResult is CatalogResult.Ok) {
            categories = catResult.value
        }
        if (initialQuery.isBlank()) {
            focusRequester.requestFocus()
        }
    }

    // Perform Search on submitted query or filter changes
    fun performSearch(targetQuery: String, targetFilter: SearchFilter = filter) {
        val trimmed = targetQuery.trim()
        submittedQuery = trimmed
        hasSearched = true
        if (trimmed.isNotBlank()) {
            searchState.addQuery(trimmed)
        }
        isLoading = true
        scope.launch {
            val res = catalog.products(
                CatalogQuery(
                    search = trimmed.takeIf { it.isNotBlank() },
                    category = targetFilter.categorySlug,
                    minPrice = targetFilter.minPrice,
                    maxPrice = targetFilter.maxPrice,
                    sortBy = targetFilter.sortOption.apiKey,
                    limit = 60,
                ),
            )
            isLoading = false
            if (res is CatalogResult.Ok) {
                var items = res.value.items
                if (targetFilter.inStockOnly) {
                    items = items.filter { it.inStock != false }
                }
                results = items
            }
        }
    }

    // Trigger initial search if query provided
    LaunchedEffect(submittedQuery, filter) {
        if (hasSearched) {
            performSearch(submittedQuery, filter)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Search Toolbar
        SearchTopBar(
            query = query,
            onQueryChange = {
                query = it
                if (it.isBlank()) {
                    hasSearched = false
                }
            },
            onClearQuery = {
                query = ""
                hasSearched = false
            },
            onSearch = {
                focusManager.clearFocus()
                performSearch(query)
            },
            onBack = onBack,
            onOpenFilter = { showFilterSheet = true },
            activeFilterCount = filter.activeFilterCount,
            focusRequester = focusRequester,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (!hasSearched && query.isBlank()) {
            // Mode 1: Recent searches and trending queries
            SearchDiscoveryContent(
                searchState = searchState,
                categories = categories,
                onSelectQuery = { selectedQuery ->
                    query = selectedQuery
                    focusManager.clearFocus()
                    performSearch(selectedQuery)
                },
                onSelectCategory = { catSlug ->
                    filter = filter.copy(categorySlug = catSlug)
                    performSearch(query, filter.copy(categorySlug = catSlug))
                },
            )
        } else if (!hasSearched && query.isNotBlank()) {
            // Mode 2: Live suggestions
            SearchSuggestionsContent(
                query = query,
                searchState = searchState,
                categories = categories,
                onSelectSuggestion = { suggestion ->
                    query = suggestion
                    focusManager.clearFocus()
                    performSearch(suggestion)
                },
            )
        } else {
            // Mode 3: Search results
            SearchResultsContent(
                query = submittedQuery,
                results = results,
                isLoading = isLoading,
                filter = filter,
                onUpdateSort = { newSort ->
                    filter = filter.copy(sortOption = newSort)
                },
                onClearFilter = {
                    filter = SearchFilter()
                },
                onOpenProduct = onOpenProduct,
            )
        }
    }

    if (showFilterSheet) {
        SearchFilterBottomSheet(
            filter = filter,
            categories = categories,
            onDismiss = { showFilterSheet = false },
            onApply = { newFilter ->
                filter = newFilter
                showFilterSheet = false
            },
            onReset = {
                filter = SearchFilter()
            },
        )
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onOpenFilter: () -> Unit,
    activeFilterCount: Int,
    focusRequester: FocusRequester,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = VelnoxSpacing.sm, vertical = VelnoxSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = VelnoxIcons.ChevronLeft,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Search input field
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(Squircle)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, Squircle)
                    .padding(horizontal = VelnoxSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = VelnoxIcons.Search,
                    contentDescription = null,
                    tint = VelnoxBrand,
                    modifier = Modifier.size(18.dp),
                )

                Spacer(Modifier.width(VelnoxSpacing.xs))

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
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { onSearch() },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .padding(vertical = 10.dp),
                    )
                }

                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = onClearQuery,
                        modifier = Modifier.size(28.dp),
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

            Spacer(Modifier.width(VelnoxSpacing.xs))

            // Filter button with badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(Circle)
                    .background(
                        if (activeFilterCount > 0) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .velnoxClickable(role = Role.Button, onClick = onOpenFilter),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VelnoxIcons.Sliders,
                    contentDescription = stringResource(R.string.search_filter_title),
                    tint = if (activeFilterCount > 0) VelnoxBrand else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                if (activeFilterCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp)
                            .size(16.dp)
                            .clip(Circle)
                            .background(VelnoxBrand),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = activeFilterCount.toString(),
                            style = Text2xs,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchDiscoveryContent(
    searchState: SearchState,
    categories: List<CatalogCategory>,
    onSelectQuery: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PageGutter),
    ) {
        // Recent searches
        if (searchState.history.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.search_recent_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(
                    onClick = { searchState.clearHistory() },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = stringResource(R.string.search_recent_clear),
                        style = TextXs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(VelnoxSpacing.xs))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                searchState.history.forEach { item ->
                    Row(
                        modifier = Modifier
                            .clip(Squircle)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), Squircle)
                            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item,
                            style = TextXs,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.velnoxClickable(role = Role.Button, onClick = { onSelectQuery(item) }),
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = VelnoxIcons.Close,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(Circle)
                                .velnoxClickable(role = Role.Button, onClick = { searchState.removeQuery(item) })
                                .padding(2.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(VelnoxSpacing.xl))
        }

        // Trending searches
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = VelnoxIcons.Sparkles,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(VelnoxSpacing.xs))
            Text(
                text = stringResource(R.string.search_trending_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(VelnoxSpacing.sm))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SearchState.trendingSearches.forEachIndexed { index, trend ->
                Row(
                    modifier = Modifier
                        .clip(Squircle)
                        .background(
                            if (index < 2) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            1.dp,
                            if (index < 2) Color(0xFFFDE68A) else MaterialTheme.colorScheme.outline,
                            Squircle,
                        )
                        .velnoxClickable(role = Role.Button, onClick = { onSelectQuery(trend) })
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (index < 2) {
                        Text(
                            text = "🔥",
                            style = Text2xs,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = trend,
                        style = TextXs,
                        fontWeight = if (index < 2) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (index < 2) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (categories.isNotEmpty()) {
            Spacer(Modifier.height(VelnoxSpacing.xl))

            Text(
                text = stringResource(R.string.search_filter_categories),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(VelnoxSpacing.sm))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                categories.forEach { cat ->
                    Box(
                        modifier = Modifier
                            .clip(Squircle)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .velnoxClickable(role = Role.Button, onClick = { onSelectCategory(cat.slug) })
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = cat.displayName ?: cat.name,
                            style = TextXs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSuggestionsContent(
    query: String,
    searchState: SearchState,
    categories: List<CatalogCategory>,
    onSelectSuggestion: (String) -> Unit,
) {
    val q = query.trim().lowercase()

    val suggestions = remember(q) {
        val list = mutableListOf<String>()
        // From history
        searchState.history.forEach { if (it.lowercase().contains(q)) list.add(it) }
        // From trending
        SearchState.trendingSearches.forEach { if (it.lowercase().contains(q) && !list.contains(it)) list.add(it) }
        // From category names
        categories.forEach {
            val name = it.displayName ?: it.name
            if (name.lowercase().contains(q) && !list.contains(name)) list.add(name)
        }
        if (list.isEmpty()) {
            list.add(query)
        }
        list.take(8)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = PageGutter, vertical = VelnoxSpacing.sm),
    ) {
        items(suggestions) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .velnoxClickable(role = Role.Button, onClick = { onSelectSuggestion(item) })
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = VelnoxIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(VelnoxSpacing.md))
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = VelnoxIcons.ArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun SearchResultsContent(
    query: String,
    results: List<CatalogProduct>,
    isLoading: Boolean,
    filter: SearchFilter,
    onUpdateSort: (SearchSortOption) -> Unit,
    onClearFilter: () -> Unit,
    onOpenProduct: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Sort Pill Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = VelnoxSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.xs),
            ) {
                items(SearchSortOption.values()) { sortOpt ->
                    val isSelected = filter.sortOption == sortOpt
                    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    val textCol = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .clip(Squircle)
                            .background(bg)
                            .velnoxClickable(role = Role.Tab, onClick = { onUpdateSort(sortOpt) })
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(sortOpt.labelRes),
                            style = TextXs,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = textCol,
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PageGutter),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = VelnoxBrand,
                    strokeWidth = 2.dp,
                )
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PageGutter),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 300.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(Circle)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = VelnoxIcons.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.height(VelnoxSpacing.md))
                    Text(
                        text = stringResource(R.string.search_no_results),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(VelnoxSpacing.xs))
                    Text(
                        text = stringResource(R.string.search_no_results_hint),
                        style = TextXs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                    )
                    if (!filter.isDefault) {
                        Spacer(Modifier.height(VelnoxSpacing.md))
                        TextButton(onClick = onClearFilter) {
                            Text(
                                text = stringResource(R.string.search_filter_reset),
                                color = VelnoxBrand,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                val columns = if (maxWidth < 500.dp) 2 else 3
                val columnSpacing = 12.dp

                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(
                        start = PageGutter,
                        end = PageGutter,
                        top = VelnoxSpacing.sm,
                        bottom = VelnoxSpacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(columnSpacing),
                ) {
                    val rows = results.chunked(columns)
                    items(rows) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(columnSpacing),
                        ) {
                            for (item in rowItems) {
                                ProductCard(
                                    product = item,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOpenProduct(item.id) },
                                )
                            }
                            // Empty spacer to balance incomplete rows
                            for (i in 0 until (columns - rowItems.size)) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFilterBottomSheet(
    filter: SearchFilter,
    categories: List<CatalogCategory>,
    onDismiss: () -> Unit,
    onApply: (SearchFilter) -> Unit,
    onReset: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var workingFilter by remember { mutableStateOf(filter) }

    var minPriceInput by remember { mutableStateOf(filter.minPrice?.toInt()?.toString() ?: "") }
    var maxPriceInput by remember { mutableStateOf(filter.maxPrice?.toInt()?.toString() ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SquircleLg,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter, vertical = VelnoxSpacing.sm)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.search_filter_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(
                    onClick = {
                        workingFilter = SearchFilter()
                        minPriceInput = ""
                        maxPriceInput = ""
                        onReset()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.search_filter_reset),
                        color = MaterialTheme.colorScheme.error,
                        style = TextXs,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = VelnoxSpacing.sm), color = MaterialTheme.colorScheme.outline)

            // Price Range
            Text(
                text = stringResource(R.string.search_filter_price_range),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(VelnoxSpacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = minPriceInput,
                    onValueChange = { minPriceInput = it.filter { char -> char.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(VelnoxBrand),
                    modifier = Modifier
                        .weight(1f)
                        .clip(SquircleSm)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, SquircleSm)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (minPriceInput.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_filter_min_price),
                                    style = TextXs,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                Text(
                    text = " - ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                BasicTextField(
                    value = maxPriceInput,
                    onValueChange = { maxPriceInput = it.filter { char -> char.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(VelnoxBrand),
                    modifier = Modifier
                        .weight(1f)
                        .clip(SquircleSm)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, SquircleSm)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (maxPriceInput.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_filter_max_price),
                                    style = TextXs,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            Spacer(Modifier.height(VelnoxSpacing.lg))

            // Categories
            if (categories.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.search_filter_categories),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(VelnoxSpacing.xs))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    categories.forEach { cat ->
                        val isSelected = workingFilter.categorySlug == cat.slug
                        val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        val textCol = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .clip(Squircle)
                                .background(bg)
                                .border(
                                    1.dp,
                                    if (isSelected) VelnoxBrand else MaterialTheme.colorScheme.outline,
                                    Squircle,
                                )
                                .velnoxClickable(
                                    role = Role.Button,
                                    onClick = {
                                        workingFilter = if (isSelected) {
                                            workingFilter.copy(categorySlug = null)
                                        } else {
                                            workingFilter.copy(categorySlug = cat.slug)
                                        }
                                    },
                                )
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = cat.displayName ?: cat.name,
                                style = TextXs,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = textCol,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(VelnoxSpacing.lg))
            }

            // Toggles: In Stock Only
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.search_filter_in_stock_only),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = workingFilter.inStockOnly,
                    onCheckedChange = { workingFilter = workingFilter.copy(inStockOnly = it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = VelnoxBrand,
                    ),
                )
            }

            Spacer(Modifier.height(VelnoxSpacing.xl))

            // Apply Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Squircle)
                    .background(VelnoxBrand)
                    .velnoxClickable(
                        role = Role.Button,
                        onClick = {
                            val minVal = minPriceInput.toDoubleOrNull()
                            val maxVal = maxPriceInput.toDoubleOrNull()
                            val updated = workingFilter.copy(
                                minPrice = minVal,
                                maxPrice = maxVal,
                            )
                            onApply(updated)
                        },
                    )
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.search_filter_apply),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(VelnoxSpacing.xl))
        }
    }
}
