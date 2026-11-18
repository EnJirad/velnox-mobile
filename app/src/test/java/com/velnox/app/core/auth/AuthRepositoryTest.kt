package com.velnox.app.core.auth

import com.velnox.app.core.session.SessionManager
import com.velnox.app.core.session.SessionStore
import com.velnox.app.core.session.StoredSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * The rules that make a client-side flag insufficient.
 *
 * Every case here is about *not* trusting something: a token is only valid if the
 * API says so, an unreachable API is never success, and a failure never leaves a
 * usable session behind.
 */
class AuthRepositoryTest {

    private val user = AuthUser(id = "u-1", email = "a@b.co", name = "Ann", role = "customer")

    private class FakeApi : AuthApi {
        var loginResult: ApiResult<AuthSession> = ApiResult.Error(AuthError.Network)
        var registerResult: ApiResult<AuthSession> = ApiResult.Error(AuthError.Network)
        var meResult: ApiResult<AuthUser> = ApiResult.Error(AuthError.Network)
        var logoutResult: ApiResult<Unit> = ApiResult.Ok(Unit)

        var loginCalls = 0
        var meCalls = 0
        var logoutCalls = 0
        var lastLoginEmail: String? = null
        var lastMeToken: String? = null

        override suspend fun login(email: String, password: String): ApiResult<AuthSession> {
            loginCalls++
            lastLoginEmail = email
            return loginResult
        }

        override suspend fun register(name: String, email: String, password: String): ApiResult<AuthSession> {
            return registerResult
        }

        override suspend fun me(token: String): ApiResult<AuthUser> {
            meCalls++
            lastMeToken = token
            return meResult
        }

        override suspend fun logout(token: String): ApiResult<Unit> {
            logoutCalls++
            return logoutResult
        }
    }

    private class FakeStore(var token: String? = null, var failWrite: Boolean = false) : SessionStore {
        override suspend fun read(): StoredSession? = token?.let { StoredSession(it) }
        override suspend fun write(session: StoredSession) {
            if (failWrite) throw IllegalStateException("keystore unavailable")
            token = session.token
        }

        override suspend fun clear() {
            token = null
        }
    }

    private fun tokenExpiringAt(millis: Long): String {
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"alg":"HS256"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":${millis / 1000}}""".toByteArray())
        return "$header.$payload.sig"
    }

    private fun repository(
        api: FakeApi,
        store: FakeStore,
        now: Long = 1_800_000_000_000L,
    ): AuthRepository = AuthRepository(api, SessionManager(store, now = { now }))

    // ------------------------------------------------------------- restoration

    @Test
    fun `restore without a stored token is NoSession and never calls the API`() = runBlocking {
        val api = FakeApi()
        val outcome = repository(api, FakeStore()).restore()

        assertEquals(RestoreOutcome.NoSession, outcome)
        assertEquals(0, api.meCalls)
    }

    @Test
    fun `restore with a valid token is confirmed by the API`() = runBlocking {
        val api = FakeApi().apply { meResult = ApiResult.Ok(user) }
        val store = FakeStore(token = tokenExpiringAt(1_900_000_000_000L))

        val outcome = repository(api, store).restore()

        assertEquals(RestoreOutcome.Active(user), outcome)
        assertEquals(1, api.meCalls)
        assertEquals(store.token, api.lastMeToken)
    }

    @Test
    fun `restore with a rejected token clears it and reports Expired`() = runBlocking {
        val api = FakeApi().apply { meResult = ApiResult.Error(AuthError.Unauthorized) }
        val store = FakeStore(token = tokenExpiringAt(1_900_000_000_000L))

        val outcome = repository(api, store).restore()

        assertEquals(RestoreOutcome.Expired, outcome)
        assertNull(store.token)
    }

    @Test
    fun `restore with an expired token never calls the API`() = runBlocking {
        val api = FakeApi()
        val now = 1_800_000_000_000L
        val store = FakeStore(token = tokenExpiringAt(now - 1))

        val outcome = repository(api, store, now = now).restore()

        assertEquals(RestoreOutcome.Expired, outcome)
        assertEquals(0, api.meCalls)
        assertNull(store.token)
    }

    @Test
    fun `restore with an unreachable API keeps the token but grants nothing`() = runBlocking {
        val api = FakeApi().apply { meResult = ApiResult.Error(AuthError.Network) }
        val token = tokenExpiringAt(1_900_000_000_000L)
        val store = FakeStore(token = token)

        val outcome = repository(api, store).restore()

        assertEquals(RestoreOutcome.Unverified, outcome)
        assertEquals(token, store.token)
    }

    @Test
    fun `restore with a server error keeps the token and grants nothing`() = runBlocking {
        val api = FakeApi().apply { meResult = ApiResult.Error(AuthError.Server) }
        val store = FakeStore(token = tokenExpiringAt(1_900_000_000_000L))

        val outcome = repository(api, store).restore()

        assertEquals(RestoreOutcome.Unverified, outcome)
        assertTrue(store.token != null)
    }

    // ------------------------------------------------------------------ sign in

    @Test
    fun `sign in stores the issued token and returns the account`() = runBlocking {
        val session = AuthSession("issued-token", user)
        val api = FakeApi().apply { loginResult = ApiResult.Ok(session) }
        val store = FakeStore()

        val result = repository(api, store).signIn("  A@B.CO ", "pw")

        assertEquals(SignInResult.Success(user), result)
        assertEquals("issued-token", store.token)
        // The API normalizes too; the client does not send a different identity
        // than the one the user typed.
        assertEquals("a@b.co", api.lastLoginEmail)
    }

    @Test
    fun `a rejected sign in stores nothing`() = runBlocking {
        val api = FakeApi().apply { loginResult = ApiResult.Error(AuthError.InvalidCredentials) }
        val store = FakeStore()

        val result = repository(api, store).signIn("a@b.co", "wrong")

        assertEquals(SignInResult.Failure(AuthError.InvalidCredentials), result)
        assertNull(store.token)
    }

    @Test
    fun `a sign in that cannot be stored securely is reported as a failure`() = runBlocking {
        val api = FakeApi().apply { loginResult = ApiResult.Ok(AuthSession("issued-token", user)) }
        val store = FakeStore(failWrite = true)

        val result = repository(api, store).signIn("a@b.co", "pw")

        assertEquals(SignInResult.Failure(AuthError.Unknown), result)
        assertNull(store.token)
    }

    // ----------------------------------------------------------------- sign out

    @Test
    fun `sign out clears storage and revokes the token`() = runBlocking {
        val api = FakeApi()
        val store = FakeStore(token = "issued-token")

        repository(api, store).signOut()

        assertNull(store.token)
        assertEquals(1, api.logoutCalls)
    }

    @Test
    fun `sign out clears locally even when the API is unreachable`() = runBlocking {
        val api = FakeApi().apply { logoutResult = ApiResult.Error(AuthError.Network) }
        val store = FakeStore(token = "issued-token")

        repository(api, store).signOut()

        assertNull(store.token)
    }

    @Test
    fun `sign out with no session does not call the API`() = runBlocking {
        val api = FakeApi()
        repository(api, FakeStore()).signOut()

        assertEquals(0, api.logoutCalls)
    }
}
