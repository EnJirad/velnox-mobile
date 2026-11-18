package com.velnox.app.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.components.velnoxToggleable
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Circle
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleXs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand

/**
 * Shared building blocks for Login, Register and Forgot password.
 *
 * One copy of each control so the three screens cannot drift: same field
 * geometry, same error placement, same 48dp touch targets, same semantic colours
 * in light and dark.
 */

@Composable
fun FieldGroup(
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
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun VelnoxTextField(
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
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        focused -> VelnoxBrand
        else -> MaterialTheme.colorScheme.outline
    }

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

/** Show/hide control for a password field. 44dp, labelled for TalkBack. */
@Composable
fun PasswordVisibilityToggle(
    visible: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.6f }
            .clip(SquircleXs)
            .velnoxClickable(enabled = enabled, role = Role.Button, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (visible) VelnoxIcons.EyeOff else VelnoxIcons.Lock,
            contentDescription = stringResource(
                if (visible) R.string.login_password_hide else R.string.login_password_show,
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A 20dp checkbox on a 48dp touch target, for the consent row. */
@Composable
fun ConsentToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    label: String,
) {
    val shape = SquircleXs
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .velnoxToggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
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
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun PrimaryButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val shape = Squircle
    val active = enabled && !loading
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary)
            .graphicsLayer { alpha = if (active) 1f else 0.6f }
            .velnoxClickable(enabled = active, role = Role.Button, onClick = onClick),
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
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
fun OrDivider() {
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

/**
 * A provider entry point.
 *
 * Present from Part 4 by request but inert: tapping it explains that the provider
 * arrives later instead of starting a flow that cannot finish. Part 6 gives it a
 * real handler.
 */
@Composable
fun SocialButton(
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
            .velnoxClickable(enabled = enabled, role = Role.Button, onClick = onClick)
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

/**
 * A failed request. Uses the error role, not the brand accent: the outcome is bad
 * news, and it must read the same in light and dark schemes.
 */
@Composable
fun ErrorBanner(errorRes: Int?) {
    if (errorRes == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.errorContainer)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.error), Squircle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = VelnoxIcons.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(errorRes),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/** A neutral, non-blocking note (e.g. "this provider arrives later"). */
@Composable
fun NoticeBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), Squircle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(Circle)
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A successful request (e.g. "check your email"). */
@Composable
fun SuccessBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(BorderStroke(1.dp, VelnoxBrand), Squircle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = VelnoxIcons.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Four bars that visibly change as the password improves. */
@Composable
fun PasswordStrengthMeter(
    strength: PasswordStrength,
    description: String,
) {
    if (strength == PasswordStrength.Empty) return

    val color = when (strength) {
        PasswordStrength.Empty -> MaterialTheme.colorScheme.outline
        PasswordStrength.Weak -> MaterialTheme.colorScheme.error
        PasswordStrength.Fair -> MaterialTheme.colorScheme.tertiary
        PasswordStrength.Good -> VelnoxBrand
        PasswordStrength.Strong -> VelnoxBrand
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(4) { index ->
                val active = index < strength.bars
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(SquircleXs)
                        .background(
                            if (active) color else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = TextXs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
