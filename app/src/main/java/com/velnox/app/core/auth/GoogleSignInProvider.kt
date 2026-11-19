package com.velnox.app.core.auth

/**
 * The outcome of asking Google for an identity token.
 *
 * Every failure the Credential Manager flow can produce is mapped to one of
 * these, because the UI has to say something different for each: "you cancelled"
 * is not "this build is misconfigured", and neither is "no internet".
 */
sealed interface GoogleSignInOutcome {

    /** Google returned an ID token. It is **not** yet proof of anything until the
     *  backend verifies it. */
    data class Token(val idToken: String) : GoogleSignInOutcome

    /** The user dismissed the sheet. Not an error; show nothing. */
    data object Cancelled : GoogleSignInOutcome

    /** No Google account is available on the device to choose from. */
    data object NoAccount : GoogleSignInOutcome

    /**
     * This build cannot use Google Sign-In at all: no OAuth client configured, or
     * the package/SHA-1 pair is not registered in the Google Cloud project.
     *
     * [detail] carries the technical reason for the log, never for the user.
     */
    data class NotConfigured(val detail: String) : GoogleSignInOutcome

    /** The device is offline, or Google Play services could not be reached. */
    data object Network : GoogleSignInOutcome

    /** Anything else, including a malformed credential response. */
    data class Failed(val detail: String) : GoogleSignInOutcome
}

/**
 * Asks the platform for a Google ID token.
 *
 * An interface so the flow can be exercised without Play services in tests;
 * [CredentialManagerGoogleSignIn] is the single production implementation.
 *
 * Whether the flow can be *offered* is not decided here: that belongs to
 * [GoogleClientIdSource], which knows where a client id comes from and can say
 * whether the answer was "none" or "could not ask".
 */
interface GoogleSignInProvider {

    /** Open the Google account sheet and return the outcome. Never throws. */
    suspend fun signIn(): GoogleSignInOutcome
}
