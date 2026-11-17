package com.velnox.app.feature.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.velnox.app.R
import com.velnox.app.ui.icons.VelnoxIcons

/**
 * Local catalogue models for Home, category browsing and search.
 *
 * Every value exists only to give the layout realistic proportions while the app
 * has no backend: there are no prices, stock, orders or user data. [CatalogSource]
 * is the seam a repository-backed implementation can replace later without the
 * UI changing.
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
    /** Category ids this preview belongs to; drives category listings. */
    val categoryIds: Set<String> = emptySet(),
)

/** Flat vector product art. Illustrations keep the app free of bundled photos. */
enum class ProductArt { Jacket, Headphones, Tote, Watch, Mug, Lamp, Sneaker, Serum }

/**
 * Read side of the catalogue. Today this is [HomeCatalog]; a future repository
 * implements the same three calls over an API.
 */
interface CatalogSource {
    fun categories(): List<Category>
    fun products(): List<ProductPreview>
    fun productsIn(categoryId: String): List<ProductPreview>
    fun search(query: String, context: Context): List<ProductPreview>
}

object HomeCatalog : CatalogSource {

    /** Category id used by the "All" chip and by unfiltered listings. */
    const val ALL_CATEGORY = "all"

    override fun categories(): List<Category> = categoryItems

    override fun products(): List<ProductPreview> = productItems

    override fun productsIn(categoryId: String): List<ProductPreview> = when (categoryId) {
        ALL_CATEGORY -> productItems
        else -> productItems.filter { categoryId in it.categoryIds }
    }

    override fun search(query: String, context: Context): List<ProductPreview> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return productItems.filter { product ->
            val name = context.getString(product.name)
            val store = context.getString(product.store)
            val tag = context.getString(product.tag)
            val categories = product.categoryIds.mapNotNull { id ->
                categoryItems.firstOrNull { it.id == id }?.let { context.getString(it.label) }
            }
            name.contains(needle, ignoreCase = true) ||
                store.contains(needle, ignoreCase = true) ||
                tag.contains(needle, ignoreCase = true) ||
                categories.any { it.contains(needle, ignoreCase = true) }
        }
    }

    private val categoryItems: List<Category> = listOf(
        Category(ALL_CATEGORY, R.string.home_category_all, VelnoxIcons.Sparkles),
        Category("fashion", R.string.home_category_fashion, VelnoxIcons.Shirt),
        Category("electronics", R.string.home_category_electronics, VelnoxIcons.Laptop),
        Category("home", R.string.home_category_home, VelnoxIcons.Coffee),
        Category("beauty", R.string.home_category_beauty, VelnoxIcons.Sparkles),
        Category("sports", R.string.home_category_sports, VelnoxIcons.Truck),
        Category("food", R.string.home_category_food, VelnoxIcons.Coffee),
    )

    /**
     * The complete local catalogue, one entry per product id. The recommended,
     * popular and new-arrival rails are views over this list, so an id never
     * appears twice in the feed.
     */
    private val productItems: List<ProductPreview> = listOf(
        ProductPreview(
            "p1", R.string.product_1_name, R.string.product_1_store,
            R.string.product_tag_new, ProductArt.Jacket, setOf("fashion"),
        ),
        ProductPreview(
            "p2", R.string.product_2_name, R.string.product_2_store,
            R.string.product_tag_popular, ProductArt.Headphones, setOf("electronics"),
        ),
        ProductPreview(
            "p3", R.string.product_3_name, R.string.product_3_store,
            R.string.product_tag_new, ProductArt.Tote, setOf("fashion"),
        ),
        ProductPreview(
            "p4", R.string.product_4_name, R.string.product_4_store,
            R.string.product_tag_editor, ProductArt.Watch, setOf("fashion", "electronics"),
        ),
        ProductPreview(
            "p5", R.string.product_5_name, R.string.product_5_store,
            R.string.product_tag_popular, ProductArt.Mug, setOf("home", "food"),
        ),
        ProductPreview(
            "p6", R.string.product_6_name, R.string.product_6_store,
            R.string.product_tag_new, ProductArt.Lamp, setOf("home"),
        ),
        ProductPreview(
            "p7", R.string.product_7_name, R.string.product_7_store,
            R.string.product_tag_popular, ProductArt.Sneaker, setOf("sports", "fashion"),
        ),
        ProductPreview(
            "p8", R.string.product_8_name, R.string.product_8_store,
            R.string.product_tag_editor, ProductArt.Serum, setOf("beauty"),
        ),
    )

    /** Curated views over [products]; ids stay unique per rail. */
    val recommended: List<ProductPreview> = productItems.filter { it.id in setOf("p1", "p2", "p3", "p4", "p5", "p6") }
    val popular: List<ProductPreview> = productItems.filter { it.id in setOf("p2", "p7", "p5", "p8", "p4") }
    val newArrivals: List<ProductPreview> = productItems.filter { it.id in setOf("p7", "p8", "p1", "p6") }

    fun product(id: String): ProductPreview? = productItems.firstOrNull { it.id == id }

    fun category(id: String): Category? = categoryItems.firstOrNull { it.id == id }
}
