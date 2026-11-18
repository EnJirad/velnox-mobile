package com.velnox.app.core.auth

import com.velnox.app.core.session.SessionManager

/**
 * The one way the app signs in, restores and signs out.
 *
 * ### Rules it enforces
 *
 * - **A token is never trusted on its own.** [restore] asks the API who the
 *   token belongs to before reporting [RestoreOutcome.Active]; a token that clears
 *   its own `exp` check is reported [RestoreOutcome.Expired] without a pointless
 *   request.
 * - **Network failure is never success.** An unreachable API yields
 *   [RestoreOutcome.Unverified]; the token is kept so a later launch can retry it,
 *   and no account is produced.
 * - **Signing out clears storage first**, so a crash mid-logout cannot leave a
 *   usable token behind, and only then asks the server to revoke it.
 */
class AuthRepository(
    private val api: AuthApi,
    private val sessions: SessionManager,
) {

    suspend fun signIn(email: String, password: String): SignInResult {
        val normalized = email.trim().lowercase()
        return when (val result = api.login(normalized, password)) {
            is ApiResult.Error -> SignInResult.Failure(result.error)
            is ApiResult.Ok -> persist(result.value)
        }
    }

    suspend fun register(name: String, email: String, password: String): SignInResult {
        val normalized = email.trim().lowercase()
        return when (val result = api.register(name.trim(), normalized, password)) {
            is ApiResult.Error -> SignInResult.Failure(result.error)
            is ApiResult.Ok -> persist(result.value)
        }
    }

    /**
     * Resolve whatever session is stored.
     *
     * The token is handed to the API, which verifies signature, revocation and the
     * account row — the client never decides on its own that a token is good.
     */
    suspend fun restore(): RestoreOutcome {
        val token = sessions.current() ?: return RestoreOutcome.NoSession

        if (sessions.isExpired(token)) {
            sessions.clear()
            return RestoreOutcome.Expired
        }

        return when (val result = api.me(token)) {
            is ApiResult.Ok -> RestoreOutcome.Active(result.value)
            is ApiResult.Error -> when (result.error) {
                AuthError.Unauthorized -> {
                    sessions.clear()
                    RestoreOutcome.Expired
                }

                AuthError.Network, AuthError.Server -> RestoreOutcome.Unverified

                // A 404 or an unexpected body is not proof the token is valid.
                else -> RestoreOutcome.Unverified
            }
        }
    }

    /** Drop the session locally, then revoke it on the server. Never throws. */
    suspend fun signOut() {
        val token = sessions.current()
        sessions.clear()
        if (token != null) api.logout(token)
    }

    /**
     * Exchange a Google ID token for a session.
     *
     * The token is handed straight to the API, which verifies it with Google. The
     * app never reads the claims itself and never decides that a Google account
     * belongs to anyone: if the server says `LINK_REQUIRED`, an account already
     * exists for that address and the user has to prove ownership with their
     * password before Google is attached to it.
     */
    suspend fun signInWithGoogle(idToken: String): SignInResult {
        return when (val result = api.googleSignIn(idToken.trim())) {
            is ApiResult.Error -> SignInResult.Failure(result.error)
            is ApiResult.Ok -> persist(result.value)
        }
    }

    /**
     * Ask the server to send a reset link.
     *
     * The outcome is intentionally not "does this account exist": the API answers
     * uniformly, and a caller that branches on the result would rebuild the
     * enumeration oracle the endpoint avoids.
     */
    suspend fun requestPasswordReset(email: String): ApiResult<Unit> =
        api.forgotPassword(email.trim().lowercase())

    /**
     * Choose a new password with a reset token.
     *
     * A successful reset invalidates the tokens that existed before it, so any
     * session this device is holding is cleared: the user must sign in again with
     * the password they just chose.
     */
    suspend fun resetPassword(token: String, password: String): ApiResult<Unit> {
        val result = api.resetPassword(token.trim(), password)
        if (result is ApiResult.Ok) sessions.clear()
        return result
    }

    private suspend fun persist(session: AuthSession): SignInResult {
        return try {
            sessions.save(session.token)
            SignInResult.Success(session.user)
        } catch (_: Exception) {
            // The token could not be stored securely. Staying signed in for this
            // process only would be a lie the next launch exposes, so the call
            // fails instead of half-succeeding.
            sessions.clear()
            SignInResult.Failure(AuthError.Unknown)
        }
    }
}
