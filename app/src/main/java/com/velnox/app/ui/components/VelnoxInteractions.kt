package com.velnox.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * The Velnox tap model: clickable semantics and role without a ripple.
 *
 * The brand chrome (cards, rows, chips, icon buttons) reports presses through the
 * resulting state change, not through a decorative ripple. The interaction source
 * is remembered per node so recomposition never restarts the gesture handler, and
 * [Role], [enabled] and the click label are preserved for TalkBack and keyboard
 * navigation. Material 3's own components (Switch, dialogs) keep their built-in
 * feedback.
 */
@Composable
fun Modifier.velnoxClickable(
    role: Role? = null,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick,
    )
}

/** [velnoxClickable] for selection controls such as checkboxes and rows. */
@Composable
fun Modifier.velnoxToggleable(
    value: Boolean,
    role: Role? = null,
    enabled: Boolean = true,
    onValueChange: (Boolean) -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return toggleable(
        value = value,
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        role = role,
        onValueChange = onValueChange,
    )
}
