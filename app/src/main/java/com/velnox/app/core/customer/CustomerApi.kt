package com.velnox.app.core.customer

/**
 * The authenticated customer API. Every call takes the session's bearer token
 * explicitly, so the implementation holds no session state and a fake can stand
 * in for it in tests.
 */
interface CustomerApi {

    // ---- profile & addresses
    /** `GET /api/customer/profile` */
    suspend fun profile(token: String): CustomerResult<CustomerProfile>

    /** `PUT /api/customer/profile` */
    suspend fun updateProfile(token: String, update: ProfileUpdate): CustomerResult<CustomerProfile>

    /** `GET /api/customer/addresses` */
    suspend fun addresses(token: String): CustomerResult<List<CustomerAddress>>

    /**
     * `POST /api/customer/addresses` — creates the address, or overwrites
     * [addressId] when given. Returns the saved address id.
     */
    suspend fun saveAddress(
        token: String,
        addressId: String?,
        input: AddressInput,
    ): CustomerResult<String>

    /** `DELETE /api/customer/addresses/:id` */
    suspend fun deleteAddress(token: String, id: String): CustomerResult<Unit>

    // ---- cart
    /** `GET /api/customer/cart` */
    suspend fun cart(token: String): CustomerResult<RemoteCart>

    /** `POST /api/customer/cart/add` */
    suspend fun addToCart(
        token: String,
        productId: String,
        variantId: String?,
        quantity: Int,
    ): CustomerResult<RemoteCart>

    /** `PUT /api/customer/cart/item/:id` */
    suspend fun updateCartItem(token: String, itemId: String, quantity: Int): CustomerResult<RemoteCart>

    /** `DELETE /api/customer/cart/item/:id` */
    suspend fun removeCartItem(token: String, itemId: String): CustomerResult<RemoteCart>

    // ---- wishlist
    /** `GET /api/customer/wishlist` */
    suspend fun wishlist(token: String): CustomerResult<List<WishlistEntry>>

    /** `POST /api/customer/wishlist/toggle` — returns true when the product is now wishlisted. */
    suspend fun toggleWishlist(token: String, productId: String): CustomerResult<Boolean>

    // ---- checkout & orders
    /** `POST /api/customer/shipping/quote` — server-authoritative shipping & total quote. */
    suspend fun shippingQuote(
        token: String,
        addressId: String,
        cartItemIds: List<String>? = null,
    ): CustomerResult<ShippingQuote>

    /** `POST /api/customer/checkout` */
    suspend fun checkout(token: String, request: CheckoutRequest): CustomerResult<CheckoutOutcome>

    /** `GET /api/customer/orders` */
    suspend fun orders(token: String): CustomerResult<List<CustomerOrder>>

    /** `GET /api/customer/orders/:id` */
    suspend fun order(token: String, id: String): CustomerResult<CustomerOrder>

    /** `PATCH /api/customer/orders/:id/cancel` */
    suspend fun cancelOrder(token: String, id: String): CustomerResult<Unit>

    /** `POST /api/customer/reorder` — puts the order's items back in the cart. */
    suspend fun reorder(token: String, orderId: String): CustomerResult<RemoteCart>

    // ---- notifications
    /** `GET /api/customer/notifications` */
    suspend fun notifications(token: String): CustomerResult<List<CustomerNotification>>

    /** `PATCH /api/customer/notifications/:id/read` */
    suspend fun markNotificationRead(token: String, id: String): CustomerResult<Unit>

    /** `PUT /api/customer/notifications/read-all` */
    suspend fun markAllNotificationsRead(token: String): CustomerResult<Unit>

    // ---- conversations
    /** `GET /api/customer/conversations` */
    suspend fun conversations(token: String): CustomerResult<List<CustomerConversation>>

    /** `POST /api/customer/conversations` — opens (or reuses) a thread with a shop. */
    suspend fun startConversation(
        token: String,
        shopId: String,
        message: String?,
    ): CustomerResult<CustomerConversation>

    /** `GET /api/customer/conversations/:id/messages` */
    suspend fun messages(token: String, conversationId: String): CustomerResult<List<CustomerMessage>>

    /** `POST /api/customer/conversations/:id/messages` */
    suspend fun sendMessage(
        token: String,
        conversationId: String,
        text: String,
    ): CustomerResult<CustomerMessage>
}
