package com.velnox.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.velnox.app.core.i18n.VelnoxLocaleProvider
import com.velnox.app.core.preferences.LocalPreferences
import com.velnox.app.core.preferences.ThemeMode
import com.velnox.app.core.preferences.VelnoxPreferencesProvider
import com.velnox.app.core.session.LocalSession
import com.velnox.app.core.session.SessionState
import com.velnox.app.core.session.VelnoxSessionProvider
import com.velnox.app.feature.auth.LoginScreen
import com.velnox.app.feature.cart.CartScreen
import com.velnox.app.feature.cart.CartState
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
 * by a guest sets [pendingDestinationKey] and opens Login with a reason instead
 * of navigating. Because the current tab is left untouched, dismissing Login
 * returns to where the guest already was — there is no loop to fall into.
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

    val tab = AppTab.fromKey(tabKey)
    val loginReason = LoginReason.fromKey(loginReasonKey)
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
        // While the stored session is still being resolved we do not yet know
        // whether this is a guest. Prompting would be a guess, so the tap is
        // ignored for the few milliseconds the restore takes.
        if (sessionState is SessionState.Restoring) return
        pendingDestinationKey = destination
        loginReasonKey = reason.key
    }

    /** The only way a bottom-bar destination may change. */
    fun requestTab(next: AppTab) {
        val reason = AccessPolicy.reasonForTab(next)
        if (reason != null && !sessionState.isAuthenticated) {
            askForSignIn(reason, tabDestination(next))
            return
        }
        openTab(next)
    }

    /** The only way an overlay (including checkout) may open. */
    fun requestOverlay(key: String) {
        val reason = AccessPolicy.reasonForOverlay(key)
        if (reason != null && !sessionState.isAuthenticated) {
            askForSignIn(reason, overlayDestination(key))
            return
        }
        overlayKey = key
    }

    /** Guest declined to sign in: stay exactly where they were. */
    fun dismissLogin() {
        loginReasonKey = null
        pendingDestinationKey = null
    }

    /**
     * Sign-in finished. Part 2 uses the Login screen's pre-existing demo submit;
     * Part 3 swaps the credential call and this handler keeps working unchanged.
     */
    fun completeSignIn() {
        session.completeDemoSignIn()
        loginReasonKey = null
        val pending = pendingDestinationKey
        pendingDestinationKey = null
        if (pending != null) {
            when {
                pending.startsWith(TAB_DESTINATION) ->
                    openTab(AppTab.fromKey(pending.removePrefix(TAB_DESTINATION)))

                pending.startsWith(OVERLAY_DESTINATION) ->
                    overlayKey = pending.removePrefix(OVERLAY_DESTINATION)
            }
        }
    }

    fun signOut() {
        loginReasonKey = null
        pendingDestinationKey = null
        overlayKey = null
        tabKey = AppTab.Home.key
        scope.launch { session.signOut() }
    }

    BackHandler(
        enabled = loginReason != null || overlayKey != null || tab != AppTab.Home,
    ) {
        val current = overlayKey
        when {
            loginReason != null -> dismissLogin()
            current != null && current.startsWith("settings/") -> overlayKey = "settings"
            current != null && current.startsWith("category:") -> overlayKey = "category"
            current != null -> closeOverlay()
            tab != AppTab.Home -> openTab(AppTab.Home)
        }
    }

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
                LoginScreen(
                    onSignedIn = ::completeSignIn,
                    reason = reason,
                    onDismiss = ::dismissLogin,
                )
            } else {
                when (val overlay = overlayKey) {
                    null -> TabScaffold(selected = tab, onSelectTab = ::requestTab) {
                        tabStateHolder.SaveableStateProvider(tab.key) {
                            TabContent(
                                tab = tab,
                                cart = cart,
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

                    "category" -> CategoryScreen(
                        initialCategoryId = null,
                        onBack = ::closeOverlay,
                        onOpenListing = ::requestOverlay,
                        onOpenProduct = ::requestOverlay,
                    )

                    else -> {
                        if (overlay.startsWith("category:")) {
                            CategoryScreen(
                                initialCategoryId = overlay.removePrefix("category:"),
                                onBack = { overlayKey = "category" },
                                onOpenListing = ::requestOverlay,
                                onOpenProduct = ::requestOverlay,
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
                                    onBack = ::closeOverlay,
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
    }
}

@Composable
private fun TabContent(
    tab: AppTab,
    cart: CartState,
    onOpenOverlay: (String) -> Unit,
    onOpenTab: (AppTab) -> Unit,
) {
    when (tab) {
        AppTab.Home -> HomeScreen(
            onOpenCategoryHub = { onOpenOverlay("category") },
            onOpenListing = { onOpenOverlay("category:$it") },
            onOpenProduct = { onOpenOverlay("product:$it") },
        )

        AppTab.Messages -> MessagesScreen(
            onOpenThread = { onOpenOverlay("thread:$it") },
            onBrowseProducts = { onOpenTab(AppTab.Home) },
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

        AppTab.Profile -> ProfileScreen(
            onOpenSettings = { onOpenOverlay("settings") },
            onOpenEditProfile = { onOpenOverlay(SettingsPage.EditProfile.key) },
            onOpenPersonalInfo = { onOpenOverlay(SettingsPage.PersonalInfo.key) },
            onOpenAddresses = { onOpenOverlay(SettingsPage.Addresses.key) },
            onOpenOrders = { onOpenTab(AppTab.Orders) },
            onOpenWishlist = { onOpenOverlay("wishlist") },
            onOpenMessages = { onOpenTab(AppTab.Messages) },
        )
    }
}
