package com.velnox.app.feature.home

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.velnox.app.R
import com.velnox.app.ui.icons.VelnoxIcons

/**
 * UI preview models for the Home screen.
 *
 * Every value here exists only to give the layout realistic proportions while
 * the app has no backend. There are no prices, no stock, no orders and no user
 * data — Phase 2 replaces this file with a repository backed by a real API.
 */
data class Category(
    val id: String,
    @StringRes val label: Int,
    val icon: ImageVector,
)

data class ProductPreview(
    val id: String,
    @StringRes val name: Int,
    @StringRes val store: Int,
    /** Shown as a qualitative label only; deliberately not a number or currency. */
    @StringRes val tag: Int,
    val art: ProductArt,
)

/** Flat vector product art. Illustrations keep the app free of bundled photos. */
enum class ProductArt { Jacket, Headphones, Tote, Watch, Mug, Lamp, Sneaker, Serum }

object HomeCatalog {

    val categories: List<Category> = listOf(
        Category("all", R.string.home_category_all, VelnoxIcons.Sparkles),
        Category("fashion", R.string.home_category_fashion, VelnoxIcons.Shirt),
        Category("electronics", R.string.home_category_electronics, VelnoxIcons.Laptop),
        Category("home", R.string.home_category_home, VelnoxIcons.Coffee),
        Category("beauty", R.string.home_category_beauty, VelnoxIcons.Sparkles),
        Category("sports", R.string.home_category_sports, VelnoxIcons.Truck),
        Category("food", R.string.home_category_food, VelnoxIcons.Coffee),
    )

    val recommended: List<ProductPreview> = listOf(
        ProductPreview("p1", R.string.product_1_name, R.string.product_1_store, R.string.product_tag_new, ProductArt.Jacket),
        ProductPreview("p2", R.string.product_2_name, R.string.product_2_store, R.string.product_tag_popular, ProductArt.Headphones),
        ProductPreview("p3", R.string.product_3_name, R.string.product_3_store, R.string.product_tag_new, ProductArt.Tote),
        ProductPreview("p4", R.string.product_4_name, R.string.product_4_store, R.string.product_tag_editor, ProductArt.Watch),
        ProductPreview("p5", R.string.product_5_name, R.string.product_5_store, R.string.product_tag_popular, ProductArt.Mug),
        ProductPreview("p6", R.string.product_6_name, R.string.product_6_store, R.string.product_tag_new, ProductArt.Lamp),
    )

    /** A different slice of the same preview catalogue, ordered by "popularity". */
    val popular: List<ProductPreview> = listOf(
        ProductPreview("p2", R.string.product_2_name, R.string.product_2_store, R.string.product_tag_popular, ProductArt.Headphones),
        ProductPreview("p7", R.string.product_7_name, R.string.product_7_store, R.string.product_tag_popular, ProductArt.Sneaker),
        ProductPreview("p5", R.string.product_5_name, R.string.product_5_store, R.string.product_tag_popular, ProductArt.Mug),
        ProductPreview("p8", R.string.product_8_name, R.string.product_8_store, R.string.product_tag_popular, ProductArt.Serum),
        ProductPreview("p4", R.string.product_4_name, R.string.product_4_store, R.string.product_tag_popular, ProductArt.Watch),
    )

    val newArrivals: List<ProductPreview> = listOf(
        ProductPreview("p7", R.string.product_7_name, R.string.product_7_store, R.string.product_tag_new, ProductArt.Sneaker),
        ProductPreview("p8", R.string.product_8_name, R.string.product_8_store, R.string.product_tag_editor, ProductArt.Serum),
        ProductPreview("p1b", R.string.product_1_name, R.string.product_1_store, R.string.product_tag_popular, ProductArt.Jacket),
        ProductPreview("p6b", R.string.product_6_name, R.string.product_6_store, R.string.product_tag_new, ProductArt.Lamp),
    )
}
