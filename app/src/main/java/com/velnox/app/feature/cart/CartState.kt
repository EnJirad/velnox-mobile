package com.velnox.app.feature.cart

import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateListOf
import com.velnox.app.R
import com.velnox.app.feature.home.ProductArt

/**
 * One line in the cart.
 */
data class CartItem(
    val productId: String,
    @StringRes val nameRes: Int = 0,
    @StringRes val storeRes: Int = 0,
    val art: ProductArt = ProductArt.Headphones,
    val quantity: Int,
    val selected: Boolean,
    val name: String? = null,
    val storeName: String? = null,
    val imageUrl: String? = null,
    val variantId: String? = null,
    val variantName: String? = null,
    val price: Double? = null,
    val currency: String = "THB",
    val maxStock: Int? = null,
)

/**
 * Cart UI state, hoisted above the tab host so quantities and selections
 * survive tab switches without a ViewModel or a persistence layer.
 */
class CartState {

    val items = mutableStateListOf(
        CartItem("p2", R.string.product_2_name, R.string.product_2_store, ProductArt.Headphones, 1, true),
        CartItem("p3", R.string.product_3_name, R.string.product_3_store, ProductArt.Tote, 2, true),
        CartItem("p6", R.string.product_6_name, R.string.product_6_store, ProductArt.Lamp, 1, false),
    )

    val selectedCount: Int get() = items.count { it.selected }
    val allSelected: Boolean get() = items.isNotEmpty() && items.all { it.selected }

    val selectedTotalPrice: Double?
        get() {
            val selectedItems = items.filter { it.selected }
            if (selectedItems.isEmpty()) return 0.0
            val pricedItems = selectedItems.filter { it.price != null }
            if (pricedItems.isEmpty()) return null
            return pricedItems.sumOf { (it.price ?: 0.0) * it.quantity }
        }

    private fun update(id: String, transform: (CartItem) -> CartItem) {
        val index = items.indexOfFirst { it.productId == id }
        if (index >= 0) items[index] = transform(items[index])
    }

    fun increase(id: String) = update(id) {
        val limit = it.maxStock ?: 99
        it.copy(quantity = (it.quantity + 1).coerceAtMost(limit))
    }

    fun decrease(id: String) = update(id) { it.copy(quantity = (it.quantity - 1).coerceAtLeast(1)) }

    fun toggleSelection(id: String) = update(id) { it.copy(selected = !it.selected) }

    fun remove(id: String) {
        items.removeAll { it.productId == id }
    }

    fun toggleAll() {
        val target = !allSelected
        items.replaceAll { it.copy(selected = target) }
    }

    fun toggleShopSelection(shopItems: List<CartItem>, target: Boolean) {
        val ids = shopItems.map { it.productId }.toSet()
        items.replaceAll { if (it.productId in ids) it.copy(selected = target) else it }
    }

    fun removeSelected(): List<CartItem> {
        val selected = items.filter { it.selected }
        items.removeAll { it.selected }
        return selected
    }

    fun add(
        productId: String,
        name: String,
        storeName: String?,
        imageUrl: String?,
        variantId: String? = null,
        variantName: String? = null,
        price: Double?,
        currency: String = "THB",
        quantity: Int = 1,
        maxStock: Int? = null,
    ) {
        val key = when {
            !variantId.isNullOrBlank() -> "${productId}_${variantId}"
            !variantName.isNullOrBlank() -> "${productId}_${variantName.replace(" ", "_")}"
            !imageUrl.isNullOrBlank() -> "${productId}_${imageUrl.hashCode()}"
            else -> productId
        }
        val existingIndex = items.indexOfFirst { it.productId == key }
        val stockLimit = maxStock ?: 99
        if (existingIndex >= 0) {
            val current = items[existingIndex]
            items[existingIndex] = current.copy(
                quantity = (current.quantity + quantity).coerceAtMost(stockLimit),
                selected = true,
                imageUrl = imageUrl ?: current.imageUrl,
            )
        } else {
            items.add(
                0,
                CartItem(
                    productId = key,
                    nameRes = 0,
                    storeRes = 0,
                    art = ProductArt.Headphones,
                    quantity = quantity.coerceAtMost(stockLimit),
                    selected = true,
                    name = name,
                    storeName = storeName,
                    imageUrl = imageUrl,
                    variantId = variantId,
                    variantName = variantName,
                    price = price,
                    currency = currency,
                    maxStock = maxStock,
                ),
            )
        }
    }
}
