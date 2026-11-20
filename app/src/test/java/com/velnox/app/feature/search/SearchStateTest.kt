package com.velnox.app.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchStateTest {

    @Test
    fun `initial state has default search history`() {
        val state = SearchState()
        assertTrue(state.history.isNotEmpty())
        assertEquals(SearchState.defaultHistory(), state.history.toList())
    }

    @Test
    fun `addQuery prepends new search term`() {
        val state = SearchState(initialHistory = listOf("term1", "term2"))
        state.addQuery("term3")

        assertEquals(3, state.history.size)
        assertEquals("term3", state.history.first())
    }

    @Test
    fun `addQuery deduplicates existing term and moves it to front`() {
        val state = SearchState(initialHistory = listOf("apple", "banana", "cherry"))
        state.addQuery("banana")

        assertEquals(3, state.history.size)
        assertEquals("banana", state.history[0])
        assertEquals("apple", state.history[1])
        assertEquals("cherry", state.history[2])
    }

    @Test
    fun `addQuery ignores blank or whitespace-only inputs`() {
        val state = SearchState(initialHistory = listOf("existing"))
        state.addQuery("")
        state.addQuery("   ")

        assertEquals(1, state.history.size)
        assertEquals("existing", state.history.first())
    }

    @Test
    fun `removeQuery removes specific query from history`() {
        val state = SearchState(initialHistory = listOf("one", "two", "three"))
        state.removeQuery("two")

        assertEquals(2, state.history.size)
        assertFalse(state.history.contains("two"))
    }

    @Test
    fun `clearHistory empties entire search history`() {
        val state = SearchState(initialHistory = listOf("one", "two"))
        state.clearHistory()

        assertTrue(state.history.isEmpty())
    }

    @Test
    fun `history capacity is capped at 20 items`() {
        val state = SearchState(initialHistory = emptyList())
        for (i in 1..25) {
            state.addQuery("query_$i")
        }

        assertEquals(20, state.history.size)
        assertEquals("query_25", state.history.first())
        assertEquals("query_6", state.history.last())
    }

    @Test
    fun `search filter default values and active count calculation`() {
        val defaultFilter = SearchFilter()
        assertTrue(defaultFilter.isDefault)
        assertEquals(0, defaultFilter.activeFilterCount)

        val filtered = SearchFilter(
            categorySlug = "electronics",
            minPrice = 100.0,
            maxPrice = 500.0,
            inStockOnly = true,
            sortOption = SearchSortOption.TOP_SALES,
        )
        assertFalse(filtered.isDefault)
        assertEquals(4, filtered.activeFilterCount)
    }
}
