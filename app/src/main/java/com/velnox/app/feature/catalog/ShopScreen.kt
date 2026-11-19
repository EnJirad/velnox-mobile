package com.velnox.app.feature.catalog

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.velnox.app.R
import com.velnox.app.VelnoxApplication
import com.velnox.app.core.catalog.CatalogError
import com.velnox.app.core.catalog.CatalogResult
import com.velnox.app.core.catalog.ShopDetail
import com.velnox.app.feature.home.ProductCard
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PageGutter
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

/**
 * Seller Shop screen displaying seller profile, metadata, and their real catalog products.
 */
@Composable
fun ShopScreen(
    shopSlugOrId: String,
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalog = (LocalContext.current.applicationContext as VelnoxApplication).catalog

    var state by remember(shopSlugOrId) {
        mutableStateOf<ShopState>(ShopState.Loading)
    }
    var reloadKey by remember(shopSlugOrId) { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(shopSlugOrId, reloadKey) {
        state = ShopState.Loading
        state = when (val result = catalog.shop(shopSlugOrId, force = reloadKey > 0)) {
            is CatalogResult.Ok -> ShopState.Content(result.value)
            is CatalogResult.Failed -> when (result.error) {
                CatalogError.NotFound -> ShopState.NotFound
                CatalogError.Network, CatalogError.Server -> ShopState.Error
            }
        }
    }

    val title = (state as? ShopState.Content)?.detail?.shop?.name.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(
            title = title.ifBlank { stringResource(R.string.product_seller_title) },
            onBack = onBack,
        )

        when (val current = state) {
            ShopState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }

            ShopState.Error -> EmptyState(
                icon = VelnoxIcons.EyeOff,
                title = stringResource(R.string.catalog_error_title),
                body = stringResource(R.string.catalog_error_body),
                actionLabel = stringResource(R.string.common_retry),
                onAction = { reloadKey++ },
            )

            ShopState.NotFound -> EmptyState(
                icon = VelnoxIcons.ShoppingBag,
                title = stringResource(R.string.product_unavailable_title),
                body = stringResource(R.string.product_unavailable_body),
                actionLabel = stringResource(R.string.common_back),
                onAction = onBack,
            )

            is ShopState.Content -> {
                val shop = current.detail.shop
                val filteredProducts = if (searchQuery.isBlank()) {
                    current.detail.products
                } else {
                    current.detail.products.filter {
                        it.name.contains(searchQuery, ignoreCase = true)
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = PageGutter,
                        top = VelnoxSpacing.md,
                        end = PageGutter,
                        bottom = VelnoxSpacing.xxl,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Shop Header Card
                    item(span = { GridItemSpan(2) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SquircleLg)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
                                .padding(VelnoxSpacing.md),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(VelnoxBrand.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (shop.logoUrl != null) {
                                        AsyncImage(
                                            model = shop.logoUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Icon(
                                            imageVector = VelnoxIcons.ShoppingBag,
                                            contentDescription = null,
                                            tint = VelnoxBrand,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }

                                Spacer(Modifier.width(VelnoxSpacing.md))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = shop.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )

                                    if (shop.isVerified) {
                                        Spacer(Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = VelnoxIcons.ShieldCheck,
                                                contentDescription = null,
                                                tint = VelnoxBrand,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.product_shop_verified),
                                                style = Text2xs.copy(fontWeight = FontWeight.Medium),
                                                color = VelnoxBrand,
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.category_product_count, current.detail.products.size),
                                        style = Text2xs,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            shop.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                Spacer(Modifier.height(VelnoxSpacing.sm))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // Shop Product Search Input
                    item(span = { GridItemSpan(2) }) {
                        Row(
                            modifier = Modifier
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
                            Spacer(Modifier.width(8.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.product_shop_search_hint),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    innerTextField()
                                },
                            )
                            if (searchQuery.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .velnoxClickable(role = Role.Button) { searchQuery = "" },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = VelnoxIcons.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Section header
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            text = stringResource(R.string.product_shop_all_products),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = VelnoxSpacing.xs),
                        )
                    }

                    if (filteredProducts.isEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.product_shop_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(filteredProducts, key = { it.id }) { product ->
                            ProductCard(
                                product = product,
                                onClick = { onOpenProduct("product:${product.id}") },
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface ShopState {
    data object Loading : ShopState
    data object Error : ShopState
    data object NotFound : ShopState
    data class Content(val detail: ShopDetail) : ShopState
}
