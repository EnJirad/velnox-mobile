package com.velnox.app.core.catalog

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Parsing against responses captured from the deployed API.
 *
 * `catalog_sample.json`, `product_detail_sample.json` and `categories_sample.json`
 * are real answers from `https://velnox-api.onrender.com`, trimmed to one product
 * and two images. The field names, types and values are untouched, so a backend
 * change that renames or re-types a field breaks these tests instead of silently
 * emptying the app.
 */
class CatalogRemoteDataSourceTest {

    private val requested = mutableListOf<String>()

    private fun client(
        code: Int,
        body: String,
        throws: Boolean = false,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            requested += chain.request().url.toString()
            if (throws) throw IOException("offline")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/$name")) { "missing fixture $name" }
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun `a captured catalogue response parses into products`() = runBlocking {
        val api = CatalogRemoteDataSource(
            baseUrl = "https://example.test",
            client = client(200, fixture("catalog_sample.json")),
        )

        val result = api.products(CatalogQuery())

        assertTrue(result is CatalogResult.Ok)
        val page = (result as CatalogResult.Ok).value
        assertEquals(1, page.items.size)
        val product = page.items.first()
        assertEquals("2411c1da-f01d-408f-8106-c1f5a303ddcf", product.id)
        assertTrue(product.name.startsWith("MAIXIAN"))
        assertEquals(0.0, product.price!!, 0.0001)
        assertEquals(144.53, product.variantPrice!!, 0.0001)
        assertEquals(144.53, product.displayPrice!!, 0.0001)
        assertEquals("THB", product.currency)
        assertEquals("chargers-cables", product.categorySlug)
        assertEquals("SUPER GADGET ZENE", product.shopName)
        assertEquals(true, product.inStock)
        assertEquals(149.0, product.compareAtPrice!!, 0.0001)
        assertEquals(3, product.discountPercent)
        assertEquals("c7163869-84be-4ef7-9f97-ad774f87177d", product.sellerId)
        assertEquals("2017a0fe-2086-49a7-a857-6d2086301270", product.shopId)
        assertTrue(product.imageUrl!!.startsWith("https://"))
    }

    @Test
    fun `a captured product response parses into a detail`() = runBlocking {
        val api = CatalogRemoteDataSource(
            baseUrl = "https://example.test",
            client = client(200, fixture("product_detail_sample.json")),
        )

        val result = api.product("2411c1da-f01d-408f-8106-c1f5a303ddcf")

        assertTrue(result is CatalogResult.Ok)
        val detail = (result as CatalogResult.Ok).value
        assertEquals("2411c1da-f01d-408f-8106-c1f5a303ddcf", detail.id)
        assertEquals(1117, detail.availableStock)
        assertEquals(true, detail.isInStock)
        assertEquals(2, detail.images.size)
        assertEquals(12, detail.variants.size)
        assertEquals("สีแดง / 1m", detail.variants.first().title)
        assertEquals(144.53, detail.variants.first().price!!, 0.0001)
        assertEquals(149.0, detail.compareAtPrice!!, 0.0001)
        assertEquals(3, detail.discountPercent)
        assertEquals(144.53, detail.displayPrice!!, 0.0001)
        // The primary image is pulled to the front, everything else keeps its order.
        assertTrue(detail.images.first().isPrimary)
        assertTrue(detail.images.all { it.url.startsWith("https://") })
    }

    @Test
    fun `the category tree is flattened parent first`() = runBlocking {
        val api = CatalogRemoteDataSource(
            baseUrl = "https://example.test",
            client = client(200, fixture("categories_sample.json")),
        )

        val result = api.categories()

        assertTrue(result is CatalogResult.Ok)
        val categories = (result as CatalogResult.Ok).value
        assertEquals(
            listOf("mobile-accessories", "chargers-cables", "fashion-clothing", "mens-clothing"),
            categories.map { it.slug },
        )
        assertEquals(0, categories[0].productCount)
        assertEquals(1, categories[1].productCount)
        assertEquals("ที่ชาร์จและสายชาร์จ", categories[1].displayName)
    }

    @Test
    fun `search and category become query parameters`() = runBlocking {
        val api = CatalogRemoteDataSource(
            baseUrl = "https://example.test",
            client = client(200, """{"success":true,"data":[]}"""),
        )

        api.products(CatalogQuery(search = "usb cable", category = "chargers-cables"))

        val url = requested.single()
        assertTrue(url, url.contains("q=usb+cable") || url.contains("q=usb%20cable"))
        assertTrue(url, url.contains("category=chargers-cables"))
        assertTrue(url, url.contains("limit=20"))
        assertTrue(url, url.contains("offset=0"))
    }

    @Test
    fun `filters and sorting become query parameters`() = runBlocking {
        val api = CatalogRemoteDataSource(
            baseUrl = "https://example.test",
            client = client(200, """{"success":true,"data":[]}"""),
        )

        api.products(
            CatalogQuery(
                search = "headphone",
                category = "audio",
                minPrice = 50.0,
                maxPrice = 300.0,
                sortBy = "price_asc",
                shopId = "shop-123",
            ),
        )

        val url = requested.single()
        assertTrue(url, url.contains("q=headphone"))
        assertTrue(url, url.contains("category=audio"))
        assertTrue(url, url.contains("minPrice=50.0"))
        assertTrue(url, url.contains("maxPrice=300.0"))
        assertTrue(url, url.contains("sortBy=price_asc"))
        assertTrue(url, url.contains("shopId=shop-123"))
    }

    @Test
    fun `a route that answers 404 is NotFound, not an empty product`() = runBlocking {
        val api = CatalogRemoteDataSource(
            baseUrl = "https://example.test",
            client = client(404, """{"success":false,"error":{"code":"NOT_FOUND"}}"""),
        )

        assertEquals(CatalogResult.Failed(CatalogError.NotFound), api.product("missing"))
    }

    @Test
    fun `a malformed body is a server error rather than a crash`() = runBlocking {
        val api = CatalogRemoteDataSource(
            baseUrl = "https://example.test",
            client = client(200, "<html>not json</html>"),
        )

        assertEquals(CatalogResult.Failed(CatalogError.Server), api.products(CatalogQuery()))
        assertEquals(CatalogResult.Failed(CatalogError.Server), api.product("x"))
        assertEquals(CatalogResult.Failed(CatalogError.Server), api.categories())
    }

    @Test
    fun `a body with unusable items keeps the usable ones`() = runBlocking {
        val body = """
            {"success":true,"data":[
              {"id":"a","name":"Real","price":10,"currency":"THB","currentStock":2},
              {"name":"no id"},
              {"id":"b","name":"Also real","price":"12.50","currency":"THB"}
            ]}
        """.trimIndent()
        val api = CatalogRemoteDataSource(
            baseUrl = "https://example.test",
            client = client(200, body),
        )

        val page = (api.products(CatalogQuery()) as CatalogResult.Ok).value
        assertEquals(listOf("a", "b"), page.items.map { it.id })
        // The legacy list route sends price as a string; it must parse too.
        assertEquals(12.5, page.items[1].price!!, 0.0001)
        // No stock field at all means "unknown", never "out of stock".
        assertNull(page.items[1].inStock)
    }

    @Test
    fun `an unreachable API is a network error`() = runBlocking {
        val api = CatalogRemoteDataSource(
            baseUrl = "https://example.test",
            client = client(200, "", throws = true),
        )

        assertEquals(CatalogResult.Failed(CatalogError.Network), api.products(CatalogQuery()))
    }

    @Test
    fun `no more pages are reported when a short page arrives`() = runBlocking {
        val api = CatalogRemoteDataSource(
            baseUrl = "https://example.test",
            client = client(200, """{"success":true,"data":[{"id":"a","name":"A"}]}"""),
        )

        val page = (api.products(CatalogQuery(limit = 20)) as CatalogResult.Ok).value
        assertEquals(false, page.hasMore)
    }
}
