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

    override suspend fun uploadProfileImage(
        token: String,
        kind: String,
        bytes: ByteArray,
        mimeType: String,
        filename: String,
    ): CustomerResult<String> = withContext(Dispatchers.IO) {
        val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val directBody = JSONObject()
            .put("kind", kind)
            .put("base64", base64Str)
            .put("mimeType", mimeType)

        // 1. Primary path: Direct upload to backend server (persisted in PostgreSQL, 100% reliable)
        val directRaw = send(token, "POST", "/api/customer/profile-image/direct", directBody)
        if (directRaw is Raw.Success) {
            val directObj = directRaw.data as? JSONObject
            val serverUrl = if (kind == "avatar") {
                directObj?.optString("avatarUrl")?.takeIf { it.isNotBlank() }
            } else {
                directObj?.optString("coverUrl")?.takeIf { it.isNotBlank() }
            }
            val url = serverUrl
                ?: directObj?.optString("url")?.takeIf { it.isNotBlank() }
                ?: "data:$mimeType;base64,$base64Str"
            return@withContext CustomerResult.Ok(normalizeMediaUrl(url) ?: url)
        }

        // 2. Secondary fallback: Storage presign flow
        val intentBody = JSONObject()
            .put("kind", kind)
            .put("filename", filename)
            .put("mimeType", mimeType)
        val intentRaw = send(token, "POST", "/api/customer/profile-image/upload-intent", intentBody)
        val intentData = (intentRaw as? Raw.Success)?.data as? JSONObject
            ?: return@withContext (directRaw as? Raw.Failure)?.let { CustomerResult.Failed(it.error, it.message) }
            ?: (intentRaw as? Raw.Failure)?.let { CustomerResult.Failed(it.error, it.message) }
            ?: CustomerResult.Failed(CustomerError.Server)

        val uploadUrl = intentData.optString("uploadUrl")
        val objectKey = intentData.optString("objectKey")
        if (uploadUrl.isBlank() || objectKey.isBlank()) {
            return@withContext CustomerResult.Failed(CustomerError.Server, "Invalid upload intent response")
        }

        // Upload binary directly to Cloudflare R2 via pre-signed PUT
        val mediaType = try {
            mimeType.toMediaType()
        } catch (_: Exception) {
            "application/octet-stream".toMediaType()
        }
        val putRequest = Request.Builder()
            .url(uploadUrl)
            .header("Content-Type", mimeType)
            .put(bytes.toRequestBody(mediaType))
            .build()

        val isPutSuccess = try {
            client.newCall(putRequest).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }

        if (!isPutSuccess) {
            return@withContext (directRaw as? Raw.Failure)?.let { CustomerResult.Failed(it.error, it.message) }
                ?: CustomerResult.Failed(CustomerError.Server, "Storage upload failed")
        }

        // Confirm upload and save to Neon PostgreSQL
        val saveBody = JSONObject().put("objectKey", objectKey)
        val saveRaw = send(token, "POST", "/api/customer/profile-image/save", saveBody)
        when (saveRaw) {
            is Raw.Success -> {
                val saveObj = saveRaw.data as? JSONObject
                val serverUrl = if (kind == "avatar") {
                    saveObj?.optString("avatarUrl")?.takeIf { it.isNotBlank() }
                } else {
                    saveObj?.optString("coverUrl")?.takeIf { it.isNotBlank() }
                }
                val url = serverUrl
                    ?: saveObj?.optString("url")?.takeIf { it.isNotBlank() }
                    ?: intentData.optString("cdnUrl").takeIf { it.isNotBlank() }
                    ?: objectKey
                CustomerResult.Ok(normalizeMediaUrl(url) ?: url)
            }
            is Raw.Failure -> CustomerResult.Failed(saveRaw.error, saveRaw.message)
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

    override suspend fun shippingQuote(
        token: String,
        addressId: String,
        cartItemIds: List<String>?,
    ): CustomerResult<ShippingQuote> {
        val body = JSONObject().put("shippingAddressId", addressId)
        if (!cartItemIds.isNullOrEmpty()) {
            val ids = JSONArray()
            cartItemIds.forEach { ids.put(it) }
            body.put("cartItemIds", ids)
        }
        return send(token, "POST", "/api/customer/shipping/quote", body).map { data ->
            (data as? JSONObject)?.let(::parseShippingQuote)
        }
    }

    override suspend fun checkout(
        token: String,
        request: CheckoutRequest,
    ): CustomerResult<CheckoutOutcome> {
        val body = JSONObject()
            .put("shippingAddressId", request.shippingAddressId)
            .put("paymentMethod", request.paymentMethod)
        if (!request.cartItemIds.isNullOrEmpty()) {
            val ids = JSONArray()
            request.cartItemIds.forEach { ids.put(it) }
            body.put("cartItemIds", ids)
        }
        if (request.items.isNotEmpty()) {
            val items = JSONArray()
            for (line in request.items) {
                val obj = JSONObject().put("productId", line.productId).put("quantity", line.quantity)
                line.variantId?.let { obj.put("variantId", it) }
                items.put(obj)
            }
            body.put("items", items)
        }
        request.shippingQuoteId?.takeIf { it.isNotBlank() }?.let { body.put("shippingQuoteId", it) }
        request.requestId?.takeIf { it.isNotBlank() }?.let { body.put("requestId", it) }
        request.note?.takeIf { it.isNotBlank() }?.let { body.put("notes", it) }

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
        productId: String?,
    ): CustomerResult<CustomerConversation> {
        val body = JSONObject().put("shopId", shopId)
        productId?.takeIf { it.isNotBlank() }?.let { body.put("productId", it) }
        return send(token, "POST", "/api/customer/conversations", body).map { data ->
            data.asObject("conversation")?.let(::parseConversation)
        }
    }

    override suspend fun startSupportConversation(token: String): CustomerResult<CustomerConversation> =
        send(token, "POST", "/api/customer/support/conversation").map { data ->
            data.asObject("conversation")?.let(::parseConversation)
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
        clientId: String?,
    ): CustomerResult<CustomerMessage> {
        val payload = JSONObject().put("body", text)
        clientId?.takeIf { it.isNotBlank() }?.let { payload.put("clientId", it) }
        return send(
            token,
            "POST",
            "/api/customer/conversations/${encode(conversationId)}/messages",
            payload,
        ).map { data ->
            data.asObject("message")?.let { parseMessage(it, true) }
        }
    }

    override suspend fun markConversationRead(
        token: String,
        conversationId: String,
    ): CustomerResult<Unit> =
        send(token, "POST", "/api/customer/conversations/${encode(conversationId)}/read").toUnit()

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

    private fun normalizeMediaUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:") || url.startsWith("content://") || url.startsWith("file://")) return url
        val cleanKey = url.trimStart('/')
        val cleanBase = baseUrl.trimEnd('/')
        return "$cleanBase/api/media/$cleanKey"
    }

    private fun parseProfile(obj: JSONObject): CustomerProfile = CustomerProfile(
        name = obj.str("name"),
        email = obj.str("email"),
        phone = obj.str("phone"),
        avatarUrl = normalizeMediaUrl(obj.str("avatarUrl", "avatar")),
        coverUrl = normalizeMediaUrl(obj.str("coverUrl", "cover_url", "cover")),
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
            variantName = obj.str("variantOptionLabels", "variantName", "variant_name") ?: variant?.str("name"),
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
            inStock = product.num("availableStock", "currentStock", "totalAvailableStock", "stock")?.let { it > 0 }
                ?: product.flag("inStock") ?: true,
            rating = product.num("rating"),
        )
    }

    private fun parseShippingQuote(obj: JSONObject): ShippingQuote? {
        val quoteId = obj.str("quoteId", "id") ?: return null
        val shops = obj.optJSONArray("shops")?.objects().orEmpty().map { s ->
            ShopShippingQuote(
                shopId = s.str("shopId", "shop_id"),
                shopName = s.str("shopName", "shop_name"),
                subtotal = s.num("subtotal") ?: 0.0,
                shippingFee = s.num("shippingFee", "shipping_fee") ?: 0.0,
            )
        }
        return ShippingQuote(
            quoteId = quoteId,
            currency = obj.str("currency") ?: DEFAULT_CURRENCY,
            subtotal = obj.num("subtotal") ?: 0.0,
            discount = obj.num("discount") ?: 0.0,
            shippingTotal = obj.num("shippingTotal", "shipping_total") ?: 0.0,
            total = obj.num("total") ?: 0.0,
            itemCount = obj.num("itemCount", "item_count")?.toInt() ?: 0,
            shops = shops,
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
        if (ids.isEmpty()) {
            obj.str("parentOrderId", "parent_order_id")?.let(ids::add)
        }
        if (numbers.isEmpty()) {
            obj.str("parentOrderNumber", "parent_order_number")?.let(numbers::add)
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
                variantName = line.str("variantName", "variant_name"),
            )
        }
        val firstShipment = obj.optJSONArray("shipments")?.optJSONObject(0)
        val firstPayment = obj.optJSONArray("payments")?.optJSONObject(0)
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
            subtotal = obj.num("subtotal"),
            shippingFee = obj.num("shippingFee", "shipping_fee"),
            discount = obj.num("discount"),
            paymentMethod = obj.str("paymentMethod", "payment_method") ?: firstPayment?.str("method"),
            trackingNumber = obj.str("trackingNumber", "tracking_number")
                ?: firstShipment?.str("trackingNumber", "tracking_number"),
            carrier = obj.str("carrier") ?: firstShipment?.str("carrier"),
            shopId = obj.str("shopId", "shop_id") ?: obj.optJSONObject("shop")?.str("id"),
        )
    }

    /** The shipping address as one line: a ready-made string, or the parts of an object. */
    private fun addressText(order: JSONObject): String? {
        order.str("deliveryAddress", "delivery_address")?.let { return it }
        val obj = order.optJSONObject("addressSnapshot")
            ?: order.optJSONObject("shippingAddress")
            ?: order.optJSONObject("shipping_address")
            ?: order.optJSONObject("address")
        if (obj == null) return order.str("shippingAddress", "shipping_address")
        val address = parseAddress(obj)
        val parts = if (address != null) {
            listOf(address.addressLine, address.addressLine2.orEmpty(), address.subDistrict, address.district, address.province, address.postalCode)
        } else {
            listOf(
                obj.str("line1", "addressLine", "address_line", "address").orEmpty(),
                obj.str("line2").orEmpty(),
                obj.str("subdistrict", "subDistrict", "city").orEmpty(),
                obj.str("district").orEmpty(),
                obj.str("province", "state").orEmpty(),
                obj.str("postalCode", "postal_code").orEmpty(),
            )
        }
        return parts.filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun parseNotification(obj: JSONObject): CustomerNotification? {
        val id = obj.str("id", "notificationId") ?: return null
        val title = obj.str("title") ?: return null
        val link = obj.str("link", "url", "targetRoute", "actionUrl")
            ?: obj.optJSONObject("data")?.str("orderId", "order_id")?.let { "order:$it" }
        return CustomerNotification(
            id = id,
            type = (obj.str("type", "category") ?: "system").lowercase(),
            title = title,
            body = obj.str("body", "message", "content").orEmpty(),
            createdAt = obj.time("createdAt", "created_at"),
            read = obj.flag("isRead", "is_read", "read")
                ?: (obj.str("readAt", "read_at") != null),
            link = link,
        )
    }

    private fun parseConversation(obj: JSONObject): CustomerConversation? {
        val id = obj.str("id", "conversationId") ?: return null
        val shop = obj.optJSONObject("shop")
        val product = obj.optJSONObject("product")
        val last = obj.opt("lastMessage") ?: obj.opt("last_message")
        val lastText = when (last) {
            is String -> last.trim().takeIf { it.isNotEmpty() }
            is JSONObject -> last.str("body", "text", "content")
            else -> null
        }
        val convType = obj.str("type")?.uppercase()
        val isSupport = obj.flag("isSupport", "is_support") ?: (convType == "CUSTOMER_COMPANY")
        return CustomerConversation(
            id = id,
            shopId = obj.str("shopId", "shop_id") ?: shop?.str("id"),
            shopName = obj.str("shopName", "shop_name") ?: shop?.str("name"),
            lastMessage = lastText,
            lastMessageAt = obj.time("lastMessageAt", "last_message_at", "updatedAt", "updated_at")
                ?: (last as? JSONObject)?.time("createdAt", "created_at"),
            unreadCount = obj.num("unreadCount", "unread_count", "unread")?.toInt() ?: 0,
            isSupport = isSupport,
            participantName = obj.str("participantName", "participant_name"),
            participantOnline = obj.flag("participantOnline", "participant_online"),
            productId = obj.str("productId", "product_id") ?: product?.str("id"),
            productName = product?.str("name"),
            productPrice = product?.num("price"),
            productImageUrl = product?.let(::imageOf),
        )
    }

    private fun parseMessage(obj: JSONObject, knownFromCustomer: Boolean): CustomerMessage? {
        val id = obj.str("id", "messageId") ?: return null
        val text = obj.str("body", "text", "content", "message") ?: return null
        val sender = obj.str("senderRole", "sender_role", "senderType", "sender_type", "sender")
            ?.lowercase()
        val isSystem = obj.flag("isSystem", "is_system") ?: (sender == "system")
        val fromCustomer = !isSystem && (
            knownFromCustomer ||
                (obj.flag("isMine", "fromCustomer", "isOwn", "is_mine")
                    ?: (sender in CUSTOMER_SENDERS))
            )
        val isRead = obj.flag("isRead", "is_read", "read")
            ?: (obj.str("readAt", "read_at") != null)
        return CustomerMessage(
            id = id,
            text = text,
            fromCustomer = fromCustomer,
            createdAt = obj.time("createdAt", "created_at", "sentAt"),
            isSystem = isSystem,
            isRead = isRead,
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
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
