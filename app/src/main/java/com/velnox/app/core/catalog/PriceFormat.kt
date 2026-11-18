package com.velnox.app.core.catalog

import java.text.NumberFormat
import java.util.Locale

/**
 * Renders a catalogue price exactly as the API states it.
 *
 * The amount and the currency come from the database (`products.price`,
 * `products.currency`). The only decisions taken here are typographic: group the
 * digits for the reader's locale, drop the `.00` tail, and use the `฿` symbol for
 * THB because that is how the design system writes prices. No conversion,
 * rounding or "starting from" logic is applied — a product priced `0.00` displays
 * as `฿0`, which is what the seller actually entered.
 */
object PriceFormat {

    /** `null` when the API sent no usable amount, so the UI omits the price. */
    fun format(amount: Double?, currency: String, locale: Locale = Locale.getDefault()): String? {
        if (amount == null || !amount.isFinite()) return null

        val number = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
            maximumFractionDigits = 2
        }.format(amount)

        return when (currency.uppercase(Locale.ROOT)) {
            "THB" -> "฿$number"
            else -> "$number ${currency.uppercase(Locale.ROOT)}"
        }
    }
}
