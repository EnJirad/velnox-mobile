package com.velnox.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.core.auth.ApiResult
import com.velnox.app.core.auth.CredentialManagerGoogleSignIn
import com.velnox.app.core.auth.GoogleSignInOutcome
import com.velnox.app.core.auth.AuthError
import com.velnox.app.core.auth.SignInResult
import com.velnox.app.core.i18n.VelnoxLocaleProvider
import com.velnox.app.core.preferences.LocalPreferences
import com.velnox.app.core.preferences.ThemeMode
import com.velnox.app.core.preferences.VelnoxPreferencesProvider
import com.velnox.app.core.session.LocalSession
import com.velnox.app.core.session.SessionNotice
import com.velnox.app.core.session.SessionState
import com.velnox.app.core.session.VelnoxSessionProvider
import com.velnox.app.feature.auth.ForgotPasswordScreen
import com.velnox.app.feature.auth.LoginScreen
import com.velnox.app.feature.auth.RegisterScreen
import com.velnox.app.feature.auth.ResetPasswordScreen
import com.velnox.app.feature.cart.CartScreen
import com.velnox.app.feature.cart.CartState
import com.velnox.app.feature.catalog.ProductDetailScreen
import com.velnox.app.feature.home.ALL_CATEGORY
import com.velnox.app.feature.home.CategoryScreen
import com.velnox.app.feature.home.HomeScreen
import com.velnox.app.feature.messages.ConversationScreen
import com.velnox.app.feature.messages.MessagesScreen
import com.velnox.app.feature.orders.OrderDetailScreen
import com.velnox.app.feature.orders.OrdersScreen
import com.velnox.app.feature.profile.ProfileScreen
import com.velnox.app.feature.settings.SettingsDetailScreen
import com.velnox.app.feature.settings.SettingsPage
import com.velnox.app.feature.settings.SettingsScreen
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.VelnoxSpacing
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.VelnoxApplication
import com.velnox.app.ui.theme.VelnoxTheme
import kotlinx.coroutines.launch

/** Prefixes used to remember a destination across the Login gate. */
private const val TAB_DESTINATION = "tab:"
private const val OVERLAY_DESTINATION = "overlay:"

private fun tabDestination(tab: AppTab): String = TAB_DESTINATION + tab.key

private fun overlayDestination(key: String): String = OVERLAY_DESTINATION + key

/**
 * Application root.
 *
 * Preference → locale → theme → session is a strict chain: a language change
 * recomposes the tree with a localized context, an appearance change swaps the
 * colour scheme, and a session change only affects what the navigation gate
 * allows. None of them recreate the activity.
 */
@Composable
fun VelnoxApp() {
    VelnoxPreferencesProvider {
        val preferences = LocalPreferences.current.current

        VelnoxLocaleProvider(language = preferences.language) {
            val darkTheme = when (preferences.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            VelnoxTheme(darkTheme = darkTheme) {
                VelnoxSessionProvider {
                    VelnoxNavHost()
                }
            }
        }
    }
}

/**
 * Guest-first navigation.
 *
 * The app starts on Home for everyone. A stored session is resolved in the
 * background by [VelnoxSessionProvider], and while it is being resolved the shell
 * is already on screen — Login is never a startup screen.
 *
 * Every destination change goes through exactly two funnels, [requestTab] and
 * [requestOverlay]. They consult [AccessPolicy]; a protected destination opened
 * without an authenticated session sets [pendingDestinationKey] and opens Login
 * with a reason instead of navigating. Because the current tab is left untouched,
 * dismissing Login returns to where the guest already was — there is no loop to
 * fall into.
 *
 * Back handling lives in one handler so the order is unambiguous: close Login,
 * then unwind a child page to its hub, then close an overlay, then return to the
 * Home tab, then let the system exit.
 */
@Composable
private fun VelnoxNavHost() {
    val session = LocalSession.current
    val sessionState = session.state
    val scope = rememberCoroutineScope()

    var tabKey by rememberSaveable { mutableStateOf(AppTab.Home.key) }
    var overlayKey by rememberSaveable { mutableStateOf<String?>(null) }
    var loginReasonKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDestinationKey by rememberSaveable { mutableStateOf<String?>(null) }

    // Which authentication screen the gate is showing. Kept separate from
    // loginReasonKey because the reason belongs to the gate as a whole, while the
    // step (sign in / sign up / recovery) is what the user navigates between.
    var authStepKey by rememberSaveable { mutableStateOf<String?>(null) }
    var resetToken by rememberSaveable { mutableStateOf("") }
    var authSucceeded by rememberSaveable { mutableStateOf(false) }
    var forgotSent by rememberSaveable { mutableStateOf(false) }

    // Google Sign-In availability and progress. Availability is a deployment
    // fact (is there a client id at all?), so it is resolved once per gate.
    var googleAvailable by remember { mutableStateOf(false) }
    var googleBusy by remember { mutableStateOf(false) }

    // Holds the Google ID token only between LINK_REQUIRED and the password
    // sign-in that proves ownership of the account it collided with. Deliberately
    // `remember`, not `rememberSaveable`: an ID token is a bearer credential and
    // does not belong in the saved-instance-state bundle. A rotation loses it,
    // which costs the user one extra Google sheet and leaks nothing.
    var pendingGoogleIdToken by remember { mutableStateOf<String?>(null) }

    // Sign-in progress and the last failure, owned here so LoginScreen stays a
    // pure renderer of what the repository reported. `isSubmitting` is plain
    // remember: a rotation cancels the request coroutine, so restoring a stale
    // "true" would leave the button spinning forever.
    var isSubmitting by remember { mutableStateOf(false) }
    var signInErrorRes by rememberSaveable { mutableStateOf<Int?>(null) }

    val application = LocalContext.current.applicationContext as VelnoxApplication
    val activityContext = LocalContext.current
    val googleProvider = remember(activityContext) {
        CredentialManagerGoogleSignIn(
            context = activityContext,
            clientId = { application.googleClientId.clientId() },
        )
    }

    val tab = AppTab.fromKey(tabKey)
    val loginReason = LoginReason.fromKey(loginReasonKey)
    val authStep = AuthStep.fromKey(authStepKey)
    val cart = remember { CartState() }
    val tabStateHolder = rememberSaveableStateHolder()

    fun openTab(next: AppTab) {
        tabKey = next.key
        overlayKey = null
    }

    fun closeOverlay() {
        overlayKey = null
    }

    /** Open Login for [reason], remembering where the guest was trying to go. */
    fun askForSignIn(reason: LoginReason, destination: String) {
        // While the stored session is still being resolved the answer is unknown.
        // Prompting would be a guess, so the tap is ignored for the moment the
        // restore takes instead of opening Login on a false premise.
        if (sessionState is SessionState.Loading) return
        pendingDestinationKey = destination
        loginReasonKey = AccessPolicy.reasonFor(reason, sessionState).key
        authStepKey = AuthStep.Login.key
        signInErrorRes = null
        authSucceeded = false
        forgotSent = false
    }

    /** Leave the gate entirely and return to whatever was on screen before. */
    fun closeAuth() {
        loginReasonKey = null
        authStepKey = null
        pendingDestinationKey = null
        signInErrorRes = null
        authSucceeded = false
        forgotSent = false
        resetToken = ""
        // A token kept for linking is only useful inside this attempt at the gate.
        pendingGoogleIdToken = null
    }

    /** Move to another step inside the gate, keeping the pending destination. */
    fun goToStep(step: AuthStep) {
        authStepKey = step.key
        signInErrorRes = null
        forgotSent = false
    }

    /** The only way a bottom-bar destination may change. */
    fun requestTab(next: AppTab) {
        val reason = AccessPolicy.reasonForTab(next)
        if (reason != null && !AccessPolicy.isAllowed(sessionState)) {
            askForSignIn(reason, tabDestination(next))
            return
        }
        openTab(next)
    }

    /** The only way an overlay (including checkout) may open. */
    fun requestOverlay(key: String) {
        val reason = AccessPolicy.reasonForOverlay(key)
        if (reason != null && !AccessPolicy.isAllowed(sessionState)) {
            askForSignIn(reason, overlayDestination(key))
            return
        }
        overlayKey = key
    }

    /** Guest declined to sign in: stay exactly where they were. */
    fun dismissLogin() {
        closeAuth()
    }

    /** Applies the destination the guest was trying to reach before the gate. */
    fun applyPendingDestination() {
        val pending = pendingDestinationKey
        pendingDestinationKey = null
        if (pending == null) return
        when {
            pending.startsWith(TAB_DESTINATION) ->
                openTab(AppTab.fromKey(pending.removePrefix(TAB_DESTINATION)))

            pending.startsWith(OVERLAY_DESTINATION) ->
                overlayKey = pending.removePrefix(OVERLAY_DESTINATION)
        }
    }

    // Resolve whether Google can be offered whenever the gate opens. Cheap: the
    // client id comes from the build or from one cached config request.
    LaunchedEffect(loginReason) {
        googleAvailable = if (loginReason == null) false else googleProvider.isAvailable()
    }

    /**
     * Google Sign-In.
     *
     * The provider returns an ID token; the backend verifies it. Nothing about the
     * Google account is trusted until then, so a cancellation, an offline device
     * or a misconfigured build each leave the session exactly as it was.
     */
    fun startGoogleSignIn() {
        if (isSubmitting || googleBusy) return
        signInErrorRes = null
        googleBusy = true
        scope.launch {
            val outcome = googleProvider.signIn()
            when (outcome) {
                is GoogleSignInOutcome.Token -> {
                    val result = session.signInWithGoogle(outcome.idToken)
                    googleBusy = false
                    when (result) {
                        is SignInResult.Success -> {
                            authStepKey = null
                            loginReasonKey = null
                            applyPendingDestination()
                        }

                        is SignInResult.Failure -> {
                            // LINK_REQUIRED is not a dead end: an account exists, so
                            // send the user to password sign-in to prove ownership.
                            // The Google token is kept so the link can be completed
                            // straight after that sign-in succeeds — the backend
                            // will not accept it before.
                            if (result.error == AuthError.LinkRequired) {
                                pendingGoogleIdToken = outcome.idToken
                                authStepKey = AuthStep.Login.key
                            }
                            signInErrorRes = authMessage(result.error)
                        }
                    }
                }

                GoogleSignInOutcome.Cancelled -> {
                    googleBusy = false
                }

                GoogleSignInOutcome.NoAccount -> {
                    googleBusy = false
                    signInErrorRes = R.string.login_error_google_no_account
                }

                is GoogleSignInOutcome.NotConfigured -> {
                    googleBusy = false
                    googleAvailable = false
                    signInErrorRes = R.string.login_error_google_unavailable
                }

                GoogleSignInOutcome.Network -> {
                    googleBusy = false
                    signInErrorRes = R.string.login_error_network
                }

                is GoogleSignInOutcome.Failed -> {
                    googleBusy = false
                    signInErrorRes = R.string.login_error_google_failed
                }
            }
        }
    }

    /**
     * Real credential sign-in. The screen only reports what was typed; the
     * repository talks to the API and the controller owns the resulting state, so
     * nothing here can mark the user signed in without a server answer.
     */
    fun submitCredentials(email: String, password: String) {
        if (isSubmitting) return
        isSubmitting = true
        signInErrorRes = null
        scope.launch {
            val result = session.signIn(email, password)
            isSubmitting = false
            when (result) {
                is SignInResult.Success -> {
                    signInErrorRes = null
                    authStepKey = null
                    loginReasonKey = null
                    // Completing a link needs the session that was just created,
                    // so it runs after the sign-in, not before. Failures are
                    // reported through SessionNotice and never sign the user out.
                    pendingGoogleIdToken?.let { googleToken ->
                        pendingGoogleIdToken = null
                        session.linkGoogle(googleToken)
                    }
                    applyPendingDestination()
                }

                is SignInResult.Failure -> {
                    signInErrorRes = authMessage(result.error)
                }
            }
        }
    }

    /**
     * Registration. The API creates the customer and returns a session, so a
     * successful call leaves the app authenticated exactly like a sign-in and the
     * pending destination is applied the same way.
     */
    fun submitRegistration(name: String, email: String, password: String) {
        if (isSubmitting) return
        isSubmitting = true
        signInErrorRes = null
        scope.launch {
            val result = session.register(name, email, password)
            isSubmitting = false
            when (result) {
                is SignInResult.Success -> {
                    signInErrorRes = null
                    authStepKey = null
                    loginReasonKey = null
                    applyPendingDestination()
                }

                is SignInResult.Failure -> {
                    signInErrorRes = authMessage(result.error)
                }
            }
        }
    }

    /**
     * Request a reset link. The success flag is set on any non-error answer: the
     * API replies uniformly so this screen cannot reveal whether an address is
     * registered.
     */
    fun submitForgotPassword(email: String) {
        if (isSubmitting) return
        isSubmitting = true
        signInErrorRes = null
        scope.launch {
            val result = session.requestPasswordReset(email)
            isSubmitting = false
            when (result) {
                is ApiResult.Ok -> forgotSent = true
                is ApiResult.Error -> signInErrorRes = authMessage(result.error)
            }
        }
    }

    fun submitResetPassword(token: String, password: String) {
        if (isSubmitting) return
        isSubmitting = true
        signInErrorRes = null
        scope.launch {
            val result = session.resetPassword(token, password)
            isSubmitting = false
            when (result) {
                is ApiResult.Ok -> authSucceeded = true
                is ApiResult.Error -> signInErrorRes = authMessage(result.error)
            }
        }
    }

    fun signOut() {
        closeAuth()
        overlayKey = null
        tabKey = AppTab.Home.key
        scope.launch { session.signOut() }
    }

    BackHandler(
        enabled = loginReason != null || overlayKey != null || tab != AppTab.Home,
    ) {
        val current = overlayKey
        when {
            // Inside the gate, Back returns to sign-in before it leaves the gate:
            // a user who opened Sign up by mistake should not lose the reason
            // card or the destination they were trying to reach.
            loginReason != null && authStep != null && authStep != AuthStep.Login ->
                goToStep(AuthStep.Login)

            loginReason != null -> dismissLogin()
            current != null && current.startsWith("settings/") -> overlayKey = "settings"
            current != null && current.startsWith("category:") -> overlayKey = "category"
            current != null && current.startsWith("thread:") -> overlayKey = "messages"
            current != null -> closeOverlay()
            tab != AppTab.Home -> openTab(AppTab.Home)
        }
    }

    // A stored token that could not be checked is not a session: it is reported
    // through SessionNotice and the app keeps browsing as a guest.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Crossfade(
            targetState = loginReason,
            animationSpec = tween(durationMillis = 260),
            label = "sessionTransition",
        ) { reason ->
            if (reason != null) {
                when (authStep ?: AuthStep.Login) {
                    AuthStep.Login -> LoginScreen(
                        onSubmit = ::submitCredentials,
                        isSubmitting = isSubmitting,
                        errorRes = signInErrorRes,
                        onRegister = { goToStep(AuthStep.Register) },
                        onForgotPassword = { goToStep(AuthStep.Forgot) },
                        onGoogleSignIn = ::startGoogleSignIn,
                        googleAvailable = googleAvailable,
                        googleBusy = googleBusy,
                        reason = reason,
                        onDismiss = ::dismissLogin,
                    )

                    AuthStep.Register -> RegisterScreen(
                        onSubmit = ::submitRegistration,
                        isSubmitting = isSubmitting,
                        errorRes = signInErrorRes,
                        onBackToLogin = { goToStep(AuthStep.Login) },
                        onGoogleSignIn = ::startGoogleSignIn,
                        googleAvailable = googleAvailable,
                        googleBusy = googleBusy,
                    )

                    AuthStep.Forgot -> ForgotPasswordScreen(
                        onSubmit = ::submitForgotPassword,
                        isSubmitting = isSubmitting,
                        errorRes = signInErrorRes,
                        submitted = forgotSent,
                        onBackToLogin = { goToStep(AuthStep.Login) },
                        onEnterCode = { goToStep(AuthStep.Reset) },
                    )

                    AuthStep.Reset -> ResetPasswordScreen(
                        token = resetToken,
                        onSubmit = ::submitResetPassword,
                        isSubmitting = isSubmitting,
                        errorRes = signInErrorRes,
                        submitted = authSucceeded,
                        onBackToLogin = { goToStep(AuthStep.Login) },
                    )
                }
            } else {
                when (val overlay = overlayKey) {
                    null -> TabScaffold(selected = tab, onSelectTab = ::requestTab) {
                        tabStateHolder.SaveableStateProvider(tab.key) {
                            TabContent(
                                tab = tab,
                                cart = cart,
                                sessionState = sessionState,
                                onOpenOverlay = ::requestOverlay,
                                onOpenTab = ::requestTab,
                            )
                        }
                    }

                    "settings" -> SettingsScreen(
                        onBack = ::closeOverlay,
                        onOpen = { page -> requestOverlay(page.key) },
                        onSignOut = ::signOut,
                    )

                    "messages" -> MessagesScreen(
                        onOpenThread = { requestOverlay("thread:$it") },
                        onBrowseProducts = {
                            closeOverlay()
                            requestTab(AppTab.Home)
                        },
                        onBack = ::closeOverlay,
                    )

                    "category" -> CategoryScreen(
                        initialCategoryId = null,
                        onBack = ::closeOverlay,
                        onOpenListing = { slug -> requestOverlay("category:$slug") },
                        onOpenProduct = ::requestOverlay,
                    )

                    else -> {
                        if (overlay.startsWith("category:")) {
                            CategoryScreen(
                                initialCategoryId = overlay.removePrefix("category:"),
                                onBack = { overlayKey = "category" },
                                onOpenListing = { slug -> requestOverlay("category:$slug") },
                                onOpenProduct = ::requestOverlay,
                            )
                        } else if (overlay.startsWith("product:")) {
                            ProductDetailScreen(
                                productId = overlay.removePrefix("product:"),
                                onBack = ::closeOverlay,
                            )
                        } else {
                            val page = SettingsPage.fromKey(overlay)
                            if (page != null) {
                                // A child page always returns to the Settings hub; the
                                // hub's own Back is the one that closes the overlay.
                                SettingsDetailScreen(
                                    page = page,
                                    onBack = { overlayKey = "settings" },
                                )
                            } else if (overlay.startsWith("order:")) {
                                OrderDetailScreen(
                                    orderId = overlay.removePrefix("order:"),
                                    onBack = ::closeOverlay,
                                )
                            } else if (overlay.startsWith("thread:")) {
                                ConversationScreen(
                                    threadId = overlay.removePrefix("thread:"),
                                    onBack = { overlayKey = "messages" },
                                )
                            } else {
                                PlaceholderDestination(
                                    overlayKey = overlay,
                                    onBack = ::closeOverlay,
                                )
                            }
                        }
                    }
                }
            }
        }

        val activeNotice = session.notice
        if (sessionState is SessionState.Loading) {
            SessionStatusPill(
                textRes = R.string.session_checking,
                showSpinner = true,
            )
        } else if (activeNotice != null) {
            SessionStatusPill(
                textRes = noticeMessage(activeNotice),
                showSpinner = false,
                onClick = session::acknowledgeNotice,
            )
        }
    }
}

/**
 * A small, non-blocking status pill in the top corner.
 *
 * While a stored session is being verified it says so without covering the
 * catalogue — blocking the whole screen to ask "were you signed in?" would be the
 * startup screen this part set out to remove. If the API could not be reached it
 * says the session could not be verified, and tapping it dismisses the message.
 */
@Composable
private fun BoxScope.SessionStatusPill(
    textRes: Int,
    showSpinner: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = VelnoxSpacing.sm, end = PageGutter)
            .align(Alignment.TopEnd)
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (onClick != null) {
                    Modifier.velnoxClickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = VelnoxBrand,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * What the status pill says for each notice.
 *
 * Each notice is a different sentence to the user: "we could not verify your
 * session" is not "your Google account is now connected", and neither is "it could
 * not be connected".
 */
private fun noticeMessage(notice: SessionNotice): Int = when (notice) {
    SessionNotice.NetworkUnavailable -> R.string.session_unverified
    SessionNotice.GoogleLinked -> R.string.google_link_success
    SessionNotice.GoogleLinkFailed -> R.string.login_error_google_link_failed
}

/**
 * Maps a failed sign-in to a message resource.
 *
 * Network and server failures say "try again", credential failures say "check
 * your details": a user must never be told their password is wrong because the
 * phone was offline.
 */
private fun authMessage(error: AuthError): Int = when (error) {
    AuthError.InvalidCredentials -> R.string.login_error_invalid_credentials
    AuthError.Network -> R.string.login_error_network
    AuthError.RateLimited -> R.string.login_error_rate_limited
    AuthError.AccountDisabled -> R.string.login_error_account_disabled
    AuthError.Validation -> R.string.login_error_validation
    AuthError.EmailExists -> R.string.register_error_email_exists
    AuthError.EmailNotConfigured -> R.string.login_error_email_not_configured
    AuthError.InvalidResetToken -> R.string.login_error_invalid_reset_token
    AuthError.InvalidProviderToken -> R.string.login_error_google_failed
    AuthError.ProviderUnavailable -> R.string.login_error_google_unavailable
    AuthError.LinkRequired -> R.string.login_error_google_link_required
    AuthError.EmailMismatch -> R.string.login_error_google_email_mismatch
    AuthError.IdentityConflict -> R.string.login_error_google_identity_conflict
    else -> R.string.login_error_generic
}

@Composable
private fun TabContent(
    tab: AppTab,
    cart: CartState,
    sessionState: SessionState,
    onOpenOverlay: (String) -> Unit,
    onOpenTab: (AppTab) -> Unit,
) {
    when (tab) {
        AppTab.Home -> HomeScreen(
            onOpenCategoryHub = { onOpenOverlay("category") },
            onOpenListing = { categoryId ->
                // "All" is the hub, not a category of its own.
                if (categoryId == ALL_CATEGORY) {
                    onOpenOverlay("category")
                } else {
                    onOpenOverlay("category:$categoryId")
                }
            },
            onOpenProduct = { onOpenOverlay("product:$it") },
            onOpenMessages = { onOpenOverlay("messages") },
        )

        AppTab.Market -> CategoryScreen(
            initialCategoryId = null,
            onBack = null,
            onOpenListing = { slug -> onOpenOverlay("category:$slug") },
            onOpenProduct = { onOpenOverlay("product:$it") },
            titleOverride = stringResource(R.string.nav_market),
        )

        AppTab.Orders -> OrdersScreen(
            onOpenOrder = { onOpenOverlay("order:$it") },
            onBrowseProducts = { onOpenTab(AppTab.Home) },
        )

        AppTab.Cart -> CartScreen(
            state = cart,
            onCheckout = { onOpenOverlay("checkout") },
            onBrowseProducts = { onOpenTab(AppTab.Home) },
        )

        AppTab.Account -> {
            // The Account tab is protected by AccessPolicy, so a missing user
            // means the session ended between the tap and this frame. Render
            // nothing rather than an account that is not real.
            val user = sessionState.user
            if (user != null) {
                ProfileScreen(
                    user = user,
                    onOpenSettings = { onOpenOverlay("settings") },
                    onOpenEditProfile = { onOpenOverlay(SettingsPage.EditProfile.key) },
                    onOpenPersonalInfo = { onOpenOverlay(SettingsPage.PersonalInfo.key) },
                    onOpenAddresses = { onOpenOverlay(SettingsPage.Addresses.key) },
                    onOpenOrders = { onOpenTab(AppTab.Orders) },
                    onOpenWishlist = { onOpenOverlay("wishlist") },
                    onOpenMessages = { onOpenOverlay("messages") },
                )
            }
        }
    }
}
