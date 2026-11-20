package com.velnox.app.core.auth

import com.velnox.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The Velnox API client for authentication calls.
 *
 * Scope is deliberately small: only the endpoints this part needs, parsed into
 * the models in [AuthModels]. Everything is executed on [Dispatchers.IO] and no
 * call ever throws for an expected failure — the caller gets an [ApiResult].
 *
 * ### The token problem this class has to live with
 *
 * The API's session is an **httpOnly cookie** (`velnox_session`, `SameSite=None;
 * Secure`, 7 days). The mobile auth routes do not return the token in the body,
 * and there is no refresh endpoint (`backend/routes/mobile-auth.ts` verified).
 * The server *does* accept `Authorization: Bearer <same token>` (`bearerOrCookie`
 * in `backend/routes/auth.ts`), but on the native routes only the `Set-Cookie`
 * header carries the value.
 *
 * So [captureSession] reads the token from `Set-Cookie` when the server sends it
 * and reports [AuthError.Server] when it does not. Nothing is invented: if the
 * token cannot be obtained, the app refuses to pretend it is signed in. Flipping
 * this to a body-carried token later is a change in this one function.
 */
class AuthRemoteDataSource(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val client: OkHttpClient = defaultClient(),
) : AuthApi {

    /** `POST /api/auth/mobile/login` — email + password. */
    override suspend fun login(email: String, password: String): ApiResult<AuthSession> = post(
        path = "/api/auth/mobile/login",
        body = JSONObject().put("email", email).put("password", password),
        isSignIn = true,
    ).toSessionResult()

    /**
     * `POST /api/auth/mobile/register` — creates a customer account and signs in.
     * Not wired to UI yet (Part 4 owns the registration screen); the endpoint is
     * implemented here so the repository contract is complete.
     */
    override suspend fun register(name: String, email: String, password: String): ApiResult<AuthSession> = post(
        path = "/api/auth/mobile/register",
        body = JSONObject().put("name", name).put("email", email).put("password", password),
        isSignIn = true,
    ).toSessionResult()

    /**
     * `GET /api/auth/me` — the only proof of session validity.
     *
     * This is the call that makes a client-side flag insufficient: the token is
     * sent to the server, which checks the signature, the `jti` revocation list
     * and the account row before answering.
     */
    override suspend fun me(token: String): ApiResult<AuthUser> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl + "/api/auth/me")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 401 -> ApiResult.Error(AuthError.Unauthorized)
                    response.isSuccessful -> parseUser(body)?.let { ApiResult.Ok(it) }
                        ?: ApiResult.Error(AuthError.Server)
                    response.code >= 500 -> ApiResult.Error(AuthError.Server)
                    else -> ApiResult.Error(AuthError.Unknown)
                }
            }
        } catch (_: IOException) {
            ApiResult.Error(AuthError.Network)
        }
    }

    /** `POST /api/auth/logout` — revokes the token server-side. Best effort. */
    override suspend fun logout(token: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl + "/api/auth/logout")
            .header("Authorization", "Bearer $token")
            .post(EMPTY_JSON.toRequestBody(JSON))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) ApiResult.Ok(Unit)
                else ApiResult.Error(AuthError.Server)
            }
        } catch (_: IOException) {
            ApiResult.Error(AuthError.Network)
        }
    }

    /**
     * `POST /api/auth/mobile/google`.
     *
     * Only the ID token is sent. The server verifies it with Google and derives
     * the account from the claims; nothing the client claims about itself is used.
     */
    override suspend fun googleSignIn(idToken: String): ApiResult<AuthSession> = post(
        path = "/api/auth/mobile/google",
        body = JSONObject().put("idToken", idToken),
    ).toSessionResult()

    /**
     * `POST /api/auth/mobile/link`.
     *
     * The session travels in `Authorization: Bearer …`, which the backend's
     * `sessionToken()` accepts alongside the cookie. The app never holds a cookie
     * jar, so this header is the only way to prove ownership of the account.
     *
     * A `200` is only reported as success when the body actually says
     * `data.linked === true`: a success status without the confirmation is
     * treated as a server fault instead of being assumed to have worked.
     */
    override suspend fun linkGoogle(
        idToken: String,
        sessionToken: String,
    ): ApiResult<Unit> {
        val raw = post(
            path = "/api/auth/mobile/link",
            body = JSONObject().put("idToken", idToken),
            token = sessionToken,
        )
        return when (raw) {
            is Raw.Fail -> ApiResult.Error(raw.error)
            is Raw.Ok -> {
                val linked = try {
                    JSONObject(raw.body)
                        .optJSONObject("data")
                        ?.optBoolean("linked") == true
                } catch (_: JSONException) {
                    false
                }
                if (linked) ApiResult.Ok(Unit) else ApiResult.Error(AuthError.Server)
            }
        }
    }

    /**
     * `GET /api/auth/mobile/config`.
     *
     * The Google client id is a public identifier, so publishing it lets the app
     * stay correct when the deployment changes without shipping a new build. The
     * response may omit it entirely, which is reported as `null`, not as a failure.
     */
    override suspend fun googleClientId(): ApiResult<String?> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl + "/api/auth/mobile/config")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    ApiResult.Error(if (response.code >= 500) AuthError.Server else AuthError.Unknown)
                } else {
                    val body = response.body?.string().orEmpty()
                    val id = try {
                        JSONObject(body)
                            .optJSONObject("data")
                            ?.optString("googleClientId")
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                    } catch (_: JSONException) {
                        null
                    }
                    ApiResult.Ok(id)
                }
            }
        } catch (_: IOException) {
            ApiResult.Error(AuthError.Network)
        }
    }

    // ------------------------------------------------------------------ parsing

    /**
     * `POST /api/auth/forgot-password`.
     *
     * The endpoint exists only once the backend branch lands; until then the live
     * API answers 404, which maps to [AuthError.Unknown] and is shown as "try
     * again later" rather than as a false success.
     */
    override suspend fun forgotPassword(email: String): ApiResult<Unit> = when (
        val raw = post(path = "/api/auth/forgot-password", body = JSONObject().put("email", email))
    ) {
        is Raw.Ok -> ApiResult.Ok(Unit)
        is Raw.Fail -> ApiResult.Error(raw.error)
    }

    /** `POST /api/auth/reset-password`. */
    override suspend fun resetPassword(token: String, password: String): ApiResult<Unit> = when (
        val raw = post(
            path = "/api/auth/reset-password",
            body = JSONObject().put("token", token).put("password", password),
        )
    ) {
        is Raw.Ok -> ApiResult.Ok(Unit)
        is Raw.Fail -> ApiResult.Error(raw.error)
    }

    private sealed interface Raw {
        data class Ok(val body: String, val setCookies: List<String>) : Raw
        data class Fail(val error: AuthError) : Raw
    }

    private suspend fun post(
        path: String,
        body: JSONObject,
        isSignIn: Boolean = false,
        token: String? = null,
    ): Raw =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl + path)
                .header("Accept", "application/json")
                .apply { if (token != null) header("Authorization", "Bearer " + token) }
                .post(body.toString().toRequestBody(JSON))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    val codes = response.headers.values("Set-Cookie")
                    if (response.isSuccessful) Raw.Ok(text, codes)
                    else Raw.Fail(errorFor(response.code, text, isSignIn))
                }
            } catch (_: IOException) {
                Raw.Fail(AuthError.Network)
            }
        }

    private fun Raw.toSessionResult(): ApiResult<AuthSession> = when (this) {
        is Raw.Fail -> ApiResult.Error(error)
        is Raw.Ok -> {
            val user = parseUser(body)
            val token = captureSession(setCookies)
            when {
                user == null -> ApiResult.Error(AuthError.Server)
                token == null -> ApiResult.Error(AuthError.Server)
                else -> ApiResult.Ok(AuthSession(token = token, user = user))
            }
        }
    }

    /**
     * Extract the session value from `Set-Cookie`.
     *
     * The server never returns the token in the body, so this header is the only
     * carrier. Returns `null` when the response carried no usable cookie, which
     * the caller reports as a failure rather than guessing.
     */
    private fun captureSession(setCookies: List<String>): String? {
        for (cookie in setCookies) {
            val first = cookie.substringBefore(';').trim()
            val name = first.substringBefore('=', missingDelimiterValue = "")
            if (name != SESSION_COOKIE) continue
            val value = first.substringAfter('=', missingDelimiterValue = "").trim()
            // An empty value is a clear-cookie instruction, not a session.
            if (value.isNotEmpty()) return value
        }
        return null
    }

    private fun parseUser(body: String): AuthUser? {
        return try {
            val root = JSONObject(body)
            if (!root.optBoolean("success", false)) return null
            val user = root.optJSONObject("data")?.optJSONObject("user") ?: return null
            val id = user.optString("id")
            val email = user.optString("email")
            if (id.isEmpty() || email.isEmpty()) return null
            val rawAvatar = user.optString("avatar").takeIf { it.isNotBlank() }
                ?: user.optString("avatarUrl").takeIf { it.isNotBlank() }
            val rawCover = user.optString("coverUrl").takeIf { it.isNotBlank() }
                ?: user.optString("cover_url").takeIf { it.isNotBlank() }

            fun normalize(u: String?): String? {
                if (u.isNullOrBlank()) return null
                if (u.startsWith("http://") || u.startsWith("https://") || u.startsWith("data:") || u.startsWith("content://") || u.startsWith("file://")) return u
                val cleanKey = u.trimStart('/')
                val cleanBase = baseUrl.trimEnd('/')
                return "$cleanBase/api/media/$cleanKey"
            }

            AuthUser(
                id = id,
                email = email,
                name = user.optString("name"),
                role = user.optString("role", "customer"),
                avatar = normalize(rawAvatar),
                coverUrl = normalize(rawCover),
            )
        } catch (_: JSONException) {
            null
        } catch (_: NullPointerException) {
            null
        }
    }

    private fun errorFor(status: Int, body: String, isSignIn: Boolean): AuthError {
        val code = try {
            JSONObject(body).optJSONObject("error")?.optString("code").orEmpty()
        } catch (_: JSONException) {
            ""
        }

        return when {
            status == 401 && isSignIn -> AuthError.InvalidCredentials

            // Provider rejections are matched on their code before any status
            // check. `POST /api/auth/mobile/google` answers 401 for a token it
            // will not accept — wrong audience, expired, unverified email — and a
            // status-first `when` reported that as "your session expired", which
            // is both wrong and unactionable. A real expired session carries the
            // `UNAUTHORIZED` code and still falls through to that branch below.
            code == "INVALID_GOOGLE_TOKEN" || code == "EMAIL_NOT_VERIFIED" ->
                AuthError.InvalidProviderToken
            code == "GOOGLE_NOT_CONFIGURED" || code == "GOOGLE_UNAVAILABLE" ->
                AuthError.ProviderUnavailable
            code == "LINK_REQUIRED" -> AuthError.LinkRequired
            code == "EMAIL_MISMATCH" -> AuthError.EmailMismatch
            code == "IDENTITY_CONFLICT" -> AuthError.IdentityConflict

            status == 401 -> AuthError.Unauthorized
            status == 429 -> AuthError.RateLimited
            code == "EMAIL_EXISTS" -> AuthError.EmailExists
            code == "ACCOUNT_DISABLED" || code == "ACCOUNT_SUSPENDED" -> AuthError.AccountDisabled
            code == "ACCOUNT_CONFLICT" -> AuthError.AccountConflict
            code == "EMAIL_NOT_CONFIGURED" -> AuthError.EmailNotConfigured
            code == "INVALID_RESET_TOKEN" -> AuthError.InvalidResetToken
            status == 400 -> AuthError.Validation
            status >= 500 -> AuthError.Server
            else -> AuthError.Unknown
        }
    }

    companion object {
        const val SESSION_COOKIE = "velnox_session"

        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_JSON = "{}"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            // Redirects are not followed: an auth endpoint must not be able to
            // move the request somewhere else and silently turn a failure into a
            // success-looking response.
            .followRedirects(false)
            .build()
    }
}
