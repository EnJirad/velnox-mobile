package com.velnox.app.core.customer

/**
 * The single door through which the app reaches [CustomerApi].
 *
 * It owns the two things every authenticated call needs and no screen should
 * repeat: the session's bearer token, and the reaction to a 401.
 *
 * - No token (a guest) → [CustomerError.Unauthorized] without touching the network.
 * - The server answers 401 → [onUnauthorized] runs (the session drops to
 *   `ExpiredSession`) and the failure is still returned to the caller.
 */
class CustomerRepository(
    private val api: CustomerApi,
    private val token: suspend () -> String?,
    private val onUnauthorized: suspend () -> Unit = {},
) {

    suspend fun <T> call(
        block: suspend (api: CustomerApi, token: String) -> CustomerResult<T>,
    ): CustomerResult<T> {
        val current = token() ?: return CustomerResult.Failed(CustomerError.Unauthorized)
        val result = block(api, current)
        if (result is CustomerResult.Failed && result.error == CustomerError.Unauthorized) {
            onUnauthorized()
        }
        return result
    }
}
