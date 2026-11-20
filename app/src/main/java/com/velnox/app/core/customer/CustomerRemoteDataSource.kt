package com.velnox.app.core.customer

import com.velnox.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

/**
 * The Velnox API client for the authenticated customer area.
 *
 * Every response uses the backend envelope `{ success, data }` (errors:
 * `{ success: false, error: { code, message } }`). No call throws for an expected
 * failure: the caller receives a [CustomerResult.Failed]. Nothing is synthesised
 * when the API fails or omits a field.
 *
 * The parsers accept the documented key and its snake_case / nested variants
 * (`productId` / `product_id` / `product.id`), because the customer routes are
 * served by several handlers that do not all share one casing.
 */
class CustomerRemoteDataSource(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val client: OkHttpClient = defaultClient(),
) : CustomerApi {

    // ------------------------------------------------------ profile & addresses

    override suspend fun profile(token: String): CustomerResult<CustomerProfile> =
        send(token, "GET", "/api/customer/profile").map { data ->
            data.asObject("profile", "user")?.let(::parseProfile)
        }

    override suspend fun updateProfile(
        token: String,
        update: ProfileUpdate,
    ): CustomerResult<CustomerProfile> {
        val body = JSONObject().put("name", update.name)
        update.phone?.let { body.put("phone", it) }
        return send(token, "PUT", "/api/customer/profile", body).map { data ->
            data.asObject("profile", "user")?.let(::parseProfile)
        }
    }

    override suspend fun addresses(token: String): CustomerResult<List<CustomerAddress>> =
        send(token, "GET", "/api/customer/addresses").map { data ->
            data.asArray("addresses", "items")?.objects()?.mapNotNull(::parseAddress)
        }

    override suspend fun saveAddress(
        token: String,
        addressId: String?,
        input: AddressInput,
    ): CustomerResult<String> {
        val body = JSONObject()
            .put("recipientName", input.recipientName)
            .put("phone", input.phone)
            .put("line1", input.addressLine)
            .put("subdistrict", input.subDistrict)
            .put("district", input.district)
            .put("province", input.province)
            .put("postalCode", input.postalCode)
            .put("isDefault", input.isDefault)
        addressId?.let { body.put("addressId", it) }
        input.label?.let { body.put("label", it) }
        input.addressLine2?.let { body.put("line2", it) }
        input.latitude?.let { body.put("latitude", it) }
        input.longitude?.let { body.put("longitude", it) }
        return send(token, "POST", "/api/customer/addresses", body).map { data ->
            (data as? JSONObject)?.str("id") ?: addressId
        }
    }

    override suspend fun deleteAddress(token: String, id: String): CustomerResult<Unit> =
        send(token, "DELETE", "/api/customer/addresses/${encode(id)}").toUnit()

    // ------------------------------------------------------------------- cart

    override suspend fun cart(token: String): CustomerResult<RemoteCart> =
        send(token, "GET", "/api/customer/cart").map { data -> data.toCart() }

    override suspend fun addToCart(
        token: String,
        productId: String,
        variantId: String?,
        quantity: Int,
    ): CustomerResult<RemoteCart> {
        val body = JSONObject().put("productId", productId).put("quantity", quantity)
        variantId?.let { body.put("variantId", it) }
        return cartAfter(token, send(token, "POST", "/api/customer/cart/add", body))
    }

    override suspend fun updateCartItem(
        token: String,
        itemId: String,
        quantity: Int,
    ): CustomerResult<RemoteCart> = cartAfter(
        token,
        send(
            token,
            "PUT",
            "/api/customer/cart/item/${encode(itemId)}",
            JSONObject().put("quantity", quantity),
        ),
    )

    override suspend fun removeCartItem(token: String, itemId: String): CustomerResult<RemoteCart> =
        cartAfter(token, send(token, "DELETE", "/api/customer/cart/item/${encode(itemId)}"))

    /**
     * A mutation may answer with the new cart or with a bare acknowledgement. The
     * cart shown afterwards must be the server's, so an acknowledgement triggers a
     * fresh `GET`.
     */
    private suspend fun cartAfter(token: String, raw: Raw): CustomerResult<RemoteCart> =
        when (raw) {
            is Raw.Failure -> CustomerResult.Failed(raw.error, raw.message)
            is Raw.Success -> raw.data.toCart()?.let { CustomerResult.Ok(it) } ?: cart(token)
        }

    // --------------------------------------------------------------- wishlist

    override suspend fun wishlist(token: String): CustomerResult<List<WishlistEntry>> =
        send(token, "GET", "/api/customer/wishlist").map { data ->
            data.asArray("items", "wishlist", "products")?.objects()?.mapNotNull(::parseWishlist)
        }

    override suspend fun toggleWishlist(token: String, productId: String): CustomerResult<Boolean> {
        val raw = send(
            token,
            "POST",
            "/api/customer/wishlist/toggle",
            JSONObject().put("productId", productId),
        )
        return when (raw) {
            is Raw.Failure -> CustomerResult.Failed(raw.error, raw.message)
            is Raw.Success -> {
                val data = raw.data as? JSONObject
                val state = data?.flag("wishlisted", "isWishlisted", "inWishlist", "liked", "added")
                if (state != null) {
                    CustomerResult.Ok(state)
                } else {
                    // Acknowledgement without a state: the list is the source of truth.
                    when (val list = wishlist(token)) {
                        is CustomerResult.Failed -> list
                        is CustomerResult.Ok ->
                            CustomerResult.Ok(list.value.any { it.productId == productId })
                    }
                }
            }
        }
    }

    // -------------------------------------------------------- checkout & orders

    override suspend fun checkout(
        token: String,
        request: CheckoutRequest,
    ): CustomerResult<CheckoutOutcome> {
        val items = JSONArray()
        for (line in request.items) {
            val obj = JSONObject().put("productId", line.productId).put("quantity", line.quantity)
            line.variantId?.let { obj.put("variantId", it) }
            items.put(obj)
        }
        val body = JSONObject()
            .put("shippingAddressId", request.shippingAddressId)
            .put("items", items)
            .put("paymentMethod", request.paymentMethod)
        request.shippingMethod?.let { body.put("shippingMethod", it) }
        request.note?.let { body.put("note", it) }

        return send(token, "POST", "/api/customer/checkout", body).map { data ->
            (data as? JSONObject)?.let(::parseCheckout)
        }
    }

    override suspend fun orders(token: String): CustomerResult<List<CustomerOrder>> =
        send(token, "GET", "/api/customer/orders").map { data ->
            data.asArray("orders", "items")?.objects()?.mapNotNull(::parseOrder)
        }

    override suspend fun order(token: String, id: String): CustomerResult<CustomerOrder> =
        send(token, "GET", "/api/customer/orders/${encode(id)}").map { data ->
            data.asObject("order")?.let(::parseOrder)
        }

    override suspend fun cancelOrder(token: String, id: String): CustomerResult<Unit> =
        send(token, "PATCH", "/api/customer/orders/${encode(id)}/cancel").toUnit()

    override suspend fun reorder(token: String, orderId: String): CustomerResult<RemoteCart> =
        cartAfter(
            token,
            send(token, "POST", "/api/customer/reorder", JSONObject().put("orderId", orderId)),
        )

    // ---------------------------------------------------------- notifications

    override suspend fun notifications(token: String): CustomerResult<List<CustomerNotification>> =
        send(token, "GET", "/api/customer/notifications").map { data ->
            data.asArray("notifications", "items")?.objects()?.mapNotNull(::parseNotification)
        }

    override suspend fun markNotificationRead(token: String, id: String): CustomerResult<Unit> =
        send(token, "PATCH", "/api/customer/notifications/${encode(id)}/read").toUnit()

    override suspend fun markAllNotificationsRead(token: String): CustomerResult<Unit> =
        send(token, "PUT", "/api/customer/notifications/read-all").toUnit()

    // ----------------------------------------------------------- conversations

    override suspend fun conversations(token: String): CustomerResult<List<CustomerConversation>> =
        send(token, "GET", "/api/customer/conversations").map { data ->
            data.asArray("conversations", "items")?.objects()?.mapNotNull(::parseConversation)
        }

    override suspend fun startConversation(
        token: String,
        shopId: String,
        message: String?,
    ): CustomerResult<CustomerConversation> {
        val body = JSONObject().put("shopId", shopId)
        message?.let { body.put("message", it) }
        return send(token, "POST", "/api/customer/conversations", body).map { data ->
            data.asObject("conversation")?.let(::parseConversation)
        }
    }

    override suspend fun messages(
        token: String,
        conversationId: String,
    ): CustomerResult<List<CustomerMessage>> =
        send(token, "GET", "/api/customer/conversations/${encode(conversationId)}/messages").map { data ->
            data.asArray("messages", "items")?.objects()?.mapNotNull { parseMessage(it, false) }
        }

    override suspend fun sendMessage(
        token: String,
        conversationId: String,
        text: String,
    ): CustomerResult<CustomerMessage> =
        send(
            token,
            "POST",
            "/api/customer/conversations/${encode(conversationId)}/messages",
            JSONObject().put("text", text).put("content", text),
        ).map { data ->
            data.asObject("message")?.let { parseMessage(it, true) }
        }

    // -------------------------------------------------------------- transport

    private sealed interface Raw {
        /** [data] is the envelope's `data` (null when absent, `null`, or an empty body). */
        data class Success(val data: Any?) : Raw
        data class Failure(val error: CustomerError, val message: String?) : Raw
    }

    private suspend fun send(
        token: String,
        method: String,
        path: String,
        body: JSONObject? = null,
    ): Raw = withContext(Dispatchers.IO) {
        val requestBody = if (method == "GET") {
            null
        } else {
            (body?.toString() ?: "{}").toRequestBody(JSON)
        }
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .method(method, requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val envelope = text.takeIf { it.isNotBlank() }?.let {
                    try {
                        JSONObject(it)
                    } catch (_: JSONException) {
                        null
                    }
                }
                val message = envelope?.optJSONObject("error")?.str("message")
                    ?: envelope?.str("message")
                when {
                    response.code == 401 -> Raw.Failure(CustomerError.Unauthorized, message)
                    response.code == 404 -> Raw.Failure(CustomerError.NotFound, message)
                    response.code in REJECTED_CODES -> Raw.Failure(CustomerError.Rejected, message)
                    !response.isSuccessful -> Raw.Failure(CustomerError.Server, message)
                    text.isBlank() -> Raw.Success(null)
                    envelope == null -> Raw.Failure(CustomerError.Server, null)
                    envelope.has("success") && !envelope.optBoolean("success", false) ->
                        Raw.Failure(CustomerError.Rejected, message)
                    else -> Raw.Success(
                        envelope.opt("data")?.takeUnless { it === JSONObject.NULL },
                    )
                }
            }
        } catch (_: IOException) {
            Raw.Failure(CustomerError.Network, null)
        }
    }

    private inline fun <T> Raw.map(transform: (Any?) -> T?): CustomerResult<T> = when (this) {
        is Raw.Failure -> CustomerResult.Failed(error, message)
        is Raw.Success -> transform(data)?.let { CustomerResult.Ok(it) }
            ?: CustomerResult.Failed(CustomerError.Server)
    }

    private fun Raw.toUnit(): CustomerResult<Unit> = when (this) {
        is Raw.Failure -> CustomerResult.Failed(error, message)
        is Raw.Success -> CustomerResult.Ok(Unit)
    }

    // ---------------------------------------------------------------- parsing

    /** `data` as an object: itself, or the object under one of [keys]. */
    private fun Any?.asObject(vararg keys: String): JSONObject? {
        val obj = this as? JSONObject ?: return null
        for (key in keys) {
            obj.optJSONObject(key)?.let { return it }
        }
        return obj
    }

    /** `data` as an array: itself, or the array under one of [keys]. */
    private fun Any?.asArray(vararg keys: String): JSONArray? {
        if (this is JSONArray) return this
        val obj = this as? JSONObject ?: return null
        for (key in keys) {
            obj.optJSONArray(key)?.let { return it }
        }
        return null
    }

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    private fun Any?.toCart(): RemoteCart? {
        val obj = this as? JSONObject
        val array = when {
            this is JSONArray -> this
            obj == null -> null
            else -> obj.optJSONArray("items")
                ?: obj.optJSONObject("cart")?.optJSONArray("items")
        } ?: return null
        return RemoteCart(array.objects().mapNotNull(::parseCartItem))
    }

    private fun parseProfile(obj: JSONObject): CustomerProfile = CustomerProfile(
        name = obj.str("name"),
        email = obj.str("email"),
        phone = obj.str("phone"),
        avatarUrl = obj.str("avatarUrl", "avatar"),
        memberSince = obj.time("memberSince", "createdAt"),
    )

    private fun parseAddress(obj: JSONObject): CustomerAddress? {
        val id = obj.str("id", "addressId", "address_id") ?: return null
        return CustomerAddress(
            id = id,
            label = obj.str("label"),
            recipientName = obj.str("recipientName", "recipient_name", "recipient", "fullName", "name").orEmpty(),
            phone = obj.str("phone", "phoneNumber", "phone_number").orEmpty(),
            addressLine = obj.str("line1", "addressLine", "address_line", "address", "street").orEmpty(),
            addressLine2 = obj.str("line2", "addressLine2"),
            subDistrict = obj.str("subdistrict", "subDistrict", "sub_district").orEmpty(),
            district = obj.str("district").orEmpty(),
            province = obj.str("province", "state").orEmpty(),
            postalCode = obj.str("postalCode", "postal_code", "zipCode", "zip").orEmpty(),
            latitude = obj.num("latitude"),
            longitude = obj.num("longitude"),
            isDefault = obj.flag("isDefault", "is_default", "default") ?: false,
        )
    }

    private fun parseCartItem(obj: JSONObject): RemoteCartItem? {
        val product = obj.optJSONObject("product")
        val variant = obj.optJSONObject("variant")
        val productId = obj.str("productId", "product_id") ?: product?.str("id") ?: return null
        val id = obj.str("id", "cartItemId", "cart_item_id", "itemId") ?: return null
        val quantity = obj.num("quantity", "qty")?.toInt() ?: return null
        return RemoteCartItem(
            id = id,
            productId = productId,
            variantId = obj.str("variantId", "variant_id") ?: variant?.str("id"),
            name = obj.str("name", "productName", "product_name") ?: product?.str("name"),
            variantName = obj.str("variantName", "variant_name") ?: variant?.str("name"),
            shopName = obj.str("shopName", "shop_name") ?: product?.str("shopName")
                ?: obj.optJSONObject("shop")?.str("name"),
            imageUrl = imageOf(obj) ?: product?.let(::imageOf),
            price = obj.num("priceSnapshot", "price", "unitPrice", "unit_price")
                ?: variant?.num("price") ?: product?.num("price"),
            currency = obj.str("currency") ?: DEFAULT_CURRENCY,
            quantity = quantity,
            maxStock = (obj.num("maxStock", "availableStock", "stock") ?: variant?.num("stock")
                ?: product?.num("totalAvailableStock", "stock"))?.toInt(),
        )
    }

    private fun parseWishlist(obj: JSONObject): WishlistEntry? {
        val product = obj.optJSONObject("product") ?: obj
        val productId = obj.str("productId", "product_id") ?: product.str("id") ?: return null
        val name = product.str("name", "productName") ?: return null
        return WishlistEntry(
            productId = productId,
            name = name,
            price = product.num("price"),
            currency = product.str("currency") ?: DEFAULT_CURRENCY,
            imageUrl = imageOf(product),
            shopName = product.str("shopName", "shop_name") ?: product.optJSONObject("shop")?.str("name"),
            inStock = product.num("currentStock", "totalAvailableStock", "stock")?.let { it > 0 }
                ?: product.flag("inStock") ?: true,
            rating = product.num("rating"),
        )
    }

    private fun parseCheckout(obj: JSONObject): CheckoutOutcome? {
        val ids = ArrayList<String>()
        val numbers = ArrayList<String>()
        fun collect(order: JSONObject) {
            order.str("id", "orderId", "order_id")?.let(ids::add)
            order.str("orderNumber", "order_number", "number")?.let(numbers::add)
        }
        val orders = obj.optJSONArray("orders")
        if (orders != null) {
            for (i in 0 until orders.length()) {
                orders.optJSONObject(i)?.let(::collect) ?: orders.optString(i)
                    .takeIf { it.isNotBlank() }?.let(ids::add)
            }
        } else {
            collect(obj.optJSONObject("order") ?: obj)
        }
        if (ids.isEmpty()) return null
        return CheckoutOutcome(
            orderIds = ids,
            orderNumbers = numbers,
            total = obj.num("total", "totalAmount", "total_amount", "grandTotal"),
            currency = obj.str("currency") ?: DEFAULT_CURRENCY,
        )
    }

    private fun parseOrder(obj: JSONObject): CustomerOrder? {
        val id = obj.str("id", "orderId", "order_id") ?: return null
        val lines = (obj.optJSONArray("items") ?: obj.optJSONArray("orderItems")
            ?: obj.optJSONArray("lines"))?.objects().orEmpty().map { line ->
            val product = line.optJSONObject("product")
            OrderLine(
                productId = line.str("productId", "product_id") ?: product?.str("id"),
                name = line.str("name", "productName", "product_name") ?: product?.str("name"),
                imageUrl = imageOf(line) ?: product?.let(::imageOf),
                quantity = line.num("quantity", "qty")?.toInt() ?: 1,
                price = line.num("price", "unitPrice", "unit_price"),
            )
        }
        return CustomerOrder(
            id = id,
            orderNumber = obj.str("orderNumber", "order_number", "number") ?: id,
            status = obj.str("status")?.lowercase().orEmpty(),
            total = obj.num("total", "totalAmount", "total_amount", "grandTotal"),
            currency = obj.str("currency") ?: DEFAULT_CURRENCY,
            createdAt = obj.time("createdAt", "created_at", "placedAt"),
            shopName = obj.str("shopName", "shop_name") ?: obj.optJSONObject("shop")?.str("name"),
            deliveryAddress = addressText(obj),
            items = lines,
        )
    }

    /** The shipping address as one line: a ready-made string, or the parts of an object. */
    private fun addressText(order: JSONObject): String? {
        order.str("deliveryAddress", "delivery_address")?.let { return it }
        val obj = order.optJSONObject("shippingAddress") ?: order.optJSONObject("shipping_address")
            ?: order.optJSONObject("address")
        if (obj == null) return order.str("shippingAddress", "shipping_address")
        val address = parseAddress(obj)
        val parts = if (address != null) {
            listOf(address.addressLine, address.subDistrict, address.district, address.province, address.postalCode)
        } else {
            listOf("addressLine", "subDistrict", "district", "province", "postalCode")
                .map { obj.str(it).orEmpty() }
        }
        return parts.filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun parseNotification(obj: JSONObject): CustomerNotification? {
        val id = obj.str("id", "notificationId") ?: return null
        val title = obj.str("title") ?: return null
        return CustomerNotification(
            id = id,
            type = (obj.str("type", "category") ?: "system").lowercase(),
            title = title,
            body = obj.str("body", "message", "content").orEmpty(),
            createdAt = obj.time("createdAt", "created_at"),
            read = obj.flag("isRead", "is_read", "read")
                ?: (obj.str("readAt", "read_at") != null),
            link = obj.str("link", "url", "targetRoute", "actionUrl"),
        )
    }

    private fun parseConversation(obj: JSONObject): CustomerConversation? {
        val id = obj.str("id", "conversationId") ?: return null
        val shop = obj.optJSONObject("shop")
        val last = obj.opt("lastMessage") ?: obj.opt("last_message")
        val lastText = when (last) {
            is String -> last.trim().takeIf { it.isNotEmpty() }
            is JSONObject -> last.str("text", "content", "body")
            else -> null
        }
        return CustomerConversation(
            id = id,
            shopId = obj.str("shopId", "shop_id") ?: shop?.str("id"),
            shopName = obj.str("shopName", "shop_name") ?: shop?.str("name"),
            lastMessage = lastText,
            lastMessageAt = obj.time("lastMessageAt", "last_message_at", "updatedAt", "updated_at")
                ?: (last as? JSONObject)?.time("createdAt", "created_at"),
            unreadCount = obj.num("unreadCount", "unread_count", "unread")?.toInt() ?: 0,
        )
    }

    private fun parseMessage(obj: JSONObject, knownFromCustomer: Boolean): CustomerMessage? {
        val id = obj.str("id", "messageId") ?: return null
        val text = obj.str("text", "content", "body", "message") ?: return null
        val sender = obj.str("senderRole", "sender_role", "senderType", "sender_type", "sender")
            ?.lowercase()
        val fromCustomer = knownFromCustomer ||
            (obj.flag("isMine", "fromCustomer", "isOwn", "is_mine")
                ?: (sender in CUSTOMER_SENDERS))
        return CustomerMessage(
            id = id,
            text = text,
            fromCustomer = fromCustomer,
            createdAt = obj.time("createdAt", "created_at", "sentAt"),
        )
    }

    /** `primaryImage`, `images[]`, or a plain url field. */
    private fun imageOf(obj: JSONObject): String? {
        obj.str("productImageUrl", "imageUrl", "image_url", "image", "thumbnail")?.let { return it }
        obj.optJSONObject("primaryImage")?.let { it.str("displayUrl", "url")?.let { u -> return u } }
        val images = obj.optJSONArray("images") ?: return null
        for (i in 0 until images.length()) {
            when (val entry = images.opt(i)) {
                is JSONObject -> entry.str("displayUrl", "url")?.let { return it }
                is String -> entry.trim().takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return null
    }

    private fun JSONObject.str(vararg keys: String): String? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = opt(key)
            if (value is String || value is Number) {
                value.toString().trim().takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return null
    }

    /** Accepts JSON numbers and numeric strings (`0` and `"0.00"` both occur). */
    private fun JSONObject.num(vararg keys: String): Double? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val parsed = when (val raw = opt(key)) {
                is Number -> raw.toDouble()
                is String -> raw.trim().toDoubleOrNull()
                else -> null
            }
            if (parsed != null && parsed.isFinite()) return parsed
        }
        return null
    }

    private fun JSONObject.flag(vararg keys: String): Boolean? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            when (val raw = opt(key)) {
                is Boolean -> return raw
                is Number -> return raw.toInt() != 0
                is String -> when (raw.trim().lowercase()) {
                    "true", "1" -> return true
                    "false", "0" -> return false
                }
            }
        }
        return null
    }

    /** Epoch seconds, epoch millis, or an ISO-8601 string → epoch millis. */
    private fun JSONObject.time(vararg keys: String): Long? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val millis = when (val raw = opt(key)) {
                is Number -> raw.toLong().let { if (it in 1 until SECONDS_LIMIT) it * 1000 else it }
                is String -> raw.trim().let { s ->
                    s.toLongOrNull()?.let { if (it in 1 until SECONDS_LIMIT) it * 1000 else it }
                        ?: parseIso(s)
                }
                else -> null
            }
            if (millis != null && millis > 0) return millis
        }
        return null
    }

    private fun parseIso(value: String): Long? = try {
        Instant.parse(value).toEpochMilli()
    } catch (_: DateTimeParseException) {
        try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val DEFAULT_CURRENCY = "THB"

        /** Anything below this is epoch *seconds* (year 5138 in millis, 1973 in seconds). */
        const val SECONDS_LIMIT = 100_000_000_000L

        val REJECTED_CODES = setOf(400, 403, 409, 422)
        val CUSTOMER_SENDERS = setOf("customer", "buyer", "user", "me")
        val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
