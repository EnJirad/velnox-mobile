package com.velnox.app.core.customer

import com.velnox.app.core.auth.RefreshResult
import com.velnox.app.core.auth.TokenRefresher
import com.velnox.app.core.session.SessionManager

/**
 * The single door through which the app reaches [CustomerApi].
 *
 * Responsibilities:
 * - Proactively refreshes near-expiry access tokens before making authenticated calls.
 * - Reactively attempts single-flight token refresh on 401 before giving up.
 * - Triggers [onUnauthorized] only when a token is unrecoverable.
 */
class CustomerRepository(
    private val api: CustomerApi,
    private val token: suspend () -> String?,
    private val onUnauthorized: suspend () -> Unit = {},
    private val tokenRefresher: TokenRefresher? = null,
    private val sessions: SessionManager? = null,
) {

    suspend fun <T> call(
        block: suspend (api: CustomerApi, token: String) -> CustomerResult<T>,
    ): CustomerResult<T> {
        // 1. Proactive pre-flight refresh if token is near/past expiry
        if (tokenRefresher != null && sessions?.shouldRefresh() == true) {
            tokenRefresher.refresh()
        }

        var current = token() ?: return CustomerResult.Failed(CustomerError.Unauthorized)
        var result = block(api, current)

        // 2. Reactive single-flight refresh on 401
        if (result is CustomerResult.Failed && result.error == CustomerError.Unauthorized) {
            if (tokenRefresher != null) {
                when (val refreshOutcome = tokenRefresher.refresh(current)) {
                    is RefreshResult.Success -> {
                        current = refreshOutcome.accessToken
                        result = block(api, current)
                        if (result is CustomerResult.Failed && result.error == CustomerError.Unauthorized) {
                            onUnauthorized()
                        }
                    }
                    is RefreshResult.TransientFailure -> {
                        // Keep failure result without logging user out on network failure
                    }
                    else -> {
                        onUnauthorized()
                    }
                }
            } else {
                onUnauthorized()
            }
        }
        return result
    }
}
