@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.ui.components.BrandBadge
import com.velnox.app.ui.components.BrandMark
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Circle
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleXs
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxBrandSoft
import com.velnox.app.ui.theme.VelnoxBrandSoftForeground
import com.velnox.app.ui.theme.VelnoxDestructive
import com.velnox.app.ui.theme.VelnoxPrimary
import com.velnox.app.ui.theme.VelnoxPrimaryForeground
import com.velnox.app.ui.theme.VelnoxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Velnox Login.
 *
 * Same visual language as Home: white surfaces on the slate page, dark primary
 * action, emerald accents, squircle controls and the shared type scale. The
 * screen keeps its original behaviour — inline validation, show/hide password,
 * remember-me — and now calls [onSignedIn] so navigation owns the transition to
 * Home. Authentication itself arrives in Phase 2.
 */
@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var rememberMe by rememberSaveable { mutableStateOf(true) }

    var emailTouched by rememberSaveable { mutableStateOf(false) }
    var passwordTouched by rememberSaveable { mutableStateOf(false) }

    var isSubmitting by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<LoginNotice?>(null) }

    val emailError = emailErrorRes(email)
    val passwordError = passwordErrorRes(password)

    fun submit() {
        if (isSubmitting) return
        emailTouched = true
        passwordTouched = true
        if (emailError != null || passwordError != null) {
            notice = null
            return
        }
        focusManager.clearFocus()
        isSubmitting = true
        notice = null
        // Phase 2 replaces this with the real sign-in call. Until then the
        // screen waits a moment, then hands control to navigation.
        scope.launch {
            delay(700L)
            isSubmitting = false
            notice = LoginNotice.Success
            delay(450L)
            onSignedIn()
        }
    }

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
                    error = if (emailTouched) emailError?.let { stringResource(it) } else null,
                ) {
                    VelnoxTextField(
                        value = email,
                        onValueChange = {
                            email = it.trim()
                            emailTouched = true
                            notice = null
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
                    error = if (passwordTouched) passwordError?.let { stringResource(it) } else null,
                ) {
                    VelnoxTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordTouched = true
                            notice = null
                        },
                        placeholder = stringResource(R.string.login_password_placeholder),
                        isError = passwordTouched && passwordError != null,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        onImeAction = { submit() },
                        enabled = !isSubmitting,
                        masked = !passwordVisible,
                        trailing = {
                            Text(
                                text = stringResource(
                                    if (passwordVisible) R.string.login_password_hide
                                    else R.string.login_password_show
                                ),
                                style = TextXs.copy(fontWeight = FontWeight.Medium),
                                color = if (passwordVisible) {
                                    VelnoxBrandSoftForeground
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .clip(SquircleXs)
                                    .clickable(
                                        enabled = !isSubmitting,
                                        role = Role.Button,
                                        onClick = { passwordVisible = !passwordVisible },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
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
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.login_remember),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(SquircleXs)
                            .clickable(enabled = !isSubmitting) { rememberMe = !rememberMe }
                            .padding(vertical = 8.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.login_forgot),
                        style = TextXs.copy(fontWeight = FontWeight.Medium),
                        color = VelnoxBrand,
                        modifier = Modifier
                            .clip(SquircleXs)
                            .clickable(enabled = !isSubmitting) {
                                notice = LoginNotice.ComingSoon
                            }
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

                Text(
                    text = stringResource(R.string.login_demo_hint),
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedVisibility(
                visible = notice != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                exit = fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    NoticeBanner(notice)
                }
            }

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
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f),
                    onClick = { notice = LoginNotice.ComingSoon },
                )
                SocialButton(
                    label = stringResource(R.string.login_continue_facebook),
                    glyph = "f",
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f),
                    onClick = { notice = LoginNotice.ComingSoon },
                )
            }

            Spacer(Modifier.height(24.dp))
            SignUpRow(
                enabled = !isSubmitting,
                onClick = { notice = LoginNotice.ComingSoon },
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.login_footer),
                style = Text2xs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------- primitives

@Composable
private fun FieldGroup(
    label: String,
    error: String?,
    field: @Composable () -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        field()
        AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = error.orEmpty(),
                style = TextXs,
                color = VelnoxDestructive,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun VelnoxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    enabled: Boolean,
    masked: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val shape = Squircle
    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> VelnoxDestructive
            focused -> VelnoxBrand
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(durationMillis = 160),
        label = "fieldBorder",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.6f }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, borderColor), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(VelnoxBrand),
                visualTransformation = if (masked) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { onImeAction() },
                    onDone = { onImeAction() },
                    onSend = { onImeAction() },
                ),
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

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
            .toggleable(
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
                .background(if (checked) VelnoxPrimary else Color.Transparent)
                .border(
                    BorderStroke(
                        1.dp,
                        if (checked) VelnoxPrimary else MaterialTheme.colorScheme.outline,
                    ),
                    shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = VelnoxIcons.Check,
                    contentDescription = null,
                    tint = VelnoxPrimaryForeground,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val shape = Squircle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(VelnoxPrimary)
            .clickable(enabled = !loading, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = VelnoxBrand,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = VelnoxPrimaryForeground,
        )
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = stringResource(R.string.login_divider),
            style = TextXs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun SocialButton(
    label: String,
    glyph: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = Squircle
    Row(
        modifier = modifier
            .height(48.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.6f }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = glyph,
            style = TextXs.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

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
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        )
    }
}

// --------------------------------------------------------------------- notice

private sealed interface LoginNotice {
    data object Success : LoginNotice
    data object ComingSoon : LoginNotice
}

@Composable
private fun NoticeBanner(notice: LoginNotice?) {
    val isSuccess = notice is LoginNotice.Success
    val background = if (isSuccess) VelnoxBrandSoft else MaterialTheme.colorScheme.surface
    val foreground = if (isSuccess) {
        VelnoxBrandSoftForeground
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val title = when (notice) {
        LoginNotice.Success -> stringResource(R.string.login_success_title)
        LoginNotice.ComingSoon -> stringResource(R.string.common_coming_soon)
        null -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Squircle)
            .background(background)
            .border(
                BorderStroke(
                    1.dp,
                    if (isSuccess) VelnoxBrand else MaterialTheme.colorScheme.outline,
                ),
                Squircle,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(Circle)
                .background(if (isSuccess) VelnoxBrand else VelnoxPrimary),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
        )
    }
}

// ----------------------------------------------------------------- validation

private fun emailErrorRes(email: String): Int? = when {
    email.isBlank() -> R.string.login_error_email_required
    !EMAIL_PATTERN.matches(email) -> R.string.login_error_email_invalid
    else -> null
}

private fun passwordErrorRes(password: String): Int? = when {
    password.isEmpty() -> R.string.login_error_password_required
    password.length < 6 -> R.string.login_error_password_short
    else -> null
}

private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

// --------------------------------------------------------------------- previews

@Preview(name = "Login", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LoginScreenPreview() {
    VelnoxTheme(darkTheme = false) {
        LoginScreen(onSignedIn = {})
    }
}

@Preview(name = "Login small", showBackground = true, widthDp = 320, heightDp = 760)
@Composable
private fun LoginScreenSmallPreview() {
    VelnoxTheme(darkTheme = false) {
        LoginScreen(onSignedIn = {})
    }
}

@Preview(name = "Login dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LoginScreenDarkPreview() {
    VelnoxTheme(darkTheme = true) {
        LoginScreen(onSignedIn = {})
    }
}
