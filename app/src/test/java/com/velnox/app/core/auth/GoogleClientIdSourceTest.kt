package com.velnox.app.core.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where the Google client id comes from, and when it is allowed to be remembered.
 *
 * It must be the **Web** client: the ID token's `aud` carries it, and the backend
 * compares `aud` against its own configured value. Getting this wrong produces
 * tokens the server rejects, so the resolution order is pinned here.
 *
 * The second half of this file pins the difference between "this deployment has
 * none" and "the question could not be asked". Conflating them is what disabled the
 * Google button for a whole session after one failed request.
 */
class GoogleClientIdSourceTest {

    private class FakeApi(
        var result: ApiResult<String?> = ApiResult.Ok(null),
    ) : AuthApi {
        var calls = 0

        override suspend fun login(email: String, password: String): ApiResult<AuthSession> =
            ApiResult.Error(AuthError.Network)

        override suspend fun register(
            name: String,
            email: String,
            password: String
        ): ApiResult<AuthSession> = ApiResult.Error(AuthError.Network)

        override suspend fun me(token: String): ApiResult<AuthUser> =
            ApiResult.Error(AuthError.Network)

        override suspend fun logout(token: String): ApiResult<Unit> =
            ApiResult.Error(AuthError.Network)

        override suspend fun forgotPassword(email: String): ApiResult<Unit> =
            ApiResult.Error(AuthError.Network)

        override suspend fun resetPassword(token: String, password: String): ApiResult<Unit> =
            ApiResult.Error(AuthError.Network)

        override suspend fun googleSignIn(idToken: String): ApiResult<AuthSession> =
            ApiResult.Error(AuthError.Network)

        override suspend fun linkGoogle(
            idToken: String,
            sessionToken: String,
        ): ApiResult<Unit> = ApiResult.Error(AuthError.Network)

        override suspend fun googleClientId(): ApiResult<String?> {
            calls++
            return result
        }
    }

    // -------------------------------------------------------------- resolution

    @Test
    fun `a build-time value wins and makes no request`() = runBlocking {
        val api = FakeApi(ApiResult.Ok("remote-id"))
        val source = GoogleClientIdSource(api, buildValue = "build-id")

        assertEquals("build-id", source.clientId())
        assertEquals(0, api.calls)
    }

    @Test
    fun `a blank build value falls back to the API`() = runBlocking {
        val api = FakeApi(ApiResult.Ok("remote-id"))
        val source = GoogleClientIdSource(api, buildValue = "   ")

        assertEquals("remote-id", source.clientId())
        assertEquals(1, api.calls)
    }

    @Test
    fun `the remote value is cached across calls`() = runBlocking {
        val api = FakeApi(ApiResult.Ok("remote-id"))
        val source = GoogleClientIdSource(api, buildValue = "")

        source.clientId()
        source.clientId()
        source.clientId()

        assertEquals(1, api.calls)
    }

    @Test
    fun `an empty remote value is treated as not configured`() = runBlocking {
        val source = GoogleClientIdSource(FakeApi(ApiResult.Ok("  ")), buildValue = "")

        assertNull(source.clientId())
        assertEquals(
            GoogleClientIdStatus.ABSENT,
            source.lookup().status,
        )
    }

    @Test
    fun `a missing field is treated as not configured`() = runBlocking {
        val source = GoogleClientIdSource(FakeApi(ApiResult.Ok(null)), buildValue = "")

        assertNull(source.clientId())
        assertEquals(GoogleClientIdStatus.ABSENT, source.lookup().status)
    }

    @Test
    fun `a configured id reports the configured status`() = runBlocking {
        val source = GoogleClientIdSource(FakeApi(ApiResult.Ok("remote-id")), buildValue = "")

        assertEquals(
            GoogleClientIdLookup.configured("remote-id"),
            source.lookup(),
        )
    }

    // -------------------------------------------------------------- retrying

    @Test
    fun `a failed lookup is not cached and is retried`() = runBlocking {
        val api = FakeApi(ApiResult.Error(AuthError.Network))
        val source = GoogleClientIdSource(api, buildValue = "")

        assertEquals(GoogleClientIdStatus.UNREACHABLE, source.lookup().status)
        assertEquals(1, api.calls)

        // The phone is back online, or Render has finished waking up.
        api.result = ApiResult.Ok("remote-id")

        assertEquals("remote-id", source.clientId())
        assertEquals(2, api.calls)
    }

    @Test
    fun `an unreachable API never looks like a deployment without Google`() = runBlocking {
        val source = GoogleClientIdSource(
            FakeApi(ApiResult.Error(AuthError.Network)),
            buildValue = "",
        )

        val lookup = source.lookup()

        assertNull(lookup.id)
        assertEquals(GoogleClientIdStatus.UNREACHABLE, lookup.status)
    }

    @Test
    fun `a definitive answer is asked for once, even across a retry button`() = runBlocking {
        val api = FakeApi(ApiResult.Ok(null))
        val source = GoogleClientIdSource(api, buildValue = "")

        source.lookup()
        source.lookup()

        // "This deployment has none" will not change in the next second; re-asking
        // on every gate open would just be traffic.
        assertEquals(1, api.calls)
    }

    @Test
    fun `invalidate allows a retry after a failed lookup`() = runBlocking {
        val api = FakeApi(ApiResult.Error(AuthError.Network))
        val source = GoogleClientIdSource(api, buildValue = "")

        assertNull(source.clientId())
        api.result = ApiResult.Ok("remote-id")
        source.invalidate()

        assertEquals("remote-id", source.clientId())
    }
}
