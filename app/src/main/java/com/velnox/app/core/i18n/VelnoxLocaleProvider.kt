package com.velnox.app.core.i18n

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.velnox.app.core.preferences.AppLanguage

/**
 * Applies the selected language to the composition immediately.
 *
 * `stringResource` resolves through [LocalResources], which in turn follows the
 * context and configuration. Providing a context created with the chosen locale
 * means every string in the tree changes on the same frame the preference is
 * written — no activity recreation, no restart, and it works on API 26+ rather
 * than only the API 33 per-app language API.
 */
@Composable
fun VelnoxLocaleProvider(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val configuration = remember(language, context) {
        Configuration(context.resources.configuration).apply {
            setLocale(language.locale)
            setLayoutDirection(language.locale)
        }
    }
    val localizedContext = remember(language, context) {
        context.createConfigurationContext(configuration)
    }

    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        LocalContext provides localizedContext,
        LocalResources provides localizedContext.resources,
    ) {
        content()
    }
}
