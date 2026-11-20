package com.velnox.app.feature.search

import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateListOf
import com.velnox.app.R

/**
 * Supported sorting options for search results.
 */
enum class SearchSortOption(val apiKey: String?, @StringRes val labelRes: Int) {
    RELEVANCE(null, R.string.search_sort_relevance),
    TOP_SALES("soldCount:desc", R.string.search_sort_top_sales),
    PRICE_LOW_HIGH("price:asc", R.string.search_sort_price_low_high),
    PRICE_HIGH_LOW("price:desc", R.string.search_sort_price_high_low),
    RATING("rating:desc", R.string.search_sort_rating),
}

/**
 * Filter criteria applied to search queries.
 */
data class SearchFilter(
    val categorySlug: String? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val inStockOnly: Boolean = false,
    val verifiedOnly: Boolean = false,
    val sortOption: SearchSortOption = SearchSortOption.RELEVANCE,
) {
    val isDefault: Boolean
        get() = categorySlug == null &&
            minPrice == null &&
            maxPrice == null &&
            !inStockOnly &&
            !verifiedOnly &&
            sortOption == SearchSortOption.RELEVANCE

    val activeFilterCount: Int
        get() {
            var count = 0
            if (categorySlug != null) count++
            if (minPrice != null || maxPrice != null) count++
            if (inStockOnly) count++
            if (verifiedOnly) count++
            if (sortOption != SearchSortOption.RELEVANCE) count++
            return count
        }
}

/**
 * State holder for recent search queries and discovery trends.
 */
class SearchState(
    initialHistory: List<String> = defaultHistory(),
) {
    private val _history = mutableStateListOf<String>().apply {
        addAll(initialHistory)
    }

    val history: List<String>
        get() = _history

    fun addQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _history.remove(trimmed)
        _history.add(0, trimmed)
        if (_history.size > 20) {
            _history.removeAt(_history.lastIndex)
        }
    }

    fun removeQuery(query: String) {
        _history.remove(query)
    }

    fun clearHistory() {
        _history.clear()
    }

    companion object {
        fun defaultHistory(): List<String> = listOf(
            "Wireless Headphones",
            "Mechanical Keyboard",
            "Coffee Tumbler",
            "Cotton T-Shirt",
        )

        val trendingSearches: List<String> = listOf(
            "Noise Cancelling",
            "Smart Watch",
            "Gaming Mouse",
            "Hoodie Oversized",
            "Espresso Mug",
            "USB-C Hub",
            "Backpack Waterproof",
        )
    }
}
