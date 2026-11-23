package com.velnox.app.core.auth

/**
 * The authentication calls the app makes against the Velnox API.
 * Supports both Cloudflare Workers v1 API and Express legacy API.
 */
interface AuthApi {

    suspend fun login(email: String, password: String): ApiResult<AuthSession>

    suspend fun register(name: String, email: String, password: String): ApiResult<AuthSession>

    /** Validates a token against the server and returns the account behind it. */
    suspend fun me(token: String): ApiResult<AuthUser>

    /** Rotates an expired access token using the long-lived refresh token. */
    suspend fun refresh(refreshToken: String): ApiResult<AuthTokens> =
        ApiResult.Error(AuthError.Network)

    /** Revokes tokens server-side. Callers treat a failure as non-fatal. */
    suspend fun logout(token: String): ApiResult<Unit> = logout(token, null)

    suspend fun logout(token: String, refreshToken: String?): ApiResult<Unit> = logout(token)

    /**
     * `POST /api/auth/forgot-password` — asks for a reset link.
     */
    suspend fun forgotPassword(email: String): ApiResult<Unit>

    /** `POST /api/auth/reset-password` — consumes a reset token. */
    suspend fun resetPassword(token: String, password: String): ApiResult<Unit>

    /**
     * `POST /api/v1/auth/google` or `POST /api/auth/mobile/google` — exchanges a Google ID token for a session.
     */
    suspend fun googleSignIn(idToken: String): ApiResult<AuthSession>

    /**
     * `POST /api/auth/mobile/link` — attaches a Google identity to the account
     * that is already signed in.
     */
    suspend fun linkGoogle(idToken: String, sessionToken: String): ApiResult<Unit>

    /**
     * `GET /api/auth/mobile/config` — the deployment's public Google client id.
     * `null` when the deployment does not publish one.
     */
    suspend fun googleClientId(): ApiResult<String?>
}
