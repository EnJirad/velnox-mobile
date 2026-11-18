package com.velnox.app.core.auth

import com.velnox.app.BuildConfig

/**
 * Resolves the **Web** OAuth client id that Google Sign-In needs as its
 * `serverClientId`.
 *
 * It has to be the Web client, not the Android one: the ID token Google mints has
 * `aud` equal to the `serverClientId`, and the backend compares `aud` against its
 * own configured client id. Using the Android client id here would produce tokens
 * the server rejects.
 *
 * Resolution order:
 *
 * 1. `BuildConfig.GOOGLE_WEB_CLIENT_ID` — baked in at build time with
 *    `-PvelnoxGoogleClientId=…`. Wins when present, so a release can be pinned.
 * 2. `GET /api/auth/mobile/config` — the deployment's own value. One source of
 *    truth, no rebuild needed when it changes.
 *
 * A client id is a public identifier, not a secret: it appears in every OAuth URL
 * and in the browser bundle. The client **secret** never leaves the server.
 *
 * When neither source yields a value the result is `null`, and the UI says Google
 * Sign-In is unavailable instead of opening a sheet that would fail.
 */
class GoogleClientIdSource(
    private val api: AuthApi,
    private val buildValue: String = BuildConfig.GOOGLE_WEB_CLIENT_ID,
) {

    private var cached: String? = null
    private var resolved = false

    suspend fun clientId(): String? {
        buildValue.trim().let { if (it.isNotEmpty()) return it }

        if (resolved) return cached

        cached = when (val result = api.googleClientId()) {
            is ApiResult.Ok -> result.value?.trim()?.takeIf { it.isNotEmpty() }
            is ApiResult.Error -> null
        }
        resolved = true
        return cached
    }

    /** Forget a failed lookup so a later attempt can retry (e.g. after reconnect). */
    fun invalidate() {
        cached = null
        resolved = false
    }
}
