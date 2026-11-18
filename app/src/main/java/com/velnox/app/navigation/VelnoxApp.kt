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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.velnox.app.AuthState
import com.velnox.app.core.i18n.VelnoxLocaleProvider
import com.velnox.app.core.preferences.LocalPreferences
import com.velnox.app.core.preferences.ThemeMode
import com.velnox.app.core.preferences.VelnoxPreferencesProvider
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

/**
 * Application root.
 *
 * Preference → locale → theme is a strict chain: a language change recomposes
 * the tree with a locale-swapped context; a theme change re-reads the
 * resolution (Light / Dark / System) from DataStore through the same flow.
 */
@Composable
fun VelnoxApp() {
    VelnoxPreferencesProvider { preferences ->
        VelnoxLocaleProvider(language = preferences.language) {
            val darkTheme = when (preferences.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            VelnoxTheme(darkTheme = darkTheme) {
                VelnoxNavHost()
            }
        }
    }
}

/**
 * Session and navigation state.
 *
 * The app starts in Guest mode: Home and browsing are visible without
 * authentication. Protected tabs (Orders, Cart, Profile, Messages) and
 * account-only actions redirect to Login.
 */
@Composable
private fun VelnoxNavHost() {
    var authState by rememberSaveable { mutableStateOf(AuthState.Guest.name) }
    var tabKey by rememberSaveable { mutableStateOf(AppTab.Home.key) }
    var overlayKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTabKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showLoginGate by remember { mutableStateOf(false) }

    val currentAuth = AuthState.entries.firstOrNull { it.name == authState } ?: AuthState.Guest
    val tab = AppTab.fromKey(tabKey)
    val cart = remember { CartState() }
    val tabStateHolder = rememberSaveableStateHolder()

    fun openTab(next: AppTab) {
        // Protected tabs require authentication.
        if (currentAuth == AuthState.Guest && next != AppTab.Home) {
            pendingTabKey = next.key
            showLoginGate = true
            return
        }
        tabKey = next.key
        overlayKey = null
    }

    fun closeOverlay() {
        overlayKey = null
    }

    fun onLoginSuccess() {
        authState = AuthState.Authenticated.name
        showLoginGate = false
        val pending = pendingTabKey
        pendingTabKey = null
        if (pending != null) {
            tabKey = pending
        } else {
            tabKey = AppTab.Home.key
        }
        overlayKey = null
    }

    fun onSignOut() {
        overlayKey = null
        tabKey = AppTab.Home.key
        pendingTabKey = null
        showLoginGate = false
        authState = AuthState.Guest.name
    }

    // Child page → Settings hub / category hub → tabs → Home tab.
    BackHandler(enabled = currentAuth == AuthState.Authenticated && (overlayKey != null || tab != AppTab.Home)) {
        val current = overlayKey
        when {
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
            targetState = showLoginGate,
            animationSpec = tween(durationMillis = 260),
            label = "sessionTransition",
        ) { loginVisible ->
            if (loginVisible) {
                LoginScreen(
                    onSignedIn = { onLoginSuccess() },
                )
            } else {
                when (val overlay = overlayKey) {
                    null -> TabScaffold(selected = tab, onSelectTab = ::openTab) {
                        tabStateHolder.SaveableStateProvider(tab.key) {
                            TabContent(
                                tab = tab,
                                cart = cart,
                                authState = currentAuth,
                                onOpenOverlay = { overlayKey = it },
                                onOpenTab = ::openTab,
                            )
                        }
                    }

                    "settings" -> SettingsScreen(
                        onBack = ::closeOverlay,
                        onOpen = { page -> overlayKey = page.key },
                        onSignOut = { onSignOut() },
                    )

                    "category" -> CategoryScreen(
                        initialCategoryId = null,
                        onBack = ::closeOverlay,
                        onOpenListing = { overlayKey = "category:$it" },
                        onOpenProduct = { overlayKey = "product:$it" },
                    )

                    else -> {
                        if (overlay.startsWith("category:")) {
                            CategoryScreen(
                                initialCategoryId = overlay.removePrefix("category:"),
                                onBack = { overlayKey = "category" },
                                onOpenListing = { overlayKey = "category:$it" },
                                onOpenProduct = { overlayKey = "product:$it" },
                            )
                        } else {
                            val page = SettingsPage.fromKey(overlay)
                            if (page != null) {
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
    authState: AuthState,
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
