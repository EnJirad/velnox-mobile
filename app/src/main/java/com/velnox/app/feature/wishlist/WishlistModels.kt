package com.velnox.app.feature.wishlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.velnox.app.core.customer.CustomerRepository
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.LoadStatus
import com.velnox.app.core.customer.WishlistEntry

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

fun WishlistEntry.toWishlistItem(): WishlistItem = WishlistItem(
    productId = productId,
    name = name,
    price = price,
    currency = currency,
    imageUrl = imageUrl,
    shopName = shopName,
    inStock = inStock,
    rating = rating,
)

/**
 * Global reactive wishlist state mirrored from `/api/customer/wishlist`.
 *
 * Starts empty (zero mock items). Signed-in toggles persist through
 * `POST /api/customer/wishlist/toggle`; signed-out users keep local fallback
 * state that is cleared on session change.
 */
object WishlistState {

    val items = mutableStateListOf<WishlistItem>()

    var status by mutableStateOf(LoadStatus.Idle)
        private set

    fun isFavorite(productId: String): Boolean = items.any { it.productId == productId }

    /** Fetch the real wishlist from `GET /api/customer/wishlist`. */
    suspend fun load(repository: CustomerRepository): CustomerResult<Unit> {
        status = LoadStatus.Loading
        return when (val result = repository.call { api, token -> api.wishlist(token) }) {
            is CustomerResult.Ok -> {
                items.clear()
                items.addAll(result.value.map { it.toWishlistItem() })
                status = LoadStatus.Ready
                CustomerResult.Ok(Unit)
            }

            is CustomerResult.Failed -> {
                status = LoadStatus.Failed
                result
            }
        }
    }

    /**
     * Toggle a product via `POST /api/customer/wishlist/toggle` and update local
     * state to match the server's authoritative answer.
     */
    suspend fun toggleRemote(
        repository: CustomerRepository,
        item: WishlistItem,
    ): CustomerResult<Boolean> {
        val result = repository.call { api, token -> api.toggleWishlist(token, item.productId) }
        if (result is CustomerResult.Ok) {
            if (result.value) {
                add(item)
            } else {
                remove(item.productId)
            }
            status = LoadStatus.Ready
        }
        return result
    }

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
        status = LoadStatus.Idle
    }

    /** Test seam: put the wishlist into a known state without the network. */
    fun reset(initialItems: List<WishlistItem> = emptyList()) {
        items.clear()
        items.addAll(initialItems)
        status = if (initialItems.isEmpty()) LoadStatus.Idle else LoadStatus.Ready
    }
}
