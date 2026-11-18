package com.velnox.app.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
}
