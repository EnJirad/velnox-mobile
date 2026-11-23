package com.velnox.app.core.catalog

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The catalogue the app is allowed to see.
 *
 * Responsibilities:
 * - ask [CatalogApi] for data and hand back its result unchanged;
 * - keep a small in-memory cache so switching tabs, rotating the device or
 *   returning from a product page does not re-hit the network;
 * - resolve categorySlug -> categoryId when querying products to work around
 *   backend SQL filter limitations.
 */
class CatalogRepository(private val api: CatalogApi) {

    private val mutex = Mutex()

    private var categories: List<CatalogCategory>? = null
    private val lists = LinkedHashMap<String, CatalogPage>()
    private val details = LinkedHashMap<String, ProductDetail>()
    private val shops = LinkedHashMap<String, ShopDetail>()
    private val reviewsCache = LinkedHashMap<String, ProductReviewsData>()

    suspend fun categories(force: Boolean = false): CatalogResult<List<CatalogCategory>> {
        mutex.withLock {
            if (!force) categories?.let { return CatalogResult.Ok(it) }
        }

        return when (val result = api.categories()) {
            is CatalogResult.Ok -> {
                mutex.withLock { categories = result.value }
                result
            }
            is CatalogResult.Failed -> result
        }
    }

    suspend fun products(query: CatalogQuery, force: Boolean = false): CatalogResult<CatalogPage> {
        // Resolve categoryId if category slug is present but categoryId is null
        var effectiveQuery = query
        if (query.category != null && query.categoryId == null) {
            val cachedCats = mutex.withLock { categories } ?: run {
                when (val catRes = categories()) {
                    is CatalogResult.Ok -> catRes.value
                    is CatalogResult.Failed -> null
                }
            }
            val matched = cachedCats?.firstOrNull { it.slug.equals(query.category, ignoreCase = true) }
            if (matched?.id != null) {
                effectiveQuery = query.copy(categoryId = matched.id)
            }
        }

        val key = effectiveQuery.cacheKey()
        mutex.withLock {
            if (!force) lists[key]?.let { return CatalogResult.Ok(it) }
        }

        return when (val result = api.products(effectiveQuery)) {
            is CatalogResult.Ok -> {
                mutex.withLock {
                    lists.remove(key)
                    lists[key] = result.value
                    while (lists.size > MAX_LIST_ENTRIES) {
                        lists.remove(lists.keys.first())
                    }
                }
                result
            }
            is CatalogResult.Failed -> result
        }
    }

    suspend fun product(id: String, force: Boolean = false): CatalogResult<ProductDetail> {
        mutex.withLock {
            if (!force) details[id]?.let { return CatalogResult.Ok(it) }
        }

        return when (val result = api.product(id)) {
            is CatalogResult.Ok -> {
                mutex.withLock {
                    details.remove(id)
                    details[id] = result.value
                    while (details.size > MAX_DETAIL_ENTRIES) {
                        details.remove(details.keys.first())
                    }
                }
                result
            }
            is CatalogResult.Failed -> {
                if (result.error == CatalogError.NotFound) {
                    mutex.withLock { details.remove(id) }
                }
                result
            }
        }
    }

    suspend fun shop(slugOrId: String, force: Boolean = false): CatalogResult<ShopDetail> {
        mutex.withLock {
            if (!force) shops[slugOrId]?.let { return CatalogResult.Ok(it) }
        }

        return when (val result = api.shop(slugOrId)) {
            is CatalogResult.Ok -> {
                mutex.withLock {
                    shops.remove(slugOrId)
                    shops[slugOrId] = result.value
                    while (shops.size > MAX_SHOP_ENTRIES) {
                        shops.remove(shops.keys.first())
                    }
                }
                result
            }
            is CatalogResult.Failed -> {
                if (result.error == CatalogError.NotFound) {
                    mutex.withLock { shops.remove(slugOrId) }
                }
                result
            }
        }
    }

    suspend fun reviews(productId: String, force: Boolean = false): CatalogResult<ProductReviewsData> {
        mutex.withLock {
            if (!force) reviewsCache[productId]?.let { return CatalogResult.Ok(it) }
        }

        return when (val result = api.reviews(productId)) {
            is CatalogResult.Ok -> {
                mutex.withLock {
                    reviewsCache.remove(productId)
                    reviewsCache[productId] = result.value
                    while (reviewsCache.size > MAX_REVIEWS_ENTRIES) {
                        reviewsCache.remove(reviewsCache.keys.first())
                    }
                }
                result
            }
            is CatalogResult.Failed -> result
        }
    }

    suspend fun evict() = mutex.withLock {
        categories = null
        lists.clear()
        details.clear()
        shops.clear()
        reviewsCache.clear()
    }

    /** Drop everything: used when the catalogue is known to have changed. */
    suspend fun invalidate() = evict()

    companion object {
        const val MAX_LIST_ENTRIES = 16
        const val MAX_DETAIL_ENTRIES = 32
        const val MAX_SHOP_ENTRIES = 8
        const val MAX_REVIEWS_ENTRIES = 16
    }
}
