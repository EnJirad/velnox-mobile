package com.velnox.app.core.catalog

/**
 * Models for the public Velnox catalogue.
 *
 * Every field here exists in a response the deployed API actually returns
 * (`GET /api/products/catalog`, `GET /api/products/:id`,
 * `GET /api/categories/tree`). Nothing is derived, invented or defaulted to a
 * plausible-looking value: when the API omits something the field stays `null`
 * and the UI hides it.
 *
 * The API formats money differently per endpoint (`price` is a JSON number on
 * the catalogue and detail routes, a string such as `"0.00"` on the legacy list
 * route), so it is parsed into a [Double] once, here.
 */
data class CatalogProduct(
    val id: String,
    val name: String,
    val price: Double?,
    val currency: String,
    /** Primary image URL, already resolved to display size when the API offers it. */
    val imageUrl: String?,
    /** The category *slug*; the API stores the slug in `products.category_id`. */
    val categorySlug: String?,
    val shopName: String?,
    /** `null` when the response carries no stock information at all. */
    val inStock: Boolean?,
    val soldCount: Int,
    val compareAtPrice: Double? = null,
    val discountPercent: Int? = null,
    val rating: Double? = null,
    val reviewCount: Int = 0,
    val sellerId: String? = null,
    val shopId: String? = null,
    val variantPrice: Double? = null,
) {
    /** The actual selling price (featured variant price if present and product base price is 0 or compareAtPrice). */
    val displayPrice: Double?
        get() = when {
            variantPrice != null && (price == null || price == 0.0 || (compareAtPrice != null && price == compareAtPrice)) -> variantPrice
            else -> price ?: variantPrice
        }
}

/** One variant of a product, with its own price, comparison price, stock, and attributes. */
data class ProductVariant(
    val id: String,
    val name: String,
    val price: Double?,
    val compareAtPrice: Double? = null,
    val discountPercent: Int? = null,
    val stock: Int? = null,
    val options: Map<String, String> = emptyMap(),
) {
    val title: String get() = name
    val isAvailable: Boolean? get() = stock?.let { it > 0 }
    val inventoryQuantity: Int? get() = stock
}

/** One image of a product, in the order the seller arranged it. */
data class CatalogImage(
    val url: String,
    val isPrimary: Boolean,
)

/** One option value within an option group (e.g. "Red" in "Color"). */
data class ProductOptionValue(
    val id: String,
    val value: String,
    val label: String,
    val imageUrl: String? = null,
    val stock: Int? = null,
)

/** One option group configured by the seller (e.g. Color, Size, Model). */
data class ProductOptionGroup(
    val id: String,
    val name: String,
    val displayType: String? = null,
    val required: Boolean = false,
    val values: List<ProductOptionValue> = emptyList(),
)

/** One key-value attribute / specification (e.g. "Brand: Sony", "Material: Aluminum"). */
data class ProductAttribute(
    val id: String,
    val name: String,
    val value: String,
)

/** One seller shop profile from `GET /api/shops/:idOrSlug`. */
data class SellerShop(
    val id: String,
    val sellerId: String?,
    val name: String,
    val slug: String,
    val description: String?,
    val logoUrl: String? = null,
    val coverUrl: String? = null,
    val phone: String? = null,
    val status: String? = null,
    val productCount: Int = 0,
    val rating: Double? = null,
    val reviewCount: Int = 0,
    val isVerified: Boolean = false,
)

/** Shop detail including its shop metadata and product list. */
data class ShopDetail(
    val shop: SellerShop,
    val products: List<CatalogProduct> = emptyList(),
)

/** One product review from `GET /api/products/:id/reviews`. */
data class ProductReview(
    val id: String,
    val rating: Int,
    val comment: String?,
    val authorName: String?,
    val createdAt: Long?,
    val images: List<String> = emptyList(),
)

/** Aggregate review data for a product. */
data class ProductReviewsData(
    val items: List<ProductReview> = emptyList(),
    val total: Int = 0,
    val avgRating: Double = 0.0,
    val distribution: Map<String, Int> = emptyMap(),
)

/**
 * A product's own page. [description] is the seller's text; when it is empty the
 * UI shows nothing rather than filler.
 */
data class ProductDetail(
    val id: String,
    val name: String,
    val description: String?,
    val price: Double?,
    val currency: String,
    val images: List<CatalogImage>,
    val categorySlug: String?,
    val shopName: String?,
    val unit: String?,
    val availableStock: Int?,
    val soldCount: Int,
    val compareAtPrice: Double? = null,
    val discountPercent: Int? = null,
    val variantPrice: Double? = null,
    val variants: List<ProductVariant> = emptyList(),
    val optionGroups: List<ProductOptionGroup> = emptyList(),
    val attributes: List<ProductAttribute> = emptyList(),
    val shopId: String? = null,
    val shopSlug: String? = null,
    val sellerId: String? = null,
) {
    val isInStock: Boolean? get() = availableStock?.let { it > 0 }

    val displayPrice: Double?
        get() = when {
            variantPrice != null && (price == null || price == 0.0 || (compareAtPrice != null && price == compareAtPrice)) -> variantPrice
            else -> price ?: variantPrice
        }
}

/**
 * A category from `GET /api/categories/tree`.
 *
 * [name] is the English name, [displayName] the localised one the backend
 * carries (Thai today). The app prefers [displayName] for Thai and [name]
 * otherwise instead of inventing a translation.
 */
data class CatalogCategory(
    val slug: String,
    val name: String,
    val displayName: String?,
    /** Published-product count reported by the backend, used to skip empty chips. */
    val productCount: Int,
)

/** One page of the catalogue plus whether another page exists. */
data class CatalogPage(
    val items: List<CatalogProduct>,
    val hasMore: Boolean,
)

/**
 * What to ask the catalogue for. [offset] is a row offset, matching the API's
 * `offset` parameter; [limit] is bounded by the API at 200.
 */
data class CatalogQuery(
    val search: String? = null,
    val category: String? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val sortBy: String? = null,
    val shopId: String? = null,
    val offset: Int = 0,
    val limit: Int = PAGE_SIZE,
) {
    companion object {
        const val PAGE_SIZE = 20
    }
}

/** Outcome of a catalogue call. A failure is never converted into data. */
sealed interface CatalogResult<out T> {
    data class Ok<T>(val value: T) : CatalogResult<T>
    data class Failed(val error: CatalogError) : CatalogResult<Nothing>
}

enum class CatalogError {
    /** The device could not reach the API. Retrying may work. */
    Network,

    /** The API answered, but not with a product (404, or an unusable body). */
    NotFound,

    /** The API answered 4xx/5xx, or the body was not the shape documented above. */
    Server,
}
