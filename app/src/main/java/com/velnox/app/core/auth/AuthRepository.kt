package com.velnox.app.core.auth

import com.velnox.app.core.session.SessionManager

/**
 * The one way the app signs in, restores and signs out.
 *
 * Enforces security & resilience rules:
 * - A token is validated against the server on startup before reporting [RestoreOutcome.Active].
 * - Supports short-lived access tokens (15m) + long-lived refresh tokens (7d) on Cloudflare Workers.
 * - Single-flight refresh coordination prevents concurrent refresh token reuse revocation.
 * - Network failure keeps the session locally as [RestoreOutcome.Unverified].
 * - Signing out clears local storage first, then revokes tokens on the server.
 */
class AuthRepository(
    private val api: AuthApi,
    private val sessions: SessionManager,
    private val tokenRefresher: TokenRefresher? = null,
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
     */
    suspend fun restore(): RestoreOutcome {
        val session = sessions.currentSession() ?: return RestoreOutcome.NoSession

        // Check proactive refresh if token has refresh token and is near or past expiry
        var activeToken = session.token
        if (tokenRefresher != null && session.refreshToken != null && sessions.shouldRefresh(session)) {
            when (val refreshOutcome = tokenRefresher.refresh(session.token)) {
                is RefreshResult.Success -> {
                    activeToken = refreshOutcome.accessToken
                }
                is RefreshResult.Expired -> {
                    sessions.clear()
                    return RestoreOutcome.Expired
                }
                is RefreshResult.TransientFailure -> {
                    // If access token is still valid according to exp, continue to me(); otherwise unverified
                    if (sessions.isExpired(session.token)) {
                        return RestoreOutcome.Unverified
                    }
                }
                RefreshResult.NoRefreshToken -> {
                    // Continue with existing token
                }
            }
        } else if (sessions.isExpired(activeToken)) {
            if (tokenRefresher != null && session.refreshToken != null) {
                when (val refreshOutcome = tokenRefresher.refresh(activeToken)) {
                    is RefreshResult.Success -> {
                        activeToken = refreshOutcome.accessToken
                    }
                    is RefreshResult.Expired -> {
                        sessions.clear()
                        return RestoreOutcome.Expired
                    }
                    is RefreshResult.TransientFailure -> {
                        return RestoreOutcome.Unverified
                    }
                    RefreshResult.NoRefreshToken -> {
                        sessions.clear()
                        return RestoreOutcome.Expired
                    }
                }
            } else {
                sessions.clear()
                return RestoreOutcome.Expired
            }
        }

        return when (val result = api.me(activeToken)) {
            is ApiResult.Ok -> RestoreOutcome.Active(result.value)
            is ApiResult.Error -> when (result.error) {
                AuthError.Unauthorized -> {
                    // Reactive refresh attempt on 401
                    if (tokenRefresher != null && session.refreshToken != null) {
                        when (val refreshOutcome = tokenRefresher.refresh(activeToken)) {
                            is RefreshResult.Success -> {
                                when (val retry = api.me(refreshOutcome.accessToken)) {
                                    is ApiResult.Ok -> RestoreOutcome.Active(retry.value)
                                    is ApiResult.Error -> {
                                        if (retry.error == AuthError.Unauthorized) {
                                            sessions.clear()
                                            RestoreOutcome.Expired
                                        } else {
                                            RestoreOutcome.Unverified
                                        }
                                    }
                                }
                            }
                            is RefreshResult.Expired -> {
                                sessions.clear()
                                RestoreOutcome.Expired
                            }
                            is RefreshResult.TransientFailure -> RestoreOutcome.Unverified
                            RefreshResult.NoRefreshToken -> {
                                sessions.clear()
                                RestoreOutcome.Expired
                            }
                        }
                    } else {
                        sessions.clear()
                        RestoreOutcome.Expired
                    }
                }

                AuthError.Network, AuthError.Server -> RestoreOutcome.Unverified

                else -> RestoreOutcome.Unverified
            }
        }
    }

    /** Drop the session locally, then revoke it on the server. Never throws. */
    suspend fun signOut() {
        val session = sessions.currentSession()
        sessions.clear()
        if (session != null) {
            api.logout(session.token, session.refreshToken)
        }
    }

    /**
     * Exchange a Google ID token for a session.
     */
    suspend fun signInWithGoogle(idToken: String): SignInResult {
        return when (val result = api.googleSignIn(idToken.trim())) {
            is ApiResult.Error -> SignInResult.Failure(result.error)
            is ApiResult.Ok -> persist(result.value)
        }
    }

    suspend fun linkGoogle(idToken: String): ApiResult<Unit> {
        val token = sessions.current() ?: return ApiResult.Error(AuthError.Unauthorized)
        return api.linkGoogle(idToken.trim(), token)
    }

    /**
     * Ask the server to send a reset link.
     */
    suspend fun requestPasswordReset(email: String): ApiResult<Unit> =
        api.forgotPassword(email.trim().lowercase())

    /**
     * Choose a new password with a reset token.
     */
    suspend fun resetPassword(token: String, password: String): ApiResult<Unit> {
        val result = api.resetPassword(token.trim(), password)
        if (result is ApiResult.Ok<*>) sessions.clear()
        return result
    }

    private suspend fun persist(session: AuthSession): SignInResult {
        sessions.save(session)
        return SignInResult.Success(session.user)
    }
}
