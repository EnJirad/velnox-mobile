package com.velnox.app.core.auth

import com.velnox.app.core.session.SessionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface RefreshResult {
    data class Success(val accessToken: String) : RefreshResult
    data object NoRefreshToken : RefreshResult
    data object Expired : RefreshResult
    data class TransientFailure(val error: AuthError) : RefreshResult
}

/**
 * Single-flight mutex-protected coordinator for access token refreshing.
 *
 * Cloudflare Workers implements strict refresh token rotation with reuse detection:
 * if the same refresh token is presented twice, all refresh tokens for that user
 * are immediately revoked server-side.
 *
 * This coordinator ensures that concurrent calls (e.g. parallel 401s from Cart and Orders)
 * perform exactly ONE network refresh, and subsequent callers reuse the newly acquired token.
 */
class TokenRefresher(
    private val api: AuthApi,
    private val sessions: SessionManager,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val refreshMutex = Mutex()

    suspend fun refresh(staleAccessToken: String? = null): RefreshResult = refreshMutex.withLock {
        val currentSession = sessions.currentSession() ?: return RefreshResult.NoRefreshToken

        // Single-flight check: if another coroutine already refreshed the token,
        // and it is different from the stale token, return the new token immediately!
        if (staleAccessToken != null &&
            currentSession.token != staleAccessToken &&
            !sessions.isExpired(currentSession.token)
        ) {
            return RefreshResult.Success(currentSession.token)
        }

        val refreshToken = currentSession.refreshToken ?: return RefreshResult.NoRefreshToken

        return when (val result = api.refresh(refreshToken)) {
            is ApiResult.Ok -> {
                val tokens = result.value
                val expiresAt = tokens.expiresIn?.let { now() + it * 1000L }
                sessions.updateTokens(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken ?: refreshToken,
                    expiresAtMillis = expiresAt,
                )
                RefreshResult.Success(tokens.accessToken)
            }
            is ApiResult.Error -> when (result.error) {
                AuthError.Unauthorized -> {
                    sessions.clear()
                    RefreshResult.Expired
                }
                AuthError.Network, AuthError.Server -> {
                    // Do NOT clear sessions on transient network errors!
                    RefreshResult.TransientFailure(result.error)
                }
                else -> {
                    RefreshResult.TransientFailure(result.error)
                }
            }
        }
    }
}
