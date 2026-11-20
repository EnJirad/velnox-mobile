@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.velnox.app.feature.home

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
import androidx.compose.ui.text.style.TextAlign
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
import com.velnox.app.ui.theme.VelnoxTheme
import com.velnox.app.feature.wishlist.WishlistItem
import com.velnox.app.feature.wishlist.WishlistState
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

/** Chip id for "All". The API is asked for every category when this is selected. */
internal const val ALL_CATEGORY = "all"

/** How long typing must pause before the query is sent to the server. */
private const val SEARCH_DEBOUNCE_MS = 300L

/** How many products the horizontal featured rail shows. */
private const val FEATURED_LIMIT = 6

/**
 * Velnox Home — a fixed toolbar over one coherent lazy feed.
 *
 * Order: fixed search toolbar → seasonal banner → sticky Popular Categories →
 * featured rail → the live product feed. The catalogue comes from the deployed
 * Velnox API (`/api/products/catalog`, `/api/categories/tree`); search is sent to
 * the server after a short debounce and category chips become the `category`
 * query parameter, so the app never downloads the whole database.
 *
 * Every state is explicit. A failed request shows a retry card, never a
 * placeholder product: the app has no demo catalogue to fall back to.
 */
@Composable
fun HomeScreen(
    onOpenCategoryHub: () -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenMessages: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    unreadNotificationsCount: Int = 0,
    onOpenSearch: () -> Unit = {},
) {
    val catalog = (LocalContext.current.applicationContext as VelnoxApplication).catalog
    val isThai = LocalConfiguration.current.locales[0].language == "th"

    var query by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf<List<CatalogCategory>>(emptyList()) }
    var feed by remember { mutableStateOf<HomeFeed>(HomeFeed.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Typing must not fire one request per keystroke.
    LaunchedEffect(query) {
        if (debouncedQuery == query) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = query
    }

    LaunchedEffect(debouncedQuery, reloadKey) {
        feed = HomeFeed.Loading
        if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)

        if (categories.isEmpty()) {
            val loaded = catalog.categories()
            if (loaded is CatalogResult.Ok) categories = loaded.value
        }

        val limit = if (debouncedQuery.isNotBlank()) CatalogQuery.PAGE_SIZE else 40
        feed = catalog.products(
            CatalogQuery(
                search = debouncedQuery.takeIf { it.isNotBlank() },
                limit = limit,
            ),
        ).toFeed()
    }

    val isSearching = query.isNotBlank()

    fun loadMore() {
        val current = feed as? HomeFeed.Content ?: return
        if (!current.hasMore || current.loadingMore) return
        feed = current.copy(loadingMore = true)
        scope.launch {
            val next = catalog.products(
                CatalogQuery(
                    search = debouncedQuery.takeIf { it.isNotBlank() },
                    offset = current.items.size,
                ),
            )
            feed = when (next) {
                is CatalogResult.Ok -> current.copy(
                    items = current.items + next.value.items,
                    hasMore = next.value.hasMore,
                    loadingMore = false,
                )
                // Keep what is already on screen; the retry row stays available.
                is CatalogResult.Failed -> current.copy(loadingMore = false, hasMore = false)
            }
        }
    }

    fun retry() {
        scope.launch {
            // A retry must actually ask the server again.
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
            unreadNotificationsCount = unreadNotificationsCount,
            onOpenNotifications = onOpenNotifications,
            onOpenMessages = onOpenMessages,
            onOpenSearch = onOpenSearch,
        )

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
                if (isSearching) {
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
                }

                when (val current = feed) {
                    HomeFeed.Loading -> item(key = "loading", contentType = "state") {
                        LoadingRow()
                    }

                    HomeFeed.Error -> item(key = "error", contentType = "state") {
                        EmptyState(
                            icon = VelnoxIcons.EyeOff,
                            title = stringResource(R.string.catalog_error_title),
                            body = stringResource(R.string.catalog_error_body),
                            actionLabel = stringResource(R.string.common_retry),
                            onAction = { retry() },
                        )
                    }

                    HomeFeed.Empty -> item(key = "empty", contentType = "state") {
                        EmptyState(
                            icon = VelnoxIcons.Search,
                            title = if (isSearching) {
                                stringResource(R.string.home_search_no_results_title)
                            } else {
                                stringResource(R.string.catalog_empty_title)
                            },
                            body = if (isSearching) {
                                stringResource(R.string.home_search_no_results_body)
                            } else {
                                stringResource(R.string.catalog_empty_body)
                            },
                            actionLabel = if (isSearching) {
                                stringResource(R.string.home_search_clear)
                            } else {
                                null
                            },
                            onAction = if (isSearching) {
                                { query = "" }
                            } else {
                                null
                            },
                        )
                    }

                    is HomeFeed.Content -> {
                        if (!isSearching) {
                            // 2. Promotional banner
                            item(key = "seasonal", contentType = "seasonal") {
                                SeasonalBanner(
                                    onClick = onOpenCategoryHub,
                                    modifier = Modifier.padding(
                                        start = PageGutter,
                                        top = VelnoxSpacing.md,
                                        end = PageGutter,
                                        bottom = VelnoxSpacing.sm,
                                    ),
                                )
                            }

                            // 3. Quick Categories
                            item(key = "quick-categories", contentType = "section") {
                                QuickCategoriesSection(
                                    categories = categories,
                                    onSelectCategory = { onOpenListing(it) },
                                    onViewAll = onOpenCategoryHub,
                                    isThai = isThai,
                                )
                            }

                            val items = current.items
                            val popularProducts = items.take(6)
                            val trendingProducts = items.drop(3).take(6)
                            val bestSellers = items
                                .filter { it.soldCount > 0 }
                                .sortedByDescending { it.soldCount }
                                .take(6)
                                .ifEmpty { items.drop(6).take(6) }
                            val recommendedProducts = items.drop(9).take(6).ifEmpty { items.take(6) }
                            val newArrivals = items.takeLast(6)
                            val specialDeals = items.filter { it.price != null && it.price == 0.0 }

                            // 4. Popular Products
                            if (popularProducts.isNotEmpty()) {
                                item(key = "popular-products", contentType = "section") {
                                    ProductSectionRail(
                                        title = stringResource(R.string.home_popular_products_title),
                                        subtitle = stringResource(R.string.home_popular_products_subtitle),
                                        products = popularProducts,
                                        onOpenProduct = onOpenProduct,
                                        onViewAll = { onOpenListing(ALL_CATEGORY) },
                                    )
                                }
                            }

                            // 5. Trending Products or Trending Categories
                            if (trendingProducts.isNotEmpty()) {
                                item(key = "trending-products", contentType = "section") {
                                    ProductSectionRail(
                                        title = stringResource(R.string.home_trending_title),
                                        subtitle = stringResource(R.string.home_trending_subtitle),
                                        products = trendingProducts,
                                        onOpenProduct = onOpenProduct,
                                        onViewAll = { onOpenListing(ALL_CATEGORY) },
                                    )
                                }
                            }

                            // 6. Best Sellers
                            if (bestSellers.isNotEmpty()) {
                                item(key = "best-sellers", contentType = "section") {
                                    ProductSectionRail(
                                        title = stringResource(R.string.home_best_sellers_title),
                                        subtitle = stringResource(R.string.home_best_sellers_subtitle),
                                        products = bestSellers,
                                        onOpenProduct = onOpenProduct,
                                        onViewAll = { onOpenListing(ALL_CATEGORY) },
                                    )
                                }
                            }

                            // 7. Recommended Products
                            if (recommendedProducts.isNotEmpty()) {
                                item(key = "recommended-products", contentType = "section") {
                                    ProductSectionRail(
                                        title = stringResource(R.string.home_recommended_title),
                                        subtitle = stringResource(R.string.home_recommended_subtitle),
                                        products = recommendedProducts,
                                        onOpenProduct = onOpenProduct,
                                        onViewAll = { onOpenListing(ALL_CATEGORY) },
                                    )
                                }
                            }

                            // 8. New Arrivals
                            if (newArrivals.isNotEmpty()) {
                                item(key = "new-arrivals", contentType = "section") {
                                    ProductSectionRail(
                                        title = stringResource(R.string.home_new_arrivals_title),
                                        subtitle = stringResource(R.string.home_new_arrivals_subtitle),
                                        products = newArrivals,
                                        onOpenProduct = onOpenProduct,
                                        onViewAll = { onOpenListing(ALL_CATEGORY) },
                                    )
                                }
                            }

                            // 9. Special Deals (only when genuine promotions exist)
                            if (specialDeals.isNotEmpty()) {
                                item(key = "special-deals", contentType = "section") {
                                    ProductSectionRail(
                                        title = stringResource(R.string.home_special_deals_title),
                                        subtitle = stringResource(R.string.home_special_deals_subtitle),
                                        products = specialDeals,
                                        onOpenProduct = onOpenProduct,
                                        onViewAll = { onOpenListing(ALL_CATEGORY) },
                                    )
                                }
                            }

                            // Trust Row for trust signals
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

                            // 10. Additional product discovery section
                            item(key = "discovery-header", contentType = "section") {
                                SectionHeader(
                                    title = stringResource(R.string.home_discovery_title),
                                    subtitle = stringResource(R.string.home_discovery_subtitle),
                                    modifier = Modifier.padding(
                                        start = PageGutter,
                                        top = VelnoxSpacing.lg,
                                        end = PageGutter,
                                        bottom = VelnoxSpacing.sm,
                                    ),
                                )
                            }
                        }

                        productRows(
                            products = current.items,
                            columns = columns,
                            onOpenProduct = onOpenProduct,
                        )

                        if (current.hasMore) {
                            item(key = "load-more", contentType = "state") {
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

/** The state of the Home feed. One of these is always true; there is no default. */
private sealed interface HomeFeed {
    data object Loading : HomeFeed
    data object Empty : HomeFeed
    data object Error : HomeFeed
    data class Content(
        val items: List<CatalogProduct>,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
    ) : HomeFeed
}

private fun CatalogResult<CatalogPage>.toFeed(): HomeFeed = when (this) {
    is CatalogResult.Failed -> HomeFeed.Error
    is CatalogResult.Ok -> when {
        value.items.isEmpty() -> HomeFeed.Empty
        else -> HomeFeed.Content(items = value.items, hasMore = value.hasMore)
    }
}

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

// ------------------------------------------------------------------- toolbar

@Composable
private fun HomeToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    unreadNotificationsCount: Int,
    onOpenNotifications: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenSearch: () -> Unit,
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
                .padding(horizontal = VelnoxSpacing.sm, vertical = VelnoxSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(size = 40)
            Spacer(Modifier.width(VelnoxSpacing.xs))
            HomeSearchField(
                query = query,
                onQueryChange = onQueryChange,
                onClearQuery = onClearQuery,
                onOpenSearch = onOpenSearch,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(VelnoxSpacing.xs))
            NotificationBell(
                unreadCount = unreadNotificationsCount,
                onClick = onOpenNotifications,
            )
            Spacer(Modifier.width(VelnoxSpacing.xs))
            HeaderMessagesButton(onClick = onOpenMessages)
        }
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun HeaderMessagesButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(Circle)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .velnoxClickable(role = Role.Button, onClick = onClick),
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

@Composable
private fun HomeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, Squircle)
            .velnoxClickable(role = Role.Button, onClick = onOpenSearch)
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
            Text(
                text = if (query.isEmpty()) stringResource(R.string.home_search_placeholder) else query,
                style = MaterialTheme.typography.bodyMedium,
                color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
private fun NotificationBell(
    unreadCount: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(Circle)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .velnoxClickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = VelnoxIcons.Bell,
            contentDescription = stringResource(R.string.home_notifications),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(19.dp),
        )
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 5.dp)
                    .clip(Circle)
                    .background(VelnoxBrand)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                    style = Text2xs,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// --------------------------------------------------------------- quick categories

@Composable
private fun QuickCategoriesSection(
    categories: List<CatalogCategory>,
    onSelectCategory: (String) -> Unit,
    onViewAll: () -> Unit,
    isThai: Boolean,
    modifier: Modifier = Modifier,
) {
    if (categories.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = VelnoxSpacing.sm),
    ) {
        SectionHeader(
            title = stringResource(R.string.home_quick_categories_title),
            subtitle = stringResource(R.string.home_quick_categories_subtitle),
            action = stringResource(R.string.home_see_all),
            onAction = onViewAll,
            modifier = Modifier.padding(horizontal = PageGutter),
        )
        Spacer(Modifier.height(VelnoxSpacing.sm))
        LazyRow(
            contentPadding = PaddingValues(horizontal = PageGutter),
            horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
        ) {
            items(categories, key = { it.slug }, contentType = { "quickCategory" }) { category ->
                QuickCategoryCard(
                    icon = categoryIcon(category.slug),
                    label = if (isThai) category.displayName ?: category.name else category.name,
                    onClick = { onSelectCategory(category.slug) },
                )
            }
        }
    }
}

@Composable
private fun QuickCategoryCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(82.dp)
            .clip(Squircle)
            .velnoxClickable(role = Role.Button, onClick = onClick)
            .padding(vertical = VelnoxSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(Squircle)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, Squircle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VelnoxBrand,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(VelnoxSpacing.xs))
        Text(
            text = label,
            style = Text2xs.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            minLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The backend returns a Lucide icon name per category (`plug`, `shirt`, …).
 * This map is presentation only: an unknown name never invents a category, it
 * just falls back to the neutral tag glyph.
 */
internal fun categoryIcon(slug: String): ImageVector = when {
    slug.contains("charger") || slug.contains("cable") || slug.contains("phone") -> VelnoxIcons.Headphones
    slug.contains("clothing") || slug.contains("shirt") || slug.contains("fashion") -> VelnoxIcons.Shirt
    slug.contains("electronic") || slug.contains("computer") -> VelnoxIcons.Laptop
    slug.contains("kitchen") || slug.contains("food") || slug.contains("cook") -> VelnoxIcons.Coffee
    slug.contains("beauty") || slug.contains("health") -> VelnoxIcons.Sparkles
    else -> VelnoxIcons.Tag
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

// --------------------------------------------------------------- product sections

@Composable
private fun ProductSectionRail(
    title: String,
    subtitle: String? = null,
    products: List<CatalogProduct>,
    onOpenProduct: (String) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (products.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = VelnoxSpacing.sm),
    ) {
        SectionHeader(
            title = title,
            subtitle = subtitle,
            action = stringResource(R.string.home_see_all),
            onAction = onViewAll,
            modifier = Modifier.padding(horizontal = PageGutter),
        )
        Spacer(Modifier.height(VelnoxSpacing.sm))
        LazyRow(
            contentPadding = PaddingValues(horizontal = PageGutter),
            horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
        ) {
            items(products, key = { it.id }, contentType = { "productCard" }) { product ->
                ProductCard(
                    product = product,
                    modifier = Modifier.width(160.dp),
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
        Box {
            ProductThumbnail(
                url = product.imageUrl,
                name = product.name,
                outOfStock = product.inStock == false,
                modifier = Modifier.aspectRatio(1f),
            )
            val isFav = WishlistState.isFavorite(product.id)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(Circle)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .velnoxClickable(role = Role.Button) {
                        WishlistState.toggle(
                            WishlistItem(
                                productId = product.id,
                                name = product.name,
                                price = product.displayPrice,
                                currency = product.currency,
                                imageUrl = product.imageUrl,
                                shopName = product.shopName,
                                inStock = product.inStock ?: true,
                                rating = product.rating,
                            )
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isFav) VelnoxIcons.HeartFill else VelnoxIcons.Heart,
                    contentDescription = null,
                    tint = if (isFav) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
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
        val priceToDisplay = product.displayPrice
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                PriceFormat.format(priceToDisplay, product.currency, locale)?.let { price ->
                    Text(
                        text = price,
                        style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
                if (product.compareAtPrice != null && priceToDisplay != null && product.compareAtPrice > priceToDisplay) {
                    PriceFormat.format(product.compareAtPrice, product.currency, locale)?.let { original ->
                        Text(
                            text = original,
                            style = Text2xs.copy(
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            product.discountPercent?.takeIf { it > 0 }?.let { discount ->
                Text(
                    text = "-$discount%",
                    style = Text2xs.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(SquircleXs)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            } ?: Icon(
                imageVector = VelnoxIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
        if (product.soldCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.product_sold, product.soldCount),
                style = Text2xs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The product image, loaded from the URL the API returns (Cloudflare R2 today).
 *
 * A missing or unloadable image is a neutral tile with the package glyph: the app
 * has no stock photography to fall back on, and pretending a product looks like
 * something it does not would be worse than showing nothing.
 */
@Composable
fun ProductThumbnail(
    url: String?,
    name: String,
    outOfStock: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = VelnoxIcons.Package,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
        if (outOfStock) {
            Text(
                text = stringResource(R.string.product_out_of_stock),
                style = Text2xs.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(SquircleXs)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * Product art for the screens that are still demo data (Cart, Orders).
 *
 * The catalogue itself no longer uses it: Home, categories and the product page
 * render the seller's real image. It stays here, unchanged, so the unrelated
 * demo screens do not break while they wait for their own API work.
 */
enum class ProductArt { Jacket, Headphones, Tote, Watch, Mug, Lamp, Sneaker, Serum }

/**
 * Flat vector product art. Colour is derived from theme roles, so a card never
 * ships a bright light-mode tile in dark mode.
 */
@Composable
fun ProductArtTile(
    art: ProductArt,
    modifier: Modifier = Modifier,
) {
    val tint = artTint(art)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(Squircle)
            .background(tint.soft),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = tint.strong.copy(alpha = 0.10f),
                radius = size.width * 0.52f,
                center = Offset(size.width * 0.18f, size.height * 0.12f),
            )
        }
        ProductGlyph(
            art = art,
            color = tint.strong,
            modifier = Modifier.size(56.dp),
        )
    }
}

private data class ArtTint(val soft: Color, val strong: Color)

@Composable
private fun artTint(art: ProductArt): ArtTint = when (art) {
    ProductArt.Headphones, ProductArt.Serum -> ArtTint(
        soft = MaterialTheme.colorScheme.secondaryContainer,
        strong = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    else -> ArtTint(
        soft = MaterialTheme.colorScheme.surfaceVariant,
        strong = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Minimal line glyphs, drawn on the same 24-unit grid as the icon set. */
@Composable
private fun ProductGlyph(
    art: ProductArt,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val s = size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val stroke = 1.6f * s

        when (art) {
            ProductArt.Jacket -> {
                val body = Path().apply {
                    moveTo(p(9f, 3f).x, p(9f, 3f).y)
                    lineTo(p(4f, 6f).x, p(4f, 6f).y)
                    lineTo(p(3f, 12f).x, p(3f, 12f).y)
                    lineTo(p(6f, 13f).x, p(6f, 13f).y)
                    lineTo(p(6f, 21f).x, p(6f, 21f).y)
                    lineTo(p(18f, 21f).x, p(18f, 21f).y)
                    lineTo(p(18f, 13f).x, p(18f, 13f).y)
                    lineTo(p(21f, 12f).x, p(21f, 12f).y)
                    lineTo(p(20f, 6f).x, p(20f, 6f).y)
                    lineTo(p(15f, 3f).x, p(15f, 3f).y)
                }
                drawPath(body, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
                drawLine(color, p(12f, 3f), p(12f, 21f), strokeWidth = stroke, cap = StrokeCap.Round)
            }

            ProductArt.Headphones -> {
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = p(3f, 4f),
                    size = androidx.compose.ui.geometry.Size(18f * s, 18f * s),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
                )
                drawRoundRect(
                    color = color,
                    topLeft = p(3f, 13f),
                    size = androidx.compose.ui.geometry.Size(5f * s, 8f * s),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * s),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
                drawRoundRect(
                    color = color,
                    topLeft = p(16f, 13f),
                    size = androidx.compose.ui.geometry.Size(5f * s, 8f * s),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * s),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
            }

            ProductArt.Tote -> {
                drawRoundRect(
                    color = color,
                    topLeft = p(5f, 8f),
                    size = androidx.compose.ui.geometry.Size(14f * s, 13f * s),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * s),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = p(8f, 3f),
                    size = androidx.compose.ui.geometry.Size(8f * s, 8f * s),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
                )
            }

            ProductArt.Watch -> {
                drawCircle(
                    color = color,
                    radius = 5f * s,
                    center = p(12f, 12f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
                drawLine(color, p(12f, 12f), p(12f, 9f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, p(12f, 12f), p(14f, 13f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, p(10f, 5f), p(10f, 2f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, p(14f, 5f), p(14f, 2f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, p(10f, 19f), p(10f, 22f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, p(14f, 19f), p(14f, 22f), strokeWidth = stroke, cap = StrokeCap.Round)
            }

            ProductArt.Mug -> {
                drawRoundRect(
                    color = color,
                    topLeft = p(4f, 7f),
                    size = androidx.compose.ui.geometry.Size(11f * s, 14f * s),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * s),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
                drawArc(
                    color = color,
                    startAngle = -80f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = p(14f, 10f),
                    size = androidx.compose.ui.geometry.Size(7f * s, 7f * s),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
                )
            }

            ProductArt.Lamp -> {
                drawLine(color, p(7f, 21f), p(17f, 21f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, p(12f, 21f), p(12f, 12f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawRoundRect(
                    color = color,
                    topLeft = p(6f, 3f),
                    size = androidx.compose.ui.geometry.Size(12f * s, 9f * s),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * s),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
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
