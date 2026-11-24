package com.velnox.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VelnoxMediaUrlResolverTest {

    @Test
    fun `resolve returns null for null or blank input`() {
        assertNull(VelnoxMediaUrlResolver.resolve(null))
        assertNull(VelnoxMediaUrlResolver.resolve(""))
        assertNull(VelnoxMediaUrlResolver.resolve("   "))
    }

    @Test
    fun `resolve keeps fully qualified URLs untouched`() {
        val httpUrl = "http://example.com/images/pic.jpg"
        val httpsUrl = "https://images.unsplash.com/photo-123"
        assertEquals(httpUrl, VelnoxMediaUrlResolver.resolve(httpUrl))
        assertEquals(httpsUrl, VelnoxMediaUrlResolver.resolve(httpsUrl))
    }

    @Test
    fun `resolve routes R2 keys to CDN on Workers dialect`() {
        val key = "products/prod-123/main.png"
        val resolved = VelnoxMediaUrlResolver.resolve(
            rawUrl = key,
            baseUrl = "https://api.velnox.live",
            dialect = BackendDialect.WORKERS_V1,
        )
        assertEquals("https://pub-75f6f26e59df431b854fe53caedb49b6.r2.dev/products/prod-123/main.png", resolved)
    }

    @Test
    fun `resolve routes R2 keys to api media on Express dialect`() {
        val key = "products/prod-123/main.png"
        val resolved = VelnoxMediaUrlResolver.resolve(
            rawUrl = key,
            baseUrl = "https://legacy.test",
            dialect = BackendDialect.EXPRESS_LEGACY,
        )
        assertEquals("https://legacy.test/api/media/products/prod-123/main.png", resolved)
    }

    @Test
    fun `resolve prepends base url to relative paths starting with slash`() {
        val relative = "/api/media/uploads/photo.jpg"
        val resolved = VelnoxMediaUrlResolver.resolve(
            rawUrl = relative,
            baseUrl = "https://legacy.test",
            dialect = BackendDialect.EXPRESS_LEGACY,
        )
        assertEquals("https://legacy.test/api/media/uploads/photo.jpg", resolved)
    }
}
