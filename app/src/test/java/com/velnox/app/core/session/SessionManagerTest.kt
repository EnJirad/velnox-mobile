package com.velnox.app.core.session

import com.velnox.app.core.auth.Jwt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SessionManagerTest {

    /** In-memory stand-in for the Keystore-backed vault. */
    private class FakeStore(
        var stored: String? = null,
        var failWrite: Boolean = false,
        var failRead: Boolean = false,
        var failClear: Boolean = false,
    ) : SessionStore {

        var clearCount = 0

        override suspend fun read(): StoredSession? {
            if (failRead) throw IllegalStateException("keystore unavailable")
            return stored?.let { StoredSession(it) }
        }

        override suspend fun write(session: StoredSession) {
            if (failWrite) throw IllegalStateException("keystore unavailable")
            stored = session.token
        }

        override suspend fun clear() {
            clearCount++
            if (failClear) throw IllegalStateException("delete failed")
            stored = null
        }
    }

    private fun tokenExpiringAt(millis: Long): String {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":${millis / 1000}}""".toByteArray())
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"alg":"HS256"}""".toByteArray())
        return "$header.$payload.sig"
    }

    @Test
    fun `no stored token reads as null`() = runBlocking {
        val manager = SessionManager(FakeStore())
        assertNull(manager.current())
    }

    @Test
    fun `save then read returns the same token`() = runBlocking {
        val store = FakeStore()
        val manager = SessionManager(store)

        manager.save("token-abc")

        assertEquals("token-abc", manager.current())
        assertEquals("token-abc", store.stored)
    }

    @Test
    fun `clear removes the token from memory and storage`() = runBlocking {
        val store = FakeStore(stored = "token-abc")
        val manager = SessionManager(store)

        assertEquals("token-abc", manager.current())
        manager.clear()

        assertNull(manager.current())
        assertNull(store.stored)
        assertEquals(1, store.clearCount)
    }

    @Test
    fun `an unreadable store is treated as no session`() = runBlocking {
        val manager = SessionManager(FakeStore(failRead = true))
        assertNull(manager.current())
    }

    @Test
    fun `a failed clear still drops the in-memory token`() = runBlocking {
        val store = FakeStore(stored = "token-abc", failClear = true)
        val manager = SessionManager(store)

        assertEquals("token-abc", manager.current())
        manager.clear()

        assertNull(manager.current())
    }

    @Test
    fun `expiry follows the token exp claim`() = runBlocking {
        val expiry = 1_900_000_000_000L
        val manager = SessionManager(FakeStore(), now = { expiry - 1 })
        val token = tokenExpiringAt(expiry)

        assertFalse(manager.isExpired(token))
        assertTrue(Jwt.isExpired(token, expiry))
    }
}
