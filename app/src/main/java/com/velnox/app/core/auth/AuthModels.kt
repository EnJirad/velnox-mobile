package com.velnox.app.core.auth

/**
 * The signed-in account, as the Velnox API describes it.
 *
 * Never constructed by the client from user input: it comes from a `200` on
 * `/api/auth/mobile/login`, `/api/auth/mobile/google|line` or `/api/auth/me`.
 */
data class AuthUser(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
)

/** A session issued by the API: the opaque token plus the account it belongs to. */
data class AuthSession(
    val token: String,
    val user: AuthUser,
)

/**
 * Every way a call can fail, reduced to the cases the UI can actually say
 * something useful about.
 *
 * [Unauthorized] and [InvalidCredentials] are deliberately distinct: the first
 * means "the session you have is no longer valid", the second "the password you
 * just typed is wrong". Collapsing them would either log people out for a typo
 * or keep a dead session alive.
 */
enum class AuthError {
    /** 401 on an authenticated call: the token is expired, revoked or invalid. */
    Unauthorized,

    /** 401 on sign-in: wrong email or password. */
    InvalidCredentials,

    /** 409 `EMAIL_EXISTS` on registration. */
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
     * link cannot be delivered. Reported honestly instead of pretending an email
     * was sent.
     */
    EmailNotConfigured,

    /** 400 `INVALID_RESET_TOKEN`: the reset code is wrong, used or expired. */
    InvalidResetToken,

    /**
     * 401 `INVALID_GOOGLE_TOKEN` / `EMAIL_NOT_VERIFIED`: the identity token the
     * provider returned could not be trusted. Never treated as a sign-in.
     */
    InvalidProviderToken,

    /**
     * 409 `LINK_REQUIRED`: an account already exists for that email but is not
     * linked to this Google identity. The user must sign in with their password
     * once to prove ownership, then link.
     */
    LinkRequired,

    /** 503 `GOOGLE_NOT_CONFIGURED` / `GOOGLE_UNAVAILABLE`. */
    ProviderUnavailable,

    /** The request never reached the server, or the reply never arrived. */
    Network,

    /** 5xx, or a success body we could not use. */
    Server,

    /** Anything else. Never treated as success. */
    Unknown,
}

/** Result of one API call. There is no "probably worked" case. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
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
     *
     * The token is **kept** — it may still be valid — but the app must not act
     * as if the user were signed in, because that has not been proven.
     */
    data object Unverified : RestoreOutcome
}
