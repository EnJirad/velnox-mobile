package com.velnox.app.core.auth

import java.util.Base64

/**
 * Reads the `exp` claim out of a JWT without verifying it.
 *
 * **This is not a security check.** The signature is only ever verified by the
 * API; the value here exists so the app can skip a pointless round trip for a
 * token it already knows is stale, and so an expired session can be reported as
 * expired instead of as a generic failure.
 *
 * Unknown or malformed tokens report `null` and are therefore treated as "not
 * known to be expired" — the API still gets the final say.
 */
object Jwt {

    /** Expiry instant in epoch millis, or `null` when the token carries none. */
    fun expiresAtMillis(token: String): Long? {
        val payload = payloadJson(token) ?: return null
        val expSeconds = readExpSeconds(payload) ?: return null
        if (expSeconds <= 0) return null
        return expSeconds * 1000L
    }

    /**
     * `true` only when the token carries an `exp` that is at or before [nowMillis].
     * A token without a readable `exp` is not treated as expired.
     */
    fun isExpired(token: String, nowMillis: Long): Boolean {
        val expiresAt = expiresAtMillis(token) ?: return false
        return expiresAt <= nowMillis
    }

    private fun payloadJson(token: String): String? {
        val parts = token.split('.')
        if (parts.size != 3) return null
        val decoded = try {
            Base64.getUrlDecoder().decode(parts[1].trim())
        } catch (_: IllegalArgumentException) {
            return null
        }
        return String(decoded, Charsets.UTF_8)
    }

    /**
     * Minimal scan for `"exp":<number>`.
     *
     * Deliberately not a JSON parser: the payload is a flat object of claims and
     * this must work in a JVM unit test without pulling in a JSON runtime.
     */
    private fun readExpSeconds(payload: String): Long? {
        val match = EXP_PATTERN.find(payload) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    private val EXP_PATTERN = Regex("\"exp\"\\s*:\\s*(\\d{1,15})")
}
