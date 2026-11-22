package com.velnox.app.core.customer

/**
 * Wire-level models for the authenticated customer endpoints under `/api/customer`.
 *
 * These carry exactly what the server returned. Anything the API did not send is
 * `null` (or an empty list); nothing is invented, so a screen that has no data
 * shows an honest empty or error state rather than a placeholder.
 */

data class CustomerProfile(
    val name: String?,
    /** Absent from the `PUT` answer; only `GET` returns it. */
    val email: String?,
    val phone: String?,
    val avatarUrl: String?,
    val coverUrl: String? = null,
    /** Epoch millis the account was created. */
    val memberSince: Long?,
)

/** Fields the customer may change through `PUT /api/customer/profile`. */
data class ProfileUpdate(
    val name: String,
    val phone: String?,
)

data class CustomerAddress(
    val id: String,
    val label: String?,
    val recipientName: String,
    val phone: String,
    /** The server's `line1`. */
    val addressLine: String,
    val addressLine2: String?,
    val subDistrict: String,
    val district: String,
    val province: String,
    val postalCode: String,
    val latitude: Double?,
    val longitude: Double?,
    val isDefault: Boolean,
)

/**
 * Body of `POST /api/customer/addresses`. The same route creates and updates: an
 * update sends every field again (the server overwrites the whole row, so fields
 * the app does not edit — label, line 2, coordinates — are passed through).
 */
data class AddressInput(
    val label: String?,
    val recipientName: String,
    val phone: String,
    val addressLine: String,
    val addressLine2: String?,
    val subDistrict: String,
    val district: String,
    val province: String,
    val postalCode: String,
    val latitude: Double?,
    val longitude: Double?,
    val isDefault: Boolean,
)

data class RemoteCartItem(
    /** Cart line id, used by `PUT`/`DELETE /api/customer/cart/item/:id`. */
    val id: String,
    val productId: String,
    val variantId: String?,
    val name: String?,
    val variantName: String?,
    val shopName: String?,
    val imageUrl: String?,
    val price: Double?,
    val currency: String,
    val quantity: Int,
    val maxStock: Int?,
)

data class RemoteCart(
    val items: List<RemoteCartItem>,
)

data class WishlistEntry(
    val productId: String,
    val name: String,
    val price: Double?,
    val currency: String,
    val imageUrl: String?,
    val shopName: String?,
    val inStock: Boolean,
    val rating: Double?,
)

data class CheckoutLine(
    val productId: String,
    val variantId: String?,
    val quantity: Int,
)

data class ShopShippingQuote(
    val shopId: String?,
    val shopName: String?,
    val subtotal: Double,
    val shippingFee: Double,
)

/** Authoritative server-priced shipping breakdown from `POST /api/customer/shipping/quote`. */
data class ShippingQuote(
    val quoteId: String,
    val currency: String,
    val subtotal: Double,
    val discount: Double,
    val shippingTotal: Double,
    val total: Double,
    val itemCount: Int,
    val shops: List<ShopShippingQuote>,
)

data class CheckoutRequest(
    val shippingAddressId: String,
    val items: List<CheckoutLine> = emptyList(),
    /** Wire value: `PROMPTPAY`, `CARD`, or `COD`. */
    val paymentMethod: String,
    val cartItemIds: List<String>? = null,
    val shippingQuoteId: String? = null,
    val requestId: String? = null,
    val note: String? = null,
)

/** What `POST /api/customer/checkout` created: one or more real orders. */
data class CheckoutOutcome(
    val orderIds: List<String>,
    val orderNumbers: List<String>,
    val total: Double?,
    val currency: String,
) {
    val primaryOrderId: String get() = orderIds.first()
    val primaryOrderNumber: String get() = orderNumbers.firstOrNull() ?: primaryOrderId
}

data class OrderLine(
    val productId: String?,
    val name: String?,
    val imageUrl: String?,
    val quantity: Int,
    val price: Double?,
    val variantName: String? = null,
)

data class CustomerOrder(
    val id: String,
    val orderNumber: String,
    /** Raw server status, lower-cased (`pending`, `shipped`, …). */
    val status: String,
    val total: Double?,
    val currency: String,
    /** Epoch millis, or null when the server sent no usable timestamp. */
    val createdAt: Long?,
    val shopName: String?,
    val deliveryAddress: String?,
    val items: List<OrderLine>,
    val subtotal: Double? = null,
    val shippingFee: Double? = null,
    val discount: Double? = null,
    val paymentMethod: String? = null,
    val trackingNumber: String? = null,
    val carrier: String? = null,
    val shopId: String? = null,
) {
    val itemCount: Int get() = items.sumOf { it.quantity }.coerceAtLeast(items.size)

    /** Matches backend `CANCELABLE_STATUSES` in `routes/cart.ts`. */
    val isCancelable: Boolean get() = status in CANCELABLE_STATUSES

    companion object {
        val CANCELABLE_STATUSES = setOf("pending", "pending_payment", "confirmed")
    }
}

data class CustomerNotification(
    val id: String,
    /** Raw server type (`order`, `promotion`, `system`, …), lower-cased. */
    val type: String,
    val title: String,
    val body: String,
    val createdAt: Long?,
    val read: Boolean,
    val link: String?,
)

data class CustomerConversation(
    val id: String,
    val shopId: String?,
    val shopName: String?,
    val lastMessage: String?,
    val lastMessageAt: Long?,
    val unreadCount: Int,
    val isSupport: Boolean = false,
    val participantName: String? = null,
    val participantOnline: Boolean? = null,
    val productId: String? = null,
    val productName: String? = null,
    val productPrice: Double? = null,
    val productImageUrl: String? = null,
)

data class CustomerMessage(
    val id: String,
    val text: String,
    /** True when the customer wrote it; false for the shop's or system's replies. */
    val fromCustomer: Boolean,
    val createdAt: Long?,
    val isSystem: Boolean = false,
    val isRead: Boolean = false,
    /** True only for a local optimistic bubble awaiting server confirmation. */
    val isPending: Boolean = false,
    val isFailed: Boolean = false,
    val failureReason: String? = null,
    val clientId: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
)

sealed interface CustomerResult<out T> {
    data class Ok<T>(val value: T) : CustomerResult<T>
    data class Failed(val error: CustomerError, val message: String? = null) : CustomerResult<Nothing>
}

enum class CustomerError {
    /** The device could not reach the API. Retrying may work. */
    Network,

    /** 401: no session, or the token was revoked/expired. */
    Unauthorized,

    /** 404: the resource does not exist (or is not the caller's). */
    NotFound,

    /** 400/409/422: the server rejected the request; see the message. */
    Rejected,

    /** 5xx, or a body that is not the documented envelope. */
    Server,
}
