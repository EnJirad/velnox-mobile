package com.velnox.app.core.catalog

/**
 * The read side of the public catalogue.
 *
 * Kept as an interface for the same reason `AuthApi` is: the repository's
 * caching and the screens' states can be tested against a fake without an HTTP
 * server, while the production implementation stays the only thing that talks to
 * the network.
 */
interface CatalogApi {

    /** `GET /api/categories/tree` — every active category, flattened. */
    suspend fun categories(): CatalogResult<List<CatalogCategory>>

    /** `GET /api/products/catalog` — published products only. */
    suspend fun products(query: CatalogQuery): CatalogResult<CatalogPage>

    /** `GET /api/products/:id` — one published product, or [CatalogError.NotFound]. */
    suspend fun product(id: String): CatalogResult<ProductDetail>
}
