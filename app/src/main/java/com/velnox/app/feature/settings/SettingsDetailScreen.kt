@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import com.velnox.app.ui.components.velnoxClickable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.LoadStatus
import com.velnox.app.core.customer.ProfileUpdate
import com.velnox.app.core.customer.rememberCustomerRepository
import com.velnox.app.core.customer.userMessage
import com.velnox.app.core.preferences.AppLanguage
import com.velnox.app.core.preferences.LocalPreferences
import com.velnox.app.core.preferences.ThemeMode
import com.velnox.app.core.session.LocalSession
import com.velnox.app.core.session.SessionState
import com.velnox.app.feature.messages.MessagesState
import com.velnox.app.feature.profile.AddressBookState
import com.velnox.app.feature.profile.AddressFormValidator
import com.velnox.app.feature.profile.AddressTags
import com.velnox.app.feature.profile.AddressValidationResult
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
import kotlinx.coroutines.launch

/**
 * Renders the child page for a [SettingsPage]. Every page shares the standalone
 * frame (no bottom bar) and a single Back target.
 */
@Composable
fun SettingsDetailScreen(
    page: SettingsPage,
    onBack: () -> Unit,
    onOpenSupportChat: ((String) -> Unit)? = null,
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
            SettingsPage.ContactSupport -> ContactSupportPage(onOpenSupportChat = onOpenSupportChat)
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
    val repository = rememberCustomerRepository()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentAuthUser = (session.state as? SessionState.Authenticated)?.user

    var name by rememberSaveable(currentAuthUser?.name) {
        mutableStateOf(currentAuthUser?.name ?: "")
    }
    var phone by rememberSaveable { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // The phone number lives only on the server; the session does not carry it.
    LaunchedEffect(Unit) {
        when (val result = repository.call { api, token -> api.profile(token) }) {
            is CustomerResult.Ok -> if (phone.isEmpty()) phone = result.value.phone.orEmpty()
            is CustomerResult.Failed -> errorMessage = result.userMessage(context)
        }
    }

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
                placeholder = stringResource(R.string.settings_field_name),
                onValueChange = { name = it; saved = false; errorMessage = null },
            )
            LabeledField(
                label = stringResource(R.string.settings_field_email),
                value = currentAuthUser?.email.orEmpty(),
                placeholder = stringResource(R.string.settings_field_email),
                keyboardType = KeyboardType.Email,
                enabled = false,
                onValueChange = {},
            )
            LabeledField(
                label = stringResource(R.string.settings_field_phone),
                value = phone,
                placeholder = stringResource(R.string.settings_field_phone_placeholder),
                keyboardType = KeyboardType.Phone,
                onValueChange = { phone = it; saved = false; errorMessage = null },
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
        label = stringResource(if (saving) R.string.settings_saving else R.string.settings_save),
        onClick = {
            if (saving) {
                // A save is already in flight.
            } else if (name.isBlank()) {
                errorMessage = context.getString(R.string.address_validation_error)
            } else {
                saving = true
                errorMessage = null
                scope.launch {
                    val update = ProfileUpdate(name = name.trim(), phone = phone.trim())
                    when (val result = repository.call { api, token -> api.updateProfile(token, update) }) {
                        is CustomerResult.Ok -> {
                            // The email is not editable here, so the session keeps its own.
                            session.updateProfile(
                                name = result.value.name ?: update.name,
                                email = currentAuthUser?.email.orEmpty(),
                            )
                            saved = true
                        }

                        is CustomerResult.Failed -> errorMessage = result.userMessage(context)
                    }
                    saving = false
                }
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
                text = stringResource(R.string.settings_saved),
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
    val repository = rememberCustomerRepository()
    val context = LocalContext.current
    val authUser = (session.state as? SessionState.Authenticated)?.user
    var phone by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        when (val result = repository.call { api, token -> api.profile(token) }) {
            is CustomerResult.Ok -> phone = result.value.phone
            is CustomerResult.Failed -> errorMessage = result.userMessage(context)
        }
    }

    SectionCard {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            DetailLine(
                stringResource(R.string.settings_field_name),
                authUser?.name ?: stringResource(R.string.common_value_placeholder),
            )
            CardDivider()
            DetailLine(
                stringResource(R.string.settings_field_email),
                authUser?.email ?: stringResource(R.string.common_value_placeholder),
            )
            CardDivider()
            DetailLine(
                stringResource(R.string.settings_field_phone),
                phone ?: stringResource(R.string.common_value_placeholder),
            )
        }
    }
    if (errorMessage != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = errorMessage ?: "",
            style = Text2xs,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

// ----------------------------------------------------------------- addresses

@Composable
private fun AddressesPage() {
    val repository = rememberCustomerRepository()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAddress by remember { mutableStateOf<UserAddress?>(null) }
    var deletingAddressId by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val addresses = AddressBookState.addresses
    val status = AddressBookState.status

    // Always re-read on open: the website may have changed the book since the app did.
    LaunchedEffect(Unit) { AddressBookState.load(repository) }

    Text(
        text = stringResource(R.string.settings_addresses_title),
        style = TextXs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    if (addresses.isEmpty()) {
        SectionCard {
            when (status) {
                LoadStatus.Idle, LoadStatus.Loading -> Text(
                    text = stringResource(R.string.customer_loading),
                    style = TextXs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )

                LoadStatus.Failed -> Text(
                    text = stringResource(R.string.address_load_failed) + " · " +
                        stringResource(R.string.common_retry),
                    style = TextXs,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .velnoxClickable(role = Role.Button) {
                            scope.launch { AddressBookState.load(repository) }
                        }
                        .padding(16.dp),
                )

                LoadStatus.Ready -> Text(
                    text = stringResource(R.string.address_empty),
                    style = TextXs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
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
                        if (!addr.label.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(SquircleSm)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = localizedAddressTag(addr.label),
                                    style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                        }
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
                    if (addr.latitude != null && addr.longitude != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = VelnoxIcons.MapPin,
                                contentDescription = null,
                                tint = VelnoxBrand,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${stringResource(R.string.address_map_pinned_badge)} · ${formatCoordinates(addr.latitude, addr.longitude)}",
                                style = Text2xs,
                                color = VelnoxBrand,
                            )
                        }
                    }
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
                                        scope.launch {
                                            actionError = AddressBookState
                                                .setDefault(repository, addr)
                                                .failureText(context)
                                        }
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

    if (actionError != null) {
        Text(
            text = actionError ?: "",
            style = Text2xs,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 10.dp),
        )
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
                AddressBookState.save(repository, newAddr, existing = false)
                    .failureText(context)
                    .also { if (it == null) showAddDialog = false }
            },
        )
    }

    if (editingAddress != null) {
        AddressFormDialog(
            initialAddress = editingAddress,
            onDismiss = { editingAddress = null },
            onSave = { updated ->
                AddressBookState.save(repository, updated, existing = true)
                    .failureText(context)
                    .also { if (it == null) editingAddress = null }
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
                            scope.launch {
                                actionError = AddressBookState.delete(repository, targetId)
                                    .failureText(context)
                            }
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

private fun CustomerResult<Unit>.failureText(context: Context): String? =
    (this as? CustomerResult.Failed)?.userMessage(context)

@Composable
private fun localizedAddressTag(tag: String): String = when (tag.trim().lowercase()) {
    "home", "บ้าน" -> stringResource(R.string.address_tag_home)
    "office", "work", "ที่ทำงาน" -> stringResource(R.string.address_tag_office)
    "other", "อื่นๆ" -> stringResource(R.string.address_tag_other)
    else -> tag
}

/**
 * Enterprise-grade e-commerce address form with tag selection, 10-digit phone
 * validation, Line 1 & 2, subdistrict/district/province/postal code, default
 * toggle, and an interactive Map Pin Picker storing real lat/lng coordinates.
 *
 * [onSave] talks to the server and returns the text to show when it failed, or
 * null when the address was saved (the caller then closes the dialog).
 */
@Composable
private fun AddressFormDialog(
    initialAddress: UserAddress?,
    onDismiss: () -> Unit,
    onSave: suspend (UserAddress) -> String?,
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var tag by rememberSaveable {
        mutableStateOf(
            when (initialAddress?.label?.trim()?.lowercase()) {
                "office", "work" -> AddressTags.OFFICE
                "other" -> AddressTags.OTHER
                else -> AddressTags.HOME
            },
        )
    }
    var recipientName by rememberSaveable { mutableStateOf(initialAddress?.recipientName ?: "") }
    var phone by rememberSaveable { mutableStateOf(initialAddress?.phone ?: "") }
    var addressLine by rememberSaveable { mutableStateOf(initialAddress?.addressLine ?: "") }
    var addressLine2 by rememberSaveable { mutableStateOf(initialAddress?.addressLine2 ?: "") }
    var subDistrict by rememberSaveable { mutableStateOf(initialAddress?.subDistrict ?: "") }
    var district by rememberSaveable { mutableStateOf(initialAddress?.district ?: "") }
    var province by rememberSaveable { mutableStateOf(initialAddress?.province ?: "") }
    var postalCode by rememberSaveable { mutableStateOf(initialAddress?.postalCode ?: "") }
    var latitude by rememberSaveable { mutableStateOf(initialAddress?.latitude) }
    var longitude by rememberSaveable { mutableStateOf(initialAddress?.longitude) }
    var isDefault by rememberSaveable { mutableStateOf(initialAddress?.isDefault ?: false) }
    var showMapPicker by remember { mutableStateOf(false) }
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
                // Address Tag Selector (Home / Office / Other)
                Column {
                    Text(
                        text = stringResource(R.string.address_tag_label),
                        style = Text2xs,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val tags = listOf(
                            Triple(AddressTags.HOME, R.string.address_tag_home, VelnoxIcons.Home),
                            Triple(AddressTags.OFFICE, R.string.address_tag_office, VelnoxIcons.Briefcase),
                            Triple(AddressTags.OTHER, R.string.address_tag_other, VelnoxIcons.Tag),
                        )
                        tags.forEach { (value, labelRes, icon) ->
                            val selected = tag == value
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(SquircleSm)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) VelnoxBrand else MaterialTheme.colorScheme.outline,
                                        shape = SquircleSm,
                                    )
                                    .velnoxClickable(role = Role.RadioButton) { tag = value },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (selected) VelnoxBrand else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(labelRes),
                                    style = Text2xs.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    ),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                LabeledField(
                    label = stringResource(R.string.address_recipient_name),
                    value = recipientName,
                    placeholder = stringResource(R.string.address_recipient_name),
                    onValueChange = { recipientName = it; errorMessage = null },
                )
                LabeledField(
                    label = stringResource(R.string.address_phone),
                    value = phone,
                    placeholder = "0812345678",
                    keyboardType = KeyboardType.Phone,
                    isError = phone.isNotEmpty() && !AddressFormValidator.isValidPhone(phone),
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() || it == '-' || it == ' ' }.take(12)
                        phone = digits
                        errorMessage = null
                    },
                )
                LabeledField(
                    label = stringResource(R.string.address_line),
                    value = addressLine,
                    placeholder = "123/45 ซอย 8 ถนนสุขุมวิท",
                    onValueChange = { addressLine = it; errorMessage = null },
                )
                LabeledField(
                    label = stringResource(R.string.address_line2),
                    value = addressLine2,
                    placeholder = stringResource(R.string.address_line2_placeholder),
                    onValueChange = { addressLine2 = it; errorMessage = null },
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
                            imeAction = ImeAction.Done,
                            isError = postalCode.isNotEmpty() && !AddressFormValidator.isValidPostalCode(postalCode),
                            onValueChange = { raw ->
                                postalCode = raw.filter { it.isDigit() }.take(5)
                                errorMessage = null
                            },
                        )
                    }
                }

                // Interactive Map Pin Picker Launcher Card
                Column {
                    Text(
                        text = stringResource(R.string.address_map_section_title),
                        style = Text2xs,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    val hasPin = latitude != null && longitude != null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Squircle)
                            .background(
                                if (hasPin) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .border(
                                width = 1.dp,
                                color = if (hasPin) VelnoxBrand else MaterialTheme.colorScheme.outline,
                                shape = Squircle,
                            )
                            .velnoxClickable(role = Role.Button) { showMapPicker = true }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(SquircleSm)
                                .background(
                                    if (hasPin) VelnoxBrand else MaterialTheme.colorScheme.surface,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = VelnoxIcons.MapPin,
                                contentDescription = null,
                                tint = if (hasPin) Color.White else VelnoxBrand,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasPin) {
                                    stringResource(R.string.address_map_change_pin)
                                } else {
                                    stringResource(R.string.address_map_pick_button)
                                },
                                style = TextXs.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (hasPin) {
                                    formatCoordinates(latitude!!, longitude!!)
                                } else {
                                    stringResource(R.string.address_map_instruction)
                                },
                                style = Text2xs,
                                color = if (hasPin) VelnoxBrand else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = VelnoxIcons.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
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
                    if (saving) return@TextButton
                    when (
                        AddressFormValidator.validate(
                            recipientName = recipientName,
                            phone = phone,
                            addressLine = addressLine,
                            province = province,
                            postalCode = postalCode,
                        )
                    ) {
                        AddressValidationResult.MissingRequired -> {
                            errorMessage = context.getString(R.string.address_validation_error)
                        }

                        AddressValidationResult.InvalidPhone -> {
                            errorMessage = context.getString(R.string.address_phone_invalid)
                        }

                        AddressValidationResult.InvalidPostalCode -> {
                            errorMessage = context.getString(R.string.address_postal_invalid)
                        }

                        AddressValidationResult.Valid -> {
                            val newOrUpdated = (initialAddress ?: UserAddress(
                                id = "",
                                recipientName = "",
                                phone = "",
                                addressLine = "",
                                subDistrict = "",
                                district = "",
                                province = "",
                                postalCode = "",
                            )).copy(
                                label = tag,
                                recipientName = recipientName.trim(),
                                phone = AddressFormValidator.normalizePhone(phone),
                                addressLine = addressLine.trim(),
                                addressLine2 = addressLine2.trim().takeIf { it.isNotEmpty() },
                                subDistrict = subDistrict.trim(),
                                district = district.trim(),
                                province = province.trim(),
                                postalCode = postalCode.trim(),
                                latitude = latitude,
                                longitude = longitude,
                                isDefault = isDefault,
                            )
                            saving = true
                            scope.launch {
                                errorMessage = onSave(newOrUpdated)
                                saving = false
                            }
                        }
                    }
                },
            ) {
                Text(
                    text = if (saving) stringResource(R.string.settings_saving) else stringResource(R.string.address_save),
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

    if (showMapPicker) {
        MapPinPickerDialog(
            initialLatitude = latitude,
            initialLongitude = longitude,
            onDismiss = { showMapPicker = false },
            onConfirm = { lat, lng ->
                latitude = lat
                longitude = lng
                showMapPicker = false
            },
        )
    }
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

    // Search bar (overlay placeholder inside decorationBox so cursor stays at index 0)
    val searchInteraction = remember { MutableInteractionSource() }
    val searchFocused by searchInteraction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (searchFocused) VelnoxBrand else MaterialTheme.colorScheme.outline,
                shape = Squircle,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = VelnoxIcons.Search,
            contentDescription = null,
            tint = if (searchFocused) VelnoxBrand else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(VelnoxBrand),
            interactionSource = searchInteraction,
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_help_search_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
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
private fun ContactSupportPage(onOpenSupportChat: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val repository = rememberCustomerRepository()
    val scope = rememberCoroutineScope()
    var openingChat by remember { mutableStateOf(false) }
    var chatError by remember { mutableStateOf<String?>(null) }

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
            value = if (openingChat) stringResource(R.string.customer_loading) else stringResource(R.string.messages_contact_support),
            onClick = {
                if (!openingChat) {
                    openingChat = true
                    chatError = null
                    scope.launch {
                        when (val res = MessagesState.openSupportConversation(repository)) {
                            is CustomerResult.Ok -> onOpenSupportChat?.invoke(res.value.id)
                            is CustomerResult.Failed -> chatError = res.userMessage(context)
                        }
                        openingChat = false
                    }
                }
            },
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
    if (chatError != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = chatError.orEmpty(),
            style = Text2xs,
            color = MaterialTheme.colorScheme.error,
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

/**
 * Velnox-styled single-line text field used inside settings & address forms.
 * Uses [BasicTextField]'s `decorationBox` with a `Box(contentAlignment = Alignment.CenterStart)`
 * overlay so the blinking cursor is always anchored at index 0 over the placeholder.
 */
@Composable
private fun LabeledField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    masked: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        focused -> VelnoxBrand
        else -> MaterialTheme.colorScheme.outline
    }

    Column {
        Text(
            text = label,
            style = Text2xs,
            fontWeight = FontWeight.Medium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
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
                imeAction = imeAction,
            ),
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.65f }
                .clip(Squircle)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, borderColor, Squircle)
                .padding(horizontal = 12.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
