package com.velnox.app.core.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * Google Sign-In through Android Credential Manager.
 *
 * This is the flow Google currently supports. `GoogleSignInClient` (Play services
 * auth) is deprecated, and the older `GoogleSignInOptions` path is what its own
 * documentation now points away from.
 *
 * What this class does **not** do is decide that anyone is authenticated. It
 * returns the raw ID token; the backend verifies signature, issuer, audience,
 * expiry and subject before any session exists.
 *
 * [context] must be an Activity when [signIn] runs: the account sheet is a system
 * UI that needs a foreground owner.
 */
class CredentialManagerGoogleSignIn(
    private val context: Context,
    private val clientId: suspend () -> String?,
) : GoogleSignInProvider {

    override suspend fun isAvailable(): Boolean = clientId() != null

    override suspend fun signIn(): GoogleSignInOutcome {
        val serverClientId = clientId()
            ?: return GoogleSignInOutcome.NotConfigured(
                "No Google OAuth client id is configured for this build",
            )

        return try {
            val credentialManager = CredentialManager.create(context)

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setServerClientId(serverClientId)
                        // Show every account on the device, not only those that have
                        // already signed in to this app: a first-time user has none.
                        .setFilterByAuthorizedAccounts(false)
                        // Never sign someone in without an explicit choice.
                        .setAutoSelectEnabled(false)
                        .build(),
                )
                .build()

            val response = credentialManager.getCredential(context = context, request = request)
            val credential = response.credential

            if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return GoogleSignInOutcome.Failed(
                    "Unexpected credential type: ${credential.type}",
                )
            }

            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdToken.idToken
            if (idToken.isBlank()) {
                GoogleSignInOutcome.Failed("Google returned an empty id token")
            } else {
                GoogleSignInOutcome.Token(idToken)
            }
        } catch (_: GetCredentialCancellationException) {
            // The user closed the sheet. Not a failure; the UI says nothing.
            GoogleSignInOutcome.Cancelled
        } catch (_: NoCredentialException) {
            GoogleSignInOutcome.NoAccount
        } catch (e: GetCredentialProviderConfigurationException) {
            // Play services missing, or this package/SHA-1 is not registered in
            // the Google Cloud project. Deployment problem, not a user problem.
            GoogleSignInOutcome.NotConfigured(
                e.message ?: "Credential provider is not configured",
            )
        } catch (_: GetCredentialInterruptedException) {
            GoogleSignInOutcome.Failed("Sign-in was interrupted")
        } catch (_: GetCredentialUnsupportedException) {
            GoogleSignInOutcome.NotConfigured("Credential Manager is not supported here")
        } catch (_: GoogleIdTokenParsingException) {
            GoogleSignInOutcome.Failed("Google returned a malformed id token")
        } catch (e: GetCredentialException) {
            GoogleSignInOutcome.Failed(e.message ?: e.type)
        } catch (e: Exception) {
            GoogleSignInOutcome.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
