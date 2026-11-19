package com.velnox.app.core.catalog

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The catalogue the app is allowed to see.
 *
 * Responsibilities, and nothing else:
 *
 * - ask [CatalogApi] for data and hand back its result unchanged;
 * - keep a small in-memory cache so switching tabs, rotating the device or
 *   returning from a product page does not re-hit the network;
 * - never turn a failure into data. If the API is unreachable the caller gets
 *   [CatalogResult.Failed] and must show an error state.
 *
 * The cache is deliberately unbounded-but-small: one entry per distinct query
 * plus one per opened product. A catalogue page is at most 20 rows and detail
 * objects are dropped when the query set grows past [MAX_LIST_ENTRIES], so a long
 * browsing session cannot grow the heap without limit.
 */
class CatalogRepository(private val api: CatalogApi) {

    private val mutex = Mutex()

    private var categories: List<CatalogCategory>? = null
    private val lists = LinkedHashMap<String, CatalogPage>()
    private val details = LinkedHashMap<String, ProductDetail>()

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
        val key = query.cacheKey()
        mutex.withLock {
            if (!force) lists[key]?.let { return CatalogResult.Ok(it) }
        }

        return when (val result = api.products(query)) {
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
            // A product that vanished must not stay reachable from the cache.
            is CatalogResult.Failed -> {
                if (result.error == CatalogError.NotFound) {
                    mutex.withLock { details.remove(id) }
                }
                result
            }
        }
    }

    /** Drop everything: used when the catalogue is known to have changed. */
    suspend fun invalidate() {
        mutex.withLock {
            categories = null
            lists.clear()
            details.clear()
        }
    }

    private fun CatalogQuery.cacheKey(): String =
        "${category.orEmpty()}|${search.orEmpty().trim().lowercase()}|$minPrice|$maxPrice|$sortBy|$shopId|$offset|$limit"

    private companion object {
        const val MAX_LIST_ENTRIES = 24
        const val MAX_DETAIL_ENTRIES = 24
    }
}
