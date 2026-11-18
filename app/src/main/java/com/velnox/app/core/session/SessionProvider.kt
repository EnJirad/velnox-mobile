package com.velnox.app.core.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.velnox.app.VelnoxApplication

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

    CompositionLocalProvider(LocalSession provides controller) {
        content()
    }
}
