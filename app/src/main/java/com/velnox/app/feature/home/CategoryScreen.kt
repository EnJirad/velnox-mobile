@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.StandaloneScreen
import com.velnox.app.ui.components.VelnoxSpacing
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxTheme
import androidx.compose.ui.tooling.preview.Preview

/**
 * Dedicated category destination.
 *
 * [initialCategoryId] null shows the category hub. A non-null id shows that
 * category's local product listing; its Back arrow returns to the hub, and the
 * hub's Back arrow returns to Home.
 */
@Composable
fun CategoryScreen(
    initialCategoryId: String?,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
    catalog: CatalogSource = HomeCatalog,
) {
    val category = initialCategoryId?.let { id ->
        catalog.categories().firstOrNull { it.id == id }
    }
    if (category == null) {
        CategoryHub(
            catalog = catalog,
            onBack = onBack,
            onOpenListing = onOpenListing,
            modifier = modifier,
        )
    } else {
        CategoryListing(
            category = category,
            catalog = catalog,
            onBack = onBack,
            onOpenListing = onOpenListing,
            onOpenProduct = onOpenProduct,
            modifier = modifier,
        )
    }
}

@Composable
private fun CategoryHub(
    catalog: CatalogSource,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StandaloneScreen(
        title = stringResource(R.string.home_categories_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.home_rail_categories_subtitle),
            style = TextXs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(VelnoxSpacing.md))

        catalog.categories().chunked(2).forEach { rowCategories ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
            ) {
                rowCategories.forEach { category ->
                    CategoryBrowseCard(
                        category = category,
                        count = catalog.productsIn(category.id).size,
                        onClick = { onOpenListing(category.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowCategories.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(VelnoxSpacing.sm))
        }
    }
}

@Composable
private fun CategoryBrowseCard(
    category: Category,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = 112.dp)
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
            .velnoxClickable(role = Role.Button, onClick = onClick)
            .padding(VelnoxSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(SquircleSm)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.height(VelnoxSpacing.sm))
        Text(
            text = stringResource(category.label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = count.toString(),
            style = Text2xs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryListing(
    category: Category,
    catalog: CatalogSource,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val products = catalog.productsIn(category.id)

    StandaloneScreen(
        title = stringResource(category.label),
        onBack = onBack,
        modifier = modifier,
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(catalog.categories(), key = { it.id }, contentType = { "category" }) { item ->
                ListingCategoryChip(
                    category = item,
                    selected = item.id == category.id,
                    onClick = { onOpenListing(item.id) },
                )
            }
        }
        Spacer(Modifier.height(VelnoxSpacing.md))

        if (products.isEmpty()) {
            EmptyState(
                icon = VelnoxIcons.Tag,
                title = stringResource(R.string.home_category_empty_title),
                body = stringResource(R.string.home_category_empty_body),
                actionLabel = stringResource(R.string.home_all_products_title),
                onAction = { onOpenListing(HomeCatalog.ALL_CATEGORY) },
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = if (maxWidth < 400.dp) 2 else 3
                Column(verticalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm)) {
                    products.chunked(columns).forEach { rowProducts ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(VelnoxSpacing.sm),
                        ) {
                            rowProducts.forEach { product ->
                                ProductCard(
                                    product = product,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOpenProduct(product.id) },
                                )
                            }
                            repeat(columns - rowProducts.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(VelnoxSpacing.lg))
    }
}

@Composable
private fun ListingCategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(Squircle)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface,
            )
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
            tint = if (selected) VelnoxBrand else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(category.label),
            style = TextXs.copy(fontWeight = FontWeight.Medium),
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CategoryHubPreview() {
    VelnoxTheme(darkTheme = false) {
        CategoryScreen(initialCategoryId = null, onBack = {}, onOpenListing = {}, onOpenProduct = {})
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CategoryListingPreview() {
    VelnoxTheme(darkTheme = true) {
        CategoryScreen(
            initialCategoryId = "fashion",
            onBack = {},
            onOpenListing = {},
            onOpenProduct = {},
        )
    }
}
