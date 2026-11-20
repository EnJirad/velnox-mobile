package com.velnox.app.core.customer

/**
 * A [CustomerApi] whose every call fails with a server error. Tests subclass it
 * and override only the calls they exercise, so an accidental call to anything
 * else shows up as a failure instead of invented data.
 */
open class FakeCustomerApi : CustomerApi {

    protected fun <T> unexpected(): CustomerResult<T> = CustomerResult.Failed(CustomerError.Server)

    override suspend fun profile(token: String): CustomerResult<CustomerProfile> = unexpected()
    override suspend fun updateProfile(token: String, update: ProfileUpdate): CustomerResult<CustomerProfile> = unexpected()
    override suspend fun addresses(token: String): CustomerResult<List<CustomerAddress>> = unexpected()
    override suspend fun saveAddress(token: String, addressId: String?, input: AddressInput): CustomerResult<String> = unexpected()
    override suspend fun deleteAddress(token: String, id: String): CustomerResult<Unit> = unexpected()
    override suspend fun cart(token: String): CustomerResult<RemoteCart> = unexpected()
    override suspend fun addToCart(token: String, productId: String, variantId: String?, quantity: Int): CustomerResult<RemoteCart> = unexpected()
    override suspend fun updateCartItem(token: String, itemId: String, quantity: Int): CustomerResult<RemoteCart> = unexpected()
    override suspend fun removeCartItem(token: String, itemId: String): CustomerResult<RemoteCart> = unexpected()
    override suspend fun wishlist(token: String): CustomerResult<List<WishlistEntry>> = unexpected()
    override suspend fun toggleWishlist(token: String, productId: String): CustomerResult<Boolean> = unexpected()
    override suspend fun shippingQuote(token: String, addressId: String, cartItemIds: List<String>?): CustomerResult<ShippingQuote> = unexpected()
    override suspend fun checkout(token: String, request: CheckoutRequest): CustomerResult<CheckoutOutcome> = unexpected()
    override suspend fun orders(token: String): CustomerResult<List<CustomerOrder>> = unexpected()
    override suspend fun order(token: String, id: String): CustomerResult<CustomerOrder> = unexpected()
    override suspend fun cancelOrder(token: String, id: String): CustomerResult<Unit> = unexpected()
    override suspend fun reorder(token: String, orderId: String): CustomerResult<RemoteCart> = unexpected()
    override suspend fun notifications(token: String): CustomerResult<List<CustomerNotification>> = unexpected()
    override suspend fun markNotificationRead(token: String, id: String): CustomerResult<Unit> = unexpected()
    override suspend fun markAllNotificationsRead(token: String): CustomerResult<Unit> = unexpected()
    override suspend fun conversations(token: String): CustomerResult<List<CustomerConversation>> = unexpected()
    override suspend fun startConversation(token: String, shopId: String, message: String?): CustomerResult<CustomerConversation> = unexpected()
    override suspend fun messages(token: String, conversationId: String): CustomerResult<List<CustomerMessage>> = unexpected()
    override suspend fun sendMessage(token: String, conversationId: String, text: String): CustomerResult<CustomerMessage> = unexpected()
}
