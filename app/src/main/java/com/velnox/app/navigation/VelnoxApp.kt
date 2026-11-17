package com.velnox.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.velnox.app.R
import com.velnox.app.feature.auth.LoginScreen
import com.velnox.app.feature.home.HomeScreen
import com.velnox.app.ui.theme.VelnoxTheme

/**
 * Application destinations.
 *
 * A typed, closed set rather than a string route table: Phase 1 only has Login
 * and Home, and this is exactly what the Phase 2 auth gate will observe. Each
 * destination carries a stable [key] so it can be persisted with rememberSaveable
 * without a custom Saver.
 */
sealed interface AppDestination {
    val key: String

    data object Login : AppDestination {
        override val key: String = "login"
    }

    data object Home : AppDestination {
        override val key: String = "home"
    }

    companion object {
        fun fromKey(key: String?): AppDestination = when (key) {
            Home.key -> Home
            else -> Login
        }
    }
}

/**
 * Phase 2 seam.
 *
 * Today a cold start always lands on Login. Phase 2 replaces this value with the
 * resolved auth state (unknown / signed in / signed out) from the auth
 * repository, and no screen has to change.
 */
val AppRouting: AppDestination = AppDestination.Login

@Composable
fun VelnoxApp() {
    VelnoxTheme {
        // A String keeps rememberSaveable honest: an arbitrary sealed object
        // cannot be written to the saved-state Bundle.
        var routeKey by rememberSaveable { mutableStateOf(AppRouting.key) }
        var signedIn by rememberSaveable { mutableStateOf(false) }
        var dialog by remember { mutableStateOf<AppDialog?>(null) }

        val destination = AppDestination.fromKey(routeKey)

        fun show(dialogValue: AppDialog) {
            dialog = dialogValue
        }

        // Home is only reachable once the demo flow has been completed.
        BackHandler(enabled = destination == AppDestination.Home) {
            routeKey = AppDestination.Login.key
            signedIn = false
            SignedInStateHolder.value = false
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Crossfade(
                targetState = destination,
                animationSpec = tween(durationMillis = 260),
                label = "appTransition",
            ) { target ->
                when (target) {
                    AppDestination.Login -> LoginScreen(
                        onSignedIn = {
                            // Demo gate only: no credentials are stored or sent.
                            signedIn = true
                            SignedInStateHolder.value = true
                            routeKey = AppDestination.Home.key
                        },
                    )

                    AppDestination.Home -> HomeScreen(
                        onOpenNotifications = { show(AppDialog.Notifications) },
                        onOpenFilter = { show(AppDialog.Search) },
                        onOpenCategory = { id -> show(AppDialog.Category(id)) },
                        onOpenProduct = { id -> show(AppDialog.Product(id)) },
                        onOpenTab = { id -> show(AppDialog.Tab(id)) },
                    )
                }
            }
        }

        dialog?.let { current ->
            VelnoxInfoDialog(
                title = current.title(),
                onDismiss = { dialog = null },
            )
        }

        // Keeps the compiler honest about the flag that Phase 2 will read.
        if (signedIn) {
            SignedInStateHolder.value = true
        }
    }
}

/** Minimal holder so later phases can read the demo auth flag without prop drilling. */
object SignedInStateHolder {
    var value: Boolean = false
}

private sealed interface AppDialog {
    data object Search : AppDialog
    data object Notifications : AppDialog
    data class Category(val id: String) : AppDialog
    data class Product(val id: String) : AppDialog
    data class Tab(val id: String) : AppDialog
}

@Composable
private fun AppDialog.title(): String = when (this) {
    AppDialog.Search -> stringResource(R.string.home_search_action)
    AppDialog.Notifications -> stringResource(R.string.home_notifications)
    is AppDialog.Category -> stringResource(R.string.nav_categories)
    is AppDialog.Product -> stringResource(R.string.home_products_title)
    is AppDialog.Tab -> when (id) {
        "categories" -> stringResource(R.string.nav_categories)
        "wishlist" -> stringResource(R.string.nav_wishlist)
        "profile" -> stringResource(R.string.nav_profile)
        else -> stringResource(R.string.nav_home)
    }
}
