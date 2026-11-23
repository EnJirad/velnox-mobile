package com.velnox.app.core.auth

import com.velnox.app.BuildConfig
import com.velnox.app.core.network.BackendConfig
import com.velnox.app.core.network.BackendDialect
import com.velnox.app.core.network.BackendEndpointRouter
import com.velnox.app.core.network.VelnoxMediaUrlResolver
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
 * Supports both Cloudflare Workers v1 API (`api.velnox.live`) and Express legacy API
 * (`velnox-api.onrender.com`), with automatic host failover and dual token extraction
 * (JSON tokens + Set-Cookie).
 */
class AuthRemoteDataSource(
    baseUrl: String = BuildConfig.API_BASE_URL,
    private val client: OkHttpClient = defaultClient(),
    val router: BackendEndpointRouter = BackendEndpointRouter(
        primaryHost = baseUrl,
        fallbackHosts = BackendConfig.FALLBACK_HOSTS,
        client = client,
    ),
) : AuthApi {

    /** `POST /api/v1/auth/login` (Workers) or `POST /api/auth/mobile/login` (Express). */
    override suspend fun login(email: String, password: String): ApiResult<AuthSession> =
        router.executeWithFailover(isIdempotent = true) { host, dialect ->
            val path = if (dialect == BackendDialect.WORKERS_V1) "/api/v1/auth/login" else "/api/auth/mobile/login"
            val body = JSONObject().put("email", email).put("password", password)
            val raw = post(host, path, body, isSignIn = true, dialect = dialect)
            if (raw is Raw.Fail && raw.statusCode == 404 && dialect == BackendDialect.WORKERS_V1) {
                // Compatibility fallback
                post(host, "/api/auth/mobile/login", body, isSignIn = true, dialect = BackendDialect.EXPRESS_LEGACY)
                    .toSessionResult(host, BackendDialect.EXPRESS_LEGACY)
            } else {
                raw.toSessionResult(host, dialect)
            }
        }

    /** `POST /api/v1/auth/register` (Workers) or `POST /api/auth/mobile/register` (Express). */
    override suspend fun register(name: String, email: String, password: String): ApiResult<AuthSession> =
        router.executeWithFailover(isIdempotent = false) { host, dialect ->
            val path = if (dialect == BackendDialect.WORKERS_V1) "/api/v1/auth/register" else "/api/auth/mobile/register"
            val body = JSONObject().put("name", name).put("email", email).put("password", password)
            val raw = post(host, path, body, isSignIn = true, isRegister = true, dialect = dialect)
            if (raw is Raw.Fail && raw.statusCode == 404 && dialect == BackendDialect.WORKERS_V1) {
                post(host, "/api/auth/mobile/register", body, isSignIn = true, isRegister = true, dialect = BackendDialect.EXPRESS_LEGACY)
                    .toSessionResult(host, BackendDialect.EXPRESS_LEGACY)
            } else {
                raw.toSessionResult(host, dialect)
            }
        }

    /** `GET /api/v1/auth/me` or `GET /api/auth/me`. */
    override suspend fun me(token: String): ApiResult<AuthUser> = withContext(Dispatchers.IO) {
        router.executeWithFailover(isIdempotent = true) { host, dialect ->
            val path = if (dialect == BackendDialect.WORKERS_V1) "/api/v1/auth/me" else "/api/auth/me"
            val request = Request.Builder()
                .url(host.trimEnd('/') + path)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 401 -> ApiResult.Error(AuthError.Unauthorized)
                    response.code == 404 && dialect == BackendDialect.WORKERS_V1 -> {
                        // Retry with alternate /api/auth/me
                        val altRequest = Request.Builder()
                            .url(host.trimEnd('/') + "/api/auth/me")
                            .header("Authorization", "Bearer $token")
                            .header("Accept", "application/json")
                            .get()
                            .build()
                        client.newCall(altRequest).execute().use { altResp ->
                            val altBody = altResp.body?.string().orEmpty()
                            if (altResp.isSuccessful) {
                                parseUser(altBody, host, BackendDialect.EXPRESS_LEGACY)?.let { ApiResult.Ok(it) }
                                    ?: ApiResult.Error(AuthError.Server)
                            } else {
                                ApiResult.Error(errorFor(altResp.code, altBody, false, false))
                            }
                        }
                    }
                    response.isSuccessful -> parseUser(body, host, dialect)?.let { ApiResult.Ok(it) }
                        ?: ApiResult.Error(AuthError.Server)
                    response.code >= 500 -> ApiResult.Error(AuthError.Server)
                    else -> ApiResult.Error(AuthError.Unknown)
                }
            }
        }
    }

    /** `POST /api/v1/auth/refresh` — rotates access token. */
    override suspend fun refresh(refreshToken: String): ApiResult<AuthTokens> = withContext(Dispatchers.IO) {
        router.executeWithFailover(isIdempotent = true) { host, dialect ->
            val path = "/api/v1/auth/refresh"
            val body = JSONObject().put("refreshToken", refreshToken)
            val request = Request.Builder()
                .url(host.trimEnd('/') + path)
                .header("Accept", "application/json")
                .header("Cookie", "$REFRESH_COOKIE=$refreshToken")
                .post(body.toString().toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val root = try { JSONObject(text) } catch (_: Exception) { null }
                    val data = root?.optJSONObject("data")
                    val tokensObj = data?.optJSONObject("tokens") ?: data

                    val setCookies = response.headers.values("Set-Cookie")
                    val accessToken = tokensObj?.optString("accessToken")?.takeIf { it.isNotEmpty() }
                        ?: captureSession(setCookies, SESSION_COOKIE)
                    val newRefreshToken = tokensObj?.optString("refreshToken")?.takeIf { it.isNotEmpty() }
                        ?: captureSession(setCookies, REFRESH_COOKIE)
                    val expiresIn = if (tokensObj != null && tokensObj.has("expiresIn")) {
                        tokensObj.optLong("expiresIn")
                    } else null

                    if (!accessToken.isNullOrEmpty()) {
                        ApiResult.Ok(AuthTokens(accessToken = accessToken, refreshToken = newRefreshToken, expiresIn = expiresIn))
                    } else {
                        ApiResult.Error(AuthError.Server)
                    }
                } else {
                    ApiResult.Error(errorFor(response.code, text, false, false))
                }
            }
        }
    }

    /** `POST /api/v1/auth/logout` or `POST /api/auth/logout`. */
    override suspend fun logout(token: String, refreshToken: String?): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            router.executeWithFailover(isIdempotent = true) { host, dialect ->
                val path = if (dialect == BackendDialect.WORKERS_V1) "/api/v1/auth/logout" else "/api/auth/logout"
                val body = JSONObject()
                refreshToken?.let { body.put("refreshToken", it) }

                val builder = Request.Builder()
                    .url(host.trimEnd('/') + path)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")

                val cookieHeader = buildString {
                    append("$SESSION_COOKIE=$token")
                    if (!refreshToken.isNullOrEmpty()) {
                        append("; $REFRESH_COOKIE=$refreshToken")
                    }
                }
                builder.header("Cookie", cookieHeader)
                builder.post(body.toString().toRequestBody(JSON))

                client.newCall(builder.build()).execute().use { response ->
                    if (response.isSuccessful) ApiResult.Ok(Unit)
                    else ApiResult.Error(AuthError.Server)
                }
            }
        } catch (_: IOException) {
            ApiResult.Error(AuthError.Network)
        }
    }

    /** `POST /api/v1/auth/google` (Workers) or `POST /api/auth/mobile/google` (Express). */
    override suspend fun googleSignIn(idToken: String): ApiResult<AuthSession> =
        router.executeWithFailover(isIdempotent = true) { host, dialect ->
            val path = if (dialect == BackendDialect.WORKERS_V1) "/api/v1/auth/google" else "/api/auth/mobile/google"
            val body = JSONObject().put("idToken", idToken)
            val raw = post(host, path, body, isSignIn = true, dialect = dialect)
            if (raw is Raw.Fail && raw.statusCode == 404 && dialect == BackendDialect.WORKERS_V1) {
                post(host, "/api/auth/mobile/google", body, isSignIn = true, dialect = BackendDialect.EXPRESS_LEGACY)
                    .toSessionResult(host, BackendDialect.EXPRESS_LEGACY)
            } else {
                raw.toSessionResult(host, dialect)
            }
        }

    /** `POST /api/auth/mobile/link`. */
    override suspend fun linkGoogle(idToken: String, sessionToken: String): ApiResult<Unit> =
        router.executeWithFailover(isIdempotent = true) { host, dialect ->
            val raw = post(
                host = host,
                path = "/api/auth/mobile/link",
                body = JSONObject().put("idToken", idToken),
                token = sessionToken,
                dialect = dialect,
            )
            when (raw) {
                is Raw.Fail -> ApiResult.Error(raw.error)
                is Raw.Ok -> {
                    val linked = try {
                        JSONObject(raw.body).optJSONObject("data")?.optBoolean("linked") == true
                    } catch (_: JSONException) {
                        false
                    }
                    if (linked) ApiResult.Ok(Unit) else ApiResult.Error(AuthError.Server)
                }
            }
        }

    /** `GET /api/auth/mobile/config` or `/api/v1/auth/config`. */
    override suspend fun googleClientId(): ApiResult<String?> = withContext(Dispatchers.IO) {
        try {
            router.executeWithFailover(isIdempotent = true) { host, _ ->
                val request = Request.Builder()
                    .url(host.trimEnd('/') + "/api/auth/mobile/config")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.code == 404) {
                        // Workers may omit config probe; fallback cleanly to null (BuildConfig will supply it)
                        ApiResult.Ok(null)
                    } else if (!response.isSuccessful) {
                        ApiResult.Error(if (response.code >= 500) AuthError.Server else AuthError.Unknown)
                    } else {
                        val body = response.body?.string().orEmpty()
                        val id = try {
                            val data = JSONObject(body).optJSONObject("data")
                            data?.optString("googleClientId")
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() }
                                ?: data?.optString("google_client_id")?.trim()?.takeIf { it.isNotEmpty() }
                        } catch (_: JSONException) {
                            null
                        }
                        ApiResult.Ok(id)
                    }
                }
            }
        } catch (_: IOException) {
            ApiResult.Error(AuthError.Network)
        }
    }

    override suspend fun forgotPassword(email: String): ApiResult<Unit> =
        router.executeWithFailover(isIdempotent = true) { host, dialect ->
            when (val raw = post(host, "/api/auth/forgot-password", JSONObject().put("email", email), dialect = dialect)) {
                is Raw.Ok -> ApiResult.Ok(Unit)
                is Raw.Fail -> ApiResult.Error(raw.error)
            }
        }

    override suspend fun resetPassword(token: String, password: String): ApiResult<Unit> =
        router.executeWithFailover(isIdempotent = false) { host, dialect ->
            val body = JSONObject().put("token", token).put("password", password)
            when (val raw = post(host, "/api/auth/reset-password", body, dialect = dialect)) {
                is Raw.Ok -> ApiResult.Ok(Unit)
                is Raw.Fail -> ApiResult.Error(raw.error)
            }
        }

    private sealed interface Raw {
        data class Ok(val body: String, val setCookies: List<String>) : Raw
        data class Fail(val error: AuthError, val statusCode: Int) : Raw
    }

    private suspend fun post(
        host: String,
        path: String,
        body: JSONObject,
        isSignIn: Boolean = false,
        isRegister: Boolean = false,
        token: String? = null,
        dialect: BackendDialect = BackendDialect.WORKERS_V1,
    ): Raw = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(host.trimEnd('/') + path)
            .header("Accept", "application/json")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .post(body.toString().toRequestBody(JSON))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val setCookies = response.headers.values("Set-Cookie")
                if (response.isSuccessful) {
                    Raw.Ok(text, setCookies)
                } else {
                    Raw.Fail(errorFor(response.code, text, isSignIn, isRegister), response.code)
                }
            }
        } catch (_: IOException) {
            Raw.Fail(AuthError.Network, 0)
        }
    }

    private fun Raw.toSessionResult(host: String, dialect: BackendDialect): ApiResult<AuthSession> = when (this) {
        is Raw.Fail -> ApiResult.Error(error)
        is Raw.Ok -> {
            val user = parseUser(body, host, dialect)
            val root = try { JSONObject(body) } catch (_: Exception) { null }
            val data = root?.optJSONObject("data")
            val tokensObj = data?.optJSONObject("tokens")

            val jsonAccessToken = tokensObj?.optString("accessToken")?.takeIf { it.isNotEmpty() }
            val jsonRefreshToken = tokensObj?.optString("refreshToken")?.takeIf { it.isNotEmpty() }
            val jsonExpiresIn = if (tokensObj != null && tokensObj.has("expiresIn")) tokensObj.optLong("expiresIn") else null

            val cookieAccessToken = captureSession(setCookies, SESSION_COOKIE)
            val cookieRefreshToken = captureSession(setCookies, REFRESH_COOKIE)

            val token = jsonAccessToken ?: cookieAccessToken
            val refreshToken = jsonRefreshToken ?: cookieRefreshToken

            when {
                user == null -> ApiResult.Error(AuthError.Server)
                token == null -> ApiResult.Error(AuthError.Server)
                else -> ApiResult.Ok(
                    AuthSession(
                        token = token,
                        user = user,
                        refreshToken = refreshToken,
                        expiresInSeconds = jsonExpiresIn,
                        backendFlavor = dialect.name,
                    ),
                )
            }
        }
    }

    private fun captureSession(setCookies: List<String>, cookieName: String): String? {
        for (cookie in setCookies) {
            val first = cookie.substringBefore(';').trim()
            val name = first.substringBefore('=', missingDelimiterValue = "")
            if (name != cookieName) continue
            val value = first.substringAfter('=', missingDelimiterValue = "").trim()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    private fun parseUser(body: String, host: String, dialect: BackendDialect): AuthUser? {
        return try {
            val root = JSONObject(body)
            if (!root.optBoolean("success", false)) return null
            val data = root.optJSONObject("data") ?: return null

            // Support both Express nested { data: { user: {...} } } and Workers flat { data: { id, email, ... } }
            val user = data.optJSONObject("user") ?: data
            val id = user.optString("id")
            val email = user.optString("email")
            if (id.isEmpty() || email.isEmpty()) return null

            val rawAvatar = user.optString("avatar").takeIf { it.isNotBlank() }
                ?: user.optString("avatarUrl").takeIf { it.isNotBlank() }
            val rawCover = user.optString("coverUrl").takeIf { it.isNotBlank() }
                ?: user.optString("cover_url").takeIf { it.isNotBlank() }

            AuthUser(
                id = id,
                email = email,
                name = user.optString("name"),
                role = user.optString("role", "customer"),
                avatar = VelnoxMediaUrlResolver.resolve(rawAvatar, host, dialect),
                coverUrl = VelnoxMediaUrlResolver.resolve(rawCover, host, dialect),
            )
        } catch (_: JSONException) {
            null
        } catch (_: NullPointerException) {
            null
        }
    }

    private fun errorFor(status: Int, body: String, isSignIn: Boolean, isRegister: Boolean): AuthError {
        val code = try {
            JSONObject(body).optJSONObject("error")?.optString("code").orEmpty()
        } catch (_: JSONException) {
            ""
        }

        return when {
            status == 401 && isSignIn -> AuthError.InvalidCredentials
            code == "INVALID_GOOGLE_TOKEN" || code == "EMAIL_NOT_VERIFIED" -> AuthError.InvalidProviderToken
            code == "GOOGLE_NOT_CONFIGURED" || code == "GOOGLE_UNAVAILABLE" -> AuthError.ProviderUnavailable
            code == "LINK_REQUIRED" -> AuthError.LinkRequired
            code == "EMAIL_MISMATCH" -> AuthError.EmailMismatch
            code == "IDENTITY_CONFLICT" -> AuthError.IdentityConflict
            status == 401 -> AuthError.Unauthorized
            status == 429 -> AuthError.RateLimited
            code == "EMAIL_EXISTS" -> AuthError.EmailExists
            code == "CONFLICT" && isRegister -> AuthError.EmailExists
            code == "ACCOUNT_DISABLED" || code == "ACCOUNT_SUSPENDED" || code == "FORBIDDEN" -> AuthError.AccountDisabled
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
        const val REFRESH_COOKIE = "velnox_refresh"

        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(BackendConfig.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(BackendConfig.READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(BackendConfig.WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
    }
}
