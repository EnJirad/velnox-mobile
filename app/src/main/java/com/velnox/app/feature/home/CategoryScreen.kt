@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.VelnoxApplication
import com.velnox.app.core.catalog.CatalogCategory
import com.velnox.app.core.catalog.CatalogProduct
import com.velnox.app.core.catalog.CatalogQuery
import com.velnox.app.core.catalog.CatalogResult
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.TopBar
import com.velnox.app.ui.components.VelnoxSpacing
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand

/**
 * Category browsing over the real category tree.
 *
 * With no [initialCategoryId] the screen is the hub: every active category the
 * backend reports, with its published-product count. With an id it is that
 * category's listing. Both read `GET /api/categories/tree` and
 * `GET /api/products/catalog?category=<slug>`, so an empty category says so
 * instead of showing invented products.
 */
@Composable
fun CategoryScreen(
    initialCategoryId: String?,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalog = (LocalContext.current.applicationContext as VelnoxApplication).catalog
    val isThai = LocalConfiguration.current.locales[0].language == "th"

    var categories by remember { mutableStateOf<List<CatalogCategory>>(emptyList()) }
    var categoriesLoaded by remember { mutableStateOf(false) }
    var selected by remember(initialCategoryId) { mutableStateOf(initialCategoryId) }
    var products by remember { mutableStateOf<List<CatalogProduct>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        if (categoriesLoaded) return@LaunchedEffect
        when (val result = catalog.categories()) {
            is CatalogResult.Ok -> {
                // Most-populated first; the count comes from the backend.
                categories = result.value.sortedWith(
                    compareByDescending<CatalogCategory> { it.productCount }
                        .thenBy { it.name },
                )
                categoriesLoaded = true
            }
            is CatalogResult.Failed -> failed = true
        }
    }

    LaunchedEffect(selected, reloadKey) {
        val slug = selected ?: return@LaunchedEffect
        products = null
        when (val result = catalog.products(CatalogQuery(category = slug))) {
            is CatalogResult.Ok -> {
                products = result.value.items
                failed = false
            }
            is CatalogResult.Failed -> {
                failed = true
                products = null
            }
        }
    }

    val title = when {
        selected == null -> stringResource(R.string.home_categories_title)
        else -> categories.firstOrNull { it.slug == selected }
            ?.let { if (isThai) it.displayName ?: it.name else it.name }
            ?: stringResource(R.string.home_categories_title)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(title = title, onBack = onBack)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (selected == null) {
                CategoryHub(
                    categories = categories,
                    loaded = categoriesLoaded,
                    failed = failed,
                    isThai = isThai,
                    onRetry = {
                        failed = false
                        reloadKey++
                    },
                    onOpenCategory = { slug -> onOpenListing(slug) },
                )
            } else {
                CategoryListing(
                    categories = categories,
                    selected = selected,
                    isThai = isThai,
                    products = products,
                    failed = failed,
                    onSelect = { selected = it },
                    onRetry = {
                        failed = false
                        products = null
                        reloadKey++
                    },
                    onOpenProduct = onOpenProduct,
                )
            }
        }
    }
}

@Composable
private fun CategoryHub(
    categories: List<CatalogCategory>,
    loaded: Boolean,
    failed: Boolean,
    isThai: Boolean,
    onRetry: () -> Unit,
    onOpenCategory: (String) -> Unit,
) {
    when {
        failed && categories.isEmpty() -> EmptyState(
            icon = VelnoxIcons.EyeOff,
            title = stringResource(R.string.catalog_error_title),
            body = stringResource(R.string.catalog_error_body),
            actionLabel = stringResource(R.string.common_retry),
            onAction = onRetry,
        )

        !loaded -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }

        categories.isEmpty() -> EmptyState(
            icon = VelnoxIcons.Grid,
            title = stringResource(R.string.catalog_empty_title),
            body = stringResource(R.string.catalog_empty_body),
        )

        else -> LazyColumn(
            contentPadding = PaddingValues(
                start = PageGutter,
                end = PageGutter,
                bottom = VelnoxSpacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
        ) {
            items(categories, key = { it.slug }) { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(Squircle)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, Squircle)
                        .velnoxClickable(role = Role.Button) { onOpenCategory(category.slug) }
                        .padding(horizontal = VelnoxSpacing.md, vertical = VelnoxSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = categoryIcon(category.slug),
                        contentDescription = null,
                        tint = VelnoxBrand,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(VelnoxSpacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isThai) {
                                category.displayName ?: category.name
                            } else {
                                category.name
                            },
                            style = TextXs.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.category_product_count,
                                category.productCount,
                            ),
                            style = Text2xs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = VelnoxIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryListing(
    categories: List<CatalogCategory>,
    selected: String,
    isThai: Boolean,
    products: List<CatalogProduct>?,
    failed: Boolean,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenProduct: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = PageGutter),
            horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
            modifier = Modifier.padding(bottom = VelnoxSpacing.md),
        ) {
            items(categories, key = { it.slug }) { category ->
                val isSelected = category.slug == selected
                Row(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(Squircle)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) VelnoxBrand else MaterialTheme.colorScheme.outline,
                            shape = Squircle,
                        )
                        .velnoxClickable(role = Role.Button) { onSelect(category.slug) }
                        .semantics { this.selected = isSelected }
                        .padding(horizontal = VelnoxSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isThai) {
                            category.displayName ?: category.name
                        } else {
                            category.name
                        },
                        style = TextXs.copy(fontWeight = FontWeight.Medium),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        when {
            failed -> EmptyState(
                icon = VelnoxIcons.EyeOff,
                title = stringResource(R.string.catalog_error_title),
                body = stringResource(R.string.catalog_error_body),
                actionLabel = stringResource(R.string.common_retry),
                onAction = onRetry,
            )

            products == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }

            products.isEmpty() -> EmptyState(
                icon = VelnoxIcons.Package,
                title = stringResource(R.string.home_category_empty_title),
                body = stringResource(R.string.home_category_empty_body),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = PageGutter,
                    end = PageGutter,
                    bottom = VelnoxSpacing.xxl,
                ),
            ) {
                items(products.chunked(2), key = { row -> row.joinToString("|") { it.id } }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
                    ) {
                        row.forEach { product ->
                            ProductCard(
                                product = product,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenProduct(product.id) },
                            )
                        }
                        repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(VelnoxSpacing.sm))
                }
            }
        }
    }
}
