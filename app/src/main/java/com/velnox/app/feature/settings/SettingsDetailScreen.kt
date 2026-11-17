@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.core.preferences.AppLanguage
import com.velnox.app.core.preferences.LocalPreferences
import com.velnox.app.core.preferences.ThemeMode
import com.velnox.app.ui.components.CardDivider
import com.velnox.app.ui.components.ChoiceRow
import com.velnox.app.ui.components.DetailLine
import com.velnox.app.ui.components.PageTitle
import com.velnox.app.ui.components.PrimaryPill
import com.velnox.app.ui.components.SectionCard
import com.velnox.app.ui.components.SettingRow
import com.velnox.app.ui.components.StandaloneScreen
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxBrandSoft
import com.velnox.app.ui.theme.VelnoxBrandSoftForeground
import com.velnox.app.ui.theme.VelnoxPrimaryForeground

/**
 * Renders the child page for a [SettingsPage]. Every page shares the standalone
 * frame (no bottom bar) and a single Back target.
 */
@Composable
fun SettingsDetailScreen(
    page: SettingsPage,
    onBack: () -> Unit,
) {
    val title = when (page) {
        SettingsPage.EditProfile -> stringResource(R.string.settings_edit_profile_title)
        SettingsPage.PersonalInfo -> stringResource(R.string.settings_personal_info_title)
        SettingsPage.Addresses -> stringResource(R.string.settings_addresses_title)
        SettingsPage.Language -> stringResource(R.string.settings_language_title)
        SettingsPage.Appearance -> stringResource(R.string.settings_appearance_title)
        SettingsPage.Notifications -> stringResource(R.string.settings_notifications_title)
        SettingsPage.Password -> stringResource(R.string.settings_password_title)
        SettingsPage.Security -> stringResource(R.string.settings_security_title)
        SettingsPage.HelpCenter -> stringResource(R.string.settings_help_center_title)
        SettingsPage.ContactSupport -> stringResource(R.string.settings_contact_title)
        SettingsPage.Terms -> stringResource(R.string.settings_terms_title)
        SettingsPage.Privacy -> stringResource(R.string.settings_privacy_title)
        SettingsPage.About -> stringResource(R.string.settings_about_title)
    }

    StandaloneScreen(title = title, onBack = onBack) {
        when (page) {
            SettingsPage.EditProfile -> EditProfilePage()
            SettingsPage.PersonalInfo -> PersonalInfoPage()
            SettingsPage.Addresses -> AddressesPage()
            SettingsPage.Language -> LanguagePage()
            SettingsPage.Appearance -> AppearancePage()
            SettingsPage.Notifications -> NotificationsPage()
            SettingsPage.Password -> PasswordPage()
            SettingsPage.Security -> SecurityPage()
            SettingsPage.HelpCenter -> HelpCenterPage()
            SettingsPage.ContactSupport -> ContactSupportPage()
            SettingsPage.Terms -> TextPage(
                body = stringResource(R.string.settings_terms_body),
                icon = VelnoxIcons.FileText,
            )
            SettingsPage.Privacy -> TextPage(
                body = stringResource(R.string.settings_privacy_body),
                icon = VelnoxIcons.ShieldCheck,
            )
            SettingsPage.About -> AboutPage()
        }
    }
}

// ------------------------------------------------------------------- language

@Composable
private fun LanguagePage() {
    val preferences = LocalPreferences.current
    val current = preferences.current.language

    Text(
        text = stringResource(R.string.settings_language_body),
        style = TextXs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    SectionCard {
        AppLanguage.entries.forEachIndexed { index, language ->
            ChoiceRow(
                label = stringResource(language.nativeLabelRes),
                supporting = stringResource(language.labelRes),
                selected = language == current,
                onClick = { preferences.setLanguage(language) },
            )
            if (index != AppLanguage.entries.lastIndex) CardDivider()
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.settings_language_current, stringResource(current.labelRes)),
        style = Text2xs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ----------------------------------------------------------------- appearance

@Composable
private fun AppearancePage() {
    val preferences = LocalPreferences.current
    val current = preferences.current.themeMode

    Text(
        text = stringResource(R.string.settings_appearance_body),
        style = TextXs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    SectionCard {
        ThemeMode.entries.forEachIndexed { index, mode ->
            ChoiceRow(
                label = stringResource(mode.labelRes),
                selected = mode == current,
                onClick = { preferences.setThemeMode(mode) },
            )
            if (index != ThemeMode.entries.lastIndex) CardDivider()
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.settings_appearance_current, stringResource(current.labelRes)),
        style = Text2xs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// -------------------------------------------------------------- notifications

@Composable
private fun NotificationsPage() {
    val preferences = LocalPreferences.current
    val current = preferences.current

    SectionCard {
        ToggleRow(
            title = stringResource(R.string.settings_notify_orders),
            body = stringResource(R.string.settings_notify_orders_body),
            checked = current.notifyOrderUpdates,
            onCheckedChange = preferences.setNotifyOrderUpdates,
        )
        CardDivider()
        ToggleRow(
            title = stringResource(R.string.settings_notify_promotions),
            body = stringResource(R.string.settings_notify_promotions_body),
            checked = current.notifyPromotions,
            onCheckedChange = preferences.setNotifyPromotions,
        )
        CardDivider()
        ToggleRow(
            title = stringResource(R.string.settings_notify_security),
            body = stringResource(R.string.settings_notify_security_body),
            checked = current.notifySecurity,
            onCheckedChange = preferences.setNotifySecurity,
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.settings_notifications_local_note),
        style = Text2xs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = Text2xs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VelnoxPrimaryForeground,
                checkedTrackColor = VelnoxBrand,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = MaterialTheme.colorScheme.outline,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

// --------------------------------------------------------------- edit profile

@Composable
private fun EditProfilePage() {
    var name by rememberSaveable { mutableStateOf("") }
    var handle by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.settings_edit_profile_body),
        style = TextXs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    SectionCard {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabeledField(
                label = stringResource(R.string.settings_field_name),
                value = name,
                placeholder = stringResource(R.string.profile_demo_name),
                onValueChange = { name = it; saved = false },
            )
            LabeledField(
                label = stringResource(R.string.settings_field_handle),
                value = handle,
                placeholder = stringResource(R.string.profile_demo_handle),
                onValueChange = { handle = it; saved = false },
            )
            LabeledField(
                label = stringResource(R.string.settings_field_email),
                value = email,
                placeholder = stringResource(R.string.profile_demo_email),
                keyboardType = KeyboardType.Email,
                onValueChange = { email = it; saved = false },
            )
            LabeledField(
                label = stringResource(R.string.settings_field_phone),
                value = phone,
                placeholder = "08X-XXX-XXXX",
                keyboardType = KeyboardType.Phone,
                onValueChange = { phone = it; saved = false },
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    PrimaryPill(
        label = stringResource(R.string.settings_save),
        onClick = { saved = true },
    )
    if (saved) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .clip(Squircle)
                .background(VelnoxBrandSoft)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = VelnoxIcons.Check,
                contentDescription = null,
                tint = VelnoxBrand,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.settings_saved_local),
                style = Text2xs,
                color = VelnoxBrandSoftForeground,
            )
        }
    }
}

// ------------------------------------------------------------ personal info

@Composable
private fun PersonalInfoPage() {
    SectionCard {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            DetailLine(stringResource(R.string.settings_field_name), stringResource(R.string.profile_demo_name))
            CardDivider()
            DetailLine(stringResource(R.string.settings_field_handle), stringResource(R.string.profile_demo_handle))
            CardDivider()
            DetailLine(stringResource(R.string.settings_field_email), stringResource(R.string.profile_demo_email))
            CardDivider()
            DetailLine(stringResource(R.string.settings_field_phone), "08X-XXX-XXXX")
            CardDivider()
            DetailLine(stringResource(R.string.settings_dob), "—")
            CardDivider()
            DetailLine(stringResource(R.string.settings_gender), stringResource(R.string.settings_gender_unspecified))
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.common_preview_notice),
        style = Text2xs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ----------------------------------------------------------------- addresses

@Composable
private fun AddressesPage() {
    SectionCard {
        SettingRow(
            icon = VelnoxIcons.MapPin,
            title = stringResource(R.string.settings_address_default),
            subtitle = stringResource(R.string.orders_demo_address),
            value = null,
            showChevron = false,
            onClick = {},
        )
        CardDivider()
        SettingRow(
            icon = VelnoxIcons.MapPin,
            title = stringResource(R.string.profile_address_secondary),
            subtitle = stringResource(R.string.orders_demo_address),
            showChevron = false,
            onClick = {},
        )
        CardDivider()
        SettingRow(
            icon = VelnoxIcons.Plus,
            title = stringResource(R.string.settings_address_add),
            showChevron = false,
            onClick = {},
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.common_preview_notice),
        style = Text2xs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ------------------------------------------------------------------ password

@Composable
private fun PasswordPage() {
    var current by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }

    Text(
        text = stringResource(R.string.settings_password_body),
        style = TextXs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    SectionCard {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabeledField(
                label = stringResource(R.string.settings_password_current),
                value = current,
                placeholder = "••••••",
                masked = true,
                onValueChange = { current = it },
            )
            LabeledField(
                label = stringResource(R.string.settings_password_new),
                value = next,
                placeholder = "••••••",
                masked = true,
                onValueChange = { next = it },
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    PrimaryPill(
        label = stringResource(R.string.settings_save),
        onClick = {},
    )
}

// ------------------------------------------------------------------ security

@Composable
private fun SecurityPage() {
    SectionCard {
        SettingRow(
            icon = VelnoxIcons.ShieldCheck,
            title = stringResource(R.string.settings_security_two_factor),
            subtitle = stringResource(R.string.settings_security_two_factor_body),
            onClick = {},
        )
        CardDivider()
        SettingRow(
            icon = VelnoxIcons.Laptop,
            title = stringResource(R.string.settings_security_sessions),
            subtitle = stringResource(R.string.settings_security_sessions_body),
            onClick = {},
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.settings_security_demo),
        style = Text2xs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// --------------------------------------------------------------- help center

@Composable
private fun HelpCenterPage() {
    Text(
        text = stringResource(R.string.settings_help_center_body),
        style = TextXs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    SectionCard {
        SettingRow(
            icon = VelnoxIcons.Package,
            title = stringResource(R.string.settings_help_orders),
            onClick = {},
        )
        CardDivider()
        SettingRow(
            icon = VelnoxIcons.Truck,
            title = stringResource(R.string.settings_help_shipping),
            onClick = {},
        )
        CardDivider()
        SettingRow(
            icon = VelnoxIcons.ShoppingBag,
            title = stringResource(R.string.settings_help_returns),
            onClick = {},
        )
    }
}

// ------------------------------------------------------------ contact support

@Composable
private fun ContactSupportPage() {
    Text(
        text = stringResource(R.string.settings_contact_body),
        style = TextXs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    SectionCard {
        SettingRow(
            icon = VelnoxIcons.FileText,
            title = stringResource(R.string.settings_contact_email),
            value = "support@velnox.app",
            onClick = {},
        )
        CardDivider()
        SettingRow(
            icon = VelnoxIcons.MessageCircle,
            title = stringResource(R.string.settings_contact_chat),
            onClick = {},
        )
        CardDivider()
        SettingRow(
            icon = VelnoxIcons.Headphones,
            title = stringResource(R.string.settings_contact_phone),
            value = "02-000-0000",
            onClick = {},
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.common_preview_notice),
        style = Text2xs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// --------------------------------------------------------------------- misc

@Composable
private fun TextPage(body: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SectionCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AboutPage() {
    val version = rememberAppVersion()
    Column {
        PageTitle(text = stringResource(R.string.settings_about_title))
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_about_build, version),
            style = Text2xs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        SectionCard {
            Text(
                text = stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** Velnox-styled single-line text field used inside settings forms. */
@Composable
private fun LabeledField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    masked: Boolean = false,
) {
    Column {
        Text(
            text = label,
            style = Text2xs,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(Squircle)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, Squircle)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(VelnoxBrand),
                visualTransformation = if (masked) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

