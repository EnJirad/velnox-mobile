package com.velnox.app.feature.auth

/**
 * When the Google button may open the account sheet.
 *
 * The button has three separate reasons to be inert and they are not
 * interchangeable, so they are decided in one place instead of inline in the
 * layout:
 *
 * - [isSubmitting] — a password request is in flight; a second flow would race it
 *   for the same gate.
 * - [googleBusy] — the Google sheet is already open, or the token is in transit.
 * - [googleAvailable] — the deployment publishes no client id, so the sheet could
 *   only fail with `DEVELOPER_ERROR`. Showing the button greyed out is honest;
 *   opening a flow that cannot finish is not.
 *
 * Nothing here decides whether anyone is signed in: the backend does that.
 */
internal fun googleButtonEnabled(
    isSubmitting: Boolean,
    googleBusy: Boolean,
    googleAvailable: Boolean,
): Boolean = !isSubmitting && !googleBusy && googleAvailable
