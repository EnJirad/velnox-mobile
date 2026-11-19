package com.velnox.app.navigation

import com.velnox.app.ui.components.velnoxClickable

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.VelnoxBrand

/** The five main destinations. Order is the bottom-bar order. */
enum class AppTab(
    val key: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home("home", R.string.nav_home, VelnoxIcons.Home),
    Market("market", R.string.nav_market, VelnoxIcons.ShoppingBag),
    Orders("orders", R.string.nav_orders, VelnoxIcons.Package),
    Cart("cart", R.string.nav_cart, VelnoxIcons.ShoppingCart),
    Account("account", R.string.nav_account, VelnoxIcons.User);

    companion object {
        fun fromKey(key: String?): AppTab = entries.firstOrNull { it.key == key } ?: Home
    }
}

/**
 * Bottom navigation bar shared by every main screen.
 *
 * Material 3 navigation-bar geometry is used (icon above a 12sp label, one
 * third of the width each) but the indicator is a Velnox squircle with the
 * emerald accent, per the design system rather than the default M3 pill.
 */
@Composable
fun VelnoxBottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            AppTab.entries.forEach { tab ->
                BottomBarItem(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(SquircleSm)
            .velnoxClickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 26.dp)
                .clip(SquircleSm)
                .background(
                    if (selected) {
                        VelnoxBrand.copy(alpha = 0.16f)
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = if (selected) VelnoxBrand else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(tab.labelRes),
            style = Text2xs,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

/** Frame for a tab: content plus the shared bottom bar. */
@Composable
fun TabScaffold(
    selected: AppTab,
    onSelectTab: (AppTab) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
        VelnoxBottomBar(selected = selected, onSelect = onSelectTab)
    }
}
