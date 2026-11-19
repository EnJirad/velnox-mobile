package com.velnox.app.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.velnox.app.core.auth.ApiResult
import com.velnox.app.core.auth.AuthRepository
import com.velnox.app.core.auth.RestoreOutcome
import com.velnox.app.core.auth.SignInResult

/**
 * Holds [SessionState] for the process and is the only thing that may change it.
 *
 * Created once in `VelnoxApplication`, so a rotation cannot re-run the restore or
 * lose the resolved state. Compose reads [state] through `LocalSession`.
 *
 * Every transition here is driven by an answer from the API, never by a screen
 * deciding it is signed in:
 *
 * | Trigger | Result |
 * |---|---|
 * | no stored token | [SessionState.Guest] |
 * | `/api/auth/me` 200 | [SessionState.Authenticated] |
 * | token past `exp`, or `/api/auth/me` 401 | [SessionState.ExpiredSession] |
 * | API unreachable | [SessionState.Guest] + [SessionNotice.NetworkUnavailable] |
 * | successful sign-in | [SessionState.Authenticated] |
 * | sign-out | [SessionState.Guest] |
 */
class SessionController(
    private val repository: AuthRepository,
) {

    var state by mutableStateOf<SessionState>(SessionState.Loading)
        private set

    var notice by mutableStateOf<SessionNotice?>(null)
        private set

    private var restoreStarted = false

    /**
     * Resolve the stored session exactly once per process.
     *
     * Starts in [SessionState.Loading] and always ends in one of the other three,
     * so a screen can never be stuck waiting on storage.
     */
    suspend fun restoreOnce() {
        if (restoreStarted) return
        restoreStarted = true
        notice = null

        state = when (val outcome = repository.restore()) {
            RestoreOutcome.NoSession -> SessionState.Guest
            is RestoreOutcome.Active -> SessionState.Authenticated(outcome.user)
            RestoreOutcome.Expired -> SessionState.ExpiredSession
            RestoreOutcome.Unverified -> {
                notice = SessionNotice.NetworkUnavailable
                SessionState.Guest
            }
        }
    }

    /**
     * Email + password sign-in. Returns the failure so the caller can show a
     * message; the state only becomes [SessionState.Authenticated] on success.
     */
    suspend fun signIn(email: String, password: String): SignInResult {
        notice = null
        return when (val result = repository.signIn(email, password)) {
            is SignInResult.Success -> {
                state = SessionState.Authenticated(result.user)
                result
            }

            is SignInResult.Failure -> result
        }
    }

    /**
     * Email + password registration. Creates the account and signs in with the
     * session the API returns; a duplicate email surfaces as
     * [com.velnox.app.core.auth.AuthError.EmailExists] and changes nothing.
     */
    suspend fun register(name: String, email: String, password: String): SignInResult {
        notice = null
        return when (val result = repository.register(name, email, password)) {
            is SignInResult.Success -> {
                state = SessionState.Authenticated(result.user)
                result
            }

            is SignInResult.Failure -> result
        }
    }

    /**
     * Google sign-in.
     *
     * Identical rules to email: the state only becomes
     * [SessionState.Authenticated] after the API has verified the identity token
     * and issued a session. A `LINK_REQUIRED` answer leaves the app as it was.
     */
    suspend fun signInWithGoogle(idToken: String): SignInResult {
        notice = null
        return when (val result = repository.signInWithGoogle(idToken)) {
            is SignInResult.Success -> {
                state = SessionState.Authenticated(result.user)
                result
            }

            is SignInResult.Failure -> result
        }
    }

    /**
     * Attach Google to the account that is already signed in.
     *
     * Only meaningful on [SessionState.Authenticated]; the repository refuses
     * without a stored token. The result is reported back so the caller can say
     * whether it worked, and the session state is left alone either way — linking
     * never changes *who* is signed in.
     */
    suspend fun linkGoogle(idToken: String): ApiResult<Unit> {
        val result = repository.linkGoogle(idToken)
        notice = when (result) {
            is ApiResult.Ok -> SessionNotice.GoogleLinked
            is ApiResult.Error -> SessionNotice.GoogleLinkFailed
        }
        return result
    }

    suspend fun signOut() {
        repository.signOut()
        notice = null
        state = SessionState.Guest
    }

    /**
     * Called when an authenticated API call answers 401 outside the restore path:
     * the session is gone, so it is cleared and the app drops to
     * [SessionState.ExpiredSession] instead of retrying with a dead token.
     */
    suspend fun onUnauthorized() {
        repository.signOut()
        state = SessionState.ExpiredSession
    }

    /** Clear the "could not verify" notice once it has been shown. */
    fun acknowledgeNotice() {
        notice = null
    }

    suspend fun requestPasswordReset(email: String): ApiResult<Unit> =
        repository.requestPasswordReset(email)

    /**
     * Complete a password reset. On success the stored session is gone (the server
     * invalidated it), so the app drops to [SessionState.Guest] and asks the user
     * to sign in with the new password.
     */
    suspend fun resetPassword(token: String, password: String): ApiResult<Unit> {
        val result = repository.resetPassword(token, password)
        if (result is ApiResult.Ok) {
            notice = null
            state = SessionState.Guest
        }
        return result
    }
}
