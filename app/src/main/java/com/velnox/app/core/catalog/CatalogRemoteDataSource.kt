package com.velnox.app.core.catalog

import com.velnox.app.BuildConfig
import com.velnox.app.core.network.BackendConfig
import com.velnox.app.core.network.BackendDialect
import com.velnox.app.core.network.BackendEndpointRouter
import com.velnox.app.core.network.VelnoxMediaUrlResolver
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
 * Connects to the Cloudflare Workers v1 API (`velnox-api.ma0979354966-js91934.workers.dev` / `api.velnox.live`),
 * with automatic host failover and resilient fallback.
 */
class CatalogRemoteDataSource(
    baseUrl: String = BuildConfig.API_BASE_URL,
    private val client: OkHttpClient = defaultClient(),
    val router: BackendEndpointRouter = BackendEndpointRouter(
        primaryHost = baseUrl,
        fallbackHosts = BackendConfig.FALLBACK_HOSTS,
        client = client,
    ),
) : CatalogApi {

    override suspend fun categories(): CatalogResult<List<CatalogCategory>> =
        router.executeWithFailover(isIdempotent = true) { host, dialect ->
            val path = if (dialect == BackendDialect.WORKERS_V1) "/api/v1/categories" else "/api/categories/tree"
            var raw = get(host, path)

            if (raw is Raw.Failure && raw.error == CatalogError.NotFound) {
                // Route compatibility fallback
                val altPath = if (dialect == BackendDialect.WORKERS_V1) "/api/categories/tree" else "/api/v1/categories"
                val altRaw = get(host, altPath)
                if (altRaw is Raw.Success) {
                    raw = altRaw
                }
            }

            when (raw) {
                is Raw.Failure -> CatalogResult.Failed(raw.error)
                is Raw.Success -> {
                    val data = raw.dataArray() ?: return@executeWithFailover CatalogResult.Failed(CatalogError.Server)
                    val flat = ArrayList<CatalogCategory>()
                    flattenTree(data, flat, host, dialect)
                    CatalogResult.Ok(flat)
                }
            }
        }

    private fun flattenTree(
        nodes: JSONArray,
        result: MutableList<CatalogCategory>,
        host: String,
        dialect: BackendDialect,
    ) {
        for (i in 0 until nodes.length()) {
            val node = nodes.optJSONObject(i) ?: continue
            parseCategory(node, host, dialect)?.let(result::add)
            val children = node.optJSONArray("children")
            if (children != null && children.length() > 0) {
                flattenTree(children, result, host, dialect)
            }
        }
    }

    override suspend fun products(query: CatalogQuery): CatalogResult<CatalogPage> =
        router.executeWithFailover(isIdempotent = true) { host, dialect ->
            val path = if (dialect == BackendDialect.WORKERS_V1) {
                val limit = query.limit.coerceIn(1, 100)
                val page = (query.offset.coerceAtLeast(0) / limit) + 1
                buildString {
                    append("/api/v1/products?limit=").append(limit)
                    append("&page=").append(page)
                    query.categoryId?.takeIf { it.isNotBlank() }?.let { append("&categoryId=").append(encode(it)) }
                    query.category?.takeIf { it.isNotBlank() }?.let { append("&categorySlug=").append(encode(it)) }
                    query.search?.trim()?.takeIf { it.isNotEmpty() }?.let { append("&q=").append(encode(it)) }
                    query.minPrice?.let { append("&minPrice=").append(it) }
                    query.maxPrice?.let { append("&maxPrice=").append(it) }
                    query.sortBy?.takeIf { it.isNotBlank() }?.let { append("&sort=").append(encode(it)) }
                    query.shopId?.takeIf { it.isNotBlank() }?.let { append("&shopId=").append(encode(it)) }
                }
            } else {
                buildString {
                    append("/api/products/catalog?limit=").append(query.limit.coerceIn(1, MAX_LIMIT))
                    append("&offset=").append(query.offset.coerceAtLeast(0))
                    query.category?.takeIf { it.isNotBlank() }?.let { append("&category=").append(encode(it)) }
                    query.search?.trim()?.takeIf { it.isNotEmpty() }?.let { append("&q=").append(encode(it)) }
                    query.minPrice?.let { append("&minPrice=").append(it) }
                    query.maxPrice?.let { append("&maxPrice=").append(it) }
                    query.sortBy?.takeIf { it.isNotBlank() }?.let { append("&sortBy=").append(encode(it)) }
                    query.shopId?.takeIf { it.isNotBlank() }?.let { append("&shopId=").append(encode(it)) }
                }
            }

            var raw = get(host, path)
            if (raw is Raw.Failure && raw.error == CatalogError.NotFound && dialect == BackendDialect.WORKERS_V1) {
                // Retry with legacy endpoint
                val legacyPath = buildString {
                    append("/api/products/catalog?limit=").append(query.limit.coerceIn(1, MAX_LIMIT))
                    append("&offset=").append(query.offset.coerceAtLeast(0))
                    query.category?.takeIf { it.isNotBlank() }?.let { append("&category=").append(encode(it)) }
                    query.search?.trim()?.takeIf { it.isNotEmpty() }?.let { append("&q=").append(encode(it)) }
                    query.minPrice?.let { append("&minPrice=").append(it) }
                    query.maxPrice?.let { append("&maxPrice=").append(it) }
                    query.sortBy?.takeIf { it.isNotBlank() }?.let { append("&sortBy=").append(encode(it)) }
                    query.shopId?.takeIf { it.isNotBlank() }?.let { append("&shopId=").append(encode(it)) }
                }
                raw = get(host, legacyPath)
            }

            when (raw) {
                is Raw.Failure -> CatalogResult.Failed(raw.error)
                is Raw.Success -> {
                    val data = raw.dataArray() ?: return@executeWithFailover CatalogResult.Failed(CatalogError.Server)
                    val items = ArrayList<CatalogProduct>(data.length())
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        parseProduct(item, host, dialect)?.let(items::add)
                    }

                    val envelope = raw.envelope()
                    val pagination = envelope?.optJSONObject("pagination")
                    val hasMore = if (pagination != null) {
                        val page = pagination.optInt("page", 1)
                        val totalPages = pagination.optInt("totalPages", 1)
                        page < totalPages
                    } else {
                        data.length() >= query.limit
                    }

                    CatalogResult.Ok(CatalogPage(items = items, hasMore = hasMore))
                }
            }
        }

    override suspend fun product(id: String): CatalogResult<ProductDetail> =
        router.executeWithFailover(isIdempotent = true) { host, dialect ->
            val path = if (dialect == BackendDialect.WORKERS_V1) "/api/v1/products/${encode(id)}" else "/api/products/${encode(id)}"
            var raw = get(host, path)

            if (raw is Raw.Failure && raw.error == CatalogError.NotFound && dialect == BackendDialect.WORKERS_V1) {
                raw = get(host, "/api/products/${encode(id)}")
            }

            when (raw) {
                is Raw.Failure -> CatalogResult.Failed(raw.error)
                is Raw.Success -> {
                    val data = raw.dataObject() ?: return@executeWithFailover CatalogResult.Failed(CatalogError.Server)
                    parseDetail(data, host, dialect)?.let { CatalogResult.Ok(it) }
                        ?: CatalogResult.Failed(CatalogError.Server)
                }
            }
        }

    override suspend fun shop(slugOrId: String): CatalogResult<ShopDetail> =
        router.executeWithFailover(isIdempotent = true) { host, dialect ->
            val path = if (dialect == BackendDialect.WORKERS_V1) "/api/v1/shops/${encode(slugOrId)}" else "/api/shops/${encode(slugOrId)}"
            var raw = get(host, path)

            if (raw is Raw.Failure && raw.error == CatalogError.NotFound && dialect == BackendDialect.WORKERS_V1) {
                raw = get(host, "/api/shops/${encode(slugOrId)}")
            }

            when (raw) {
                is Raw.Failure -> CatalogResult.Failed(raw.error)
                is Raw.Success -> {
                    val data = raw.dataObject() ?: return@executeWithFailover CatalogResult.Failed(CatalogError.Server)
                    val parsed = parseShop(data, host, dialect)
                    if (parsed != null) {
                        // If Workers returned direct shop record without products, fetch products by shopId
                        if (parsed.products.isEmpty()) {
                            val shopProds = products(CatalogQuery(shopId = parsed.shop.id, limit = 40))
                            if (shopProds is CatalogResult.Ok) {
                                CatalogResult.Ok(parsed.copy(products = shopProds.value.items))
                            } else {
                                CatalogResult.Ok(parsed)
                            }
                        } else {
                            CatalogResult.Ok(parsed)
                        }
                    } else {
                        CatalogResult.Failed(CatalogError.Server)
                    }
                }
            }
        }

    override suspend fun reviews(productId: String): CatalogResult<ProductReviewsData> =
        router.executeWithFailover(isIdempotent = true) { host, dialect ->
            val raw = get(host, "/api/products/${encode(productId)}/reviews")
            when (raw) {
                is Raw.Failure -> {
                    // Graceful degradation for endpoints not yet ported to Workers
                    if (raw.error == CatalogError.NotFound) {
                        CatalogResult.Ok(ProductReviewsData(items = emptyList(), total = 0))
                    } else {
                        CatalogResult.Failed(raw.error)
                    }
                }
                is Raw.Success -> {
                    val data = raw.dataObject() ?: return@executeWithFailover CatalogResult.Ok(ProductReviewsData())
                    parseReviews(data)?.let { CatalogResult.Ok(it) } ?: CatalogResult.Ok(ProductReviewsData())
                }
            }
        }

    private sealed interface Raw {
        data class Success(val body: String) : Raw
        data class Failure(val error: CatalogError) : Raw
    }

    private suspend fun get(host: String, path: String): Raw = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(host.trimEnd('/') + path)
            .get()
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 404 || response.code == 400 -> Raw.Failure(CatalogError.NotFound)
                    !response.isSuccessful -> Raw.Failure(CatalogError.Server)
                    else -> Raw.Success(body)
                }
            }
        } catch (_: IOException) {
            Raw.Failure(CatalogError.Network)
        }
    }

    private fun Raw.Success.envelope(): JSONObject? = try {
        val json = JSONObject(body)
        if (json.optBoolean("success", false)) json else null
    } catch (_: JSONException) {
        null
    }

    private fun Raw.Success.dataArray(): JSONArray? = envelope()?.optJSONArray("data")

    private fun Raw.Success.dataObject(): JSONObject? = envelope()?.optJSONObject("data")

    private fun parseCategory(node: JSONObject, host: String, dialect: BackendDialect): CatalogCategory? {
        val slug = node.optString("slug").trim().takeIf { it.isNotEmpty() } ?: return null
        val id = node.stringOrNull("id")
        val nameEn = node.stringOrNull("nameEn")
        val name = nameEn ?: node.optString("name").trim().takeIf { it.isNotEmpty() } ?: slug
        val nameTh = node.stringOrNull("nameTh")
        val displayName = node.stringOrNull("display_name") ?: nameTh

        val hasCount = node.has("product_count") || node.has("productCount")
        val count = if (hasCount) node.optInt("product_count", node.optInt("productCount", 0)) else 0
        val iconUrl = VelnoxMediaUrlResolver.resolve(node.stringOrNull("iconUrl"), host, dialect)
        val displayOrder = node.optInt("displayOrder", node.optInt("sortOrder", 0))

        return CatalogCategory(
            slug = slug,
            name = name,
            displayName = displayName,
            productCount = count,
            id = id,
            iconUrl = iconUrl,
            hasExplicitProductCount = hasCount,
            displayOrder = displayOrder,
        )
    }

    private fun parseProduct(item: JSONObject, host: String, dialect: BackendDialect): CatalogProduct? {
        val id = item.stringOrNull("id") ?: return null
        val name = item.stringOrNull("name") ?: return null
        val featuredVariant = item.optJSONObject("featuredVariant")
        val featPrice = featuredVariant?.numberOrNull("price")
        val compareAt = featuredVariant?.numberOrNull("compareAtPrice")
            ?: item.numberOrNull("compareAtPrice")
            ?: item.numberOrNull("originalPrice")
        val discount = featuredVariant?.numberOrNull("discountPercent")?.toInt()
            ?: item.numberOrNull("discountPercent")?.toInt()
        val rating = item.numberOrNull("rating")
        val reviewCount = item.optInt("reviewCount", 0)
        val sellerId = item.stringOrNull("sellerId")
        val shopId = item.stringOrNull("shopId")
        val slug = item.stringOrNull("slug")

        val inStock = if (item.has("isAvailable")) {
            item.optBoolean("isAvailable")
        } else {
            (item.numberOrNull("currentStock") ?: item.numberOrNull("totalAvailableStock"))?.let { it > 0 }
        }

        return CatalogProduct(
            id = id,
            name = name,
            price = item.numberOrNull("price"),
            currency = item.stringOrNull("currency") ?: DEFAULT_CURRENCY,
            imageUrl = primaryImageUrl(item, host, dialect),
            categorySlug = item.stringOrNull("categorySlug") ?: item.stringOrNull("category"),
            shopName = item.stringOrNull("shopName"),
            inStock = inStock,
            soldCount = item.optInt("soldCount", 0),
            compareAtPrice = compareAt,
            discountPercent = discount,
            rating = rating,
            reviewCount = reviewCount,
            sellerId = sellerId,
            shopId = shopId,
            variantPrice = featPrice,
            slug = slug,
        )
    }

    private fun parseDetail(data: JSONObject, host: String, dialect: BackendDialect): ProductDetail? {
        val id = data.stringOrNull("id") ?: return null
        val name = data.stringOrNull("name") ?: return null

        val images = ArrayList<CatalogImage>()
        val array = data.optJSONArray("images")
        if (array != null) {
            for (i in 0 until array.length()) {
                val str = array.optString(i).trim()
                if (str.isNotEmpty()) {
                    val resolved = VelnoxMediaUrlResolver.resolve(str, host, dialect)
                    if (resolved != null) {
                        images += CatalogImage(url = resolved, isPrimary = (i == 0))
                    }
                } else {
                    val image = array.optJSONObject(i) ?: continue
                    val url = image.displayUrl() ?: continue
                    val resolved = VelnoxMediaUrlResolver.resolve(url, host, dialect)
                    if (resolved != null) {
                        images += CatalogImage(url = resolved, isPrimary = image.optBoolean("isPrimary", false))
                    }
                }
            }
        }
        if (images.isEmpty()) {
            primaryImageUrl(data, host, dialect)?.let {
                images += CatalogImage(url = it, isPrimary = true)
            }
        }
        images.sortWith(compareByDescending { if (it.isPrimary) 1 else 0 })

        val isAvailable = if (data.has("isAvailable")) data.optBoolean("isAvailable") else null
        val stock = (data.numberOrNull("totalAvailableStock")
            ?: data.optJSONObject("inventory")?.numberOrNull("available"))?.toInt()

        val featVar = data.optJSONObject("featuredVariant")
        val featPrice = featVar?.numberOrNull("price")
        val featCompareAt = featVar?.numberOrNull("compareAtPrice")
            ?: data.numberOrNull("compareAtPrice")
            ?: data.numberOrNull("originalPrice")
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
                        val vImg = VelnoxMediaUrlResolver.resolve(vObj.stringOrNull("imageUrl"), host, dialect)
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
                            val resolved = VelnoxMediaUrlResolver.resolve(imgUrl, host, dialect)
                            if (resolved != null) {
                                descBlocksList += DescriptionBlock.Image(id = bId, imageUrl = resolved)
                            }
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
                    VelnoxMediaUrlResolver.resolve(str, host, dialect)?.let { detailImagesList += it }
                } else {
                    val dObj = detailImagesArray.optJSONObject(i)
                    val dUrl = dObj?.displayUrl() ?: dObj?.stringOrNull("url")
                    if (!dUrl.isNullOrBlank()) {
                        VelnoxMediaUrlResolver.resolve(dUrl, host, dialect)?.let { detailImagesList += it }
                    }
                }
            }
        }

        val categoryPathArray = data.optJSONArray("categoryPath")
        val resolvedCatSlug = data.stringOrNull("categorySlug")
            ?: data.stringOrNull("category")
            ?: categoryPathArray?.let { arr ->
                if (arr.length() > 0) arr.optJSONObject(arr.length() - 1)?.stringOrNull("slug") else null
            }

        return ProductDetail(
            id = id,
            name = name,
            description = data.stringOrNull("description"),
            price = data.numberOrNull("price"),
            currency = data.stringOrNull("currency") ?: DEFAULT_CURRENCY,
            images = images,
            categorySlug = resolvedCatSlug,
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
            isAvailable = isAvailable,
        )
    }

    private fun parseShop(data: JSONObject, host: String, dialect: BackendDialect): ShopDetail? {
        val sObj = data.optJSONObject("shop") ?: data
        val id = sObj.stringOrNull("id") ?: return null
        val name = sObj.stringOrNull("name") ?: ""
        val slug = sObj.stringOrNull("slug") ?: id

        val sellerShop = SellerShop(
            id = id,
            sellerId = sObj.stringOrNull("sellerId"),
            name = name,
            slug = slug,
            description = sObj.stringOrNull("description"),
            logoUrl = VelnoxMediaUrlResolver.resolve(sObj.stringOrNull("logo") ?: sObj.stringOrNull("logoUrl") ?: sObj.stringOrNull("imageUrl"), host, dialect),
            coverUrl = VelnoxMediaUrlResolver.resolve(sObj.stringOrNull("cover") ?: sObj.stringOrNull("coverUrl"), host, dialect),
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
                parseProduct(pObj, host, dialect)?.let { productsList += it }
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

    private fun primaryImageUrl(item: JSONObject, host: String, dialect: BackendDialect): String? {
        val raw = item.stringOrNull("primaryImageUrl")
            ?: item.optJSONObject("primaryImage")?.displayUrl()
            ?: item.optJSONArray("images")?.let { array ->
                if (array.length() > 0) {
                    val firstStr = array.optString(0).trim()
                    if (firstStr.isNotEmpty()) firstStr
                    else (0 until array.length()).mapNotNull { array.optJSONObject(it) }.firstNotNullOfOrNull { it.displayUrl() }
                } else null
            }
        return VelnoxMediaUrlResolver.resolve(raw, host, dialect)
    }

    private fun JSONObject.displayUrl(): String? =
        stringOrNull("displayUrl") ?: stringOrNull("url")

    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotEmpty() }
    }

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
            .connectTimeout(BackendConfig.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(BackendConfig.READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(BackendConfig.WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build()
    }
}
