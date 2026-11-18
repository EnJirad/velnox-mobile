package com.velnox.app.core.auth

/**
 * The authentication calls the app makes against the Velnox API.
 *
 * An interface only because tests need a deterministic stand-in for the network;
 * [AuthRemoteDataSource] is the single production implementation.
 */
interface AuthApi {

    suspend fun login(email: String, password: String): ApiResult<AuthSession>

    suspend fun register(name: String, email: String, password: String): ApiResult<AuthSession>

    /** Validates a token against the server and returns the account behind it. */
    suspend fun me(token: String): ApiResult<AuthUser>

    /** Revokes a token server-side. Callers treat a failure as non-fatal. */
    suspend fun logout(token: String): ApiResult<Unit>

    /**
     * `POST /api/auth/forgot-password` — asks for a reset link.
     *
     * Answers the same way whether or not the address exists, so the caller must
     * show a neutral "if that address has an account…" message.
     */
    suspend fun forgotPassword(email: String): ApiResult<Unit>

    /** `POST /api/auth/reset-password` — consumes a reset token. */
    suspend fun resetPassword(token: String, password: String): ApiResult<Unit>
}
