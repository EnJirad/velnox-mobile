package com.velnox.app.core.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where the Google client id comes from.
 *
 * It must be the **Web** client: the ID token's `aud` carries it, and the backend
 * compares `aud` against its own configured value. Getting this wrong produces
 * tokens the server rejects, so the resolution order is pinned here.
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
            password: String,
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

        override suspend fun googleClientId(): ApiResult<String?> {
            calls++
            return result
        }
    }

    @Test
    fun `a build-time value wins and no request is made`() = runBlocking {
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
    fun `an unavailable config means no client id`() = runBlocking {
        val source = GoogleClientIdSource(FakeApi(ApiResult.Error(AuthError.Network)), buildValue = "")
        assertNull(source.clientId())
    }

    @Test
    fun `an empty remote value is treated as not configured`() = runBlocking {
        val source = GoogleClientIdSource(FakeApi(ApiResult.Ok("  ")), buildValue = "")
        assertNull(source.clientId())
    }

    @Test
    fun `a missing field is treated as not configured`() = runBlocking {
        val source = GoogleClientIdSource(FakeApi(ApiResult.Ok(null)), buildValue = "")
        assertNull(source.clientId())
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
