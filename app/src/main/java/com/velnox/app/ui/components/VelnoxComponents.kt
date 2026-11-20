package com.velnox.app.ui.components

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.SquircleXs
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand

/**
 * Shared Velnox layout scale (§9 spacing). Every screen derives spacing from
 * these steps so Home and Login keep the same rhythm.
 */
object VelnoxSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

/** Horizontal page gutter shared by every screen. */
val PageGutter = 20.dp

/** Maximum content width so tablets do not stretch a phone layout. */
val ContentMaxWidth = 560.dp

// ------------------------------------------------------------------ brand mark

enum class TileStyle { Dark, Brand }

/** The Velnox mark: a squircle tile carrying the approved logo symbol. */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Int = 36,
    tile: TileStyle = TileStyle.Dark,
) {
    val background = when (tile) {
        TileStyle.Dark -> MaterialTheme.colorScheme.surface
        TileStyle.Brand -> VelnoxBrand
    }
    // Derive Light/Dark from the applied scheme rather than the OS setting: a
    // manual Light choice on a dark-system phone must still show the slate mark.
    val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val mark = when {
        tile == TileStyle.Brand -> R.drawable.ic_velnox_mark_white
        lightSurface -> R.drawable.ic_velnox_mark_slate
        else -> R.drawable.ic_velnox_mark_white
    }
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(SquircleSm)
            .background(background)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleSm),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(mark),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(width = size.dp * 0.62f, height = size.dp * 0.47f),
        )
    }
}

// -------------------------------------------------------------- section header

/** Section title with an optional trailing text action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (action != null && onAction != null) {
            Row(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(SquircleXs)
                    .velnoxClickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = action,
                    style = TextXs,
                    color = VelnoxBrand,
                )
                Icon(
                    imageVector = VelnoxIcons.ChevronRight,
                    contentDescription = null,
                    tint = VelnoxBrand,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(14.dp),
                )
            }
        }
    }
}

// ----------------------------------------------------------------- badge chip

@Composable
fun BrandBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Text2xs,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .clip(SquircleXs)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

// --------------------------------------------------------------- trust badges

/** Compact, sleek trust strip placed cleanly right under the hero banner on Home. */
@Composable
fun TrustRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleSm)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleSm)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrustItem(VelnoxIcons.Truck, stringResource(R.string.home_trust_delivery), Modifier.weight(1f))
        TrustItem(VelnoxIcons.ShieldCheck, stringResource(R.string.home_trust_secure), Modifier.weight(1f))
        TrustItem(VelnoxIcons.Headphones, stringResource(R.string.home_trust_support), Modifier.weight(1f))
    }
}

@Composable
private fun TrustItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = VelnoxBrand,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = Text2xs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 5.dp),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

// ------------------------------------------------------------- page container

/** A vertical column already inset by the page gutter. */
@Composable
fun PageColumn(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter),
        content = content,
    )
}
