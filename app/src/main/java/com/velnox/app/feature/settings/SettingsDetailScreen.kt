@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import com.velnox.app.ui.components.velnoxClickable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
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
import com.velnox.app.core.session.LocalSession
import com.velnox.app.core.session.SessionState
import com.velnox.app.feature.profile.AddressBookState
import com.velnox.app.feature.profile.UserAddress
import com.velnox.app.ui.components.BrandBadge
import com.velnox.app.ui.components.CardDivider
import com.velnox.app.ui.components.ChoiceRow
import com.velnox.app.ui.components.DetailLine
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.PageTitle
import com.velnox.app.ui.components.PrimaryPill
import com.velnox.app.ui.components.SectionCard
import com.velnox.app.ui.components.SettingRow
import com.velnox.app.ui.components.StandaloneScreen
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand

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
            .velnoxClickable(role = Role.Switch) { onCheckedChange(!checked) }
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
                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                checkedTrackColor = MaterialTheme.colorScheme.secondary,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

// --------------------------------------------------------------- edit profile

@Composable
private fun EditProfilePage() {
    val session = LocalSession.current
    val currentAuthUser = (session.state as? SessionState.Authenticated)?.user

    var name by rememberSaveable(currentAuthUser?.name) {
        mutableStateOf(currentAuthUser?.name ?: "")
    }
    var handle by rememberSaveable { mutableStateOf("@somchai.j") }
    var email by rememberSaveable(currentAuthUser?.email) {
        mutableStateOf(currentAuthUser?.email ?: "")
    }
    var phone by rememberSaveable { mutableStateOf("081-234-5678") }
    var saved by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

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
                onValueChange = { name = it; saved = false; validationError = null },
            )
            LabeledField(
                label = stringResource(R.string.settings_field_handle),
                value = handle,
                placeholder = stringResource(R.string.profile_demo_handle),
                onValueChange = { handle = it; saved = false; validationError = null },
            )
            LabeledField(
                label = stringResource(R.string.settings_field_email),
                value = email,
                placeholder = stringResource(R.string.profile_demo_email),
                keyboardType = KeyboardType.Email,
                onValueChange = { email = it; saved = false; validationError = null },
            )
            LabeledField(
                label = stringResource(R.string.settings_field_phone),
                value = phone,
                placeholder = stringResource(R.string.settings_field_phone_placeholder),
                keyboardType = KeyboardType.Phone,
                onValueChange = { phone = it; saved = false; validationError = null },
            )
        }
    }
    if (validationError != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = validationError ?: "",
            style = Text2xs,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(14.dp))
    PrimaryPill(
        label = stringResource(R.string.settings_save),
        onClick = {
            if (name.isBlank()) {
                validationError = context.getString(R.string.address_validation_error)
            } else if (!email.contains("@") || !email.contains(".")) {
                validationError = context.getString(R.string.address_validation_error)
            } else {
                session.updateProfile(name = name.trim(), email = email.trim())
                saved = true
                validationError = null
            }
        },
    )
    if (saved) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .clip(Squircle)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = VelnoxIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.settings_saved_local),
                style = Text2xs,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// ------------------------------------------------------------ personal info

@Composable
private fun PersonalInfoPage() {
    val session = LocalSession.current
    val authUser = (session.state as? SessionState.Authenticated)?.user

    SectionCard {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            DetailLine(stringResource(R.string.settings_field_name), authUser?.name ?: stringResource(R.string.profile_demo_name))
            CardDivider()
            DetailLine(stringResource(R.string.settings_field_handle), stringResource(R.string.profile_demo_handle))
            CardDivider()
            DetailLine(stringResource(R.string.settings_field_email), authUser?.email ?: stringResource(R.string.profile_demo_email))
            CardDivider()
            DetailLine(
                stringResource(R.string.settings_field_phone),
                stringResource(R.string.settings_field_phone_placeholder),
            )
            CardDivider()
            DetailLine(
                stringResource(R.string.settings_dob),
                stringResource(R.string.common_value_placeholder),
            )
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
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAddress by remember { mutableStateOf<UserAddress?>(null) }
    var deletingAddressId by remember { mutableStateOf<String?>(null) }
    val addresses = AddressBookState.addresses

    Text(
        text = stringResource(R.string.settings_addresses_title),
        style = TextXs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    if (addresses.isEmpty()) {
        SectionCard {
            Text(
                text = stringResource(R.string.address_empty),
                style = TextXs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
    } else {
        addresses.forEach { addr ->
            SectionCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = addr.recipientName,
                            style = TextXs.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = addr.phone,
                            style = Text2xs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (addr.isDefault) {
                            BrandBadge(text = stringResource(R.string.settings_address_default))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = addr.fullAddressText,
                        style = TextXs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    )
                    Spacer(Modifier.height(10.dp))
                    CardDivider()
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!addr.isDefault) {
                            Text(
                                text = stringResource(R.string.address_set_default),
                                style = Text2xs.copy(fontWeight = FontWeight.Medium),
                                color = VelnoxBrand,
                                modifier = Modifier
                                    .velnoxClickable(role = Role.Button) {
                                        AddressBookState.setDefault(addr.id)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(R.string.address_edit),
                            style = Text2xs.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .velnoxClickable(role = Role.Button) {
                                    editingAddress = addr
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.address_delete),
                            style = Text2xs.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .velnoxClickable(role = Role.Button) {
                                    deletingAddressId = addr.id
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    SectionCard {
        SettingRow(
            icon = VelnoxIcons.Plus,
            title = stringResource(R.string.settings_address_add),
            showChevron = false,
            onClick = { showAddDialog = true },
        )
    }

    if (showAddDialog) {
        AddressFormDialog(
            initialAddress = null,
            onDismiss = { showAddDialog = false },
            onSave = { newAddr ->
                AddressBookState.add(newAddr)
                showAddDialog = false
            },
        )
    }

    if (editingAddress != null) {
        AddressFormDialog(
            initialAddress = editingAddress,
            onDismiss = { editingAddress = null },
            onSave = { updated ->
                AddressBookState.update(updated)
                editingAddress = null
            },
        )
    }

    if (deletingAddressId != null) {
        val targetId = deletingAddressId
        AlertDialog(
            onDismissRequest = { deletingAddressId = null },
            shape = SquircleLg,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = stringResource(R.string.address_delete),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.address_delete_confirm),
                    style = TextXs,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (targetId != null) {
                            AddressBookState.delete(targetId)
                        }
                        deletingAddressId = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.address_delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAddressId = null }) {
                    Text(
                        text = stringResource(R.string.common_close),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@Composable
private fun AddressFormDialog(
    initialAddress: UserAddress?,
    onDismiss: () -> Unit,
    onSave: (UserAddress) -> Unit,
) {
    var recipientName by rememberSaveable { mutableStateOf(initialAddress?.recipientName ?: "") }
    var phone by rememberSaveable { mutableStateOf(initialAddress?.phone ?: "") }
    var addressLine by rememberSaveable { mutableStateOf(initialAddress?.addressLine ?: "") }
    var subDistrict by rememberSaveable { mutableStateOf(initialAddress?.subDistrict ?: "") }
    var district by rememberSaveable { mutableStateOf(initialAddress?.district ?: "") }
    var province by rememberSaveable { mutableStateOf(initialAddress?.province ?: "") }
    var postalCode by rememberSaveable { mutableStateOf(initialAddress?.postalCode ?: "") }
    var isDefault by rememberSaveable { mutableStateOf(initialAddress?.isDefault ?: false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = SquircleLg,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = if (initialAddress != null) stringResource(R.string.address_edit) else stringResource(R.string.settings_address_add),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LabeledField(
                    label = stringResource(R.string.address_recipient_name),
                    value = recipientName,
                    placeholder = stringResource(R.string.profile_demo_name),
                    onValueChange = { recipientName = it; errorMessage = null },
                )
                LabeledField(
                    label = stringResource(R.string.address_phone),
                    value = phone,
                    placeholder = "081-234-5678",
                    keyboardType = KeyboardType.Phone,
                    onValueChange = { phone = it; errorMessage = null },
                )
                LabeledField(
                    label = stringResource(R.string.address_line),
                    value = addressLine,
                    placeholder = "123/45 ซอย 8 ถนนหลัก",
                    onValueChange = { addressLine = it; errorMessage = null },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        LabeledField(
                            label = stringResource(R.string.address_subdistrict),
                            value = subDistrict,
                            placeholder = "คลองเตย",
                            onValueChange = { subDistrict = it; errorMessage = null },
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        LabeledField(
                            label = stringResource(R.string.address_district),
                            value = district,
                            placeholder = "คลองเตย",
                            onValueChange = { district = it; errorMessage = null },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        LabeledField(
                            label = stringResource(R.string.address_province),
                            value = province,
                            placeholder = "กรุงเทพฯ",
                            onValueChange = { province = it; errorMessage = null },
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        LabeledField(
                            label = stringResource(R.string.address_postal_code),
                            value = postalCode,
                            placeholder = "10110",
                            keyboardType = KeyboardType.Number,
                            onValueChange = { postalCode = it; errorMessage = null },
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = isDefault,
                        onCheckedChange = { isDefault = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                            checkedTrackColor = MaterialTheme.colorScheme.secondary,
                        ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.address_set_default),
                        style = TextXs,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = Text2xs,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (recipientName.isBlank() || phone.isBlank() || addressLine.isBlank() ||
                        subDistrict.isBlank() || district.isBlank() || province.isBlank() || postalCode.isBlank() ||
                        phone.trim().length < 9
                    ) {
                        errorMessage = context.getString(R.string.address_validation_error)
                    } else {
                        val newOrUpdated = (initialAddress ?: UserAddress(
                            id = java.util.UUID.randomUUID().toString(),
                            recipientName = "",
                            phone = "",
                            addressLine = "",
                            subDistrict = "",
                            district = "",
                            province = "",
                            postalCode = "",
                        )).copy(
                            recipientName = recipientName.trim(),
                            phone = phone.trim(),
                            addressLine = addressLine.trim(),
                            subDistrict = subDistrict.trim(),
                            district = district.trim(),
                            province = province.trim(),
                            postalCode = postalCode.trim(),
                            isDefault = isDefault,
                        )
                        onSave(newOrUpdated)
                    }
                },
            ) {
                Text(
                    text = stringResource(R.string.address_save),
                    color = VelnoxBrand,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.common_close),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

// ------------------------------------------------------------------ password

enum class PasswordStrength(val labelRes: Int) {
    None(R.string.settings_password_strength_weak),
    Weak(R.string.settings_password_strength_weak),
    Medium(R.string.settings_password_strength_medium),
    Strong(R.string.settings_password_strength_strong),
}

fun calculatePasswordStrength(password: String): PasswordStrength {
    if (password.isEmpty()) return PasswordStrength.None
    var score = 0
    if (password.length >= 8) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { it.isLetter() }) score++
    if (password.any { !it.isLetterOrDigit() } || password.length >= 12) score++
    return when {
        score <= 1 -> PasswordStrength.Weak
        score <= 3 -> PasswordStrength.Medium
        else -> PasswordStrength.Strong
    }
}

@Composable
private fun PasswordPage() {
    var current by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val strength = remember(next) { calculatePasswordStrength(next) }

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
                placeholder = stringResource(R.string.settings_field_password_placeholder),
                masked = true,
                onValueChange = { current = it; errorMessage = null; success = false },
            )
            LabeledField(
                label = stringResource(R.string.settings_password_new),
                value = next,
                placeholder = stringResource(R.string.settings_field_password_placeholder),
                masked = true,
                onValueChange = { next = it; errorMessage = null; success = false },
            )

            if (next.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(strength.labelRes),
                        style = Text2xs.copy(fontWeight = FontWeight.Medium),
                        color = when (strength) {
                            PasswordStrength.Strong -> MaterialTheme.colorScheme.secondary
                            PasswordStrength.Medium -> VelnoxBrand
                            else -> MaterialTheme.colorScheme.error
                        },
                    )
                }
            }

            LabeledField(
                label = stringResource(R.string.settings_password_confirm),
                value = confirm,
                placeholder = stringResource(R.string.settings_field_password_placeholder),
                masked = true,
                onValueChange = { confirm = it; errorMessage = null; success = false },
            )
        }
    }

    if (errorMessage != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = errorMessage ?: "",
            style = Text2xs,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(Modifier.height(14.dp))
    PrimaryPill(
        label = stringResource(R.string.settings_save),
        onClick = {
            if (current.isBlank()) {
                errorMessage = context.getString(R.string.settings_password_err_current)
            } else if (next.length < 8 || !next.any { it.isDigit() } || !next.any { it.isLetter() }) {
                errorMessage = context.getString(R.string.settings_password_err_length)
            } else if (next != confirm) {
                errorMessage = context.getString(R.string.settings_password_err_mismatch)
            } else {
                errorMessage = null
                success = true
                current = ""
                next = ""
                confirm = ""
            }
        },
    )

    if (success) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .clip(Squircle)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = VelnoxIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.settings_password_success),
                style = Text2xs,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// ------------------------------------------------------------------ security

@Composable
private fun SecurityPage() {
    var twoFactorEnabled by rememberSaveable { mutableStateOf(false) }
    var otherSessionsActive by rememberSaveable { mutableStateOf(true) }
    var showLogoutNotice by remember { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.settings_security_two_factor)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_security_two_factor),
                    style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.settings_security_two_factor_body),
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = twoFactorEnabled,
                onCheckedChange = { twoFactorEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    checkedTrackColor = MaterialTheme.colorScheme.secondary,
                ),
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    SectionCard(title = stringResource(R.string.settings_security_sessions_active_title)) {
        SettingRow(
            icon = VelnoxIcons.Laptop,
            title = "Velnox Mobile (Android)",
            subtitle = stringResource(R.string.settings_security_session_current),
            value = "Online",
            showChevron = false,
            onClick = {},
        )
        if (otherSessionsActive) {
            CardDivider()
            SettingRow(
                icon = VelnoxIcons.Laptop,
                title = "Chrome (Windows 11)",
                subtitle = stringResource(R.string.settings_security_session_other),
                showChevron = false,
                onClick = {},
            )
            CardDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .velnoxClickable(role = Role.Button) {
                        otherSessionsActive = false
                        showLogoutNotice = true
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = VelnoxIcons.LogOut,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_security_logout_others),
                    style = TextXs.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showLogoutNotice) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .clip(Squircle)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = VelnoxIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.settings_security_logged_out_others),
                style = Text2xs,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.settings_security_demo),
        style = Text2xs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// --------------------------------------------------------------- help center

private data class FaqItem(
    val id: String,
    val category: String,
    val question: String,
    val answer: String,
)

private val faqArticles = listOf(
    FaqItem(
        id = "faq-1",
        category = "คำสั่งซื้อและการชำระเงิน",
        question = "ชำระเงินผ่าน PromptPay อย่างไร?",
        answer = "สามารถสแกน QR Code PromptPay ที่สร้างขึ้นในหน้าชำระเงินผ่านโมบายแบงก์กิ้งของทุกธนาคาร ยอดเงินจะตัดและระบบจะยืนยันคำสั่งซื้อโดยอัตโนมัติภายใน 1-2 นาที",
    ),
    FaqItem(
        id = "faq-2",
        category = "คำสั่งซื้อและการชำระเงิน",
        question = "สามารถยกเลิกคำสั่งซื้อได้เมื่อไหร่?",
        answer = "คุณสามารถยกเลิกคำสั่งซื้อได้ทันทีเมื่ออยู่ในสถานะ 'รอดำเนินการ' (Pending) หรือ 'กำลังจัดเตรียม' (Processing) ก่อนที่ทางร้านค้าจะเริ่มจัดส่งพัสดุ",
    ),
    FaqItem(
        id = "faq-3",
        category = "การจัดส่งสินค้า",
        question = "ระยะเวลาในการจัดส่งสินค้าใช้เวลากี่วัน?",
        answer = "Standard Delivery ใช้เวลาประมาณ 2-3 วันทำการ, Express Delivery ใช้เวลา 1-2 วันทำการ และ Economy Delivery ใช้เวลา 4-5 วันทำการ",
    ),
    FaqItem(
        id = "faq-4",
        category = "การจัดส่งสินค้า",
        question = "ตรวจสอบสถานะพัสดุได้อย่างไร?",
        answer = "ไปที่แท็บ 'คำสั่งซื้อ' เลือกดูรายละเอียดคำสั่งซื้อที่ต้องการ แล้วแตะปุ่ม 'คัดลอก' หมายเลขพัสดุเพื่อนำไปตรวจสอบบนระบบขนส่ง",
    ),
    FaqItem(
        id = "faq-5",
        category = "การคืนสินค้าและคืนเงิน",
        question = "นโยบายการคืนสินค้าเป็นอย่างไร?",
        answer = "สามารถส่งคำขอคืนสินค้าได้ภายใน 7 วันหลังจากได้รับพัสดุ ในกรณีสินค้าชำรุด เสียหาย หรือไม่ตรงตามรายละเอียดที่ระบุไว้บนแพลตฟอร์ม",
    ),
    FaqItem(
        id = "faq-6",
        category = "การคืนสินค้าและคืนเงิน",
        question = "ระยะเวลาในการคืนเงินใช้เวลากี่วัน?",
        answer = "สำหรับ PromptPay ยอดเงินจะคืนเข้าบัญชีเดิมภายใน 24 ชั่วโมงหลังร้านค้าอนุมัติ สำหรับบัตรเครดิตจะคืนวงเงินภายใน 7-14 วันทำการ",
    ),
)

@Composable
private fun HelpCenterPage() {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var expandedFaqIds by remember { mutableStateOf(setOf<String>()) }

    Text(
        text = stringResource(R.string.settings_help_center_body),
        style = TextXs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))

    // Search bar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, Squircle)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = VelnoxIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        if (searchQuery.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_help_search_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(VelnoxBrand),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(12.dp))

    val filteredFaqs = remember(searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) faqArticles
        else faqArticles.filter { it.question.lowercase().contains(q) || it.answer.lowercase().contains(q) || it.category.lowercase().contains(q) }
    }

    if (filteredFaqs.isEmpty()) {
        SectionCard {
            Text(
                text = "ไม่พบหัวข้อคำถามที่ค้นหา",
                style = TextXs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    } else {
        SectionCard {
            filteredFaqs.forEachIndexed { index, faq ->
                val isExpanded = expandedFaqIds.contains(faq.id)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .velnoxClickable(role = Role.Button) {
                            expandedFaqIds = if (isExpanded) expandedFaqIds - faq.id else expandedFaqIds + faq.id
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = faq.category,
                                style = Text2xs,
                                color = VelnoxBrand,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = faq.question,
                                style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = if (isExpanded) VelnoxIcons.ChevronUp else VelnoxIcons.ChevronDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    AnimatedVisibility(visible = isExpanded) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = faq.answer,
                                style = TextXs,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            )
                        }
                    }
                }
                if (index < filteredFaqs.lastIndex) {
                    CardDivider()
                }
            }
        }
    }
}

// ------------------------------------------------------------ contact support

@Composable
private fun ContactSupportPage() {
    val context = LocalContext.current
    var showChatDialog by remember { mutableStateOf(false) }

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
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:support@velnox.app")
                    putExtra(Intent.EXTRA_SUBJECT, "Velnox Customer Support Inquiry")
                }
                runCatching { context.startActivity(intent) }
            },
        )
        CardDivider()
        SettingRow(
            icon = VelnoxIcons.MessageCircle,
            title = stringResource(R.string.settings_contact_chat),
            value = "Live 24/7",
            onClick = { showChatDialog = true },
        )
        CardDivider()
        SettingRow(
            icon = VelnoxIcons.Headphones,
            title = stringResource(R.string.settings_contact_phone),
            value = "02-000-0000",
            onClick = {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:020000000"))
                runCatching { context.startActivity(intent) }
            },
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.common_preview_notice),
        style = Text2xs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (showChatDialog) {
        var inputMessage by remember { mutableStateOf("") }
        var chatLog by remember {
            mutableStateOf(
                listOf(
                    "เจ้าหน้าที่ Velnox" to "สวัสดีครับ ยินดีต้อนรับสู่ฝ่ายสนับสนุนลูกค้า Velnox มีสิ่งใดให้เราช่วยเหลือเรื่องคำสั่งซื้อหรือสินค้าวันนี้ครับ?",
                )
            )
        }

        AlertDialog(
            onDismissRequest = { showChatDialog = false },
            shape = SquircleLg,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(Squircle)
                            .background(MaterialTheme.colorScheme.secondary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_contact_live_agent),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        chatLog.forEach { (sender, msg) ->
                            val isAgent = sender.contains("เจ้าหน้าที่")
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(Squircle)
                                    .background(
                                        if (isAgent) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    )
                                    .padding(8.dp),
                            ) {
                                Text(
                                    text = sender,
                                    style = Text2xs.copy(fontWeight = FontWeight.Bold),
                                    color = if (isAgent) MaterialTheme.colorScheme.secondary else VelnoxBrand,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = msg,
                                    style = TextXs,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .clip(Squircle)
                                .border(1.dp, MaterialTheme.colorScheme.outline, Squircle)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (inputMessage.isEmpty()) {
                                Text(
                                    text = "พิมพ์ข้อความที่นี่...",
                                    style = TextXs,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            BasicTextField(
                                value = inputMessage,
                                onValueChange = { inputMessage = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(VelnoxBrand),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                if (inputMessage.isNotBlank()) {
                                    val sent = inputMessage.trim()
                                    inputMessage = ""
                                    chatLog = chatLog + ("คุณ" to sent) + ("เจ้าหน้าที่ Velnox" to "ได้รับข้อความ '$sent' เรียบร้อยแล้วครับ เจ้าหน้าที่กำลังตรวจสอบข้อมูลคำสั่งซื้อให้สักครู่ครับ")
                                }
                            },
                        ) {
                            Text(text = "ส่ง", color = VelnoxBrand, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChatDialog = false }) {
                    Text(text = stringResource(R.string.common_close), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
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
                .heightIn(min = 48.dp)
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
