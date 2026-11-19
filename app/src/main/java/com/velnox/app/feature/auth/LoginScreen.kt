package com.velnox.app.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.navigation.LoginReason
import com.velnox.app.ui.components.BrandBadge
import com.velnox.app.ui.components.BrandMark
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.components.velnoxToggleable
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleXs
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxTheme

/**
 * Velnox Login.
 *
 * The gate the app opens when a protected destination is requested. The screen is
 * a renderer: it validates the shape of what was typed, reports it to [onSubmit],
 * and displays [isSubmitting] / [errorRes] from the caller. It never decides that
 * sign-in succeeded — the API does, and the navigation gate reacts to the
 * resulting session state.
 *
 * Google Sign-In is real: the button asks the caller, which opens the Credential
 * Manager sheet and hands the ID token to the backend. It is disabled when the
 * deployment has no Google client configured, and [googleUnavailableRes] says
 * which of the two reasons applies — a client id that is definitively absent, or
 * a check that could not be completed and is worth retrying. LINE is still an
 * entry point only and says so when tapped.
 */
@Composable
fun LoginScreen(
    onSubmit: (email: String, password: String) -> Unit,
    isSubmitting: Boolean,
    errorRes: Int?,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onGoogleSignIn: () -> Unit,
    googleAvailable: Boolean,
    googleBusy: Boolean,
    googleUnavailableRes: Int? = null,
    onRetryGoogleCheck: () -> Unit = {},
    reason: LoginReason? = null,
    onDismiss: (() -> Unit)? = null,
    emailPrefill: String = "",
) {
    val focusManager = LocalFocusManager.current

    var email by rememberSaveable(emailPrefill) { mutableStateOf(emailPrefill) }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var rememberMe by rememberSaveable { mutableStateOf(true) }

    var emailTouched by rememberSaveable { mutableStateOf(false) }
    var passwordTouched by rememberSaveable { mutableStateOf(false) }

    var showComingSoon by remember { mutableStateOf(false) }

    val emailError = CredentialRules.emailError(email)
    val passwordError = CredentialRules.loginPasswordError(password)

    fun submit() {
        if (isSubmitting) return
        emailTouched = true
        passwordTouched = true
        if (emailError != null || passwordError != null) {
            showComingSoon = false
            return
        }
        focusManager.clearFocus()
        showComingSoon = false
        onSubmit(email.trim(), password)
    }

    AuthScreenFrame {
        AuthBrandHeader()

        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.login_eyebrow),
            style = TextXs.copy(fontWeight = FontWeight.Medium),
            color = VelnoxBrand,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 340.dp),
        )

        if (reason != null) {
            Spacer(Modifier.height(14.dp))
            SignInReasonCard(reason = reason)
        }

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
                label = stringResource(R.string.login_email_label),
                error = if (emailTouched) {
                    emailError?.let { stringResource(it.messageRes) }
                } else {
                    null
                },
            ) {
                VelnoxTextField(
                    value = email,
                    onValueChange = {
                        email = it.trim()
                        emailTouched = true
                        showComingSoon = false
                    },
                    placeholder = stringResource(R.string.login_email_placeholder),
                    isError = emailTouched && emailError != null,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    enabled = !isSubmitting,
                )
            }

            FieldGroup(
                label = stringResource(R.string.login_password_label),
                error = if (passwordTouched) {
                    passwordError?.let { stringResource(it.messageRes) }
                } else {
                    null
                },
            ) {
                VelnoxTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordTouched = true
                        showComingSoon = false
                    },
                    placeholder = stringResource(R.string.login_password_placeholder),
                    isError = passwordTouched && passwordError != null,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        focusManager.clearFocus()
                        submit()
                    },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RememberToggle(
                    checked = rememberMe,
                    onCheckedChange = { rememberMe = it },
                    enabled = !isSubmitting,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.login_remember),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(SquircleXs)
                        .velnoxClickable(enabled = !isSubmitting) { rememberMe = !rememberMe }
                        .padding(vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.login_forgot),
                    style = TextXs.copy(fontWeight = FontWeight.Medium),
                    color = VelnoxBrand,
                    modifier = Modifier
                        .clip(SquircleXs)
                        .velnoxClickable(
                            enabled = !isSubmitting,
                            role = Role.Button,
                            onClick = onForgotPassword,
                        )
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }

            PrimaryButton(
                label = stringResource(
                    if (isSubmitting) R.string.login_signing_in else R.string.login_submit
                ),
                loading = isSubmitting,
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
        SignUpRow(enabled = !isSubmitting, onClick = onRegister)

        Spacer(Modifier.height(20.dp))
        OrDivider()
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SocialButton(
                label = stringResource(R.string.login_continue_google),
                glyph = "G",
                // Inert while a request is in flight, and disabled outright when
                // the deployment has no Google client configured — tapping a
                // button that cannot work is worse than showing it greyed out.
                // See `googleButtonEnabled` for the three reasons, tested there.
                enabled = googleButtonEnabled(
                    isSubmitting = isSubmitting,
                    googleBusy = googleBusy,
                    googleAvailable = googleAvailable,
                ),
                modifier = Modifier.weight(1f),
                onClick = onGoogleSignIn,
            )
            SocialButton(
                label = stringResource(R.string.login_continue_line),
                glyph = "LINE",
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
                onClick = { showComingSoon = true },
            )
        }

        // Why Google cannot be offered, when it cannot. Tapping asks again: the
        // common failure is an API that was asleep or a network that dropped, not a
        // deployment that will never have a client id.
        val googleReason = googleUnavailableRes
        if (!googleAvailable && googleReason != null) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Squircle)
                    .velnoxClickable(role = Role.Button, onClick = onRetryGoogleCheck),
            ) {
                NoticeBanner(stringResource(googleReason))
            }
        }

        if (onDismiss != null) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(Squircle)
                    .velnoxClickable(
                        enabled = !isSubmitting,
                        role = Role.Button,
                        onClick = onDismiss,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.login_continue_browsing),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        AuthFooter()
    }
}

// ------------------------------------------------------------------- shared

/** Page frame shared by Login, Register and Forgot password. */
@Composable
fun AuthScreenFrame(content: @Composable () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val horizontal = when {
            maxWidth < 360.dp -> 16.dp
            maxWidth > 520.dp -> 32.dp
            else -> PageGutter
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .widthIn(max = 480.dp)
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontal)
                .padding(top = 12.dp, bottom = 24.dp),
        ) {
            content()
        }
    }
}

@Composable
fun AuthBrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(size = 36)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        BrandBadge(text = stringResource(R.string.login_brand_badge))
    }
}

@Composable
fun AuthFooter() {
    Text(
        text = stringResource(R.string.login_footer),
        style = Text2xs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Explains why Login appeared. One sentence, semantic colours only, so it reads
 * correctly in both light and dark schemes.
 */
@Composable
private fun SignInReasonCard(reason: LoginReason) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = VelnoxIcons.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = stringResource(R.string.auth_reason_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(reason.messageRes),
                style = TextXs,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** "Remember me" checkbox on a 48dp touch target. */
@Composable
private fun RememberToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    val shape = SquircleXs
    Box(
        modifier = Modifier
            .size(48.dp)
            .velnoxToggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.6f }
                .clip(shape)
                .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(
                    BorderStroke(
                        1.dp,
                        if (checked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
                    shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = VelnoxIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/** "No account? Sign up" — the entry to registration. */
@Composable
private fun SignUpRow(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.login_no_account),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.login_signup),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = VelnoxBrand,
            modifier = Modifier
                .graphicsLayer { alpha = if (enabled) 1f else 0.6f }
                .clip(SquircleXs)
                .velnoxClickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        )
    }
}

// --------------------------------------------------------------------- previews

@Preview(name = "Login", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LoginScreenPreview() {
    VelnoxTheme(darkTheme = false) {
        LoginScreen(
            onSubmit = { _, _ -> },
            isSubmitting = false,
            errorRes = null,
            onRegister = {},
            onForgotPassword = {},
            onGoogleSignIn = {},
            googleAvailable = true,
            googleBusy = false,
        )
    }
}

@Preview(name = "Login dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LoginScreenDarkPreview() {
    VelnoxTheme(darkTheme = true) {
        LoginScreen(
            onSubmit = { _, _ -> },
            isSubmitting = false,
            errorRes = R.string.login_error_invalid_credentials,
            onRegister = {},
            onForgotPassword = {},
            onGoogleSignIn = {},
            googleAvailable = false,
            googleBusy = false,
            reason = LoginReason.Cart,
        )
    }
}
