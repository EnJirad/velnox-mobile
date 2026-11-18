package com.velnox.app.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * Prices are shown exactly as the database states them, with only typographic
 * decisions: grouping, a trimmed `.00`, and the designer's `฿` for THB.
 */
class PriceFormatTest {

    private val us = Locale.US

    @Test
    fun `a whole THB amount drops the decimals and groups the digits`() {
        assertEquals("฿1,234", PriceFormat.format(1234.0, "THB", us))
    }

    @Test
    fun `a fractional THB amount keeps two decimals`() {
        assertEquals("฿99.50", PriceFormat.format(99.5, "THB", us))
    }

    @Test
    fun `a zero price reads as zero, not as free or missing`() {
        assertEquals("฿0", PriceFormat.format(0.0, "THB", us))
    }

    @Test
    fun `another currency uses its code`() {
        // Same rule as THB: a whole amount drops `.00`, a fractional one keeps two
        // decimals, so money reads consistently across currencies.
        assertEquals("12 USD", PriceFormat.format(12.0, "USD", us))
        assertEquals("12.50 USD", PriceFormat.format(12.5, "usd", us))
    }

    @Test
    fun `a missing amount produces no text at all`() {
        assertNull(PriceFormat.format(null, "THB", us))
        assertNull(PriceFormat.format(Double.NaN, "THB", us))
        assertNull(PriceFormat.format(Double.POSITIVE_INFINITY, "THB", us))
    }
}
