package com.velnox.app.feature.cart

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.velnox.app.core.customer.CustomerRepository
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.LoadStatus
import com.velnox.app.core.customer.RemoteCart
import com.velnox.app.core.customer.RemoteCartItem
import com.velnox.app.feature.home.ProductArt

/**
 * One line in the cart.
 *
 * [id] uniquely identifies this cart row (the server's `cart_items.id` when
 * synced, or a deterministic local key while browsing as a guest).
 * [productId] is always the real product id.
 */
data class CartItem(
    val id: String,
    val productId: String,
    val remoteItemId: String? = null,
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
 * survive tab switches.
 *
 * Starts empty (zero seeded mock items). When authenticated, every read and
 * mutation syncs with `/api/customer/cart*`; while signed out, local in-memory
 * fallback keeps guest browsing functional and uploads pending lines on sign-in.
 */
class CartState {

    val items = mutableStateListOf<CartItem>()

    var status by mutableStateOf(LoadStatus.Idle)
        private set

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

    // -------------------------------------------------------- remote sync API

    /** Fetch the authoritative server cart via `GET /api/customer/cart`. */
    suspend fun load(repository: CustomerRepository): CustomerResult<Unit> {
        status = LoadStatus.Loading
        return when (val result = repository.call { api, token -> api.cart(token) }) {
            is CustomerResult.Ok -> {
                applyRemote(result.value)
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
     * Push any guest-only lines (`remoteItemId == null`) to the server and then
     * reload the authoritative cart. Called once when a session becomes active.
     */
    suspend fun syncLocalAndLoad(repository: CustomerRepository): CustomerResult<Unit> {
        val pendingLocal = items.filter { it.remoteItemId == null }
        for (local in pendingLocal) {
            repository.call { api, token ->
                api.addToCart(token, local.productId, local.variantId, local.quantity)
            }
        }
        return load(repository)
    }

    /** Add a product/variant via `POST /api/customer/cart/add` and mirror the server response. */
    suspend fun addRemote(
        repository: CustomerRepository,
        productId: String,
        variantId: String? = null,
        quantity: Int = 1,
    ): CustomerResult<Unit> {
        val result = repository.call { api, token ->
            api.addToCart(token, productId, variantId, quantity)
        }
        return when (result) {
            is CustomerResult.Ok -> {
                applyRemote(result.value, forceSelectProductId = productId, forceSelectVariantId = variantId)
                status = LoadStatus.Ready
                CustomerResult.Ok(Unit)
            }

            is CustomerResult.Failed -> result
        }
    }

    /** Increment a line's quantity; calls `PUT /api/customer/cart/item/:id` when server-backed. */
    suspend fun increaseRemote(repository: CustomerRepository, lineId: String): CustomerResult<Unit> {
        val item = items.firstOrNull { it.id == lineId } ?: return CustomerResult.Ok(Unit)
        val limit = item.maxStock ?: MAX_QTY
        val targetQty = (item.quantity + 1).coerceAtMost(limit)
        if (targetQty == item.quantity) return CustomerResult.Ok(Unit)
        return updateQuantityRemote(repository, item, targetQty)
    }

    /** Decrement a line's quantity; calls `PUT /api/customer/cart/item/:id` when server-backed. */
    suspend fun decreaseRemote(repository: CustomerRepository, lineId: String): CustomerResult<Unit> {
        val item = items.firstOrNull { it.id == lineId } ?: return CustomerResult.Ok(Unit)
        val targetQty = (item.quantity - 1).coerceAtLeast(1)
        if (targetQty == item.quantity) return CustomerResult.Ok(Unit)
        return updateQuantityRemote(repository, item, targetQty)
    }

    private suspend fun updateQuantityRemote(
        repository: CustomerRepository,
        item: CartItem,
        quantity: Int,
    ): CustomerResult<Unit> {
        val remoteId = item.remoteItemId
        if (remoteId == null) {
            update(item.id) { it.copy(quantity = quantity) }
            return CustomerResult.Ok(Unit)
        }
        val result = repository.call { api, token -> api.updateCartItem(token, remoteId, quantity) }
        return when (result) {
            is CustomerResult.Ok -> {
                applyRemote(result.value)
                status = LoadStatus.Ready
                CustomerResult.Ok(Unit)
            }

            is CustomerResult.Failed -> result
        }
    }

    /** Remove a line; calls `DELETE /api/customer/cart/item/:id` when server-backed. */
    suspend fun removeRemote(repository: CustomerRepository, lineId: String): CustomerResult<Unit> {
        val item = items.firstOrNull { it.id == lineId } ?: return CustomerResult.Ok(Unit)
        val remoteId = item.remoteItemId
        if (remoteId == null) {
            remove(lineId)
            return CustomerResult.Ok(Unit)
        }
        val result = repository.call { api, token -> api.removeCartItem(token, remoteId) }
        return when (result) {
            is CustomerResult.Ok -> {
                applyRemote(result.value)
                status = LoadStatus.Ready
                CustomerResult.Ok(Unit)
            }

            is CustomerResult.Failed -> result
        }
    }

    /** Replace cart contents from a server [RemoteCart], preserving selection flags. */
    fun applyRemote(
        remote: RemoteCart,
        forceSelectProductId: String? = null,
        forceSelectVariantId: String? = null,
    ) {
        val previousSelection = items.associate { it.id to it.selected }
        val mapped = remote.items.map { r ->
            val wasSelected = previousSelection[r.id]
            val shouldSelect = when {
                forceSelectProductId != null &&
                    r.productId == forceSelectProductId &&
                    r.variantId == forceSelectVariantId -> true
                wasSelected != null -> wasSelected
                else -> true
            }
            r.toCartItem(selected = shouldSelect)
        }
        items.clear()
        items.addAll(mapped)
    }

    fun clear() {
        items.clear()
        status = LoadStatus.Idle
    }

    // --------------------------------------------------- local / guest helpers

    private fun update(lineId: String, transform: (CartItem) -> CartItem) {
        val index = items.indexOfFirst { it.id == lineId }
        if (index >= 0) items[index] = transform(items[index])
    }

    fun increase(lineId: String) = update(lineId) {
        val limit = it.maxStock ?: MAX_QTY
        it.copy(quantity = (it.quantity + 1).coerceAtMost(limit))
    }

    fun decrease(lineId: String) = update(lineId) {
        it.copy(quantity = (it.quantity - 1).coerceAtLeast(1))
    }

    fun toggleSelection(lineId: String) = update(lineId) { it.copy(selected = !it.selected) }

    fun remove(lineId: String) {
        items.removeAll { it.id == lineId }
    }

    fun toggleAll() {
        val target = !allSelected
        items.replaceAll { it.copy(selected = target) }
    }

    fun toggleShopSelection(shopItems: List<CartItem>, target: Boolean) {
        val ids = shopItems.map { it.id }.toSet()
        items.replaceAll { if (it.id in ids) it.copy(selected = target) else it }
    }

    fun removeSelected(): List<CartItem> {
        val selected = items.filter { it.selected }
        items.removeAll { it.selected }
        return selected
    }

    /** Local in-memory add (used as guest fallback when not signed in). */
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
        val key = localKey(productId, variantId, variantName, imageUrl)
        val existingIndex = items.indexOfFirst { it.id == key }
        val stockLimit = maxStock ?: MAX_QTY
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
                    id = key,
                    productId = productId,
                    remoteItemId = null,
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

    companion object {
        const val MAX_QTY = 100

        fun localKey(
            productId: String,
            variantId: String?,
            variantName: String?,
            imageUrl: String?,
        ): String = when {
            !variantId.isNullOrBlank() -> "${productId}_${variantId}"
            !variantName.isNullOrBlank() -> "${productId}_${variantName.replace(" ", "_")}"
            !imageUrl.isNullOrBlank() -> "${productId}_${imageUrl.hashCode()}"
            else -> productId
        }
    }
}

private fun RemoteCartItem.toCartItem(selected: Boolean): CartItem = CartItem(
    id = id,
    productId = productId,
    remoteItemId = id,
    quantity = quantity,
    selected = selected,
    name = name,
    storeName = shopName,
    imageUrl = imageUrl,
    variantId = variantId,
    variantName = variantName,
    price = price,
    currency = currency,
    maxStock = maxStock,
)
