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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.core.preferences.AppLanguage
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleXs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxTheme

/**
 * Request a password reset.
 *
 * The response is deliberately uniform: the API answers the same way whether or
 * not the address is registered, so this screen can only ever say "if that address
 * has an account, a link is on its way". That is not a limitation of the UI, it is
 * the point — telling the user "no such account" would turn this form into an
 * account-enumeration oracle.
 */
@Composable
fun ForgotPasswordScreen(
    onSubmit: (email: String) -> Unit,
    isSubmitting: Boolean,
    errorRes: Int?,
    submitted: Boolean,
    onBackToLogin: () -> Unit,
    onEnterCode: () -> Unit,
    currentLanguage: AppLanguage? = null,
    onLanguageSelected: ((AppLanguage) -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableStateOf(false) }

    val emailError = CredentialRules.emailError(email)

    fun submit() {
        if (isSubmitting || submitted) return
        touched = true
        if (emailError != null) return
        focusManager.clearFocus()
        onSubmit(email.trim())
    }

    AuthScreenFrame {
        AuthBrandHeader(
            trailing = {
                LoginLanguageSelector(
                    currentLanguage = currentLanguage,
                    onLanguageSelected = onLanguageSelected,
                )
            },
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.forgot_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(20.dp))

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
                label = stringResource(R.string.login_email_label),
                error = if (touched) {
                    emailError?.let { stringResource(it.messageRes) }
                } else {
                    null
                },
            ) {
                VelnoxTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    placeholder = stringResource(R.string.login_email_placeholder),
                    isError = touched && emailError != null,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        focusManager.clearFocus()
                        submit()
                    },
                    enabled = !isSubmitting && !submitted,
                )
            }

            PrimaryButton(
                label = stringResource(
                    if (isSubmitting) {
                        R.string.forgot_sending
                    } else {
                        R.string.forgot_submit
                    }
                ),
                loading = isSubmitting,
                enabled = !submitted,
                onClick = { submit() },
            )
        }

        AnimatedVisibility(
            visible = submitted,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                SuccessBanner(stringResource(R.string.forgot_sent))
            }
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

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.forgot_back),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = VelnoxBrand,
                modifier = Modifier
                    .clip(SquircleXs)
                    .velnoxClickable(
                        enabled = !isSubmitting,
                        role = Role.Button,
                        onClick = onBackToLogin,
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.forgot_have_code),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(SquircleXs)
                    .velnoxClickable(
                        enabled = !isSubmitting,
                        role = Role.Button,
                        onClick = onEnterCode,
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Choose a new password with a reset token.
 *
 * The token comes from the link in the reset email. Deep-link handling is a later
 * concern; the form accepts the code so the flow is complete and testable today,
 * and it is the same call a deep link will make.
 */
@Composable
fun ResetPasswordScreen(
    token: String,
    onSubmit: (token: String, password: String) -> Unit,
    isSubmitting: Boolean,
    errorRes: Int?,
    submitted: Boolean,
    onBackToLogin: () -> Unit,
    currentLanguage: AppLanguage? = null,
    onLanguageSelected: ((AppLanguage) -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current

    var code by rememberSaveable(token) { mutableStateOf(token) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var passwordTouched by rememberSaveable { mutableStateOf(false) }
    var confirmTouched by rememberSaveable { mutableStateOf(false) }

    val passwordError = CredentialRules.passwordError(password)
    val confirmError = CredentialRules.confirmError(password, confirm)
    val strength = PasswordStrength.of(password)
    val needsCode = token.isBlank()

    fun submit() {
        if (isSubmitting || submitted) return
        passwordTouched = true
        confirmTouched = true
        if (passwordError != null || confirmError != null) return
        if (needsCode && code.isBlank()) return
        focusManager.clearFocus()
        onSubmit(code.trim(), password)
    }

    AuthScreenFrame {
        AuthBrandHeader(
            trailing = {
                LoginLanguageSelector(
                    currentLanguage = currentLanguage,
                    onLanguageSelected = onLanguageSelected,
                )
            },
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.reset_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SquircleLg)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (needsCode) {
                FieldGroup(
                    label = stringResource(R.string.reset_code_label),
                    error = null,
                ) {
                    VelnoxTextField(
                        value = code,
                        onValueChange = { code = it.trim() },
                        placeholder = stringResource(R.string.reset_code_placeholder),
                        isError = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                        enabled = !isSubmitting,
                    )
                }
            }

            FieldGroup(
                label = stringResource(R.string.reset_password_label),
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

            PrimaryButton(
                label = stringResource(
                    if (isSubmitting) {
                        R.string.reset_submitting
                    } else {
                        R.string.reset_submit
                    }
                ),
                loading = isSubmitting,
                enabled = !submitted,
                onClick = { submit() },
            )
        }

        AnimatedVisibility(
            visible = submitted,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                SuccessBanner(stringResource(R.string.reset_done))
            }
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

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.reset_back),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = VelnoxBrand,
                modifier = Modifier
                    .clip(SquircleXs)
                    .velnoxClickable(
                        enabled = !isSubmitting,
                        role = Role.Button,
                        onClick = onBackToLogin,
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

@Preview(name = "Forgot", showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun ForgotPasswordPreview() {
    VelnoxTheme(darkTheme = false) {
        ForgotPasswordScreen(
            onSubmit = {},
            isSubmitting = false,
            errorRes = null,
            submitted = false,
            onBackToLogin = {},
            onEnterCode = {},
        )
    }
}

@Preview(name = "Reset", showBackground = true, widthDp = 390, heightDp = 860)
@Composable
private fun ResetPasswordPreview() {
    VelnoxTheme(darkTheme = false) {
        ResetPasswordScreen(
            token = "preview-token",
            onSubmit = { _, _ -> },
            isSubmitting = false,
            errorRes = null,
            submitted = false,
            onBackToLogin = {},
        )
    }
}
