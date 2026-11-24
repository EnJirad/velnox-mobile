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
            primaryHost = "https://velnox-api.ma0979354966-js91934.workers.dev",
            fallbackHosts = listOf(
                "https://api.velnox.live",
                "https://backup.test"
            ),
        )

        assertEquals("https://velnox-api.ma0979354966-js91934.workers.dev", router.currentHost())
    }

    @Test
    fun `dialect detection identifies workers vs legacy test fixture`() {
        val router = BackendEndpointRouter(
            primaryHost = "https://velnox-api.ma0979354966-js91934.workers.dev",
            fallbackHosts = listOf(
                "https://api.velnox.live",
                "https://legacy.test"
            ),
        )

        assertEquals(BackendDialect.WORKERS_V1, router.getDialect("https://velnox-api.ma0979354966-js91934.workers.dev"))
        assertEquals(BackendDialect.WORKERS_V1, router.getDialect("https://api.velnox.live"))
        assertEquals(BackendDialect.EXPRESS_LEGACY, router.getDialect("https://legacy.test"))
    }

    @Test
    fun `host failure puts host on cooldown and switches to next fallback`() {
        var currentTime = 1000L
        val router = BackendEndpointRouter(
            primaryHost = "https://velnox-api.ma0979354966-js91934.workers.dev",
            fallbackHosts = listOf(
                "https://api.velnox.live",
                "https://backup.test"
            ),
            now = { currentTime },
        )

        assertEquals("https://velnox-api.ma0979354966-js91934.workers.dev", router.currentHost())

        // Mark primary failed
        router.markHostFailure("https://velnox-api.ma0979354966-js91934.workers.dev")
        assertEquals("https://api.velnox.live", router.currentHost())

        // Mark secondary failed
        router.markHostFailure("https://api.velnox.live")
        assertEquals("https://backup.test", router.currentHost())

        // Advance time past cooldown
        currentTime += BackendConfig.HOST_COOLDOWN_MS + 1
        assertEquals("https://velnox-api.ma0979354966-js91934.workers.dev", router.currentHost())
    }

    @Test
    fun `executeWithFailover automatically cascades on UnknownHostException`() = runBlocking {
        var currentTime = 1000L
        val router = BackendEndpointRouter(
            primaryHost = "https://velnox-api.ma0979354966-js91934.workers.dev",
            fallbackHosts = listOf(
                "https://api.velnox.live"
            ),
            now = { currentTime },
        )

        val calledHosts = mutableListOf<String>()
        val result = router.executeWithFailover(isIdempotent = true) { host, dialect ->
            calledHosts.add(host)
            if (host == "https://velnox-api.ma0979354966-js91934.workers.dev") {
                throw UnknownHostException("Unable to resolve host")
            }
            "success-from-$host"
        }

        assertEquals("success-from-https://api.velnox.live", result)
        assertEquals(listOf("https://velnox-api.ma0979354966-js91934.workers.dev", "https://api.velnox.live"), calledHosts)
        // Primary should now be in cooldown
        assertEquals("https://api.velnox.live", router.currentHost())
    }
}
