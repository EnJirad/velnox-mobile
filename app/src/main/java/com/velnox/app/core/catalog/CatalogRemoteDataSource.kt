package com.velnox.app.core.catalog

import com.velnox.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The Velnox API client for the public catalogue.
 *
 * Scope is exactly three endpoints, all of them public and already deployed:
 *
 * | Call | Endpoint | Server-side rule |
 * |---|---|---|
 * | categories | `GET /api/categories/tree` | `is_active` only, with published counts |
 * | list | `GET /api/products/catalog` | `p.status = 'published' AND sh.status = 'active'` |
 * | detail | `GET /api/products/:id` | same predicate; otherwise `404 NOT_FOUND` |
 *
 * No call throws for an expected failure: the caller receives a
 * [CatalogResult.Failed]. Nothing is ever synthesised when the API fails — that
 * rule is the whole point of this class.
 */
class CatalogRemoteDataSource(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val client: OkHttpClient = defaultClient(),
) : CatalogApi {

    override suspend fun categories(): CatalogResult<List<CatalogCategory>> =
        when (val raw = get("/api/categories/tree")) {
            is Raw.Failure -> CatalogResult.Failed(raw.error)
            is Raw.Success -> {
                val data = raw.dataArray()
                    ?: return CatalogResult.Failed(CatalogError.Server)
                val flat = ArrayList<CatalogCategory>()
                for (i in 0 until data.length()) {
                    val node = data.optJSONObject(i) ?: continue
                    parseCategory(node)?.let(flat::add)
                    val children = node.optJSONArray("children") ?: continue
                    for (c in 0 until children.length()) {
                        val child = children.optJSONObject(c) ?: continue
                        parseCategory(child)?.let(flat::add)
                    }
                }
                CatalogResult.Ok(flat)
            }
        }

    override suspend fun products(query: CatalogQuery): CatalogResult<CatalogPage> {
        val path = buildString {
            append("/api/products/catalog?limit=")
            append(query.limit.coerceIn(1, MAX_LIMIT))
            append("&offset=")
            append(query.offset.coerceAtLeast(0))
            query.category?.takeIf { it.isNotBlank() }?.let {
                append("&category=").append(encode(it))
            }
            query.search?.trim()?.takeIf { it.isNotEmpty() }?.let {
                append("&q=").append(encode(it))
            }
            query.minPrice?.let {
                append("&minPrice=").append(it)
            }
            query.maxPrice?.let {
                append("&maxPrice=").append(it)
            }
            query.sortBy?.takeIf { it.isNotBlank() }?.let {
                append("&sortBy=").append(encode(it))
            }
            query.shopId?.takeIf { it.isNotBlank() }?.let {
                append("&shopId=").append(encode(it))
            }
        }

        return when (val raw = get(path)) {
            is Raw.Failure -> CatalogResult.Failed(raw.error)
            is Raw.Success -> {
                val data = raw.dataArray()
                    ?: return CatalogResult.Failed(CatalogError.Server)
                val items = ArrayList<CatalogProduct>(data.length())
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    parseProduct(item)?.let(items::add)
                }
                // The endpoint publishes no total; a full page is the only signal
                // that another one may exist.
                CatalogResult.Ok(
                    CatalogPage(
                        items = items,
                        hasMore = data.length() >= query.limit,
                    ),
                )
            }
        }
    }

    override suspend fun product(id: String): CatalogResult<ProductDetail> =
        when (val raw = get("/api/products/${encode(id)}")) {
            is Raw.Failure -> CatalogResult.Failed(raw.error)
            is Raw.Success -> {
                val data = raw.dataObject()
                    ?: return CatalogResult.Failed(CatalogError.Server)
                parseDetail(data)?.let { CatalogResult.Ok(it) }
                    ?: CatalogResult.Failed(CatalogError.Server)
            }
        }

    // ------------------------------------------------------------- transport

    private sealed interface Raw {
        data class Success(val body: String) : Raw
        data class Failure(val error: CatalogError) : Raw
    }

    private suspend fun get(path: String): Raw = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .get()
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 404 -> Raw.Failure(CatalogError.NotFound)
                    // 400 covers a non-UUID id, which the API reports as a database
                    // error; from the app's point of view it is simply not a product.
                    response.code == 400 -> Raw.Failure(CatalogError.NotFound)
                    !response.isSuccessful -> Raw.Failure(CatalogError.Server)
                    else -> Raw.Success(body)
                }
            }
        } catch (_: IOException) {
            Raw.Failure(CatalogError.Network)
        }
    }

    // ---------------------------------------------------------------- parsing

    /** The envelope is `{ success, data }`; a missing or false `success` is an error. */
    private fun Raw.Success.envelope(): JSONObject? = try {
        val json = JSONObject(body)
        if (json.optBoolean("success", false)) json else null
    } catch (_: JSONException) {
        null
    }

    private fun Raw.Success.dataArray(): JSONArray? = envelope()?.optJSONArray("data")

    private fun Raw.Success.dataObject(): JSONObject? = envelope()?.optJSONObject("data")

    private fun parseCategory(node: JSONObject): CatalogCategory? {
        val slug = node.optString("slug").trim().takeIf { it.isNotEmpty() } ?: return null
        val name = node.optString("name").trim().takeIf { it.isNotEmpty() } ?: slug
        return CatalogCategory(
            slug = slug,
            name = name,
            displayName = node.stringOrNull("display_name"),
            productCount = node.optInt("product_count", 0),
        )
    }

    private fun parseProduct(item: JSONObject): CatalogProduct? {
        val id = item.stringOrNull("id") ?: return null
        val name = item.stringOrNull("name") ?: return null
        val featuredVariant = item.optJSONObject("featuredVariant")
        val compareAt = featuredVariant?.numberOrNull("compareAtPrice")
            ?: item.numberOrNull("compareAtPrice")
        val discount = featuredVariant?.numberOrNull("discountPercent")?.toInt()
            ?: item.numberOrNull("discountPercent")?.toInt()
        val rating = item.numberOrNull("rating")
        val reviewCount = item.optInt("reviewCount", 0)
        val sellerId = item.stringOrNull("sellerId")
        val shopId = item.stringOrNull("shopId")

        return CatalogProduct(
            id = id,
            name = name,
            price = item.numberOrNull("price"),
            currency = item.stringOrNull("currency") ?: DEFAULT_CURRENCY,
            imageUrl = primaryImageUrl(item),
            categorySlug = item.stringOrNull("categorySlug")
                ?: item.stringOrNull("category"),
            shopName = item.stringOrNull("shopName"),
            inStock = (item.numberOrNull("currentStock")
                ?: item.numberOrNull("totalAvailableStock"))?.let { it > 0 },
            soldCount = item.optInt("soldCount", 0),
            compareAtPrice = compareAt,
            discountPercent = discount,
            rating = rating,
            reviewCount = reviewCount,
            sellerId = sellerId,
            shopId = shopId,
        )
    }

    private fun parseDetail(data: JSONObject): ProductDetail? {
        val id = data.stringOrNull("id") ?: return null
        val name = data.stringOrNull("name") ?: return null

        val images = ArrayList<CatalogImage>()
        val array = data.optJSONArray("images")
        if (array != null) {
            for (i in 0 until array.length()) {
                val image = array.optJSONObject(i) ?: continue
                val url = image.displayUrl() ?: continue
                images += CatalogImage(url = url, isPrimary = image.optBoolean("isPrimary", false))
            }
        }
        // Keep the seller's order; the primary image is pulled to the front.
        images.sortWith(compareByDescending { if (it.isPrimary) 1 else 0 })

        val stock = (data.numberOrNull("totalAvailableStock")
            ?: data.optJSONObject("inventory")?.numberOrNull("available"))?.toInt()

        return ProductDetail(
            id = id,
            name = name,
            description = data.stringOrNull("description"),
            price = data.numberOrNull("price"),
            currency = data.stringOrNull("currency") ?: DEFAULT_CURRENCY,
            images = images,
            categorySlug = data.stringOrNull("categorySlug") ?: data.stringOrNull("category"),
            shopName = data.optJSONObject("shop")?.stringOrNull("name")
                ?: data.stringOrNull("shopName"),
            unit = data.stringOrNull("unit"),
            availableStock = stock,
            soldCount = data.optInt("soldCount", 0),
        )
    }

    /** `primaryImage`, else the first entry of `images`. */
    private fun primaryImageUrl(item: JSONObject): String? =
        item.optJSONObject("primaryImage")?.displayUrl()
            ?: item.optJSONArray("images")?.let { array ->
                (0 until array.length())
                    .mapNotNull { array.optJSONObject(it) }
                    .firstNotNullOfOrNull { it.displayUrl() }
            }

    /** `displayUrl` is the sized variant; `url` is the original. Either is real. */
    private fun JSONObject.displayUrl(): String? =
        stringOrNull("displayUrl") ?: stringOrNull("url")

    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotEmpty() }
    }

    /** Accepts both JSON numbers and numeric strings (`0` and `"0.00"` both occur). */
    private fun JSONObject.numberOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val raw = opt(key)
        return when (raw) {
            is Number -> raw.toDouble().takeIf { it.isFinite() }
            is String -> raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
            else -> null
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val MAX_LIMIT = 200
        const val DEFAULT_CURRENCY = "THB"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
