package com.velnox.app.core.session

import com.velnox.app.core.auth.AuthApi
import com.velnox.app.core.auth.ApiResult
import com.velnox.app.core.auth.AuthError
import com.velnox.app.core.auth.AuthRepository
import com.velnox.app.core.auth.AuthSession
import com.velnox.app.core.auth.AuthUser
import com.velnox.app.core.auth.SignInResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * The four states and the transitions between them.
 *
 * Covers the Part 3 test list end to end at the state level: guest startup, valid
 * restoration, invalid session, expired session, logout, restart and an
 * unreachable API during restoration.
 */
class SessionControllerTest {

    private val user = AuthUser(id = "u-1", email = "a@b.co", name = "Ann", role = "customer")

    private class FakeApi : AuthApi {
        var meResult: ApiResult<AuthUser> = ApiResult.Error(AuthError.Network)
        var loginResult: ApiResult<AuthSession> = ApiResult.Error(AuthError.Network)
        var logoutResult: ApiResult<Unit> = ApiResult.Ok(Unit)
        var logoutCalls = 0
        var forgotResult: ApiResult<Unit> = ApiResult.Ok(Unit)
        var resetResult: ApiResult<Unit> = ApiResult.Ok(Unit)

        override suspend fun login(email: String, password: String) = loginResult
        override suspend fun register(name: String, email: String, password: String) = loginResult
        override suspend fun me(token: String) = meResult
        override suspend fun logout(token: String): ApiResult<Unit> {
            logoutCalls++
            return logoutResult
        }

        override suspend fun forgotPassword(email: String): ApiResult<Unit> = forgotResult

        override suspend fun resetPassword(token: String, password: String): ApiResult<Unit> =
            resetResult
    }

    private class FakeStore(var token: String? = null) : SessionStore {
        override suspend fun read() = token?.let { StoredSession(it) }
        override suspend fun write(session: StoredSession) {
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

    private fun controller(
        api: FakeApi,
        store: FakeStore,
        now: Long = 1_800_000_000_000L,
    ): SessionController = SessionController(
        AuthRepository(api, SessionManager(store, now = { now })),
    )

    @Test
    fun `initial state is Loading, never a guessed Guest`() {
        val controller = controller(FakeApi(), FakeStore())
        assertEquals(SessionState.Loading, controller.state)
    }

    @Test
    fun `guest startup resolves to Guest`() = runBlocking {
        val controller = controller(FakeApi(), FakeStore())
        controller.restoreOnce()

        assertEquals(SessionState.Guest, controller.state)
        assertNull(controller.notice)
    }

    @Test
    fun `a valid stored session restores to Authenticated`() = runBlocking {
        val api = FakeApi().apply { meResult = ApiResult.Ok(user) }
        val controller = controller(api, FakeStore(tokenExpiringAt(1_900_000_000_000L)))
        controller.restoreOnce()

        assertEquals(SessionState.Authenticated(user), controller.state)
        assertEquals(user, controller.state.user)
        assertTrue(controller.state.isAuthenticated)
        assertNull(controller.notice)
    }

    @Test
    fun `an invalid session resolves to ExpiredSession and clears the token`() = runBlocking {
        val api = FakeApi().apply { meResult = ApiResult.Error(AuthError.Unauthorized) }
        val store = FakeStore(tokenExpiringAt(1_900_000_000_000L))
        val controller = controller(api, store)
        controller.restoreOnce()

        assertEquals(SessionState.ExpiredSession, controller.state)
        assertNull(store.token)
    }

    @Test
    fun `an expired token resolves to ExpiredSession without calling the API`() = runBlocking {
        val now = 1_800_000_000_000L
        val api = FakeApi()
        val controller = controller(api, FakeStore(tokenExpiringAt(now - 1)), now = now)
        controller.restoreOnce()

        assertEquals(SessionState.ExpiredSession, controller.state)
    }

    @Test
    fun `an unreachable API during restoration stays Guest and reports a notice`() = runBlocking {
        val api = FakeApi().apply { meResult = ApiResult.Error(AuthError.Network) }
        val store = FakeStore(tokenExpiringAt(1_900_000_000_000L))
        val controller = controller(api, store)
        controller.restoreOnce()

        assertEquals(SessionState.Guest, controller.state)
        assertEquals(SessionNotice.NetworkUnavailable, controller.notice)
        // The token is kept so the next launch can try again.
        assertTrue(store.token != null)
        assertFalse(controller.state.isAuthenticated)
    }

    @Test
    fun `sign in moves Guest to Authenticated`() = runBlocking {
        val api = FakeApi().apply { loginResult = ApiResult.Ok(AuthSession("token", user)) }
        val store = FakeStore()
        val controller = controller(api, store)
        controller.restoreOnce()
        assertEquals(SessionState.Guest, controller.state)

        val result = controller.signIn("a@b.co", "pw")

        assertEquals(SignInResult.Success(user), result)
        assertEquals(SessionState.Authenticated(user), controller.state)
        assertEquals("token", store.token)
    }

    @Test
    fun `a failed sign in keeps the Guest state and stores nothing`() = runBlocking {
        val api = FakeApi().apply { loginResult = ApiResult.Error(AuthError.InvalidCredentials) }
        val store = FakeStore()
        val controller = controller(api, store)
        controller.restoreOnce()

        val result = controller.signIn("a@b.co", "wrong")

        assertEquals(SignInResult.Failure(AuthError.InvalidCredentials), result)
        assertEquals(SessionState.Guest, controller.state)
        assertNull(store.token)
    }

    @Test
    fun `sign out returns to Guest and clears storage`() = runBlocking {
        val api = FakeApi().apply { meResult = ApiResult.Ok(user) }
        val store = FakeStore(tokenExpiringAt(1_900_000_000_000L))
        val controller = controller(api, store)
        controller.restoreOnce()
        assertTrue(controller.state.isAuthenticated)

        controller.signOut()

        assertEquals(SessionState.Guest, controller.state)
        assertNull(store.token)
        assertEquals(1, api.logoutCalls)
    }

    @Test
    fun `a 401 outside restoration demotes an authenticated session to ExpiredSession`() = runBlocking {
        val api = FakeApi().apply { meResult = ApiResult.Ok(user) }
        val store = FakeStore(tokenExpiringAt(1_900_000_000_000L))
        val controller = controller(api, store)
        controller.restoreOnce()

        controller.onUnauthorized()

        assertEquals(SessionState.ExpiredSession, controller.state)
        assertNull(store.token)
    }

    @Test
    fun `restart after a successful sign in restores the session`() = runBlocking {
        val api = FakeApi().apply {
            loginResult = ApiResult.Ok(AuthSession(tokenExpiringAt(1_900_000_000_000L), user))
            meResult = ApiResult.Ok(user)
        }
        val store = FakeStore()

        val first = controller(api, store)
        first.restoreOnce()
        first.signIn("a@b.co", "pw")
        assertEquals(SessionState.Authenticated(user), first.state)

        // New process: a fresh controller, the same persisted store.
        val second = controller(api, store)
        assertEquals(SessionState.Loading, second.state)
        second.restoreOnce()

        assertEquals(SessionState.Authenticated(user), second.state)
    }

    @Test
    fun `restart after a rejection starts as Guest, not as an error screen`() = runBlocking {
        val api = FakeApi().apply { loginResult = ApiResult.Ok(AuthSession("token", user)) }
        val store = FakeStore()

        val first = controller(api, store)
        first.restoreOnce()
        first.signIn("a@b.co", "pw")
        first.signOut()

        val second = controller(api, store)
        second.restoreOnce()

        assertEquals(SessionState.Guest, second.state)
    }

    @Test
    fun `restore runs once per controller even if called again`() = runBlocking {
        var reads = 0
        val countingStore = object : SessionStore {
            override suspend fun read(): StoredSession? {
                reads++
                return null
            }

            override suspend fun write(session: StoredSession) = Unit
            override suspend fun clear() = Unit
        }
        val controller = SessionController(
            AuthRepository(FakeApi(), SessionManager(countingStore)),
        )

        controller.restoreOnce()
        controller.restoreOnce()
        controller.restoreOnce()

        assertEquals(1, reads)
    }

    @Test
    fun `registration moves Guest straight to Authenticated`() = runBlocking {
        val api = FakeApi().apply { loginResult = ApiResult.Ok(AuthSession("new-token", user)) }
        val store = FakeStore()
        val controller = controller(api, store)
        controller.restoreOnce()

        val result = controller.register("Ann", "a@b.co", "Password1")

        assertEquals(SignInResult.Success(user), result)
        assertEquals(SessionState.Authenticated(user), controller.state)
        assertEquals("new-token", store.token)
    }

    @Test
    fun `a duplicate email leaves the state Guest`() = runBlocking {
        val api = FakeApi().apply { loginResult = ApiResult.Error(AuthError.EmailExists) }
        val controller = controller(api, FakeStore())
        controller.restoreOnce()

        val result = controller.register("Ann", "a@b.co", "Password1")

        assertEquals(SignInResult.Failure(AuthError.EmailExists), result)
        assertEquals(SessionState.Guest, controller.state)
    }

    @Test
    fun `a completed reset signs the user out and returns to Guest`() = runBlocking {
        val api = FakeApi().apply { meResult = ApiResult.Ok(user) }
        val store = FakeStore(token = tokenExpiringAt(1_900_000_000_000L))
        val controller = controller(api, store)
        controller.restoreOnce()
        assertTrue(controller.state.isAuthenticated)

        val result = controller.resetPassword("code", "NewPassword1")

        assertEquals(ApiResult.Ok(Unit), result)
        assertEquals(SessionState.Guest, controller.state)
        assertNull(store.token)
    }
}
