package com.velnox.app.core.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repository is where "show real data or show an error" is enforced, so the
 * tests are about two things: a failure never becomes an empty-but-successful
 * catalogue, and a repeat request is answered from memory instead of the network.
 */
class CatalogRepositoryTest {

    private class FakeApi : CatalogApi {
        var categoryCalls = 0
        var listCalls = 0
        var detailCalls = 0
        var shopCalls = 0
        var reviewsCalls = 0

        var categoriesResult: CatalogResult<List<CatalogCategory>> =
            CatalogResult.Ok(listOf(CatalogCategory("chargers-cables", "Chargers", null, 1)))
        var listResult: CatalogResult<CatalogPage> =
            CatalogResult.Ok(CatalogPage(listOf(sampleProduct("a")), hasMore = false))
        var detailResult: CatalogResult<ProductDetail> = CatalogResult.Ok(sampleDetail("a"))
        var shopResult: CatalogResult<ShopDetail> = CatalogResult.Ok(
            ShopDetail(
                shop = SellerShop(
                    id = "shop-a",
                    sellerId = "seller-a",
                    name = "Shop A",
                    slug = "shop-a",
                    description = "Description",
                ),
                products = emptyList(),
            ),
        )
        var reviewsResult: CatalogResult<ProductReviewsData> = CatalogResult.Ok(
            ProductReviewsData(
                items = emptyList(),
                total = 0,
                avgRating = 0.0,
                distribution = emptyMap(),
            ),
        )

        override suspend fun categories(): CatalogResult<List<CatalogCategory>> {
            categoryCalls++
            return categoriesResult
        }

        override suspend fun products(query: CatalogQuery): CatalogResult<CatalogPage> {
            listCalls++
            return listResult
        }

        override suspend fun product(id: String): CatalogResult<ProductDetail> {
            detailCalls++
            return detailResult
        }

        override suspend fun shop(slugOrId: String): CatalogResult<ShopDetail> {
            shopCalls++
            return shopResult
        }

        override suspend fun reviews(productId: String): CatalogResult<ProductReviewsData> {
            reviewsCalls++
            return reviewsResult
        }
    }

    private fun product(id: String) = sampleProduct(id)

    @Test
    fun `a second page request is served from the cache`() = runBlocking {
        val api = FakeApi()
        val repository = CatalogRepository(api)

        repository.products(CatalogQuery())
        val second = repository.products(CatalogQuery())

        assertEquals(1, api.listCalls)
        assertTrue(second is CatalogResult.Ok)
    }

    @Test
    fun `force bypasses the cache`() = runBlocking {
        val api = FakeApi()
        val repository = CatalogRepository(api)

        repository.products(CatalogQuery())
        repository.products(CatalogQuery(), force = true)

        assertEquals(2, api.listCalls)
    }

    @Test
    fun `different queries are cached separately`() = runBlocking {
        val api = FakeApi()
        val repository = CatalogRepository(api)

        repository.products(CatalogQuery(category = "a"))
        repository.products(CatalogQuery(category = "b"))
        repository.products(CatalogQuery(category = "a"))

        assertEquals(2, api.listCalls)
    }

    @Test
    fun `queries with different filters and sorts are cached separately`() = runBlocking {
        val api = FakeApi()
        val repository = CatalogRepository(api)

        repository.products(CatalogQuery(category = "a", minPrice = 10.0))
        repository.products(CatalogQuery(category = "a", minPrice = 20.0))
        repository.products(CatalogQuery(category = "a", minPrice = 10.0))

        assertEquals(2, api.listCalls)
    }

    @Test
    fun `a failure is never cached as data`() = runBlocking {
        val api = FakeApi()
        api.listResult = CatalogResult.Failed(CatalogError.Network)
        val repository = CatalogRepository(api)

        val first = repository.products(CatalogQuery())
        val second = repository.products(CatalogQuery())

        assertEquals(CatalogResult.Failed(CatalogError.Network), first)
        assertEquals(CatalogResult.Failed(CatalogError.Network), second)
        // Two calls: the failure was not remembered as an answer.
        assertEquals(2, api.listCalls)
    }

    @Test
    fun `a product that 404s is dropped from the cache`() = runBlocking {
        val api = FakeApi()
        val repository = CatalogRepository(api)

        repository.product("a")
        api.detailResult = CatalogResult.Failed(CatalogError.NotFound)
        val missing = repository.product("a", force = true)
        api.detailResult = CatalogResult.Ok(sampleDetail("a"))
        repository.product("a")

        assertEquals(CatalogResult.Failed(CatalogError.NotFound), missing)
        // If the 404 had been cached, this would still be a failure.
        assertEquals(3, api.detailCalls)
    }

    @Test
    fun `categories are cached until invalidated`() = runBlocking {
        val api = FakeApi()
        val repository = CatalogRepository(api)

        repository.categories()
        repository.categories()
        assertEquals(1, api.categoryCalls)

        repository.invalidate()
        repository.categories()
        assertEquals(2, api.categoryCalls)
    }

    @Test
    fun `a failed category load is not cached`() = runBlocking {
        val api = FakeApi()
        api.categoriesResult = CatalogResult.Failed(CatalogError.Server)
        val repository = CatalogRepository(api)

        repository.categories()
        api.categoriesResult = CatalogResult.Ok(emptyList())
        val second = repository.categories()

        assertEquals(2, api.categoryCalls)
        assertEquals(CatalogResult.Ok(emptyList<CatalogCategory>()), second)
    }

    @Test
    fun `shop requests are cached in memory`() = runBlocking {
        val api = FakeApi()
        val repository = CatalogRepository(api)

        val first = repository.shop("shop-a")
        val second = repository.shop("shop-a")

        assertEquals(1, api.shopCalls)
        assertTrue(first is CatalogResult.Ok)
        assertTrue(second is CatalogResult.Ok)
    }

    @Test
    fun `reviews requests are cached in memory`() = runBlocking {
        val api = FakeApi()
        val repository = CatalogRepository(api)

        val first = repository.reviews("prod-a")
        val second = repository.reviews("prod-a")

        assertEquals(1, api.reviewsCalls)
        assertTrue(first is CatalogResult.Ok)
        assertTrue(second is CatalogResult.Ok)
    }
}

// Top-level so the nested fake can build them without touching the test class.
private fun sampleProduct(id: String) = CatalogProduct(
    id = id,
    name = "Product $id",
    price = 10.0,
    currency = "THB",
    imageUrl = null,
    categorySlug = "chargers-cables",
    shopName = "Shop",
    inStock = true,
    soldCount = 0,
)

private fun sampleDetail(id: String) = ProductDetail(
    id = id,
    name = "Product $id",
    description = null,
    price = 10.0,
    currency = "THB",
    images = emptyList(),
    categorySlug = "chargers-cables",
    shopName = "Shop",
    unit = null,
    availableStock = 5,
    soldCount = 0,
)
