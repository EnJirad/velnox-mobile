package com.velnox.app.core.customer

import android.content.Context
import com.velnox.app.R

/**
 * The text a customer sees for a failed call: the server's own explanation when
 * it sent one for a rejected request, otherwise a localised sentence per cause.
 */
fun CustomerResult.Failed.userMessage(context: Context): String {
    if (error == CustomerError.Rejected && !message.isNullOrBlank()) return message
    return context.getString(
        when (error) {
            CustomerError.Network -> R.string.customer_error_network
            CustomerError.Unauthorized -> R.string.customer_error_unauthorized
            CustomerError.NotFound -> R.string.customer_error_not_found
            CustomerError.Rejected -> R.string.customer_error_rejected
            CustomerError.Server -> R.string.customer_error_server
        },
    )
}
