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

    /**
     * `POST /api/auth/mobile/google` — exchanges a Google ID token for a session.
     *
     * The token is the only thing sent. No email, name or user id from the client
     * is trusted; the server derives the identity from the token it verifies.
     */
    suspend fun googleSignIn(idToken: String): ApiResult<AuthSession>

    /**
     * `POST /api/auth/mobile/link` — attaches a Google identity to the account
     * that is already signed in.
     *
     * Requires [sessionToken]: holding a session for the account **is** the proof
     * of ownership that the backend accepts. An unauthenticated caller cannot
     * link anything, which is the whole point — a verified Google email alone is
     * not evidence that the person owns the password account.
     *
     * The token is the only payload. No email or user id is sent; the server
     * derives both from the token it verifies and from the session.
     */
    suspend fun linkGoogle(idToken: String, sessionToken: String): ApiResult<Unit>

    /**
     * `GET /api/auth/mobile/config` — the deployment's public Google client id.
     *
     * `null` when the deployment does not publish one, which means Google
     * Sign-In cannot be offered.
     */
    suspend fun googleClientId(): ApiResult<String?>
}
