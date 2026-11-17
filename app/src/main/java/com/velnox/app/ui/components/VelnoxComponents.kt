package com.velnox.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.velnox.app.ui.theme.VelnoxBrandForeground
import com.velnox.app.ui.theme.VelnoxBrandSoft
import com.velnox.app.ui.theme.VelnoxPrimary

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

/** The Velnox mark: a squircle tile carrying the emerald V. */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Int = 36,
    tile: TileStyle = TileStyle.Dark,
) {
    val background = when (tile) {
        TileStyle.Dark -> VelnoxPrimary
        TileStyle.Brand -> VelnoxBrand
    }
    val foreground = when (tile) {
        TileStyle.Dark -> VelnoxBrand
        TileStyle.Brand -> VelnoxBrandForeground
    }
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(SquircleSm)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.brand_mark),
            style = if (size >= 32) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = foreground,
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
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Row(
                modifier = Modifier
                    .clip(SquircleXs)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
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
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier
            .clip(SquircleXs)
            .background(VelnoxBrandSoft)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

// --------------------------------------------------------------- trust badges

/** Compact trust row reused under the hero on Home. */
@Composable
fun TrustRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = Text2xs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 5.dp),
            maxLines = 1,
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
