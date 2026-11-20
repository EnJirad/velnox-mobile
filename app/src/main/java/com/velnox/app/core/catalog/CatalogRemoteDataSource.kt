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

    override suspend fun shop(slugOrId: String): CatalogResult<ShopDetail> =
        when (val raw = get("/api/shops/${encode(slugOrId)}")) {
            is Raw.Failure -> CatalogResult.Failed(raw.error)
            is Raw.Success -> {
                val data = raw.dataObject()
                    ?: return CatalogResult.Failed(CatalogError.Server)
                parseShop(data)?.let { CatalogResult.Ok(it) }
                    ?: CatalogResult.Failed(CatalogError.Server)
            }
        }

    override suspend fun reviews(productId: String): CatalogResult<ProductReviewsData> =
        when (val raw = get("/api/products/${encode(productId)}/reviews")) {
            is Raw.Failure -> CatalogResult.Failed(raw.error)
            is Raw.Success -> {
                val data = raw.dataObject()
                    ?: return CatalogResult.Failed(CatalogError.Server)
                parseReviews(data)?.let { CatalogResult.Ok(it) }
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
        val featPrice = featuredVariant?.numberOrNull("price")
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
            variantPrice = featPrice,
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

        val featVar = data.optJSONObject("featuredVariant")
        val featPrice = featVar?.numberOrNull("price")
        val featCompareAt = featVar?.numberOrNull("compareAtPrice")
            ?: data.numberOrNull("compareAtPrice")
        val featDiscount = featVar?.numberOrNull("discountPercent")?.toInt()
            ?: data.numberOrNull("discountPercent")?.toInt()

        val variantsList = ArrayList<ProductVariant>()
        val variantsArray = data.optJSONArray("variants")
        if (variantsArray != null) {
            for (i in 0 until variantsArray.length()) {
                val vObj = variantsArray.optJSONObject(i) ?: continue
                val vId = vObj.stringOrNull("id") ?: continue
                val vName = vObj.stringOrNull("name") ?: continue
                val vPrice = vObj.numberOrNull("price")
                val vCompareAt = vObj.numberOrNull("compareAtPrice")
                val vDiscount = vObj.numberOrNull("discountPercent")?.toInt()
                val vStock = vObj.numberOrNull("stock")?.toInt()
                val optionsMap = LinkedHashMap<String, String>()
                val optionsObj = vObj.optJSONObject("options")
                if (optionsObj != null) {
                    val keys = optionsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        optionsObj.stringOrNull(key)?.let { optionsMap[key] = it }
                    }
                }
                variantsList += ProductVariant(
                    id = vId,
                    name = vName,
                    price = vPrice,
                    compareAtPrice = vCompareAt,
                    discountPercent = vDiscount,
                    stock = vStock,
                    options = optionsMap,
                )
            }
        }

        val optionGroupsList = ArrayList<ProductOptionGroup>()
        val groupsArray = data.optJSONArray("optionGroups")
        if (groupsArray != null) {
            for (i in 0 until groupsArray.length()) {
                val gObj = groupsArray.optJSONObject(i) ?: continue
                val gId = gObj.stringOrNull("id") ?: continue
                val gName = gObj.stringOrNull("name") ?: continue
                val gType = gObj.stringOrNull("displayType")
                val gRequired = gObj.optBoolean("required", false)
                val valsList = ArrayList<ProductOptionValue>()
                val valsArray = gObj.optJSONArray("values")
                if (valsArray != null) {
                    for (j in 0 until valsArray.length()) {
                        val vObj = valsArray.optJSONObject(j) ?: continue
                        val vId = vObj.stringOrNull("id") ?: continue
                        val vVal = vObj.stringOrNull("value") ?: continue
                        val vLabel = vObj.stringOrNull("label") ?: vVal
                        val vImg = vObj.stringOrNull("imageUrl")
                        val vStock = vObj.numberOrNull("stock")?.toInt()
                        valsList += ProductOptionValue(id = vId, value = vVal, label = vLabel, imageUrl = vImg, stock = vStock)
                    }
                }
                optionGroupsList += ProductOptionGroup(id = gId, name = gName, displayType = gType, required = gRequired, values = valsList)
            }
        }

        val attrsList = ArrayList<ProductAttribute>()
        val attrsArray = data.optJSONArray("attributes")
        if (attrsArray != null) {
            for (i in 0 until attrsArray.length()) {
                val aObj = attrsArray.optJSONObject(i) ?: continue
                val aId = aObj.stringOrNull("id") ?: continue
                val aName = aObj.stringOrNull("name") ?: continue
                val aValue = aObj.stringOrNull("value") ?: continue
                attrsList += ProductAttribute(id = aId, name = aName, value = aValue)
            }
        }

        val shopObj = data.optJSONObject("shop")
        val sId = data.stringOrNull("shopId") ?: shopObj?.stringOrNull("id")
        val sSlug = shopObj?.stringOrNull("slug") ?: data.stringOrNull("shopSlug")
        val sellerId = data.stringOrNull("sellerId") ?: shopObj?.stringOrNull("sellerId")

        val rating = data.numberOrNull("rating")
        val reviewCount = data.optInt("reviewCount", 0)

        val descBlocksList = ArrayList<DescriptionBlock>()
        val descBlocksArray = data.optJSONArray("descriptionBlocks")
        if (descBlocksArray != null) {
            for (i in 0 until descBlocksArray.length()) {
                val blockObj = descBlocksArray.optJSONObject(i) ?: continue
                val bId = blockObj.stringOrNull("id") ?: "block_$i"
                val bType = blockObj.stringOrNull("type")
                when (bType) {
                    "image" -> {
                        val imgUrl = blockObj.stringOrNull("imageUrl")
                            ?: blockObj.stringOrNull("url")
                            ?: blockObj.displayUrl()
                        if (!imgUrl.isNullOrBlank()) {
                            descBlocksList += DescriptionBlock.Image(id = bId, imageUrl = imgUrl)
                        }
                    }
                    "text" -> {
                        val text = blockObj.stringOrNull("text")
                        if (!text.isNullOrBlank()) {
                            descBlocksList += DescriptionBlock.Text(id = bId, text = text)
                        }
                    }
                }
            }
        }

        val detailImagesList = ArrayList<String>()
        val detailImagesArray = data.optJSONArray("detailImages")
        if (detailImagesArray != null) {
            for (i in 0 until detailImagesArray.length()) {
                val str = detailImagesArray.optString(i).trim()
                if (str.isNotEmpty()) {
                    detailImagesList += str
                } else {
                    val dObj = detailImagesArray.optJSONObject(i)
                    val dUrl = dObj?.displayUrl() ?: dObj?.stringOrNull("url")
                    if (!dUrl.isNullOrBlank()) {
                        detailImagesList += dUrl
                    }
                }
            }
        }

        return ProductDetail(
            id = id,
            name = name,
            description = data.stringOrNull("description"),
            price = data.numberOrNull("price"),
            currency = data.stringOrNull("currency") ?: DEFAULT_CURRENCY,
            images = images,
            categorySlug = data.stringOrNull("categorySlug") ?: data.stringOrNull("category"),
            shopName = shopObj?.stringOrNull("name") ?: data.stringOrNull("shopName"),
            unit = data.stringOrNull("unit"),
            availableStock = stock,
            soldCount = data.optInt("soldCount", 0),
            compareAtPrice = featCompareAt,
            discountPercent = featDiscount,
            variantPrice = featPrice,
            variants = variantsList,
            optionGroups = optionGroupsList,
            attributes = attrsList,
            shopId = sId,
            shopSlug = sSlug,
            sellerId = sellerId,
            rating = rating,
            reviewCount = reviewCount,
            descriptionBlocks = descBlocksList,
            detailImages = detailImagesList,
        )
    }

    private fun parseShop(data: JSONObject): ShopDetail? {
        val sObj = data.optJSONObject("shop") ?: return null
        val id = sObj.stringOrNull("id") ?: return null
        val name = sObj.stringOrNull("name") ?: ""
        val slug = sObj.stringOrNull("slug") ?: id

        val sellerShop = SellerShop(
            id = id,
            sellerId = sObj.stringOrNull("sellerId"),
            name = name,
            slug = slug,
            description = sObj.stringOrNull("description"),
            logoUrl = sObj.stringOrNull("logo") ?: sObj.stringOrNull("imageUrl"),
            coverUrl = sObj.stringOrNull("cover"),
            phone = sObj.stringOrNull("phone"),
            status = sObj.stringOrNull("status"),
            productCount = sObj.optInt("productCount", 0),
            rating = sObj.numberOrNull("rating"),
            reviewCount = sObj.optInt("reviewCount", 0),
            isVerified = sObj.optBoolean("isVerified", false) || sObj.stringOrNull("verificationStatus") == "verified",
        )

        val productsList = ArrayList<CatalogProduct>()
        val prodsArray = data.optJSONArray("products")
        if (prodsArray != null) {
            for (i in 0 until prodsArray.length()) {
                val pObj = prodsArray.optJSONObject(i) ?: continue
                parseProduct(pObj)?.let { productsList += it }
            }
        }

        return ShopDetail(shop = sellerShop, products = productsList)
    }

    private fun parseReviews(data: JSONObject): ProductReviewsData? {
        val total = data.optInt("total", 0)
        val avgRating = data.numberOrNull("avgRating") ?: 0.0
        val distObj = data.optJSONObject("distribution")
        val distMap = LinkedHashMap<String, Int>()
        if (distObj != null) {
            val keys = distObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                distMap[k] = distObj.optInt(k, 0)
            }
        }

        val itemsList = ArrayList<ProductReview>()
        val itemsArray = data.optJSONArray("items")
        if (itemsArray != null) {
            for (i in 0 until itemsArray.length()) {
                val rObj = itemsArray.optJSONObject(i) ?: continue
                val rId = rObj.stringOrNull("id") ?: continue
                val rating = rObj.optInt("rating", 5)
                val comment = rObj.stringOrNull("comment")
                val userObj = rObj.optJSONObject("user")
                val author = userObj?.stringOrNull("name")
                    ?: rObj.stringOrNull("authorName")
                    ?: rObj.stringOrNull("userName")
                val createdAt = rObj.optLong("createdAt", 0L).takeIf { it > 0 }
                val imagesList = ArrayList<String>()
                val imgArray = rObj.optJSONArray("images")
                if (imgArray != null) {
                    for (j in 0 until imgArray.length()) {
                        imgArray.optString(j).takeIf { it.isNotBlank() }?.let { imagesList += it }
                    }
                }
                itemsList += ProductReview(
                    id = rId,
                    rating = rating,
                    comment = comment,
                    authorName = author,
                    createdAt = createdAt,
                    images = imagesList,
                )
            }
        }

        return ProductReviewsData(
            items = itemsList,
            total = total,
            avgRating = avgRating,
            distribution = distMap,
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
