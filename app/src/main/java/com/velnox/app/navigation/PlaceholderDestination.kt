package com.velnox.app.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PreviewNotice
import com.velnox.app.ui.components.StandaloneScreen
import com.velnox.app.ui.icons.VelnoxIcons

/**
 * Honest destination for routes Phase 2 does not implement yet
 * (search, filters, product detail, wishlist, checkout, notifications).
 *
 * A real screen with a localized title and a preview notice — not a dead tap,
 * and not a pretending implementation.
 */
@Composable
fun PlaceholderDestination(
    overlayKey: String,
    onBack: () -> Unit,
) {
    val titleRes = when {
        overlayKey.startsWith("product:") -> R.string.placeholder_product
        overlayKey.startsWith("category:") -> R.string.placeholder_category
        overlayKey == "search" -> R.string.placeholder_search
        overlayKey == "notifications" -> R.string.placeholder_notifications
        overlayKey == "wishlist" -> R.string.placeholder_wishlist
        overlayKey == "checkout" -> R.string.placeholder_checkout
        else -> R.string.placeholder_generic
    }

    StandaloneScreen(
        title = stringResource(titleRes),
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
    ) {
        PreviewNotice(text = stringResource(R.string.common_preview_notice))
        Spacer(Modifier.height(8.dp))
        EmptyState(
            icon = VelnoxIcons.Sparkles,
            title = stringResource(titleRes),
            body = stringResource(R.string.placeholder_body),
        )
    }
}
