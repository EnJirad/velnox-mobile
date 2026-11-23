package com.velnox.app.core.auth

/**
 * The signed-in account, as the Velnox API describes it.
 *
 * Never constructed by the client from user input: it comes from a `200` on
 * `/api/v1/auth/me`, `/api/auth/me`, `/api/auth/mobile/login`, or OAuth routes.
 */
data class AuthUser(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
    val avatar: String? = null,
    val coverUrl: String? = null,
)

/** A session issued by the API: the access token plus the account it belongs to and optional refresh token. */
data class AuthSession(
    val token: String,
    val user: AuthUser,
    val refreshToken: String? = null,
    val expiresInSeconds: Long? = null,
    val backendFlavor: String? = null,
)

/** Rotated tokens returned by POST /api/v1/auth/refresh. */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresIn: Long? = null,
)

/**
 * Every way a call can fail, reduced to the cases the UI can actually say
 * something useful about.
 */
enum class AuthError {
    /** 401 on an authenticated call: the token is expired, revoked or invalid. */
    Unauthorized,

    /** 401 on sign-in: wrong email or password. */
    InvalidCredentials,

    /** 409 `EMAIL_EXISTS` or `CONFLICT` on registration. */
    EmailExists,

    /** 403 `ACCOUNT_DISABLED` / suspended / banned. */
    AccountDisabled,

    /** 409 `ACCOUNT_CONFLICT`: an existing account must sign in before linking. */
    AccountConflict,

    /** 400 `VALIDATION_ERROR`: the server rejected the payload. */
    Validation,

    /** 429 from the per-IP limiter. */
    RateLimited,

    /**
     * 503 `EMAIL_NOT_CONFIGURED`: the server has no mail transport, so a reset
     * link cannot be delivered.
     */
    EmailNotConfigured,

    /** 400 `INVALID_RESET_TOKEN`: the reset code is wrong, used or expired. */
    InvalidResetToken,

    /**
     * 401 `INVALID_GOOGLE_TOKEN` / `EMAIL_NOT_VERIFIED`: the identity token the
     * provider returned could not be trusted.
     */
    InvalidProviderToken,

    /**
     * 409 `LINK_REQUIRED`: an account already exists for that email but is not
     * linked to this Google identity.
     */
    LinkRequired,

    /** 503 `GOOGLE_NOT_CONFIGURED` / `GOOGLE_UNAVAILABLE`. */
    ProviderUnavailable,

    /**
     * 409 `EMAIL_MISMATCH` on `POST /api/auth/mobile/link`.
     */
    EmailMismatch,

    /**
     * 409 `IDENTITY_CONFLICT` on `POST /api/auth/mobile/link`.
     */
    IdentityConflict,

    /** The request never reached the server, or the reply never arrived. */
    Network,

    /** 5xx, or a success body we could not use. */
    Server,

    /** Anything else. Never treated as success. */
    Unknown,
}

/** Result of one API call. There is no "probably worked" case. */
sealed interface ApiResult<out T> {
    data class Ok<out T>(val value: T) : ApiResult<T>
    data class Error(val error: AuthError) : ApiResult<Nothing>
}

/** Outcome of an email + password sign-in. */
sealed interface SignInResult {
    data class Success(val user: AuthUser) : SignInResult
    data class Failure(val error: AuthError) : SignInResult
}

/** Outcome of resolving a stored session at startup. */
sealed interface RestoreOutcome {
    /** No token was ever stored. */
    data object NoSession : RestoreOutcome

    /** The stored token is still valid and the API confirmed it. */
    data class Active(val user: AuthUser) : RestoreOutcome

    /** The token expired or the API rejected it. It has been cleared. */
    data object Expired : RestoreOutcome

    /**
     * The token could not be checked because the API was unreachable.
     * The token is kept.
     */
    data object Unverified : RestoreOutcome
}
