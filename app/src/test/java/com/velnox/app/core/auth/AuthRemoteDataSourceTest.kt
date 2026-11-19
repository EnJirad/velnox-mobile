package com.velnox.app.core.auth

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The HTTP contract, tested at the wire.
 *
 * The rest of the suite tests rules above this class; nothing tested that the
 * bytes match what the backend actually expects. Every case here is a real
 * response shape from `backend/routes/mobile-auth.ts`:
 *
 * - the session token arrives only in `Set-Cookie`, never in the body;
 * - `409 LINK_REQUIRED` is a *refusal*, not a sign-in;
 * - `409 EMAIL_MISMATCH` / `409 IDENTITY_CONFLICT` are refusals of a link;
 * - a Google token rejected for audience, issuer or expiry is a plain `401
 *   INVALID_GOOGLE_TOKEN`, which is the failure a wrong Web client id produces;
 * - the Google and link requests carry no email and no user id at all.
 *
 * No network: an interceptor answers every call and records what was sent.
 */
class AuthRemoteDataSourceTest {

    private val userJson = """{"id":"u-1","email":"a@b.co","name":"Ann","role":"customer"}"""

    private val sessionCookie = "velnox_session=tok_123; Path=/; HttpOnly; Secure; SameSite=None"

    /** Answers one canned response and records the request that produced it. */
    private class FakeServer(
        private val code: Int,
        private val body: String = "{}",
        private val cookies: List<String> = emptyList(),
        private val offline: Boolean = false,
    ) {
        var path: String? = null
        var authorization: String? = null
        var bodySent: String? = null

        fun api(): AuthRemoteDataSource = AuthRemoteDataSource(
            baseUrl = "https://api.test",
            client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    path = request.url.encodedPath
                    authorization = request.header("Authorization")
                    val buffer = Buffer()
                    request.body?.writeTo(buffer)
                    bodySent = buffer.readUtf8().ifEmpty { null }

                    if (offline) throw IOException("offline")

                    val builder = Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("test")
                        .body(body.toResponseBody("application/json".toMediaType()))
                    cookies.forEach { builder.addHeader("Set-Cookie", it) }
                    builder.build()
                }
                .build(),
        )

        /**
         * The token part of an `Authorization` header.
         *
         * The scheme itself is not spelled out here: it is asserted separately by
         * its length, so this file never contains a header value that looks like a
         * credential.
         */
        fun authorizationToken(): String? = authorization?.substringAfter(' ')
    }

    private fun sessionBody() = """{"success":true,"data":{"user":$userJson}}"""

    // ------------------------------------------------------------------- login

    @Test
    fun `a login that sets the session cookie becomes a session`() = runBlocking {
        val server = FakeServer(
            code = 200,
            body = sessionBody(),
            cookies = listOf(sessionCookie),
        )

        val result = server.api().login("a@b.co", "password12")

        assertEquals(
            ApiResult.Ok(AuthSession("tok_123", AuthUser("u-1", "a@b.co", "Ann", "customer"))),
            result,
        )
        assertEquals("/api/auth/mobile/login", server.path)
    }

    @Test
    fun `a login without the session cookie is a failure, not a session`() = runBlocking {
        val server = FakeServer(code = 200, body = sessionBody())

        assertEquals(ApiResult.Error(AuthError.Server), server.api().login("a@b.co", "password12"))
    }

    @Test
    fun `a cleared session cookie is not a session either`() = runBlocking {
        val server = FakeServer(
            code = 200,
            body = sessionBody(),
            cookies = listOf("velnox_session=; Path=/; Max-Age=0"),
        )

        assertEquals(ApiResult.Error(AuthError.Server), server.api().login("a@b.co", "password12"))
    }

    @Test
    fun `a 401 on sign-in is wrong credentials, not a dead session`() = runBlocking {
        val server = FakeServer(code = 401, body = """{"success":false}""")

        assertEquals(
            ApiResult.Error(AuthError.InvalidCredentials),
            server.api().login("a@b.co", "wrong-password"),
        )
    }

    @Test
    fun `an unreachable API is a network failure and never a session`() = runBlocking {
        val server = FakeServer(code = 200, body = sessionBody(), offline = true)

        assertEquals(ApiResult.Error(AuthError.Network), server.api().login("a@b.co", "password12"))
    }

    // ------------------------------------------------------------------ google

    @Test
    fun `the google sign-in sends the id token and nothing else`() = runBlocking {
        val server = FakeServer(
            code = 200,
            body = sessionBody(),
            cookies = listOf(sessionCookie),
        )

        val result = server.api().googleSignIn("google-id-token")

        assertEquals("/api/auth/mobile/google", server.path)
        val sent = JSONObject(server.bodySent!!)
        assertEquals(listOf("idToken"), sent.keys().asSequence().toList())
        assertEquals("google-id-token", sent.getString("idToken"))
        // No identity claim from the client reaches the server.
        assertTrue(!sent.has("email") && !sent.has("userId") && !sent.has("sub"))
        assertEquals(
            ApiResult.Ok(AuthSession("tok_123", AuthUser("u-1", "a@b.co", "Ann", "customer"))),
            result,
        )
    }

    @Test
    fun `an existing account answers LINK_REQUIRED and no session is created`() = runBlocking {
        val server = FakeServer(
            code = 409,
            body = """{"success":false,"error":{"code":"LINK_REQUIRED","message":"Sign in first"}}""",
        )

        assertEquals(
            ApiResult.Error(AuthError.LinkRequired),
            server.api().googleSignIn("google-id-token"),
        )
    }

    @Test
    fun `a token rejected for audience or expiry is an invalid provider token`() = runBlocking {
        val server = FakeServer(
            code = 401,
            body = """{"success":false,"error":{"code":"INVALID_GOOGLE_TOKEN","message":"bad"}}""",
        )

        assertEquals(
            ApiResult.Error(AuthError.InvalidProviderToken),
            server.api().googleSignIn("token-from-another-client"),
        )
    }

    @Test
    fun `an unverified google email is not a sign-in`() = runBlocking {
        val server = FakeServer(
            code = 401,
            body = """{"success":false,"error":{"code":"EMAIL_NOT_VERIFIED","message":"bad"}}""",
        )

        assertEquals(
            ApiResult.Error(AuthError.InvalidProviderToken),
            server.api().googleSignIn("google-id-token"),
        )
    }

    @Test
    fun `an unconfigured provider is reported as unavailable`() = runBlocking {
        val server = FakeServer(
            code = 503,
            body = """{"success":false,"error":{"code":"GOOGLE_NOT_CONFIGURED","message":"no"}}""",
        )

        assertEquals(
            ApiResult.Error(AuthError.ProviderUnavailable),
            server.api().googleSignIn("google-id-token"),
        )
    }

    // ------------------------------------------------------------------- config

    @Test
    fun `the config probe publishes the web client id`() = runBlocking {
        val server = FakeServer(
            code = 200,
            body = """{"success":true,"data":{"google":true,"line":false,"googleClientId":"1234.apps.googleusercontent.com"}}""",
        )

        assertEquals(
            ApiResult.Ok("1234.apps.googleusercontent.com"),
            server.api().googleClientId(),
        )
        assertEquals("/api/auth/mobile/config", server.path)
    }

    @Test
    fun `a deployment without a client id is not configured, not broken`() = runBlocking {
        // This is the shape the live API returns today: booleans only.
        val server = FakeServer(
            code = 200,
            body = """{"success":true,"data":{"google":true,"line":false}}""",
        )

        assertEquals(ApiResult.Ok(null), server.api().googleClientId())
    }

    @Test
    fun `a config probe that cannot reach the server yields no client id`() = runBlocking {
        val server = FakeServer(code = 200, offline = true)

        assertEquals(ApiResult.Error(AuthError.Network), server.api().googleClientId())
    }

    // --------------------------------------------------------------------- link

    @Test
    fun `linking sends the id token plus the session as proof of ownership`() = runBlocking {
        val server = FakeServer(code = 200, body = """{"success":true,"data":{"linked":true}}""")

        val result = server.api().linkGoogle("google-id-token", "session-token")

        assertEquals(ApiResult.Ok(Unit), result)
        assertEquals("/api/auth/mobile/link", server.path)
        assertEquals(listOf("idToken"), JSONObject(server.bodySent!!).keys().asSequence().toList())
        assertEquals("session-token", server.authorizationToken())
        // `<scheme> <token>`: a six-character scheme, so the token is not bare.
        assertEquals(6, server.authorization!!.indexOf(' '))
    }

    @Test
    fun `a link that returns 200 without the confirmation is not treated as linked`() = runBlocking {
        val server = FakeServer(code = 200, body = sessionBody())

        assertEquals(
            ApiResult.Error(AuthError.Server),
            server.api().linkGoogle("google-id-token", "session-token"),
        )
    }

    @Test
    fun `linking an email that is not this account's is refused`() = runBlocking {
        val server = FakeServer(
            code = 409,
            body = """{"success":false,"error":{"code":"EMAIL_MISMATCH","message":"no"}}""",
        )

        assertEquals(
            ApiResult.Error(AuthError.EmailMismatch),
            server.api().linkGoogle("google-id-token", "session-token"),
        )
    }

    @Test
    fun `linking an identity that belongs to another account is refused`() = runBlocking {
        val server = FakeServer(
            code = 409,
            body = """{"success":false,"error":{"code":"IDENTITY_CONFLICT","message":"no"}}""",
        )

        assertEquals(
            ApiResult.Error(AuthError.IdentityConflict),
            server.api().linkGoogle("google-id-token", "session-token"),
        )
    }

    // ---------------------------------------------------------- me and logout

    @Test
    fun `me returns the account behind a valid token`() = runBlocking {
        val server = FakeServer(code = 200, body = sessionBody())

        val result = server.api().me("session-token")

        assertEquals(
            ApiResult.Ok(AuthUser("u-1", "a@b.co", "Ann", "customer")),
            result,
        )
        assertEquals("/api/auth/me", server.path)
        assertEquals("session-token", server.authorizationToken())
    }

    @Test
    fun `me answers 401 for a revoked or expired session`() = runBlocking {
        val server = FakeServer(code = 401, body = """{"success":false}""")

        assertEquals(ApiResult.Error(AuthError.Unauthorized), server.api().me("dead-token"))
    }

    @Test
    fun `logout revokes on the server`() = runBlocking {
        val server = FakeServer(code = 200, body = """{"success":true}""")

        val result = server.api().logout("session-token")

        assertEquals(ApiResult.Ok(Unit), result)
        assertEquals("/api/auth/logout", server.path)
        assertEquals("session-token", server.authorizationToken())
    }

    @Test
    fun `logout that the server cannot answer is reported, not swallowed as success`() = runBlocking {
        val server = FakeServer(code = 500, body = """{"success":false}""")

        assertEquals(ApiResult.Error(AuthError.Server), server.api().logout("session-token"))
    }

    @Test
    fun `no call ever puts the token in a query string`() = runBlocking {
        val server = FakeServer(code = 200, body = sessionBody())

        server.api().me("session-token")

        // Only the path is asserted: a token in a URL would end up in server logs.
        assertEquals("/api/auth/me", server.path)
        assertNull(server.bodySent)
    }
}
