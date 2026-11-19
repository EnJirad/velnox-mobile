package com.velnox.app.feature.market

import com.velnox.app.core.catalog.CatalogProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketFilterTest {

    private fun sample(
        id: String,
        price: Double = 100.0,
        categorySlug: String = "c-1",
        inStock: Boolean = true,
        compareAtPrice: Double? = null,
        discountPercent: Int? = null,
        soldCount: Int = 0,
        shopId: String? = "s-1",
    ) = CatalogProduct(
        id = id,
        name = "Product $id",
        price = price,
        currency = "THB",
        imageUrl = null,
        categorySlug = categorySlug,
        shopName = "Shop $shopId",
        inStock = inStock,
        soldCount = soldCount,
        compareAtPrice = compareAtPrice,
        discountPercent = discountPercent,
        shopId = shopId,
    )

    @Test
    fun `default filter state has no active filters`() {
        val state = MarketFilterState()
        assertFalse(state.hasActiveFilters)
        assertEquals(0, state.activeFilterCount)
    }

    @Test
    fun `category selection updates state and filter count`() {
        val state = MarketFilterState(categorySlug = "electronics")
        assertTrue(state.hasActiveFilters)
        assertEquals(1, state.activeFilterCount)

        val matching = sample(id = "1", categorySlug = "electronics")
        val other = sample(id = "2", categorySlug = "fashion")

        assertTrue(state.matches(matching))
        assertFalse(state.matches(other))
    }

    @Test
    fun `price range filter matches products in bounds`() {
        val state = MarketFilterState(minPrice = 50.0, maxPrice = 150.0)
        assertTrue(state.hasActiveFilters)
        assertEquals(1, state.activeFilterCount)

        assertTrue(state.matches(sample(id = "1", price = 50.0)))
        assertTrue(state.matches(sample(id = "2", price = 100.0)))
        assertTrue(state.matches(sample(id = "3", price = 150.0)))
        assertFalse(state.matches(sample(id = "4", price = 49.0)))
        assertFalse(state.matches(sample(id = "5", price = 151.0)))
    }

    @Test
    fun `in stock only filter excludes out-of-stock items`() {
        val state = MarketFilterState(inStockOnly = true)
        assertTrue(state.hasActiveFilters)
        assertEquals(1, state.activeFilterCount)

        assertTrue(state.matches(sample(id = "1", inStock = true)))
        assertFalse(state.matches(sample(id = "2", inStock = false)))
    }

    @Test
    fun `discount only filter requires compareAtPrice or discountPercent`() {
        val state = MarketFilterState(discountOnly = true)
        assertTrue(state.hasActiveFilters)
        assertEquals(1, state.activeFilterCount)

        // Has compareAtPrice > price
        assertTrue(state.matches(sample(id = "1", price = 80.0, compareAtPrice = 100.0)))
        // Has discountPercent
        assertTrue(state.matches(sample(id = "2", price = 100.0, discountPercent = 15)))
        // No discount
        assertFalse(state.matches(sample(id = "3", price = 100.0)))
        // compareAtPrice <= price
        assertFalse(state.matches(sample(id = "4", price = 100.0, compareAtPrice = 90.0)))
    }

    @Test
    fun `multiple filters combine with AND semantics`() {
        val state = MarketFilterState(
            categorySlug = "c-1",
            minPrice = 50.0,
            maxPrice = 200.0,
            inStockOnly = true,
            discountOnly = true,
            shopId = "shop-a",
        )
        assertTrue(state.hasActiveFilters)
        assertEquals(5, state.activeFilterCount)

        val perfect = sample(
            id = "perfect",
            price = 100.0,
            categorySlug = "c-1",
            inStock = true,
            compareAtPrice = 150.0,
            shopId = "shop-a",
        )
        assertTrue(state.matches(perfect))

        // Fails category
        assertFalse(state.matches(perfect.copy(categorySlug = "c-2")))
        // Fails min price
        assertFalse(state.matches(perfect.copy(price = 40.0)))
        // Fails stock
        assertFalse(state.matches(perfect.copy(inStock = false)))
        // Fails discount
        assertFalse(state.matches(perfect.copy(compareAtPrice = null, discountPercent = null)))
        // Fails shop
        assertFalse(state.matches(perfect.copy(shopId = "shop-b")))
    }

    @Test
    fun `sorting orders products correctly`() {
        val items = listOf(
            sample(id = "mid", price = 100.0, soldCount = 5),
            sample(id = "low", price = 20.0, soldCount = 20),
            sample(id = "high", price = 500.0, soldCount = 1),
        )

        val asc = items.sortedByMode(MarketSort.PriceAsc)
        assertEquals(listOf("low", "mid", "high"), asc.map { it.id })

        val desc = items.sortedByMode(MarketSort.PriceDesc)
        assertEquals(listOf("high", "mid", "low"), desc.map { it.id })

        val popular = items.sortedByMode(MarketSort.Popular)
        assertEquals(listOf("low", "mid", "high"), popular.map { it.id })
    }

    @Test
    fun `clearing filters resets state`() {
        var state = MarketFilterState(
            categorySlug = "cat",
            minPrice = 10.0,
            maxPrice = 100.0,
            inStockOnly = true,
            discountOnly = true,
        )
        assertTrue(state.hasActiveFilters)

        state = MarketFilterState()
        assertFalse(state.hasActiveFilters)
        assertEquals(0, state.activeFilterCount)
        assertNull(state.categorySlug)
        assertNull(state.minPrice)
        assertNull(state.maxPrice)
        assertFalse(state.inStockOnly)
        assertFalse(state.discountOnly)
    }

    @Test
    fun `toQuery populates CatalogQuery accurately`() {
        val state = MarketFilterState(
            categorySlug = "tools",
            minPrice = 15.0,
            maxPrice = 85.0,
            shopId = "shop-9",
        )

        val query = state.toQuery(
            search = "  hammer  ",
            sort = MarketSort.PriceAsc,
            offset = 10,
            limit = 20,
        )

        assertEquals("hammer", query.search)
        assertEquals("tools", query.category)
        assertEquals(15.0, query.minPrice!!, 0.0001)
        assertEquals(85.0, query.maxPrice!!, 0.0001)
        assertEquals("price_asc", query.sortBy)
        assertEquals("shop-9", query.shopId)
        assertEquals(10, query.offset)
        assertEquals(20, query.limit)
    }

    @Test
    fun `saver preserves all filter state fields including nullables and numbers`() {
        val original = MarketFilterState(
            categorySlug = "electronics",
            minPrice = 149.99,
            maxPrice = 999.50,
            inStockOnly = true,
            discountOnly = true,
            shopId = "shop-42",
        )

        val scope = object : androidx.compose.runtime.saveable.SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        val saved = with(MarketFilterState.Saver) { scope.save(original) }

        val restored = MarketFilterState.Saver.restore(saved!!)

        assertEquals(original, restored)
        assertEquals("electronics", restored?.categorySlug)
        assertEquals(149.99, restored?.minPrice!!, 0.0001)
        assertEquals(999.50, restored?.maxPrice!!, 0.0001)
        assertEquals(true, restored.inStockOnly)
        assertEquals(true, restored.discountOnly)
        assertEquals("shop-42", restored.shopId)
    }

    @Test
    fun `saver restores empty filter state with nulls correctly`() {
        val original = MarketFilterState()

        val scope = object : androidx.compose.runtime.saveable.SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        val saved = with(MarketFilterState.Saver) { scope.save(original) }
        val restored = MarketFilterState.Saver.restore(saved!!)

        assertEquals(original, restored)
        assertNull(restored?.categorySlug)
        assertNull(restored?.minPrice)
        assertNull(restored?.maxPrice)
        assertEquals(false, restored?.inStockOnly)
        assertEquals(false, restored?.discountOnly)
        assertNull(restored?.shopId)
    }

    @Test
    fun `market sort saver saves and restores all values`() {
        val scope = object : androidx.compose.runtime.saveable.SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        for (sort in MarketSort.values()) {
            val saved = with(MarketSort.Saver) { scope.save(sort) }
            val restored = MarketSort.Saver.restore(saved!!)
            assertEquals(sort, restored)
        }
    }
}
