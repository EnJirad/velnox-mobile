package com.velnox.app.core.customer

import com.velnox.app.core.auth.ApiResult
import com.velnox.app.core.auth.AuthApi
import com.velnox.app.core.auth.AuthError
import com.velnox.app.core.auth.AuthSession
import com.velnox.app.core.auth.AuthTokens
import com.velnox.app.core.auth.AuthUser
import com.velnox.app.core.auth.TokenRefresher
import com.velnox.app.core.session.SessionManager
import com.velnox.app.core.session.SessionStore
import com.velnox.app.core.session.StoredSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerRepositoryTest {

    private class Api(private val answer: CustomerResult<List<CustomerAddress>>) : FakeCustomerApi() {
        var seenToken: String? = null
        override suspend fun addresses(token: String): CustomerResult<List<CustomerAddress>> {
            seenToken = token
            return answer
        }
    }

    private class RetryApi : FakeCustomerApi() {
        val calls = mutableListOf<String>()
        override suspend fun addresses(token: String): CustomerResult<List<CustomerAddress>> {
            calls.add(token)
            return if (token == "stale-tok") {
                CustomerResult.Failed(CustomerError.Unauthorized)
            } else {
                CustomerResult.Ok(emptyList())
            }
        }
    }

    private class RefreshableAuthApi : AuthApi {
        var refreshCalls = 0
        override suspend fun login(email: String, password: String): ApiResult<AuthSession> = ApiResult.Error(AuthError.Network)
        override suspend fun register(name: String, email: String, password: String): ApiResult<AuthSession> = ApiResult.Error(AuthError.Network)
        override suspend fun me(token: String): ApiResult<AuthUser> = ApiResult.Error(AuthError.Network)
        override suspend fun forgotPassword(email: String): ApiResult<Unit> = ApiResult.Error(AuthError.Network)
        override suspend fun resetPassword(token: String, password: String): ApiResult<Unit> = ApiResult.Error(AuthError.Network)
        override suspend fun googleSignIn(idToken: String): ApiResult<AuthSession> = ApiResult.Error(AuthError.Network)
        override suspend fun linkGoogle(idToken: String, sessionToken: String): ApiResult<Unit> = ApiResult.Error(AuthError.Network)
        override suspend fun googleClientId(): ApiResult<String?> = ApiResult.Ok(null)

        override suspend fun refresh(refreshToken: String): ApiResult<AuthTokens> {
            refreshCalls++
            return ApiResult.Ok(AuthTokens(accessToken = "fresh-tok", refreshToken = "fresh-refresh"))
        }
    }

    private class MemoryStore(var session: StoredSession? = null) : SessionStore {
        override suspend fun read(): StoredSession? = session
        override suspend fun write(session: StoredSession) {
            this.session = session
        }
        override suspend fun clear() {
            session = null
        }
    }

    @Test
    fun `a guest never reaches the network and does not trigger the 401 handler`() = runBlocking {
        val api = Api(CustomerResult.Ok(emptyList()))
        var unauthorized = 0
        val repository = CustomerRepository(api, token = { null }, onUnauthorized = { unauthorized++ })

        val result = repository.call { a, t -> a.addresses(t) }

        assertEquals(CustomerResult.Failed(CustomerError.Unauthorized), result)
        assertEquals(null, api.seenToken)
        assertEquals(0, unauthorized)
    }

    @Test
    fun `the session token is attached to the call`() = runBlocking {
        val api = Api(CustomerResult.Ok(emptyList()))
        val repository = CustomerRepository(api, token = { "tok-1" })

        repository.call { a, t -> a.addresses(t) }

        assertEquals("tok-1", api.seenToken)
    }

    @Test
    fun `a 401 from the server runs the handler and is still returned`() = runBlocking {
        val api = Api(CustomerResult.Failed(CustomerError.Unauthorized))
        var unauthorized = 0
        val repository = CustomerRepository(api, token = { "dead" }, onUnauthorized = { unauthorized++ })

        val result = repository.call { a, t -> a.addresses(t) }

        assertEquals(CustomerResult.Failed(CustomerError.Unauthorized), result)
        assertEquals(1, unauthorized)
    }

    @Test
    fun `other failures do not touch the session`() = runBlocking {
        val api = Api(CustomerResult.Failed(CustomerError.Network))
        var unauthorized = 0
        val repository = CustomerRepository(api, token = { "tok" }, onUnauthorized = { unauthorized++ })

        repository.call { a, t -> a.addresses(t) }

        assertEquals(0, unauthorized)
    }

    @Test
    fun `a 401 triggers token refresh and retries successfully`() = runBlocking {
        val retryApi = RetryApi()
        val store = MemoryStore(
            StoredSession(token = "stale-tok", refreshToken = "refresh-xyz", expiresAtMillis = 1000L)
        )
        val sessions = SessionManager(store)
        val authApi = RefreshableAuthApi()
        val refresher = TokenRefresher(api = authApi, sessions = sessions)
        var unauthorized = 0

        val repository = CustomerRepository(
            api = retryApi,
            token = { sessions.current() },
            onUnauthorized = { unauthorized++ },
            tokenRefresher = refresher,
            sessions = sessions,
        )

        val result = repository.call { a, t -> a.addresses(t) }

        assertTrue(result is CustomerResult.Ok)
        assertEquals(listOf("stale-tok", "fresh-tok"), retryApi.calls)
        assertEquals(1, authApi.refreshCalls)
        assertEquals(0, unauthorized)
        assertEquals("fresh-tok", sessions.current())
    }
}
