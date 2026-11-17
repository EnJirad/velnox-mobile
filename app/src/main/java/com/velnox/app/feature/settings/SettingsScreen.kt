@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.core.preferences.LocalPreferences
import com.velnox.app.ui.components.CardDivider
import com.velnox.app.ui.components.DangerRow
import com.velnox.app.ui.components.SectionCard
import com.velnox.app.ui.components.SettingRow
import com.velnox.app.ui.components.StandaloneScreen
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxDestructive
import com.velnox.app.ui.theme.VelnoxPrimary

/**
 * Settings hub. Every entry opens a real screen; nothing here pretends to talk
 * to a server. Language and appearance write to DataStore immediately.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preferences = LocalPreferences.current.current
    var showSignOut by remember { mutableStateOf(false) }

    val versionName = rememberAppVersion()

    StandaloneScreen(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        SettingsSection(title = stringResource(R.string.settings_section_account)) {
            SettingRow(
                icon = VelnoxIcons.Pencil,
                title = stringResource(R.string.settings_edit_profile),
                onClick = { onOpen(SettingsPage.EditProfile) },
            )
            CardDivider()
            SettingRow(
                icon = VelnoxIcons.User,
                title = stringResource(R.string.settings_personal_info),
                onClick = { onOpen(SettingsPage.PersonalInfo) },
            )
            CardDivider()
            SettingRow(
                icon = VelnoxIcons.MapPin,
                title = stringResource(R.string.settings_addresses),
                onClick = { onOpen(SettingsPage.Addresses) },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_preferences)) {
            SettingRow(
                icon = VelnoxIcons.Globe,
                title = stringResource(R.string.settings_language),
                value = stringResource(preferences.language.labelRes),
                onClick = { onOpen(SettingsPage.Language) },
            )
            CardDivider()
            SettingRow(
                icon = VelnoxIcons.Moon,
                title = stringResource(R.string.settings_appearance),
                value = stringResource(preferences.themeMode.labelRes),
                onClick = { onOpen(SettingsPage.Appearance) },
            )
            CardDivider()
            SettingRow(
                icon = VelnoxIcons.Bell,
                title = stringResource(R.string.settings_notifications),
                onClick = { onOpen(SettingsPage.Notifications) },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_security)) {
            SettingRow(
                icon = VelnoxIcons.Lock,
                title = stringResource(R.string.settings_password),
                onClick = { onOpen(SettingsPage.Password) },
            )
            CardDivider()
            SettingRow(
                icon = VelnoxIcons.ShieldCheck,
                title = stringResource(R.string.settings_security),
                onClick = { onOpen(SettingsPage.Security) },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_support)) {
            SettingRow(
                icon = VelnoxIcons.HelpCircle,
                title = stringResource(R.string.settings_help_center),
                onClick = { onOpen(SettingsPage.HelpCenter) },
            )
            CardDivider()
            SettingRow(
                icon = VelnoxIcons.MessageCircle,
                title = stringResource(R.string.settings_contact_support),
                onClick = { onOpen(SettingsPage.ContactSupport) },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_legal)) {
            SettingRow(
                icon = VelnoxIcons.FileText,
                title = stringResource(R.string.settings_terms),
                onClick = { onOpen(SettingsPage.Terms) },
            )
            CardDivider()
            SettingRow(
                icon = VelnoxIcons.ShieldCheck,
                title = stringResource(R.string.settings_privacy),
                onClick = { onOpen(SettingsPage.Privacy) },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_about)) {
            SettingRow(
                icon = VelnoxIcons.Info,
                title = stringResource(R.string.settings_app_version),
                value = versionName,
                showChevron = false,
                onClick = {},
            )
            CardDivider()
            SettingRow(
                icon = VelnoxIcons.Sparkles,
                title = stringResource(R.string.settings_about),
                onClick = { onOpen(SettingsPage.About) },
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard {
            DangerRow(
                icon = VelnoxIcons.LogOut,
                title = stringResource(R.string.settings_sign_out),
                onClick = { showSignOut = true },
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.settings_sign_out_demo),
            style = TextXs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showSignOut) {
        AlertDialog(
            onDismissRequest = { showSignOut = false },
            shape = Squircle,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOut = false
                        onSignOut()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.settings_sign_out_confirm),
                        color = VelnoxDestructive,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOut = false }) {
                    Text(text = stringResource(R.string.common_close), color = VelnoxPrimary)
                }
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(12.dp))
    SectionCard(title = title) { content() }
}

/** Reads the installed versionName without enabling the BuildConfig feature. */
@Composable
fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember(versionName = context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
    }
}

/** Child destinations reached from Settings. */
enum class SettingsPage {
    EditProfile,
    PersonalInfo,
    Addresses,
    Language,
    Appearance,
    Notifications,
    Password,
    Security,
    HelpCenter,
    ContactSupport,
    Terms,
    Privacy,
    About;

    val key: String get() = "settings/${name.lowercase()}"

    companion object {
        fun fromKey(key: String?): SettingsPage? = when (key) {
            null -> null
            "settings" -> null
            else -> entries.firstOrNull { "settings/${it.name.lowercase()}" == key }
        }
    }
}

