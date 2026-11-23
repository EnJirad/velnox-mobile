package com.velnox.app.core.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class BackendEndpointRouterTest {

    @Test
    fun `default host is primary host`() {
        val router = BackendEndpointRouter(
            primaryHost = "https://api.velnox.live",
            fallbackHosts = listOf(
                "https://velnox-api.ma0979354966-js91934.workers.dev",
                "https://velnox-api.onrender.com"
            ),
        )

        assertEquals("https://api.velnox.live", router.currentHost())
    }

    @Test
    fun `dialect detection identifies workers vs render legacy`() {
        val router = BackendEndpointRouter(
            primaryHost = "https://api.velnox.live",
            fallbackHosts = listOf(
                "https://velnox-api.ma0979354966-js91934.workers.dev",
                "https://velnox-api.onrender.com"
            ),
        )

        assertEquals(BackendDialect.WORKERS_V1, router.getDialect("https://api.velnox.live"))
        assertEquals(BackendDialect.WORKERS_V1, router.getDialect("https://velnox-api.ma0979354966-js91934.workers.dev"))
        assertEquals(BackendDialect.EXPRESS_LEGACY, router.getDialect("https://velnox-api.onrender.com"))
    }

    @Test
    fun `host failure puts host on cooldown and switches to next fallback`() {
        var currentTime = 1000L
        val router = BackendEndpointRouter(
            primaryHost = "https://api.velnox.live",
            fallbackHosts = listOf(
                "https://velnox-api.ma0979354966-js91934.workers.dev",
                "https://velnox-api.onrender.com"
            ),
            now = { currentTime },
        )

        assertEquals("https://api.velnox.live", router.currentHost())

        // Mark primary failed
        router.markHostFailure("https://api.velnox.live")
        assertEquals("https://velnox-api.ma0979354966-js91934.workers.dev", router.currentHost())

        // Mark secondary failed
        router.markHostFailure("https://velnox-api.ma0979354966-js91934.workers.dev")
        assertEquals("https://velnox-api.onrender.com", router.currentHost())

        // Advance time past cooldown
        currentTime += BackendConfig.HOST_COOLDOWN_MS + 1
        assertEquals("https://api.velnox.live", router.currentHost())
    }

    @Test
    fun `executeWithFailover automatically cascades on UnknownHostException`() = runBlocking {
        var currentTime = 1000L
        val router = BackendEndpointRouter(
            primaryHost = "https://api.velnox.live",
            fallbackHosts = listOf(
                "https://velnox-api.onrender.com"
            ),
            now = { currentTime },
        )

        val calledHosts = mutableListOf<String>()
        val result = router.executeWithFailover(isIdempotent = true) { host, dialect ->
            calledHosts.add(host)
            if (host == "https://api.velnox.live") {
                throw UnknownHostException("Unable to resolve host api.velnox.live")
            }
            "success-from-$host"
        }

        assertEquals("success-from-https://velnox-api.onrender.com", result)
        assertEquals(listOf("https://api.velnox.live", "https://velnox-api.onrender.com"), calledHosts)
        // Primary should now be in cooldown
        assertEquals("https://velnox-api.onrender.com", router.currentHost())
    }
}
