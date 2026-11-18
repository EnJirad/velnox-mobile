package com.velnox.app.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * The JWT helper only ever decides "do not bother calling the API". These tests
 * pin that behaviour, including the case that matters most: an unreadable token
 * must not be reported as expired, because only the API may invalidate a session.
 */
class JwtTest {

    private fun token(payloadJson: String): String {
        val header = encode("""{"alg":"HS256","typ":"JWT"}""")
        val payload = encode(payloadJson)
        return "$header.$payload.c2lnbmF0dXJl"
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    @Test
    fun `reads exp as epoch millis`() {
        val token = token("""{"sub":"user-1","exp":1900000000}""")
        assertEquals(1_900_000_000_000L, Jwt.expiresAtMillis(token))
    }

    @Test
    fun `is expired at and after the expiry instant`() {
        val token = token("""{"exp":1900000000}""")
        val expiry = 1_900_000_000_000L

        assertFalse(Jwt.isExpired(token, expiry - 1))
        assertTrue(Jwt.isExpired(token, expiry))
        assertTrue(Jwt.isExpired(token, expiry + 1))
    }

    @Test
    fun `token without exp is never treated as expired`() {
        val token = token("""{"sub":"user-1"}""")
        assertNull(Jwt.expiresAtMillis(token))
        assertFalse(Jwt.isExpired(token, Long.MAX_VALUE))
    }

    @Test
    fun `malformed tokens are unknown, not expired`() {
        assertNull(Jwt.expiresAtMillis("not-a-jwt"))
        assertNull(Jwt.expiresAtMillis("only.two"))
        assertNull(Jwt.expiresAtMillis("a.!!!not-base64!!!.c"))
        assertFalse(Jwt.isExpired("not-a-jwt", Long.MAX_VALUE))
    }

    @Test
    fun `non numeric exp is ignored`() {
        val token = token("""{"exp":"1900000000"}""")
        assertNull(Jwt.expiresAtMillis(token))
    }
}
