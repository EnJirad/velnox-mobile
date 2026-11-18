package com.velnox.app.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleXs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxTheme

/**
 * Velnox registration.
 *
 * Creates a customer account through `POST /api/auth/mobile/register` and signs in
 * with the session that call returns. Like Login, this screen only validates the
 * shape of the input and renders the caller's state; the API decides whether the
 * account exists, whether the email is taken and whether the password is
 * acceptable.
 *
 * Consent is required before the button activates: the account is created on the
 * server the moment it is pressed, so agreeing to the terms has to come first.
 */
@Composable
fun RegisterScreen(
    onSubmit: (name: String, email: String, password: String) -> Unit,
    isSubmitting: Boolean,
    errorRes: Int?,
    onBackToLogin: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var consent by rememberSaveable { mutableStateOf(false) }

    var nameTouched by rememberSaveable { mutableStateOf(false) }
    var emailTouched by rememberSaveable { mutableStateOf(false) }
    var passwordTouched by rememberSaveable { mutableStateOf(false) }
    var confirmTouched by rememberSaveable { mutableStateOf(false) }

    var showComingSoon by remember { mutableStateOf(false) }

    val nameError = CredentialRules.nameError(name)
    val emailError = CredentialRules.emailError(email)
    val passwordError = CredentialRules.passwordError(password)
    val confirmError = CredentialRules.confirmError(password, confirm)
    val strength = PasswordStrength.of(password)

    val canSubmit = !isSubmitting &&
        consent &&
        nameError == null &&
        emailError == null &&
        passwordError == null &&
        confirmError == null

    fun submit() {
        if (isSubmitting) return
        nameTouched = true
        emailTouched = true
        passwordTouched = true
        confirmTouched = true
        if (!canSubmit) return
        focusManager.clearFocus()
        showComingSoon = false
        onSubmit(name.trim(), email.trim(), password)
    }

    AuthScreenFrame {
        AuthBrandHeader()

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.register_eyebrow),
            style = TextXs.copy(fontWeight = FontWeight.Medium),
            color = VelnoxBrand,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.register_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.register_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 340.dp),
        )

        Spacer(Modifier.height(22.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SquircleLg)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FieldGroup(
                label = stringResource(R.string.register_name_label),
                error = if (nameTouched) {
                    nameError?.let { stringResource(it.messageRes) }
                } else {
                    null
                },
            ) {
                VelnoxTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.register_name_placeholder),
                    isError = nameTouched && nameError != null,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    enabled = !isSubmitting,
                )
            }

            FieldGroup(
                label = stringResource(R.string.login_email_label),
                error = if (emailTouched) {
                    emailError?.let { stringResource(it.messageRes) }
                } else {
                    null
                },
            ) {
                VelnoxTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    placeholder = stringResource(R.string.login_email_placeholder),
                    isError = emailTouched && emailError != null,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    enabled = !isSubmitting,
                )
            }

            FieldGroup(
                label = stringResource(R.string.register_password_label),
                error = if (passwordTouched) {
                    passwordError?.let { stringResource(it.messageRes) }
                } else {
                    null
                },
            ) {
                VelnoxTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = stringResource(R.string.register_password_placeholder),
                    isError = passwordTouched && passwordError != null,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    enabled = !isSubmitting,
                    masked = !passwordVisible,
                    trailing = {
                        PasswordVisibilityToggle(
                            visible = passwordVisible,
                            enabled = !isSubmitting,
                            onToggle = { passwordVisible = !passwordVisible },
                        )
                    },
                )
            }

            PasswordStrengthMeter(
                strength = strength,
                description = stringResource(
                    if (strength == PasswordStrength.Empty) {
                        R.string.register_strength_hint
                    } else {
                        strength.labelRes
                    },
                ),
            )

            FieldGroup(
                label = stringResource(R.string.register_confirm_label),
                error = if (confirmTouched) {
                    confirmError?.let { stringResource(it.messageRes) }
                } else {
                    null
                },
            ) {
                VelnoxTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    placeholder = stringResource(R.string.register_confirm_placeholder),
                    isError = confirmTouched && confirmError != null,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        focusManager.clearFocus()
                        submit()
                    },
                    enabled = !isSubmitting,
                    masked = !passwordVisible,
                )
            }

            ConsentToggle(
                checked = consent,
                onCheckedChange = { consent = it },
                enabled = !isSubmitting,
                label = stringResource(R.string.register_consent),
            )

            PrimaryButton(
                label = stringResource(
                    if (isSubmitting) {
                        R.string.register_submitting
                    } else {
                        R.string.register_submit
                    }
                ),
                loading = isSubmitting,
                enabled = canSubmit,
                onClick = { submit() },
            )
        }

        AnimatedVisibility(
            visible = errorRes != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(errorRes)
            }
        }

        AnimatedVisibility(
            visible = showComingSoon,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                NoticeBanner(stringResource(R.string.common_coming_soon))
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.register_have_account),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.register_sign_in),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = VelnoxBrand,
                modifier = Modifier
                    .clip(SquircleXs)
                    .velnoxClickable(
                        enabled = !isSubmitting,
                        onClick = onBackToLogin,
                    )
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(18.dp))
        OrDivider()
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SocialButton(
                label = stringResource(R.string.login_continue_google),
                glyph = "G",
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
                onClick = { showComingSoon = true },
            )
            SocialButton(
                label = stringResource(R.string.login_continue_line),
                glyph = "LINE",
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
                onClick = { showComingSoon = true },
            )
        }

        Spacer(Modifier.height(20.dp))
        AuthFooter()
    }
}

@Preview(name = "Register", showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun RegisterScreenPreview() {
    VelnoxTheme(darkTheme = false) {
        RegisterScreen(
            onSubmit = { _, _, _ -> },
            isSubmitting = false,
            errorRes = null,
            onBackToLogin = {},
        )
    }
}
