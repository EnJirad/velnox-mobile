package com.velnox.app.core.customer

/**
 * Wire-level models for the authenticated customer endpoints under `/api/customer`.
 *
 * These carry exactly what the server returned. Anything the API did not send is
 * `null` (or an empty list); nothing is invented, so a screen that has no data
 * shows an honest empty or error state rather than a placeholder.
 */

data class CustomerProfile(
    val id: String,
    val name: String?,
    val email: String?,
    val phone: String?,
    val avatarUrl: String?,
)

/** Fields the customer may change through `PUT /api/customer/profile`. */
data class ProfileUpdate(
    val name: String,
    val phone: String?,
)

data class CustomerAddress(
    val id: String,
    val recipientName: String,
    val phone: String,
    val addressLine: String,
    val subDistrict: String,
    val district: String,
    val province: String,
    val postalCode: String,
    val isDefault: Boolean,
)

/** Body of `POST /api/customer/addresses`; the server assigns the id. */
data class AddressInput(
    val recipientName: String,
    val phone: String,
    val addressLine: String,
    val subDistrict: String,
    val district: String,
    val province: String,
    val postalCode: String,
    val isDefault: Boolean = false,
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

data class CheckoutRequest(
    val shippingAddressId: String,
    val items: List<CheckoutLine>,
    /** Wire value, e.g. `promptpay`, `credit_card`, `truemoney`, `cod`. */
    val paymentMethod: String,
    val shippingMethod: String? = null,
    val note: String? = null,
)

/** What `POST /api/customer/checkout` created: one or more real orders. */
data class CheckoutOutcome(
    val orderIds: List<String>,
    val orderNumbers: List<String>,
    val total: Double?,
    val currency: String,
)

data class OrderLine(
    val productId: String?,
    val name: String?,
    val imageUrl: String?,
    val quantity: Int,
    val price: Double?,
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
) {
    val itemCount: Int get() = items.sumOf { it.quantity }.coerceAtLeast(items.size)
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
)

data class CustomerMessage(
    val id: String,
    val text: String,
    /** True when the customer wrote it; false for the shop's replies. */
    val fromCustomer: Boolean,
    val createdAt: Long?,
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
