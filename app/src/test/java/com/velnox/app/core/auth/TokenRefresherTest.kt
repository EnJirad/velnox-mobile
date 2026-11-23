package com.velnox.app.core.auth

import com.velnox.app.core.session.SessionManager
import com.velnox.app.core.session.SessionStore
import com.velnox.app.core.session.StoredSession
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TokenRefresherTest {

    private class FakeSessionStore(var current: StoredSession? = null) : SessionStore {
        override suspend fun read(): StoredSession? = current
        override suspend fun write(session: StoredSession) {
            current = session
        }
        override suspend fun clear() {
            current = null
        }
    }

    private class RefreshableAuthApi(
        private val delayMs: Long = 0,
    ) : AuthApi {
        val refreshCalls = AtomicInteger(0)
        var nextTokens = AuthTokens(accessToken = "new-access-token", refreshToken = "new-refresh-token")

        override suspend fun login(email: String, password: String): ApiResult<AuthSession> = ApiResult.Error(AuthError.Network)
        override suspend fun register(name: String, email: String, password: String): ApiResult<AuthSession> = ApiResult.Error(AuthError.Network)
        override suspend fun me(token: String): ApiResult<AuthUser> = ApiResult.Error(AuthError.Network)
        override suspend fun forgotPassword(email: String): ApiResult<Unit> = ApiResult.Error(AuthError.Network)
        override suspend fun resetPassword(token: String, password: String): ApiResult<Unit> = ApiResult.Error(AuthError.Network)
        override suspend fun googleSignIn(idToken: String): ApiResult<AuthSession> = ApiResult.Error(AuthError.Network)
        override suspend fun linkGoogle(idToken: String, sessionToken: String): ApiResult<Unit> = ApiResult.Error(AuthError.Network)
        override suspend fun googleClientId(): ApiResult<String?> = ApiResult.Ok(null)

        override suspend fun refresh(refreshToken: String): ApiResult<AuthTokens> {
            refreshCalls.incrementAndGet()
            if (delayMs > 0) delay(delayMs)
            return ApiResult.Ok(nextTokens)
        }
    }

    @Test
    fun `refresh updates session and returns new access token`() = runBlocking {
        val store = FakeSessionStore(
            StoredSession(
                token = "old-token",
                refreshToken = "old-refresh-token",
                expiresAtMillis = 1000L,
            )
        )
        val sessions = SessionManager(store)
        val api = RefreshableAuthApi()
        val refresher = TokenRefresher(api = api, sessions = sessions)

        val result = refresher.refresh("old-token")

        assertTrue(result is RefreshResult.Success)
        assertEquals("new-access-token", (result as RefreshResult.Success).accessToken)
        assertEquals("new-access-token", sessions.currentSession()?.token)
        assertEquals("new-refresh-token", sessions.currentSession()?.refreshToken)
        assertEquals(1, api.refreshCalls.get())
    }

    @Test
    fun `concurrent refresh calls perform exactly one network call`() = runBlocking {
        val store = FakeSessionStore(
            StoredSession(
                token = "stale-access",
                refreshToken = "refresh-token-xyz",
                expiresAtMillis = 1000L,
            )
        )
        val sessions = SessionManager(store)
        val api = RefreshableAuthApi(delayMs = 50)
        val refresher = TokenRefresher(api = api, sessions = sessions)

        // Launch 5 concurrent calls with the same stale token
        val deferred = (1..5).map {
            async {
                refresher.refresh("stale-access")
            }
        }
        val results = deferred.awaitAll()

        // All should succeed with the new access token
        for (res in results) {
            assertTrue(res is RefreshResult.Success)
            assertEquals("new-access-token", (res as RefreshResult.Success).accessToken)
        }

        // Strictly single-flight: only ONE API refresh call was executed!
        assertEquals(1, api.refreshCalls.get())
    }

    @Test
    fun `refresh without refresh token returns NoRefreshToken`() = runBlocking {
        val store = FakeSessionStore(StoredSession(token = "token-only", refreshToken = null))
        val sessions = SessionManager(store)
        val api = RefreshableAuthApi()
        val refresher = TokenRefresher(api = api, sessions = sessions)

        val result = refresher.refresh()
        assertEquals(RefreshResult.NoRefreshToken, result)
        assertEquals(0, api.refreshCalls.get())
    }
}
