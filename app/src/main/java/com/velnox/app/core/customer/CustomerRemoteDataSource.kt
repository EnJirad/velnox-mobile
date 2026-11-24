package com.velnox.app.core.customer

import com.velnox.app.BuildConfig
import com.velnox.app.core.auth.AuthRemoteDataSource
import com.velnox.app.core.network.BackendConfig
import com.velnox.app.core.network.BackendDialect
import com.velnox.app.core.network.BackendEndpointRouter
import com.velnox.app.core.network.VelnoxMediaUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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
 * Supports both Cloudflare Workers v1 API (`api.velnox.live`) and Express legacy API
 * (`velnox-api.onrender.com`), with automatic host failover, dual routing, and
 * resilient fallback.
 */
class CustomerRemoteDataSource(
    baseUrl: String = BuildConfig.API_BASE_URL,
    private val client: OkHttpClient = defaultClient(),
    val router: BackendEndpointRouter = BackendEndpointRouter(
        primaryHost = baseUrl,
        fallbackHosts = BackendConfig.FALLBACK_HOSTS,
        client = client,
    ),
) : CustomerApi {

    // ------------------------------------------------------ profile & addresses

    override suspend fun profile(token: String): CustomerResult<CustomerProfile> {
        val isWorkers = router.getDialect() == BackendDialect.WORKERS_V1
        val path = if (isWorkers) "/api/v1/auth/me" else "/api/customer/profile"
        var raw = send(token, "GET", path)
        if (raw is Raw.Failure && raw.error == CustomerError.NotFound && isWorkers) {
            raw = send(token, "GET", "/api/customer/profile")
        }
        return raw.map { data ->
            data.asObject("profile", "user")?.let(::parseProfile)
                ?: (data as? JSONObject)?.let(::parseProfile)
        }
    }

    override suspend fun updateProfile(
        token: String,
        update: ProfileUpdate,
    ): CustomerResult<CustomerProfile> {
        val body = JSONObject().put("name", update.name)
        update.phone?.let { body.put("phone", it) }
        var raw = send(token, "PUT", "/api/customer/profile", body)
        if (raw is Raw.Failure && raw.error == CustomerError.NotFound) {
            val meRaw = send(token, "GET", "/api/v1/auth/me")
            if (meRaw is Raw.Success) raw = meRaw
        }
        return raw.map { data ->
            data.asObject("profile", "user")?.let(::parseProfile)
                ?: (data as? JSONObject)?.let(::parseProfile)
        }
    }

    override suspend fun uploadProfileImage(
        token: String,
        kind: String,
        bytes: ByteArray,
        mimeType: String,
        filename: String,
    ): CustomerResult<String> = withContext(Dispatchers.IO) {
        val dialect = router.getDialect()

        // 1. Try Cloudflare Workers multipart upload if targeting Workers
        if (dialect == BackendDialect.WORKERS_V1) {
            try {
                val mediaType = try { mimeType.toMediaType() } catch (_: Exception) { "image/webp".toMediaType() }
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("purpose", if (kind == "avatar") "avatar" else "cover")
                    .addFormDataPart("file", filename, bytes.toRequestBody(mediaType))
                    .build()

                val request = Request.Builder()
                    .url(router.currentHost().trimEnd('/') + "/api/v1/media/upload")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val text = resp.body?.string().orEmpty()
                        val root = JSONObject(text)
                        val data = root.optJSONObject("data")
                        val pubUrl = data?.optString("publicUrl")?.takeIf { it.isNotBlank() }
                            ?: data?.optString("r2Key")?.takeIf { it.isNotBlank() }
                        if (pubUrl != null) {
                            return@withContext CustomerResult.Ok(normalizeMediaUrl(pubUrl) ?: pubUrl)
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback to Express flows below
            }
        }

        // 2. Storage presign flow (Upload intent -> PUT storage -> Save metadata)
        val intentBody = JSONObject()
            .put("kind", kind)
            .put("filename", filename)
            .put("mimeType", mimeType)
        val intentRaw = send(token, "POST", "/api/customer/profile-image/upload-intent", intentBody)
        var intentFailure: CustomerResult.Failed? = null
        if (intentRaw is Raw.Success) {
            val intentData = intentRaw.data as? JSONObject
            val uploadUrl = intentData?.optString("uploadUrl")
            val objectKey = intentData?.optString("objectKey")
            if (!uploadUrl.isNullOrBlank() && !objectKey.isNullOrBlank()) {
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

                if (isPutSuccess) {
                    // Confirm upload and save to Neon PostgreSQL
                    val saveBody = JSONObject().put("objectKey", objectKey)
                    val saveRaw = send(token, "POST", "/api/customer/profile-image/save", saveBody)
                    if (saveRaw is Raw.Success) {
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
                        return@withContext CustomerResult.Ok(normalizeMediaUrl(url) ?: url)
                    } else if (saveRaw is Raw.Failure) {
                        intentFailure = CustomerResult.Failed(saveRaw.error, saveRaw.message)
                    }
                }
            }
        } else if (intentRaw is Raw.Failure) {
            intentFailure = CustomerResult.Failed(intentRaw.error, intentRaw.message)
        }

        // 3. Direct upload fallback to backend server
        val base64Str = java.util.Base64.getEncoder().encodeToString(bytes)
        val directBody = JSONObject()
            .put("kind", kind)
            .put("base64", base64Str)
            .put("mimeType", mimeType)

        val directRaw = send(token, "POST", "/api/customer/profile-image/direct", directBody)
        when (directRaw) {
            is Raw.Success -> {
                val directObj = directRaw.data as? JSONObject
                val serverUrl = if (kind == "avatar") {
                    directObj?.optString("avatarUrl")?.takeIf { it.isNotBlank() }
                } else {
                    directObj?.optString("coverUrl")?.takeIf { it.isNotBlank() }
                }
                val url = serverUrl
                    ?: directObj?.optString("url")?.takeIf { it.isNotBlank() }
                    ?: "data:$mimeType;base64,$base64Str"
                CustomerResult.Ok(normalizeMediaUrl(url) ?: url)
            }
            is Raw.Failure -> {
                intentFailure ?: CustomerResult.Failed(directRaw.error, directRaw.message)
            }
        }
    }

    override suspend fun addresses(token: String): CustomerResult<List<CustomerAddress>> {
        val raw = send(token, "GET", "/api/customer/addresses")
        return when (raw) {
            is Raw.Failure -> {
                if (raw.error == CustomerError.NotFound) {
                    CustomerResult.Ok(emptyList())
                } else {
                    CustomerResult.Failed(raw.error, raw.message)
                }
            }
            is Raw.Success -> {
                val data = raw.data
                val list = data.asArray("addresses", "items")?.objects()?.mapNotNull(::parseAddress)
                    ?: (data as? JSONArray)?.objects()?.mapNotNull(::parseAddress)
                    ?: emptyList()
                CustomerResult.Ok(list)
            }
        }
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
            .put("addressLine1", input.addressLine)
            .put("subdistrict", input.subDistrict)
            .put("district", input.district)
            .put("province", input.province)
            .put("postalCode", input.postalCode)
            .put("isDefault", input.isDefault)
        addressId?.let { body.put("addressId", it) }
        input.label?.let { body.put("label", it) }
        input.addressLine2?.let {
            body.put("line2", it)
            body.put("addressLine2", it)
        }
        input.latitude?.let { body.put("latitude", it) }
        input.longitude?.let { body.put("longitude", it) }
        return send(token, "POST", "/api/customer/addresses", body).map { data ->
            (data as? JSONObject)?.str("id") ?: addressId
        }
    }

    override suspend fun deleteAddress(token: String, id: String): CustomerResult<Unit> =
        send(token, "DELETE", "/api/customer/addresses/${encode(id)}").toUnit()

    // ------------------------------------------------------------------- cart

    override suspend fun cart(token: String): CustomerResult<RemoteCart> {
        val isWorkers = router.getDialect() == BackendDialect.WORKERS_V1
        val path = if (isWorkers) "/api/v1/cart" else "/api/customer/cart"
        var raw = send(token, "GET", path)
        if (raw is Raw.Failure && raw.error == CustomerError.NotFound && isWorkers) {
            raw = send(token, "GET", "/api/customer/cart")
        }
        return raw.map { data -> data.toCart() }
    }

    override suspend fun addToCart(
        token: String,
        productId: String,
        variantId: String?,
        quantity: Int,
    ): CustomerResult<RemoteCart> {
        val body = JSONObject().put("productId", productId).put("quantity", quantity)
        variantId?.let { body.put("variantId", it) }
        val isWorkers = router.getDialect() == BackendDialect.WORKERS_V1
        val path = if (isWorkers) "/api/v1/cart/items" else "/api/customer/cart/add"
        var raw = send(token, "POST", path, body, isIdempotent = false)
        if (raw is Raw.Failure && raw.error == CustomerError.NotFound && isWorkers) {
            raw = send(token, "POST", "/api/customer/cart/add", body, isIdempotent = false)
        }
        return cartAfter(token, raw)
    }

    override suspend fun updateCartItem(
        token: String,
        itemId: String,
        quantity: Int,
    ): CustomerResult<RemoteCart> {
        val isWorkers = router.getDialect() == BackendDialect.WORKERS_V1
        val method = if (isWorkers) "PATCH" else "PUT"
        val path = if (isWorkers) "/api/v1/cart/items/${encode(itemId)}" else "/api/customer/cart/item/${encode(itemId)}"
        val body = JSONObject().put("quantity", quantity)
        var raw = send(token, method, path, body, isIdempotent = true)
        if (raw is Raw.Failure && raw.error == CustomerError.NotFound && isWorkers) {
            raw = send(token, "PUT", "/api/customer/cart/item/${encode(itemId)}", body, isIdempotent = true)
        }
        return cartAfter(token, raw)
    }

    override suspend fun removeCartItem(token: String, itemId: String): CustomerResult<RemoteCart> {
        val isWorkers = router.getDialect() == BackendDialect.WORKERS_V1
        val path = if (isWorkers) "/api/v1/cart/items/${encode(itemId)}" else "/api/customer/cart/item/${encode(itemId)}"
        var raw = send(token, "DELETE", path, isIdempotent = true)
        if (raw is Raw.Failure && raw.error == CustomerError.NotFound && isWorkers) {
            raw = send(token, "DELETE", "/api/customer/cart/item/${encode(itemId)}", isIdempotent = true)
        }
        return cartAfter(token, raw)
    }

    private suspend fun cartAfter(token: String, raw: Raw): CustomerResult<RemoteCart> =
        when (raw) {
            is Raw.Failure -> CustomerResult.Failed(raw.error, raw.message)
            is Raw.Success -> raw.data.toCart()?.let { CustomerResult.Ok(it) } ?: cart(token)
        }

    // --------------------------------------------------------------- wishlist

    override suspend fun wishlist(token: String): CustomerResult<List<WishlistEntry>> {
        val raw = send(token, "GET", "/api/customer/wishlist")
        return when (raw) {
            is Raw.Failure -> {
                if (raw.error == CustomerError.NotFound) {
                    CustomerResult.Ok(emptyList())
                } else {
                    CustomerResult.Failed(raw.error, raw.message)
                }
            }
            is Raw.Success -> {
                val data = raw.data
                val list = data.asArray("items", "wishlist", "products")?.objects()?.mapNotNull(::parseWishlist)
                    ?: (data as? JSONArray)?.objects()?.mapNotNull(::parseWishlist)
                    ?: emptyList()
                CustomerResult.Ok(list)
            }
        }
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
        val isWorkers = router.getDialect() == BackendDialect.WORKERS_V1
        val path = if (isWorkers) "/api/v1/orders/checkout" else "/api/customer/checkout"
        val body = JSONObject()

        if (isWorkers) {
            val addr = request.shippingAddress
            if (addr != null) {
                val addrJson = JSONObject().apply {
                    put("recipientName", addr.recipientName)
                    put("phone", addr.phone)
                    put("addressLine1", addr.addressLine)
                    addr.addressLine2?.takeIf { it.isNotBlank() }?.let { put("addressLine2", it) }
                    put("subdistrict", addr.subDistrict.ifEmpty { "-" })
                    put("district", addr.district.ifEmpty { "-" })
                    put("province", addr.province.ifEmpty { "-" })
                    put("postalCode", addr.postalCode.ifEmpty { "-" })
                    put("isDefault", addr.isDefault)
                }
                body.put("shippingAddress", addrJson)
            } else {
                body.put("shippingAddressId", request.shippingAddressId)
            }

            val mappedMethod = when (request.paymentMethod.trim().uppercase()) {
                "CARD", "STRIPE" -> "stripe"
                "PROMPTPAY" -> "promptpay"
                "COD" -> "cod"
                "BANK_TRANSFER" -> "bank_transfer"
                else -> request.paymentMethod
            }
            body.put("paymentMethod", mappedMethod)
            if (request.items.isNotEmpty()) {
                val items = JSONArray()
                for (line in request.items) {
                    val obj = JSONObject().put("productId", line.productId).put("quantity", line.quantity)
                    line.variantId?.let { obj.put("variantId", it) }
                    items.put(obj)
                }
                body.put("items", items)
            }
            request.note?.takeIf { it.isNotBlank() }?.let { body.put("notes", it) }
        } else {
            body.put("shippingAddressId", request.shippingAddressId)
            body.put("paymentMethod", request.paymentMethod)
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
        }

        var raw = send(token, "POST", path, body, isIdempotent = false, idempotencyKey = request.requestId)
        if (raw is Raw.Failure && raw.error == CustomerError.NotFound && isWorkers) {
            val fbBody = JSONObject()
                .put("shippingAddressId", request.shippingAddressId)
                .put("paymentMethod", request.paymentMethod)
            request.shippingQuoteId?.takeIf { it.isNotBlank() }?.let { fbBody.put("shippingQuoteId", it) }
            request.requestId?.takeIf { it.isNotBlank() }?.let { fbBody.put("requestId", it) }
            request.note?.takeIf { it.isNotBlank() }?.let { fbBody.put("notes", it) }
            raw = send(token, "POST", "/api/customer/checkout", fbBody, isIdempotent = false, idempotencyKey = request.requestId)
        }

        return raw.map { data ->
            (data as? JSONObject)?.let(::parseCheckout)
        }
    }

    override suspend fun orders(token: String): CustomerResult<List<CustomerOrder>> {
        val isWorkers = router.getDialect() == BackendDialect.WORKERS_V1
        val path = if (isWorkers) "/api/v1/orders" else "/api/customer/orders"
        var raw = send(token, "GET", path)
        if (raw is Raw.Failure && raw.error == CustomerError.NotFound && isWorkers) {
            raw = send(token, "GET", "/api/customer/orders")
        }
        return raw.map { data ->
            data.asArray("orders", "items")?.objects()?.mapNotNull(::parseOrder)
                ?: (data as? JSONArray)?.objects()?.mapNotNull(::parseOrder)
        }
    }

    override suspend fun order(token: String, id: String): CustomerResult<CustomerOrder> {
        val isWorkers = router.getDialect() == BackendDialect.WORKERS_V1
        val path = if (isWorkers) "/api/v1/orders/${encode(id)}" else "/api/customer/orders/${encode(id)}"
        var raw = send(token, "GET", path)
        if (raw is Raw.Failure && raw.error == CustomerError.NotFound && isWorkers) {
            raw = send(token, "GET", "/api/customer/orders/${encode(id)}")
        }
        return raw.map { data ->
            data.asObject("order")?.let(::parseOrder) ?: (data as? JSONObject)?.let(::parseOrder)
        }
    }

    override suspend fun cancelOrder(token: String, id: String): CustomerResult<Unit> {
        val isWorkers = router.getDialect() == BackendDialect.WORKERS_V1
        val (method, path) = if (isWorkers) "POST" to "/api/v1/orders/${encode(id)}/cancel" else "PATCH" to "/api/customer/orders/${encode(id)}/cancel"
        var raw = send(token, method, path)
        if (raw is Raw.Failure && raw.error == CustomerError.NotFound && isWorkers) {
            raw = send(token, "PATCH", "/api/customer/orders/${encode(id)}/cancel")
        }
        return raw.toUnit()
    }

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
        val body = JSONObject()
            .put("shopId", shopId)
            .put("shop_id", shopId)
            .put("sellerId", shopId)
        productId?.takeIf { it.isNotBlank() }?.let {
            body.put("productId", it)
            body.put("product_id", it)
        }
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
            val list = data.asArray("messages", "items")?.objects()?.mapNotNull { parseMessage(it, false) }
            list?.let { rawList ->
                sortMessagesChronologically(rawList)
            }
        }

    override suspend fun sendMessage(
        token: String,
        conversationId: String,
        text: String,
        clientId: String?,
        mediaUrl: String?,
        mediaType: String?,
        attachmentKey: String?,
    ): CustomerResult<CustomerMessage> {
        val effectiveText = text.ifBlank {
            when (mediaType) {
                "video" -> "[วิดีโอ]"
                else -> if (mediaUrl != null || attachmentKey != null) "[รูปภาพ]" else ""
            }
        }
        val payload = JSONObject()
            .put("body", effectiveText)
            .put("text", effectiveText)
            .put("content", effectiveText)
            .put("message", effectiveText)
        clientId?.takeIf { it.isNotBlank() }?.let {
            payload.put("clientId", it)
            payload.put("client_id", it)
        }
        attachmentKey?.takeIf { it.isNotBlank() }?.let { key ->
            payload.put("attachmentKey", key)
            payload.put("attachment_key", key)
        }
        mediaUrl?.takeIf { it.isNotBlank() }?.let { url ->
            payload.put("mediaUrl", url)
            payload.put("media_url", url)
            payload.put("imageUrl", url)
            payload.put("image_url", url)
            payload.put("fileUrl", url)
            payload.put("file_url", url)
            payload.put("attachmentUrl", url)
            val attArr = JSONArray().put(
                JSONObject().put("url", url).put("type", mediaType ?: "image")
            )
            payload.put("attachments", attArr)
        }
        mediaType?.takeIf { it.isNotBlank() }?.let {
            payload.put("mediaType", it)
            payload.put("media_type", it)
        }
        return send(
            token,
            "POST",
            "/api/customer/conversations/${encode(conversationId)}/messages",
            payload,
        ).map { data ->
            val dataObj = data as? JSONObject
            val msgObj = dataObj?.optJSONObject("message") ?: data.asObject("message")
            val effectiveClientId = dataObj?.str("clientId", "client_id") ?: clientId
            msgObj?.let { parseMessage(it, true) }?.let { parsed ->
                var res = parsed
                if (res.clientId == null && effectiveClientId != null) {
                    res = res.copy(clientId = effectiveClientId)
                }
                if (res.mediaUrl == null && (mediaUrl != null || attachmentKey != null)) {
                    val resolvedType = mediaType
                        ?: if (attachmentKey?.endsWith(".mp4", ignoreCase = true) == true || mediaUrl?.endsWith(".mp4", ignoreCase = true) == true) "video"
                        else "image"
                    res = res.copy(
                        mediaUrl = mediaUrl ?: normalizeMediaUrl(attachmentKey),
                        mediaType = resolvedType,
                    )
                }
                res
            }
        }
    }

    override suspend fun uploadChatAttachment(
        token: String,
        conversationId: String,
        bytes: ByteArray,
        mimeType: String,
        filename: String,
    ): CustomerResult<UploadChatAttachmentResult> = withContext(Dispatchers.IO) {
        val dialect = router.getDialect()

        // 1. Try Workers multipart upload
        if (dialect == BackendDialect.WORKERS_V1) {
            try {
                val mediaType = try { mimeType.toMediaType() } catch (_: Exception) { "image/webp".toMediaType() }
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("purpose", "chat_attachment")
                    .addFormDataPart("file", filename, bytes.toRequestBody(mediaType))
                    .build()

                val request = Request.Builder()
                    .url(router.currentHost().trimEnd('/') + "/api/v1/media/upload")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val text = resp.body?.string().orEmpty()
                        val root = JSONObject(text)
                        val data = root.optJSONObject("data")
                        val objKey = data?.optString("r2Key")?.takeIf { it.isNotBlank() }
                            ?: data?.optString("id")
                        val pubUrl = data?.optString("publicUrl")?.takeIf { it.isNotBlank() }
                        if (objKey != null) {
                            return@withContext CustomerResult.Ok(
                                UploadChatAttachmentResult(
                                    objectKey = objKey,
                                    publicUrl = pubUrl ?: normalizeMediaUrl(objKey),
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 2. Try presign + direct PUT to cloud storage
        try {
            val presignBody = JSONObject()
                .put("filename", filename)
                .put("contentType", mimeType)
                .put("purpose", "chat")
                .put("conversationId", conversationId)

            val presignRaw = send(token, "POST", "/api/upload/presign", presignBody)
            if (presignRaw is Raw.Success) {
                val presignData = (presignRaw.data as? JSONObject)?.optJSONObject("data")
                    ?: presignRaw.data as? JSONObject
                val uploadUrl = presignData?.optString("uploadUrl")
                val objectKey = presignData?.optString("objectKey")
                val publicUrl = presignData?.optString("publicUrl")

                if (!uploadUrl.isNullOrBlank() && !objectKey.isNullOrBlank()) {
                    val mediaType = try {
                        mimeType.toMediaType()
                    } catch (_: Exception) {
                        "image/webp".toMediaType()
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

                    if (isPutSuccess) {
                        return@withContext CustomerResult.Ok(
                            UploadChatAttachmentResult(
                                objectKey = objectKey,
                                publicUrl = publicUrl?.takeIf { it.isNotBlank() } ?: normalizeMediaUrl(objectKey),
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 3. Direct server upload fallback
        val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val directBody = JSONObject()
            .put("conversationId", conversationId)
            .put("filename", filename)
            .put("contentType", mimeType)
            .put("base64", base64Str)

        val directRaw = send(token, "POST", "/api/upload/direct", directBody)
        if (directRaw is Raw.Success) {
            val directData = (directRaw.data as? JSONObject)?.optJSONObject("data")
                ?: directRaw.data as? JSONObject
            val dObjectKey = directData?.optString("objectKey")
            val dPublicUrl = directData?.optString("publicUrl")
            if (!dObjectKey.isNullOrBlank()) {
                return@withContext CustomerResult.Ok(
                    UploadChatAttachmentResult(
                        objectKey = dObjectKey,
                        publicUrl = dPublicUrl?.takeIf { it.isNotBlank() } ?: normalizeMediaUrl(dObjectKey),
                    )
                )
            }
        }

        val directFailure = directRaw as? Raw.Failure
        CustomerResult.Failed(
            directFailure?.error ?: CustomerError.Network,
            directFailure?.message ?: "Failed to upload media attachment to server",
        )
    }

    override suspend fun markConversationRead(
        token: String,
        conversationId: String,
    ): CustomerResult<Unit> =
        send(token, "POST", "/api/customer/conversations/${encode(conversationId)}/read").toUnit()

    // -------------------------------------------------------------- transport

    private sealed interface Raw {
        data class Success(val data: Any?) : Raw
        data class Failure(val error: CustomerError, val message: String?) : Raw
    }

    private suspend fun send(
        token: String,
        method: String,
        path: String,
        body: JSONObject? = null,
        isIdempotent: Boolean = (method == "GET" || method == "DELETE"),
        idempotencyKey: String? = null,
    ): Raw = withContext(Dispatchers.IO) {
        try {
            router.executeWithFailover(isIdempotent = isIdempotent) { host, dialect ->
                val requestBody = if (method == "GET") {
                    null
                } else {
                    (body?.toString() ?: "{}").toRequestBody(JSON)
                }

                val effectivePath = if (dialect == BackendDialect.WORKERS_V1 && path.startsWith("/api/") && !path.startsWith("/api/v1/")) {
                    "/api/v1/" + path.removePrefix("/api/")
                } else path

                val requestBuilder = Request.Builder()
                    .url(host.trimEnd('/') + effectivePath)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .header("Cookie", "${AuthRemoteDataSource.SESSION_COOKIE}=$token")
                    .method(method, requestBody)

                if (idempotencyKey != null) {
                    requestBuilder.header("Idempotency-Key", idempotencyKey)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
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

                    // Legacy capability fallback for routes not yet ported to Workers
                    if (response.code == 404 && dialect == BackendDialect.WORKERS_V1 && router.allowLegacyCapabilityFallback) {
                        for (fbHost in router.candidateHostsList()) {
                            if (fbHost != host && fbHost.contains("onrender.com")) {
                                try {
                                    val fbRequest = Request.Builder()
                                        .url(fbHost.trimEnd('/') + path)
                                        .header("Authorization", "Bearer $token")
                                        .header("Accept", "application/json")
                                        .header("Cookie", "${AuthRemoteDataSource.SESSION_COOKIE}=$token")
                                        .method(method, requestBody)
                                        .build()
                                    val fbResp = client.newCall(fbRequest).execute()
                                    fbResp.use { fbR ->
                                        if (fbR.isSuccessful) {
                                            val fbText = fbR.body?.string().orEmpty()
                                            val fbEnv = try { JSONObject(fbText) } catch (_: Exception) { null }
                                            return@executeWithFailover Raw.Success(
                                                fbEnv?.opt("data")?.takeUnless { it === JSONObject.NULL },
                                            )
                                        }
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }

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
            }
        } catch (_: IOException) {
            Raw.Failure(CustomerError.Network, null)
        } catch (e: Exception) {
            Raw.Failure(CustomerError.Server, e.message)
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

    private fun Any?.asObject(vararg keys: String): JSONObject? {
        val obj = this as? JSONObject ?: return null
        for (key in keys) {
            obj.optJSONObject(key)?.let { return it }
        }
        return obj
    }

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
        if (obj != null && obj.has("shopGroups")) {
            val groups = obj.optJSONArray("shopGroups")
            if (groups != null) {
                val allItems = ArrayList<RemoteCartItem>()
                for (g in 0 until groups.length()) {
                    val group = groups.optJSONObject(g) ?: continue
                    val gShopId = group.str("shopId", "shop_id")
                    val gShopName = group.str("shopName", "shop_name")
                    val groupItems = group.optJSONArray("items")?.objects().orEmpty()
                    for (item in groupItems) {
                        parseCartItem(item, gShopId, gShopName)?.let { allItems.add(it) }
                    }
                }
                return RemoteCart(allItems)
            }
        }
        val array = when {
            this is JSONArray -> this
            obj == null -> null
            else -> obj.optJSONArray("items")
                ?: obj.optJSONObject("cart")?.optJSONArray("items")
        } ?: return null
        return RemoteCart(array.objects().mapNotNull { parseCartItem(it, null, null) })
    }

    private fun normalizeMediaUrl(url: String?): String? {
        return VelnoxMediaUrlResolver.resolve(url, router.currentHost(), router.getDialect())
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
            addressLine = obj.str("addressLine1", "line1", "addressLine", "address_line", "address", "street").orEmpty(),
            addressLine2 = obj.str("addressLine2", "line2"),
            subDistrict = obj.str("subdistrict", "subDistrict", "sub_district").orEmpty(),
            district = obj.str("district").orEmpty(),
            province = obj.str("province", "state").orEmpty(),
            postalCode = obj.str("postalCode", "postal_code", "zipCode", "zip").orEmpty(),
            latitude = obj.num("latitude"),
            longitude = obj.num("longitude"),
            isDefault = obj.flag("isDefault", "is_default", "default") ?: false,
        )
    }

    private fun parseCartItem(obj: JSONObject, fallbackShopId: String? = null, fallbackShopName: String? = null): RemoteCartItem? {
        val product = obj.optJSONObject("product")
        val variant = obj.optJSONObject("variant")
        val productId = obj.str("productId", "product_id") ?: product?.str("id") ?: return null
        val id = obj.str("id", "cartItemId", "cart_item_id", "itemId") ?: return null
        val quantity = obj.num("quantity", "qty")?.toInt() ?: return null
        val shopName = obj.str("shopName", "shop_name") ?: fallbackShopName ?: product?.str("shopName")
            ?: obj.optJSONObject("shop")?.str("name")
        val shopId = obj.str("shopId", "shop_id") ?: fallbackShopId ?: product?.str("shopId")
            ?: obj.optJSONObject("shop")?.str("id")

        return RemoteCartItem(
            id = id,
            productId = productId,
            variantId = obj.str("variantId", "variant_id") ?: variant?.str("id"),
            name = obj.str("name", "productName", "product_name") ?: product?.str("name"),
            variantName = obj.str("variantOptionLabels", "variantName", "variant_name") ?: variant?.str("name"),
            shopName = shopName,
            imageUrl = imageOf(obj) ?: product?.let(::imageOf),
            price = obj.num("priceSnapshot", "price", "unitPrice", "unit_price")
                ?: variant?.num("price") ?: product?.num("price"),
            currency = obj.str("currency") ?: DEFAULT_CURRENCY,
            quantity = quantity,
            maxStock = (obj.num("maxStock", "availableStock", "stockAvailable", "stock") ?: variant?.num("stock")
                ?: product?.num("totalAvailableStock", "stock"))?.toInt(),
            shopId = shopId,
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

        val totalAmount = obj.num("total", "totalAmount", "total_amount", "grandTotal")
            ?: obj.num("totalSatang")?.let { it / 100.0 }

        return CheckoutOutcome(
            orderIds = ids,
            orderNumbers = numbers,
            total = totalAmount,
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
        val serverCount = obj.num("itemCount", "item_count")?.toInt()

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
            serverItemCount = serverCount,
        )
    }

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
                obj.str("addressLine1", "line1", "addressLine", "address_line", "address").orEmpty(),
                obj.str("addressLine2", "line2").orEmpty(),
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
        val seller = obj.optJSONObject("seller")
        val last = obj.opt("lastMessage") ?: obj.opt("last_message")
        val lastText = when (last) {
            is String -> last.trim().takeIf { it.isNotEmpty() }
            is JSONObject -> {
                val t = last.str("body", "text", "content")
                if (t.isNullOrBlank()) {
                    val mType = last.str("mediaType", "media_type", "type")
                    val aKey = last.str("attachmentKey", "attachment_key", "mediaUrl", "media_url", "attachmentUrl")
                    when {
                        mType == "video" || aKey?.endsWith(".mp4", ignoreCase = true) == true -> "[วิดีโอ]"
                        mType == "image" || aKey != null -> "[รูปภาพ]"
                        else -> null
                    }
                } else t
            }
            else -> null
        }
        val convType = obj.str("type")?.uppercase()
        val isSupport = obj.flag("isSupport", "is_support") ?: (convType == "CUSTOMER_COMPANY")
        val shopName = obj.str("shopName", "shop_name") ?: shop?.str("name") ?: seller?.str("shopName", "name") ?: obj.str("title")

        return CustomerConversation(
            id = id,
            shopId = obj.str("shopId", "shop_id") ?: shop?.str("id") ?: seller?.str("id"),
            shopName = shopName,
            lastMessage = lastText,
            lastMessageAt = obj.time("lastMessageAt", "last_message_at", "updatedAt", "updated_at")
                ?: (last as? JSONObject)?.time("createdAt", "created_at"),
            unreadCount = obj.num("unreadCount", "unread_count", "unread")?.toInt() ?: 0,
            isSupport = isSupport,
            participantName = obj.str("participantName", "participant_name") ?: shopName,
            participantOnline = obj.flag("participantOnline", "participant_online"),
            productId = obj.str("productId", "product_id") ?: product?.str("id"),
            productName = product?.str("name"),
            productPrice = product?.num("price"),
            productImageUrl = product?.let(::imageOf),
        )
    }

    private fun parseMessage(obj: JSONObject, knownFromCustomer: Boolean): CustomerMessage? {
        val id = obj.str("id", "messageId", "_id") ?: return null
        val rawMediaUrl = obj.str(
            "attachmentUrl", "attachment_url",
            "mediaUrl", "media_url", "imageUrl", "image_url", "fileUrl", "file_url",
        ) ?: obj.optJSONArray("attachments")?.optJSONObject(0)?.str("url")
        val senderRole = obj.str("senderRole", "sender_role", "role")?.lowercase()
        val fromCustomer = when {
            knownFromCustomer -> true
            senderRole != null -> senderRole !in SELLER_SENDERS
            obj.flag("isFromCustomer", "fromCustomer", "is_customer") != null ->
                obj.flag("isFromCustomer", "fromCustomer", "is_customer") == true
            obj.flag("isFromShop", "fromShop", "is_seller", "is_shop") != null ->
                obj.flag("isFromShop", "fromShop", "is_seller", "is_shop") == false
            else -> false
        }
        val rawType = obj.str("mediaType", "media_type", "type")
        val mediaType = when {
            rawType == "video" || rawMediaUrl?.endsWith(".mp4", ignoreCase = true) == true -> "video"
            rawType == "image" || rawMediaUrl != null -> "image"
            else -> null
        }
        return CustomerMessage(
            id = id,
            text = obj.str("content", "message", "body", "text").orEmpty(),
            fromCustomer = fromCustomer,
            createdAt = obj.time("createdAt", "created_at", "timestamp", "sentAt"),
            isSystem = senderRole == "system" || obj.flag("isSystem", "is_system") == true,
            isRead = obj.flag("isRead", "is_read", "read") == true,
            clientId = obj.str("clientId", "client_id"),
            mediaUrl = normalizeMediaUrl(rawMediaUrl),
            mediaType = mediaType,
        )
    }

    private fun imageOf(obj: JSONObject): String? {
        val raw = obj.str("imageUrl", "image_url", "image", "productImageUrl", "product_image_url")
            ?: obj.optJSONObject("primaryImage")?.str("displayUrl", "url")
            ?: obj.optJSONArray("images")?.optJSONObject(0)?.str("displayUrl", "url")
            ?: obj.optJSONArray("images")?.optString(0)?.takeIf { it.isNotBlank() }
        return normalizeMediaUrl(raw)
    }

    private fun JSONObject.str(vararg keys: String): String? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val v = optString(key).trim()
            if (v.isNotEmpty() && !v.equals("null", ignoreCase = true)) return v
        }
        return null
    }

    private fun JSONObject.num(vararg keys: String): Double? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val raw = opt(key)
            val d = when (raw) {
                is Number -> raw.toDouble()
                is String -> raw.trim().toDoubleOrNull()
                else -> null
            }
            if (d != null && d.isFinite()) return d
        }
        return null
    }

    private fun JSONObject.flag(vararg keys: String): Boolean? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            return when (val raw = opt(key)) {
                is Boolean -> raw
                is Number -> raw.toInt() != 0
                is String -> raw.trim().lowercase().let { it == "true" || it == "1" }
                else -> null
            }
        }
        return null
    }

    private fun JSONObject.time(vararg keys: String): Long? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val raw = opt(key)
            when (raw) {
                is Number -> {
                    val n = raw.toLong()
                    return if (n < 100_000_000_000L) n * 1000L else n
                }
                is String -> {
                    val s = raw.trim()
                    s.toLongOrNull()?.let { n ->
                        return if (n < 100_000_000_000L) n * 1000L else n
                    }
                    parseIsoDate(s)?.let { return it }
                }
            }
        }
        return null
    }

    private fun parseIsoDate(s: String): Long? =
        try {
            Instant.parse(s).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(s).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }

    private fun encode(v: String): String = URLEncoder.encode(v, "UTF-8")

    private fun sortMessagesChronologically(messages: List<CustomerMessage>): List<CustomerMessage> =
        messages.sortedWith { a, b ->
            val timeA = a.createdAt ?: 0L
            val timeB = b.createdAt ?: 0L
            when {
                timeA != timeB -> timeA.compareTo(timeB)
                else -> a.id.compareTo(b.id)
            }
        }

    companion object {
        private const val DEFAULT_CURRENCY = "THB"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val REJECTED_CODES = setOf(400, 409, 422)
        private val SELLER_SENDERS = setOf("seller", "shop", "support", "agent", "admin", "company")

        fun sortMessagesChronologically(list: List<CustomerMessage>): List<CustomerMessage> {
            if (list.size <= 1) return list
            val withTime = list.filter { it.createdAt != null && it.createdAt > 0L }
            val isDescending = withTime.size >= 2 &&
                (withTime.first().createdAt ?: 0L) > (withTime.last().createdAt ?: 0L)
            val baseList = if (isDescending) list.reversed() else list

            return baseList.sortedWith(
                compareBy<CustomerMessage> { it.createdAt ?: 0L }
                    .thenBy { if (it.isPending) 1 else 0 }
            )
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(BackendConfig.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(BackendConfig.READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(BackendConfig.WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build()
    }
}
