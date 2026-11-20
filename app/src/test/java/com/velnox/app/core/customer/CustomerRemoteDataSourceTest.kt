package com.velnox.app.core.customer

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CustomerRemoteDataSourceTest {

    private class Recorded(val method: String, val url: String, val auth: String?, val body: String)

    private val requests = mutableListOf<Recorded>()

    /** Answers each request in turn from [replies] (code to body); the last one repeats. */
    private fun api(vararg replies: Pair<Int, String>, throws: Boolean = false): CustomerRemoteDataSource {
        var index = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request: Request = chain.request()
                val buffer = Buffer()
                request.body?.writeTo(buffer)
                requests += Recorded(
                    request.method,
                    request.url.toString(),
                    request.header("Authorization"),
                    buffer.readUtf8(),
                )
                if (throws) throw IOException("offline")
                val (code, body) = replies[minOf(index++, replies.lastIndex)]
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("test")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return CustomerRemoteDataSource(baseUrl = "https://example.test", client = client)
    }

    @Test
    fun `addresses are parsed and the bearer token is sent`() = runBlocking {
        val body = """{"success":true,"data":[
            {"id":"a1","recipientName":"Somchai","phone":"0812345678","addressLine":"99/1 Sukhumvit",
             "subDistrict":"Khlong Toei","district":"Khlong Toei","province":"Bangkok",
             "postalCode":"10110","isDefault":true},
            {"id":"a2","recipient_name":"Malee","phone":"0899999999","address_line":"5 Silom",
             "sub_district":"Silom","district":"Bang Rak","province":"Bangkok",
             "postal_code":"10500","is_default":0},
            {"recipientName":"no id is dropped"}
        ]}"""

        val result = api(200 to body).addresses("tok")

        result as CustomerResult.Ok
        assertEquals(listOf("a1", "a2"), result.value.map { it.id })
        assertTrue(result.value[0].isDefault)
        assertEquals("Malee", result.value[1].recipientName)
        assertEquals("10500", result.value[1].postalCode)
        assertFalse(result.value[1].isDefault)
        assertEquals("Bearer tok", requests.single().auth)
        assertEquals("GET", requests.single().method)
        assertEquals("https://example.test/api/customer/addresses", requests.single().url)
    }

    @Test
    fun `a 401 is Unauthorized and carries the server message`() = runBlocking {
        val result = api(401 to """{"success":false,"error":{"code":"UNAUTHORIZED","message":"Not authenticated"}}""")
            .profile("expired")

        result as CustomerResult.Failed
        assertEquals(CustomerError.Unauthorized, result.error)
        assertEquals("Not authenticated", result.message)
    }

    @Test
    fun `an unreachable server is a Network failure`() = runBlocking {
        val result = api(200 to "{}", throws = true).cart("tok")

        assertEquals(CustomerResult.Failed(CustomerError.Network), result)
    }

    @Test
    fun `a body that is not the envelope is a Server failure, not a crash`() = runBlocking {
        val result = api(200 to "<html>gateway</html>").orders("tok")

        assertEquals(CustomerResult.Failed(CustomerError.Server), result)
    }

    @Test
    fun `cart items accept nested product and variant objects`() = runBlocking {
        val body = """{"success":true,"data":{"items":[
            {"id":"c1","quantity":2,"product":{"id":"p1","name":"Lamp","shopName":"Lumen",
              "primaryImage":{"displayUrl":"https://img.test/l.jpg"}},
             "variant":{"id":"v1","name":"Black","price":"199.50","stock":7}}
        ]}}"""

        val result = api(200 to body).cart("tok")

        result as CustomerResult.Ok
        val item = result.value.items.single()
        assertEquals("c1", item.id)
        assertEquals("p1", item.productId)
        assertEquals("v1", item.variantId)
        assertEquals("Lamp", item.name)
        assertEquals("Lumen", item.shopName)
        assertEquals("https://img.test/l.jpg", item.imageUrl)
        assertEquals(199.5, item.price!!, 0.0)
        assertEquals(2, item.quantity)
        assertEquals(7, item.maxStock)
    }

    @Test
    fun `adding to the cart refetches the cart when the server only acknowledges`() = runBlocking {
        val api = api(
            200 to """{"success":true,"data":{"added":true}}""",
            200 to """{"success":true,"data":{"items":[{"id":"c9","productId":"p9","quantity":1}]}}""",
        )

        val result = api.addToCart("tok", productId = "p9", variantId = null, quantity = 1)

        result as CustomerResult.Ok
        assertEquals("c9", result.value.items.single().id)
        assertEquals(listOf("POST", "GET"), requests.map { it.method })
        assertEquals("https://example.test/api/customer/cart/add", requests[0].url)
        val sent = JSONObject(requests[0].body)
        assertEquals("p9", sent.getString("productId"))
        assertEquals(1, sent.getInt("quantity"))
        assertFalse(sent.has("variantId"))
    }

    @Test
    fun `cart mutations use the item routes`() = runBlocking {
        val api = api(200 to """{"success":true,"data":{"items":[]}}""")

        api.updateCartItem("tok", "c 1", 3)
        api.removeCartItem("tok", "c 1")

        assertEquals(listOf("PUT", "DELETE"), requests.map { it.method })
        assertEquals("https://example.test/api/customer/cart/item/c+1", requests[0].url)
        assertEquals(3, JSONObject(requests[0].body).getInt("quantity"))
    }

    @Test
    fun `an order parses with an ISO timestamp, lines and a composed address`() = runBlocking {
        val body = """{"success":true,"data":{"orders":[
            {"id":"o1","orderNumber":"VLX-1001","status":"SHIPPED","total":"450.00",
             "createdAt":"2026-01-02T03:04:05.000Z","shop":{"name":"Lumen"},
             "shippingAddress":{"id":"a1","addressLine":"99/1","subDistrict":"A","district":"B",
               "province":"C","postalCode":"10110"},
             "items":[{"product":{"id":"p1","name":"Lamp"},"quantity":2,"price":225},
                      {"productId":"p2","name":"Mug","quantity":1,"price":0}]}
        ]}}"""

        val result = api(200 to body).orders("tok")

        result as CustomerResult.Ok
        val order = result.value.single()
        assertEquals("VLX-1001", order.orderNumber)
        assertEquals("shipped", order.status)
        assertEquals(450.0, order.total!!, 0.0)
        assertEquals(1767323045000L, order.createdAt)
        assertEquals("Lumen", order.shopName)
        assertEquals("99/1 A B C 10110", order.deliveryAddress)
        assertEquals(3, order.itemCount)
        assertEquals("p1", order.items[0].productId)
    }

    @Test
    fun `checkout posts the real payload and returns the created order ids`() = runBlocking {
        val api = api(
            200 to """{"success":true,"data":{"orders":[
                {"id":"o1","orderNumber":"VLX-1"},{"id":"o2","orderNumber":"VLX-2"}],"total":500}}""",
        )

        val result = api.checkout(
            "tok",
            CheckoutRequest(
                shippingAddressId = "a1",
                items = listOf(CheckoutLine("p1", "v1", 2), CheckoutLine("p2", null, 1)),
                paymentMethod = "promptpay",
            ),
        )

        result as CustomerResult.Ok
        assertEquals(listOf("o1", "o2"), result.value.orderIds)
        assertEquals(listOf("VLX-1", "VLX-2"), result.value.orderNumbers)
        assertEquals(500.0, result.value.total!!, 0.0)
        val sent = JSONObject(requests.single().body)
        assertEquals("a1", sent.getString("shippingAddressId"))
        assertEquals("promptpay", sent.getString("paymentMethod"))
        assertEquals(2, sent.getJSONArray("items").length())
        assertEquals("v1", sent.getJSONArray("items").getJSONObject(0).getString("variantId"))
        assertFalse(sent.getJSONArray("items").getJSONObject(1).has("variantId"))
    }

    @Test
    fun `cancel and mark-read use their documented verbs`() = runBlocking {
        val api = api(200 to """{"success":true}""")

        assertTrue(api.cancelOrder("tok", "o1") is CustomerResult.Ok)
        assertTrue(api.markNotificationRead("tok", "n1") is CustomerResult.Ok)
        assertTrue(api.markAllNotificationsRead("tok") is CustomerResult.Ok)
        assertTrue(api.deleteAddress("tok", "a1") is CustomerResult.Ok)

        assertEquals(listOf("PATCH", "PATCH", "PUT", "DELETE"), requests.map { it.method })
        assertEquals(
            listOf(
                "https://example.test/api/customer/orders/o1/cancel",
                "https://example.test/api/customer/notifications/n1/read",
                "https://example.test/api/customer/notifications/read-all",
                "https://example.test/api/customer/addresses/a1",
            ),
            requests.map { it.url },
        )
    }

    @Test
    fun `a rejected cancellation surfaces the server reason`() = runBlocking {
        val result = api(
            409 to """{"success":false,"error":{"code":"CONFLICT","message":"Order already shipped"}}""",
        ).cancelOrder("tok", "o1")

        result as CustomerResult.Failed
        assertEquals(CustomerError.Rejected, result.error)
        assertEquals("Order already shipped", result.message)
    }

    @Test
    fun `wishlist toggle reads the state from the response`() = runBlocking {
        val api = api(200 to """{"success":true,"data":{"wishlisted":false}}""")

        val result = api.toggleWishlist("tok", "p1")

        assertEquals(CustomerResult.Ok(false), result)
        assertEquals(1, requests.size)
    }

    @Test
    fun `wishlist toggle falls back to the list when no state is returned`() = runBlocking {
        val api = api(
            200 to """{"success":true}""",
            200 to """{"success":true,"data":{"items":[{"product":{"id":"p1","name":"Lamp","price":10}}]}}""",
        )

        val result = api.toggleWishlist("tok", "p1")

        assertEquals(CustomerResult.Ok(true), result)
        assertEquals(listOf("POST", "GET"), requests.map { it.method })
    }

    @Test
    fun `notifications derive read state and epoch-second timestamps`() = runBlocking {
        val body = """{"success":true,"data":[
            {"id":"n1","type":"ORDER","title":"Shipped","message":"On its way","created_at":1767323045,"is_read":false},
            {"id":"n2","title":"Promo","body":"-10%","readAt":"2026-01-02T00:00:00Z"}
        ]}"""

        val result = api(200 to body).notifications("tok")

        result as CustomerResult.Ok
        assertEquals("order", result.value[0].type)
        assertEquals("On its way", result.value[0].body)
        assertFalse(result.value[0].read)
        assertEquals(1767323045000L, result.value[0].createdAt)
        assertTrue(result.value[1].read)
        assertEquals("system", result.value[1].type)
        assertNull(result.value[1].createdAt)
    }

    @Test
    fun `conversations and messages parse and sent messages are the customer's`() = runBlocking {
        val conversations = api(
            200 to """{"success":true,"data":{"conversations":[
                {"id":"t1","shop":{"id":"s1","name":"Lumen"},"lastMessage":{"text":"Hi"},"unreadCount":2}]}}""",
        ).conversations("tok")
        conversations as CustomerResult.Ok
        assertEquals("Lumen", conversations.value.single().shopName)
        assertEquals("Hi", conversations.value.single().lastMessage)
        assertEquals(2, conversations.value.single().unreadCount)

        val messages = api(
            200 to """{"success":true,"data":[
                {"id":"m1","content":"Hello","senderRole":"seller"},
                {"id":"m2","content":"Hi!","senderRole":"customer"}]}""",
        ).messages("tok", "t1")
        messages as CustomerResult.Ok
        assertEquals(listOf(false, true), messages.value.map { it.fromCustomer })

        val sent = api(200 to """{"success":true,"data":{"id":"m3","content":"Thanks"}}""")
            .sendMessage("tok", "t1", "Thanks")
        sent as CustomerResult.Ok
        assertTrue(sent.value.fromCustomer)
        val posted = requests.last()
        assertEquals("POST", posted.method)
        assertEquals("https://example.test/api/customer/conversations/t1/messages", posted.url)
        assertEquals("Thanks", JSONObject(posted.body).getString("text"))
    }

    @Test
    fun `the real address shape - line1, subdistrict, state - parses`() = runBlocking {
        val body = """{"success":true,"data":[
            {"id":"a1","label":"Home","recipientName":"Somchai","phone":"0812345678","line1":"99/1 Sukhumvit",
             "line2":null,"subdistrict":"Khlong Toei","district":null,"province":"Bangkok","postalCode":"10110",
             "country":"TH","latitude":13.7,"longitude":null,"isDefault":true}]}"""

        val result = api(200 to body).addresses("tok")

        result as CustomerResult.Ok
        val address = result.value.single()
        assertEquals("99/1 Sukhumvit", address.addressLine)
        assertEquals("Khlong Toei", address.subDistrict)
        assertEquals("", address.district)
        assertNull(address.addressLine2)
        assertEquals(13.7, address.latitude!!, 0.0)
        assertNull(address.longitude)
        assertEquals("Home", address.label)
    }

    @Test
    fun `saving an address uses the server field names and returns the id`() = runBlocking {
        val api = api(200 to """{"success":true,"data":{"id":"new-id"}}""")

        val result = api.saveAddress(
            "tok",
            addressId = "a1",
            input = AddressInput(
                label = "Work", recipientName = "Somchai", phone = "0812345678",
                addressLine = "99/1", addressLine2 = null, subDistrict = "A", district = "B",
                province = "Bangkok", postalCode = "10110", latitude = null, longitude = null,
                isDefault = true,
            ),
        )

        assertEquals(CustomerResult.Ok("new-id"), result)
        val sent = JSONObject(requests.single().body)
        assertEquals("POST", requests.single().method)
        assertEquals("a1", sent.getString("addressId"))
        assertEquals("99/1", sent.getString("line1"))
        assertEquals("A", sent.getString("subdistrict"))
        assertEquals("Bangkok", sent.getString("province"))
        assertTrue(sent.getBoolean("isDefault"))
        assertFalse(sent.has("line2"))
        assertFalse(sent.has("latitude"))
    }

    @Test
    fun `the profile answer has no id and the PUT answer has no email`() = runBlocking {
        val get = api(
            200 to """{"success":true,"data":{"name":"Somchai","email":"s@x.test","phone":null,
                "avatarUrl":null,"coverUrl":null,"memberSince":1767323045000}}""",
        ).profile("tok")
        get as CustomerResult.Ok
        assertEquals("Somchai", get.value.name)
        assertEquals("s@x.test", get.value.email)
        assertNull(get.value.phone)
        assertEquals(1767323045000L, get.value.memberSince)

        val put = api(
            200 to """{"success":true,"data":{"name":"New","phone":"0899999999","memberSince":1767323045000}}""",
        ).updateProfile("tok", ProfileUpdate("New", "0899999999"))
        put as CustomerResult.Ok
        assertEquals("0899999999", put.value.phone)
        assertNull(put.value.email)
    }

    @Test
    fun `cart rows use the server's priceSnapshot, productName and productImageUrl`() = runBlocking {
        val body = """{"success":true,"data":{"items":[
            {"id":"c1","productId":"p1","variantId":null,"productName":"Lamp","quantity":2,
             "priceSnapshot":199.5,"availableStock":7,"shopName":"Lumen","productImageUrl":"https://img.test/l.jpg"}]}}"""

        val result = api(200 to body).cart("tok")

        result as CustomerResult.Ok
        val item = result.value.items.single()
        assertEquals("Lamp", item.name)
        assertEquals(199.5, item.price!!, 0.0)
        assertEquals(7, item.maxStock)
        assertEquals("https://img.test/l.jpg", item.imageUrl)
    }

    @Test
    fun `shipping quote posts address and cart item ids and parses server totals`() = runBlocking {
        val body = """{"success":true,"data":{
            "quoteId":"q-1","currency":"THB","subtotal":"450.00","discount":"0.00",
            "shippingTotal":"35.00","total":"485.00","itemCount":2,
            "shops":[{"shopId":"s1","shopName":"Lumen","subtotal":"450.00","shippingFee":"35.00"}]}}"""

        val api = api(200 to body)
        val result = api.shippingQuote("tok", addressId = "a1", cartItemIds = listOf("ci-1", "ci-2"))

        result as CustomerResult.Ok
        assertEquals("q-1", result.value.quoteId)
        assertEquals(450.0, result.value.subtotal, 0.0)
        assertEquals(35.0, result.value.shippingTotal, 0.0)
        assertEquals(485.0, result.value.total, 0.0)
        assertEquals(1, result.value.shops.size)
        assertEquals("Lumen", result.value.shops.single().shopName)

        val sent = JSONObject(requests.single().body)
        assertEquals("https://example.test/api/customer/shipping/quote", requests.single().url)
        assertEquals("a1", sent.getString("shippingAddressId"))
        assertEquals(2, sent.getJSONArray("cartItemIds").length())
    }

    @Test
    fun `checkout sends cartItemIds, quoteId and requestId without client money fields`() = runBlocking {
        val api = api(
            200 to """{"success":true,"data":{"parentOrderId":"ord-1","parentOrderNumber":"202611200000000001","total":485.0}}""",
        )

        val result = api.checkout(
            "tok",
            CheckoutRequest(
                shippingAddressId = "a1",
                paymentMethod = "PROMPTPAY",
                cartItemIds = listOf("ci-1"),
                shippingQuoteId = "q-1",
                requestId = "req-123",
            ),
        )

        result as CustomerResult.Ok
        assertEquals("ord-1", result.value.primaryOrderId)
        assertEquals("202611200000000001", result.value.primaryOrderNumber)
        val sent = JSONObject(requests.single().body)
        assertEquals("PROMPTPAY", sent.getString("paymentMethod"))
        assertEquals("q-1", sent.getString("shippingQuoteId"))
        assertEquals("req-123", sent.getString("requestId"))
        assertFalse(sent.has("total"))
        assertFalse(sent.has("shippingFee"))
    }
}
