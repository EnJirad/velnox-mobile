package com.velnox.app.feature.wishlist

import androidx.compose.runtime.mutableStateListOf

/**
 * A bookmarked / favorited product item.
 */
data class WishlistItem(
    val productId: String,
    val name: String,
    val price: Double? = null,
    val currency: String = "THB",
    val imageUrl: String? = null,
    val shopName: String? = null,
    val inStock: Boolean = true,
    val rating: Double? = null,
)

/**
 * Global reactive wishlist state accessible throughout the app.
 */
object WishlistState {

    val items = mutableStateListOf(
        WishlistItem(
            productId = "prod-1",
            name = "Velnox Signature Minimalist Oversized Hoodie",
            price = 1290.0,
            currency = "THB",
            imageUrl = "https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=600&auto=format&fit=crop&q=80",
            shopName = "Velnox Studio",
            inStock = true,
            rating = 4.9,
        ),
        WishlistItem(
            productId = "prod-2",
            name = "Ergonomic Ceramic Coffee Mug (Handcrafted)",
            price = 450.0,
            currency = "THB",
            imageUrl = "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=600&auto=format&fit=crop&q=80",
            shopName = "Artisan Clay Lab",
            inStock = true,
            rating = 4.8,
        ),
    )

    fun isFavorite(productId: String): Boolean = items.any { it.productId == productId }

    fun add(item: WishlistItem) {
        if (!isFavorite(item.productId)) {
            items.add(0, item)
        }
    }

    fun remove(productId: String): Boolean {
        return items.removeAll { it.productId == productId }
    }

    fun toggle(item: WishlistItem): Boolean {
        return if (isFavorite(item.productId)) {
            remove(item.productId)
            false
        } else {
            add(item)
            true
        }
    }

    fun clear() {
        items.clear()
    }

    fun reset(initialItems: List<WishlistItem>? = null) {
        items.clear()
        if (initialItems != null) {
            items.addAll(initialItems)
        } else {
            items.addAll(
                listOf(
                    WishlistItem(
                        productId = "prod-1",
                        name = "Velnox Signature Minimalist Oversized Hoodie",
                        price = 1290.0,
                        currency = "THB",
                        imageUrl = "https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=600&auto=format&fit=crop&q=80",
                        shopName = "Velnox Studio",
                        inStock = true,
                        rating = 4.9,
                    ),
                    WishlistItem(
                        productId = "prod-2",
                        name = "Ergonomic Ceramic Coffee Mug (Handcrafted)",
                        price = 450.0,
                        currency = "THB",
                        imageUrl = "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=600&auto=format&fit=crop&q=80",
                        shopName = "Artisan Clay Lab",
                        inStock = true,
                        rating = 4.8,
                    ),
                )
            )
        }
    }
}
