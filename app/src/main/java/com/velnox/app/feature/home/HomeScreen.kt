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
import com.velnox.app.ui.theme.VelnoxTheme

/**
 * Velnox Home — a fixed toolbar over one coherent lazy feed.
 *
 * Order: fixed search toolbar → seasonal banner → sticky Popular Categories →
 * featured rail → complete local product feed. Search filters the same feed
 * locally; category chips filter it in place. The toolbar never scrolls, and
 * the category header pins to the bottom edge of the toolbar until the user
 * returns toward the seasonal banner.
 */
@Composable
fun HomeScreen(
    onOpenCategoryHub: () -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
    catalog: CatalogSource = HomeCatalog,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(HomeCatalog.ALL_CATEGORY) }
    val listState = rememberLazyListState()

    val searchResults = remember(query, context, catalog) {
        if (query.isBlank()) emptyList() else catalog.search(query, context)
    }
    val isSearching = query.isNotBlank()
    val products = remember(isSearching, selectedCategory, searchResults, catalog) {
        if (isSearching) searchResults else catalog.productsIn(selectedCategory)
    }
    val selectedCategoryLabel = catalog.categories()
        .firstOrNull { it.id == selectedCategory }
        ?.label
        ?.let { stringResource(it) }
        ?: stringResource(R.string.home_all_products_title)
    val categories = remember(catalog) { catalog.categories() }

    LaunchedEffect(query) {
        if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
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
                if (!isSearching) {
                    item(key = "seasonal", contentType = "seasonal") {
                        SeasonalBanner(
                            onClick = { onOpenListing(HomeCatalog.ALL_CATEGORY) },
                            modifier = Modifier.padding(
                                start = PageGutter,
                                top = VelnoxSpacing.md,
                                end = PageGutter,
                                bottom = VelnoxSpacing.md,
                            ),
                        )
                    }

                    // Sticky: pins directly below the fixed toolbar.
                    stickyHeader(key = "popular-categories", contentType = "categories") {
                        PopularCategoriesHeader(
                            categories = categories,
                            selectedCategory = selectedCategory,
                            onSelectCategory = { selectedCategory = it },
                            onOpenCategories = onOpenCategoryHub,
                        )
                    }

                    item(key = "trust", contentType = "trust") {
                        Box(Modifier.padding(horizontal = PageGutter, vertical = VelnoxSpacing.md)) {
                            TrustRow()
                        }
                    }

                    item(key = "featured", contentType = "section") {
                        FeaturedSection(
                            products = catalog.productsIn(selectedCategory).take(6),
                            onOpenProduct = onOpenProduct,
                            onSeeAll = { onOpenListing(selectedCategory) },
                        )
                    }

                    item(key = "feed-header", contentType = "section") {
                        SectionHeader(
                            title = if (selectedCategory == HomeCatalog.ALL_CATEGORY) {
                                stringResource(R.string.home_all_products_title)
                            } else {
                                selectedCategoryLabel
                            },
                            modifier = Modifier.padding(
                                start = PageGutter,
                                top = VelnoxSpacing.lg,
                                end = PageGutter,
                                bottom = VelnoxSpacing.sm,
                            ),
                        )
                    }
                } else {
                    item(key = "search-header", contentType = "section") {
                        Column(
                            modifier = Modifier.padding(
                                start = PageGutter,
                                top = VelnoxSpacing.lg,
                                end = PageGutter,
                                bottom = VelnoxSpacing.sm,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.home_search_results_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }

                if (products.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        EmptyState(
                            icon = if (isSearching) VelnoxIcons.Search else VelnoxIcons.Tag,
                            title = stringResource(
                                if (isSearching) R.string.home_search_no_results_title
                                else R.string.home_category_empty_title
                            ),
                            body = stringResource(
                                if (isSearching) R.string.home_search_no_results_body
                                else R.string.home_category_empty_body
                            ),
                            actionLabel = if (isSearching) {
                                stringResource(R.string.home_search_clear)
                            } else {
                                stringResource(R.string.home_all_products_title)
                            },
                            onAction = {
                                if (isSearching) {
                                    query = ""
                                } else {
                                    selectedCategory = HomeCatalog.ALL_CATEGORY
                                }
                            },
                        )
                    }
                } else {
                    productRows(
                        products = products,
                        columns = columns,
                        onOpenProduct = onOpenProduct,
                    )
                }

                item(key = "preview-note", contentType = "note") {
                    Text(
                        text = stringResource(R.string.home_preview_note),
                        style = Text2xs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = PageGutter,
                            top = VelnoxSpacing.lg,
                            end = PageGutter,
                        ),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------- toolbar

@Composable
private fun HomeToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    notifications: List<HomeNotification>,
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
        Box(modifier = Modifier.weight(1f)) {
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

// --------------------------------------------------------------- categories

@Composable
private fun PopularCategoriesHeader(
    categories: List<Category>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onOpenCategories: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp)
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = PageGutter, top = VelnoxSpacing.md, end = PageGutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_categories_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(Circle)
                    .velnoxClickable(role = Role.Button, onClick = onOpenCategories),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VelnoxIcons.ArrowRight,
                    contentDescription = stringResource(R.string.home_category_open),
                    tint = VelnoxBrand,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = PageGutter),
            horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
            modifier = Modifier.padding(bottom = VelnoxSpacing.sm),
        ) {
            items(categories, key = { it.id }, contentType = { "category" }) { category ->
                CategoryChip(
                    category = category,
                    selected = category.id == selectedCategory,
                    onClick = { onSelectCategory(category.id) },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun CategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
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
            imageVector = category.icon,
            contentDescription = null,
            tint = if (selected) VelnoxBrand else content,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(category.label),
            style = TextXs.copy(fontWeight = FontWeight.Medium),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

@Composable
private fun FeaturedSection(
    products: List<ProductPreview>,
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
    products: List<ProductPreview>,
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
            repeat(columns - row.size) {
                Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(VelnoxSpacing.sm))
    }
}

@Composable
internal fun ProductCard(
    product: ProductPreview,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
            .velnoxClickable(role = Role.Button, onClick = onClick)
            .padding(VelnoxSpacing.sm),
    ) {
        ProductArtTile(art = product.art, modifier = Modifier.aspectRatio(1f))
        Spacer(Modifier.height(VelnoxSpacing.sm))
        Text(
            text = stringResource(product.name),
            style = TextXs.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            minLines = 2,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(product.store),
            style = Text2xs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(product.tag),
                style = Text2xs,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .clip(SquircleXs)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = VelnoxIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

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

// ------------------------------------------------------------------- previews

@Preview(name = "Home phone", showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun HomePhonePreview() {
    VelnoxTheme(darkTheme = false) {
        HomeScreen(onOpenCategoryHub = {}, onOpenListing = {}, onOpenProduct = {})
    }
}

@Preview(name = "Home wide", showBackground = true, widthDp = 600, heightDp = 900)
@Composable
private fun HomeWidePreview() {
    VelnoxTheme(darkTheme = false) {
        HomeScreen(onOpenCategoryHub = {}, onOpenListing = {}, onOpenProduct = {})
    }
}

@Preview(name = "Home 320dp", showBackground = true, widthDp = 320, heightDp = 760)
@Composable
private fun HomeCompactPreview() {
    VelnoxTheme(darkTheme = false) {
        HomeScreen(onOpenCategoryHub = {}, onOpenListing = {}, onOpenProduct = {})
    }
}

@Preview(name = "Home dark", showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun HomeDarkPreview() {
    VelnoxTheme(darkTheme = true) {
        HomeScreen(onOpenCategoryHub = {}, onOpenListing = {}, onOpenProduct = {})
    }
}
