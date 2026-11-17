@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.ui.components.BrandBadge
import com.velnox.app.ui.components.BrandMark
import com.velnox.app.ui.components.ContentMaxWidth
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.SectionHeader
import com.velnox.app.ui.components.TrustRow
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Circle
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.SquircleXs
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxBrandForeground
import com.velnox.app.ui.theme.VelnoxBrandSoft
import com.velnox.app.ui.theme.VelnoxForeground
import com.velnox.app.ui.theme.VelnoxPrimary
import com.velnox.app.ui.theme.VelnoxPrimaryForeground
import com.velnox.app.ui.theme.VelnoxTheme

/**
 * Velnox Home — the marketplace feed of the app.
 *
 * Layout order follows discovery priority: who/where → what can I find →
 * browse by category → promoted collection → curated rails → full catalogue.
 * Content is inset by [PageGutter] and capped at [ContentMaxWidth], and every
 * column count is derived from the measured width, so the same code serves a
 * 320dp phone and a 600dp tablet.
 *
 * All product data is UI preview data from [HomeCatalog]; nothing here talks to
 * a backend.
 */
@Composable
fun HomeScreen(
    onOpenNotifications: () -> Unit,
    onOpenFilter: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenTab: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Feed is capped and centred so tablets keep a readable line length,
        // while the bottom bar always spans the window.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            HomeFeed(
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxHeight(),
                onOpenNotifications = onOpenNotifications,
                onOpenFilter = onOpenFilter,
                onOpenCategory = onOpenCategory,
                onOpenProduct = onOpenProduct,
            )
        }
        HomeBottomBar(onSelect = onOpenTab)
    }
}

@Composable
private fun HomeFeed(
    modifier: Modifier = Modifier,
    onOpenNotifications: () -> Unit,
    onOpenFilter: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        item { HomeHeader(onOpenNotifications) }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter),
            ) {
                Spacer(Modifier.height(16.dp))
                SearchBar(onOpenFilter = onOpenFilter)
                Spacer(Modifier.height(16.dp))
                TrustRow()
                Spacer(Modifier.height(20.dp))
            }
        }

        item {
            HomeBand(title = stringResource(R.string.home_categories_title)) {
                Row(
                    modifier = Modifier.padding(horizontal = PageGutter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeCatalog.categories.forEach { category ->
                        CategoryCard(
                            category = category,
                            selected = category.id == "all",
                            onClick = { onOpenCategory(category.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter),
            ) {
                HeroBanner(onClick = { onOpenCategory("select") })
            }
        }

        item {
            Spacer(Modifier.height(28.dp))
            HomeBand(
                title = stringResource(R.string.home_rail_categories_title),
                subtitle = stringResource(R.string.home_rail_categories_subtitle),
            ) {
                ShopByCategoryRail(onOpenCategory = onOpenCategory)
            }
        }

        item {
            Spacer(Modifier.height(32.dp))
            GridSection(
                eyebrow = stringResource(R.string.home_grid_eyebrow),
                title = stringResource(R.string.home_grid_title),
                subtitle = stringResource(R.string.home_grid_subtitle),
                products = HomeCatalog.recommended,
                onOpenProduct = onOpenProduct,
            )
        }

        item {
            Spacer(Modifier.height(32.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter),
            ) {
                SectionHeader(
                    title = stringResource(R.string.home_new_title),
                    action = stringResource(R.string.home_new_action),
                    onAction = { onOpenCategory("new") },
                )
            }
            Spacer(Modifier.height(12.dp))
            ProductRail(
                products = HomeCatalog.newArrivals,
                onOpenProduct = onOpenProduct,
                cardWidth = 156,
            )
        }

        item {
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.home_preview_note),
                style = Text2xs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PageGutter),
            )
        }
    }
}
    onOpenFilter: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenTab: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = ContentMaxWidth),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
            item { HomeHeader(onOpenNotifications) }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter),
                ) {
                    Spacer(Modifier.height(16.dp))
                    SearchBar(onOpenFilter = onOpenFilter)
                    Spacer(Modifier.height(16.dp))
                    TrustRow()
                    Spacer(Modifier.height(20.dp))
                }
            }

            item {
                val categories = HomeCatalog.categories
                HomeBand(title = stringResource(R.string.home_categories_title)) {
                    Row(
                        modifier = Modifier.padding(horizontal = PageGutter),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        categories.forEach { category ->
                            CategoryCard(
                                category = category,
                                selected = category.id == "all",
                                onClick = { onOpenCategory(category.id) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter),
                ) {
                    HeroBanner(onClick = { onOpenCategory("select") })
                }
            }

            item {
                Spacer(Modifier.height(28.dp))
                HomeBand(
                    title = stringResource(R.string.home_rail_categories_title),
                    subtitle = stringResource(R.string.home_rail_categories_subtitle),
                ) {
                    ShopByCategoryRail(onOpenCategory = onOpenCategory)
                }
            }

            item {
                Spacer(Modifier.height(32.dp))
                GridSection(
                    eyebrow = stringResource(R.string.home_grid_eyebrow),
                    title = stringResource(R.string.home_grid_title),
                    subtitle = stringResource(R.string.home_grid_subtitle),
                    products = HomeCatalog.recommended,
                    onOpenProduct = onOpenProduct,
                )
            }

            item {
                Spacer(Modifier.height(32.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter),
                ) {
                    SectionHeader(
                        title = stringResource(R.string.home_new_title),
                        action = stringResource(R.string.home_new_action),
                        onAction = { onOpenCategory("new") },
                    )
                }
                Spacer(Modifier.height(12.dp))
                ProductRail(
                    products = HomeCatalog.newArrivals,
                    onOpenProduct = onOpenProduct,
                    cardWidth = 156,
                )
            }

            item {
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.home_preview_note),
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PageGutter),
                )
                }
            }
        }

        HomeBottomBar(onSelect = onOpenTab)
    }
}

// --------------------------------------------------------------------- header

@Composable
private fun HomeHeader(onOpenNotifications: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = PageGutter)
            .padding(top = 14.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 40)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(greetingRes()),
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = VelnoxIcons.Home,
                        contentDescription = null,
                        tint = VelnoxForeground,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = stringResource(R.string.home_location),
                        style = TextXs.copy(fontWeight = FontWeight.Medium),
                        color = VelnoxForeground,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Icon(
                        imageVector = VelnoxIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(12.dp),
                    )
                }
            }
            IconCircleButton(
                icon = VelnoxIcons.Bell,
                contentDescription = stringResource(R.string.home_notifications),
                onClick = onOpenNotifications,
                showDot = true,
            )
        }
    }
}

private fun greetingRes(): Int = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..11 -> R.string.home_greeting_morning
    in 12..17 -> R.string.home_greeting_afternoon
    else -> R.string.home_greeting_evening
}

@Composable
private fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    showDot: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(Circle)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, Circle)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 9.dp, end = 10.dp)
                    .size(6.dp)
                    .clip(Circle)
                    .background(VelnoxBrand),
            )
        }
    }
}

// --------------------------------------------------------------------- search

@Composable
private fun SearchBar(onOpenFilter: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, Squircle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .clip(Squircle)
                .clickable(role = Role.Button, onClick = onOpenFilter)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = VelnoxIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.home_search_placeholder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(40.dp)
                .clip(SquircleSm)
                .background(VelnoxPrimary)
                .clickable(role = Role.Button, onClick = onOpenFilter),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = VelnoxIcons.Sliders,
                contentDescription = stringResource(R.string.home_filter),
                tint = VelnoxPrimaryForeground,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ------------------------------------------------------------------- sections

@Composable
private fun HomeBand(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = PageGutter)) {
            SectionHeader(title = title)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = TextXs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** Responsive category chips: 4 across on phones, 5-6 on larger widths. */
@Composable
private fun CategoryCard(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) VelnoxPrimary else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) VelnoxBrand else MaterialTheme.colorScheme.onSurface
    val border = if (selected) VelnoxPrimary else MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .width(74.dp)
            .clip(Squircle)
            .background(background)
            .border(1.dp, border, Squircle)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(category.label),
            style = Text2xs,
            color = contentColor,
            maxLines = 1,
        )
    }
}

// ----------------------------------------------------------------- hero banner

@Composable
private fun HeroBanner(onClick: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleLg)
            .background(VelnoxPrimary)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        // Decorative emerald arc + dotted grid: keeps the hero branded without photos.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val arc = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        left = size.width * 0.52f,
                        top = -size.height * 0.35f,
                        right = size.width * 1.25f,
                        bottom = size.height * 1.15f,
                    )
                )
            }
            drawPath(path = arc, color = VelnoxBrand.copy(alpha = 0.20f))
            drawCircle(
                color = VelnoxBrand.copy(alpha = 0.55f),
                radius = 3.dp.toPx(),
                center = Offset(size.width * 0.82f, size.height * 0.28f),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = Offset(size.width * 0.70f, size.height * 0.12f),
                end = Offset(size.width * 0.95f, size.height * 0.12f),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            BrandBadge(text = stringResource(R.string.home_hero_badge))
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.home_hero_title),
                style = MaterialTheme.typography.headlineMedium,
                color = VelnoxPrimaryForeground,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.home_hero_body),
                style = TextXs,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.widthIn(max = 230.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .clip(SquircleSm)
                    .background(VelnoxBrand)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_hero_cta),
                    style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                    color = VelnoxBrandForeground,
                )
                Icon(
                    imageVector = VelnoxIcons.ArrowRight,
                    contentDescription = null,
                    tint = VelnoxBrandForeground,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(14.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------- shop by category rail

@Composable
private fun ShopByCategoryRail(onOpenCategory: (String) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = if (maxWidth < 400.dp) 176.dp else 196.dp
        LazyRow(
            contentPadding = PaddingValues(horizontal = PageGutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(HomeCatalog.categories.drop(1)) { category ->
                Column(
                    modifier = Modifier
                        .width(cardWidth)
                        .clip(SquircleLg)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
                        .clickable(role = Role.Button) { onOpenCategory(category.id) }
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(SquircleSm)
                                .background(VelnoxBrandSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = VelnoxBrand,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(
                            imageVector = VelnoxIcons.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = stringResource(category.label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------- product grid

@Composable
private fun GridSection(
    eyebrow: String,
    title: String,
    subtitle: String,
    products: List<ProductPreview>,
    onOpenProduct: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // 320dp → 2 columns · 400dp → 3 · 600dp → 4
        val columns = when {
            maxWidth < 400.dp -> 2
            maxWidth < 600.dp -> 3
            else -> 4
        }
        Column {
            Column(modifier = Modifier.padding(horizontal = PageGutter)) {
                Text(
                    text = eyebrow,
                    style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                    color = VelnoxBrand,
                )
                Spacer(Modifier.height(2.dp))
                SectionHeader(title = title)
                Text(
                    text = subtitle,
                    style = TextXs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.padding(horizontal = PageGutter),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                products.chunked(columns).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowItems.forEach { product ->
                            ProductCard(
                                product = product,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenProduct(product.id) },
                            )
                        }
                        // Keep the last row aligned when it is not full.
                        repeat(columns - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: ProductPreview,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp),
    ) {
        ProductArtTile(art = product.art, modifier = Modifier.aspectRatio(1f))
        Spacer(Modifier.height(8.dp))
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
                color = VelnoxBrand,
                modifier = Modifier
                    .clip(SquircleXs)
                    .background(VelnoxBrandSoft)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
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

// ---------------------------------------------------------------- product rail

@Composable
private fun ProductRail(
    products: List<ProductPreview>,
    onOpenProduct: (String) -> Unit,
    cardWidth: Int,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = PageGutter),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(products) { product ->
            ProductCard(
                product = product,
                modifier = Modifier.width(cardWidth.dp),
                onClick = { onOpenProduct(product.id) },
            )
        }
    }
}

/**
 * Flat vector product art. Colour is derived from the design tokens and the
 * catalogue position, so the feed reads as a curated set without shipping
 * photography or implying real inventory.
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
            val w = size.width
            val h = size.height
            // Soft corner glow anchors the tile without a shadow.
            drawCircle(
                color = tint.strong.copy(alpha = 0.10f),
                radius = w * 0.52f,
                center = Offset(w * 0.18f, h * 0.12f),
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

private fun artTint(art: ProductArt): ArtTint = when (art) {
    ProductArt.Jacket -> ArtTint(Color(0xFFE2E8F0), Color(0xFF475569))
    ProductArt.Headphones -> ArtTint(Color(0xFFECFDF5), Color(0xFF047857))
    ProductArt.Tote -> ArtTint(Color(0xFFF1F5F9), Color(0xFF334155))
    ProductArt.Watch -> ArtTint(Color(0xFFFEF3C7), Color(0xFF92400E))
    ProductArt.Mug -> ArtTint(Color(0xFFFFF7ED), Color(0xFF9A3412))
    ProductArt.Lamp -> ArtTint(Color(0xFFFEF9C3), Color(0xFF854D0E))
    ProductArt.Sneaker -> ArtTint(Color(0xFFF8FAFC), Color(0xFF0F172A))
    ProductArt.Serum -> ArtTint(Color(0xFFFCE7F3), Color(0xFF9D174D))
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

// -------------------------------------------------------------- bottom nav

private data class HomeTab(
    val id: String,
    @StringRes val label: Int,
    val icon: ImageVector,
)

private val homeTabs = listOf(
    HomeTab("home", R.string.nav_home, VelnoxIcons.Home),
    HomeTab("categories", R.string.nav_categories, VelnoxIcons.Grid),
    HomeTab("wishlist", R.string.nav_wishlist, VelnoxIcons.Heart),
    HomeTab("profile", R.string.nav_profile, VelnoxIcons.User),
)

@Composable
private fun HomeBottomBar(onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            homeTabs.forEach { tab ->
                val active = tab.id == "home"
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(SquircleSm)
                        .clickable(enabled = !active, role = Role.Button) { onSelect(tab.id) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = if (active) VelnoxBrand else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(tab.label),
                        style = Text2xs,
                        color = if (active) VelnoxForeground else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------- previews

@Preview(name = "Home phone", showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun HomePhonePreview() {
    VelnoxTheme(darkTheme = false) {
        HomeScreen(
            onOpenNotifications = {},
            onOpenFilter = {},
            onOpenCategory = {},
            onOpenProduct = {},
            onOpenTab = {},
        )
    }
}

@Preview(name = "Home wide", showBackground = true, widthDp = 600, heightDp = 900)
@Composable
private fun HomeWidePreview() {
    VelnoxTheme(darkTheme = false) {
        HomeScreen(
            onOpenNotifications = {},
            onOpenFilter = {},
            onOpenCategory = {},
            onOpenProduct = {},
            onOpenTab = {},
        )
    }
}
