package com.velnox.app.core.auth

import com.velnox.app.BuildConfig

/**
 * Whether Google Sign-In can be offered, and if not, why.
 *
 * [ABSENT] and [UNREACHABLE] are deliberately different answers. "This deployment
 * has no client id" is a fact that will not change during this session; "the check
 * could not be completed" is not an answer at all, and treating it as one is what
 * left the button disabled after a single failed request.
 */
enum class GoogleClientIdStatus {
    /** A client id was found. */
    CONFIGURED,

    /** Every source answered, and none had a client id. */
    ABSENT,

    /**
     * The lookup failed (the API was unreachable, or Render was still waking up).
     *
     * Nothing was decided: the next [GoogleClientIdSource.lookup] asks again.
     */
    UNREACHABLE,
}

/** One answer from [GoogleClientIdSource]. [id] is `null` unless [status] is CONFIGURED. */
data class GoogleClientIdLookup(
    val id: String?,
    val status: GoogleClientIdStatus,
) {
    companion object {
        fun configured(id: String) = GoogleClientIdLookup(id, GoogleClientIdStatus.CONFIGURED)
        val absent = GoogleClientIdLookup(null, GoogleClientIdStatus.ABSENT)
        val unreachable = GoogleClientIdLookup(null, GoogleClientIdStatus.UNREACHABLE)
    }
}

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
 *    `-PvelnoxGoogleClientId=…`. Wins when present, so a release can be pinned and
 *    does not depend on the deployment at all.
 * 2. `GET /api/auth/mobile/config` — the deployment's own value. One source of
 *    truth, no rebuild needed when it changes.
 *
 * Only a definitive answer is remembered. A failed request is never cached: the
 * API may have been asleep (Render spins down when idle) and asking again later is
 * exactly what a retry should do. Caching the failure made a few seconds of bad
 * network look identical to "this build has no Google client", for the rest of the
 * process.
 *
 * A client id is a public identifier, not a secret: it appears in every OAuth URL
 * and in the browser bundle. The client **secret** never leaves the server.
 */
class GoogleClientIdSource(
    private val api: AuthApi,
    private val buildValue: String = BuildConfig.GOOGLE_WEB_CLIENT_ID,
) {

    /** The last definitive answer, or `null` when nothing has been decided yet. */
    private var answer: GoogleClientIdLookup? = null

    suspend fun lookup(): GoogleClientIdLookup {
        val baked = buildValue.trim()
        if (baked.isNotEmpty()) return GoogleClientIdLookup.configured(baked)

        answer?.let { return it }

        return when (val result = api.googleClientId()) {
            is ApiResult.Ok -> {
                val id = result.value?.trim()?.takeIf { it.isNotEmpty() }
                val decision = if (id == null) {
                    GoogleClientIdLookup.absent
                } else {
                    GoogleClientIdLookup.configured(id)
                }
                answer = decision
                decision
            }

            // Not an answer. Deliberately not stored, so the next attempt retries.
            is ApiResult.Error -> GoogleClientIdLookup.unreachable
        }
    }

    /** Just the value, for callers that only need it. Never throws. */
    suspend fun clientId(): String? = lookup().id

    /**
     * Forget the last answer so a later attempt asks again.
     *
     * Used by the explicit "try again" on the sign-in gate: an answer of "this
     * deployment has none" may have been true a minute ago and false now, and only
     * the user can say they want it checked again. Automatic checks deliberately do
     * not call this — they reuse a definitive answer instead of polling.
     */
    fun invalidate() {
        answer = null
    }
}
