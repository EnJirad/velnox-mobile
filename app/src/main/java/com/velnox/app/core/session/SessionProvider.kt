package com.velnox.app.core.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.velnox.app.VelnoxApplication
import com.velnox.app.core.auth.RestoreOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Session access for composables.
 *
 * The controller is process-scoped (see `VelnoxApplication`); this provider only
 * starts the one-time resolve and exposes the controller. Screens read
 * `LocalSession.current.state` and never construct their own session flags.
 */
val LocalSession = staticCompositionLocalOf<SessionController> {
    error("LocalSession is not provided. Wrap the tree in VelnoxSessionProvider.")
}

@Composable
fun VelnoxSessionProvider(content: @Composable () -> Unit) {
    val application = LocalContext.current.applicationContext as VelnoxApplication
    val controller = application.session

    // Runs after the first frame: Home paints without waiting on storage or the
    // network, and no Login screen can flash while the session is still unknown.
    LaunchedEffect(controller) {
        controller.restoreOnce()
    }

    // Silent background retry if session verification was unverified at launch (e.g. transient network offline)
    LaunchedEffect(controller.notice, controller.state) {
        if (controller.notice == SessionNotice.NetworkUnavailable && controller.state is SessionState.Guest) {
            while (isActive && controller.notice == SessionNotice.NetworkUnavailable && controller.state is SessionState.Guest) {
                delay(4_000L)
                val outcome = controller.retryRestore()
                if (outcome is RestoreOutcome.Active || outcome is RestoreOutcome.Expired || outcome is RestoreOutcome.NoSession) {
                    break
                }
            }
        }
    }

    CompositionLocalProvider(LocalSession provides controller) {
        content()
    }
}
