@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.messages

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.InitialsAvatar
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.PreviewNotice
import com.velnox.app.ui.components.TopBar
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Circle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxBrandSoft
import com.velnox.app.ui.theme.VelnoxTheme

/** One sample conversation. Preview content only. */
data class DemoThread(
    val id: String,
    @StringRes val sellerRes: Int,
    @StringRes val previewRes: Int,
    val timeLabel: ThreadTime,
    val unread: Boolean,
)

enum class ThreadTime { Today, Yesterday }

object MessagesDemo {
    val threads = listOf(
        DemoThread("t1", R.string.messages_seller_1, R.string.messages_preview_1, ThreadTime.Today, true),
        DemoThread("t2", R.string.messages_seller_2, R.string.messages_preview_2, ThreadTime.Today, false),
        DemoThread("t3", R.string.messages_seller_3, R.string.messages_preview_3, ThreadTime.Yesterday, false),
    )
}

/**
 * Messages tab: conversation list plus an intentional empty presentation.
 * The demo list is dropped as soon as the user clears it, so both states are
 * reachable without any backend.
 */
@Composable
fun MessagesScreen(
    onOpenThread: (String) -> Unit,
    onBrowseProducts: () -> Unit,
    modifier: Modifier = Modifier,
    threads: List<DemoThread> = MessagesDemo.threads,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(title = stringResource(R.string.messages_title))

        if (threads.isEmpty()) {
            EmptyState(
                icon = VelnoxIcons.MessageCircle,
                title = stringResource(R.string.messages_empty_title),
                body = stringResource(R.string.messages_empty_body),
                actionLabel = stringResource(R.string.messages_empty_action),
                onAction = onBrowseProducts,
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = PageGutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                PreviewNotice(text = stringResource(R.string.messages_demo_notice))
                Spacer(Modifier.height(4.dp))
            }
            items(threads, key = { it.id }) { thread ->
                ThreadRow(thread = thread, onClick = { onOpenThread(thread.id) })
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: DemoThread, onClick: () -> Unit) {
    val sellerName = stringResource(thread.sellerRes)
    val initials = remember(sellerName) {
        sellerName.trim().split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, SquircleLg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialsAvatar(initials = initials, size = 44, brand = thread.unread)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sellerName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        when (thread.timeLabel) {
                            ThreadTime.Today -> R.string.messages_today
                            ThreadTime.Yesterday -> R.string.messages_yesterday
                        }
                    ),
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(thread.previewRes),
                    style = TextXs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (thread.unread) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(Circle)
                            .background(VelnoxBrand),
                    )
                }
            }
        }
    }
}

/**
 * Conversation detail. Layout is final; sending is intentionally disabled and
 * labelled, because Phase 2 has no messaging backend.
 */
@Composable
fun ConversationScreen(
    threadId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thread = MessagesDemo.threads.firstOrNull { it.id == threadId } ?: MessagesDemo.threads.first()
    val sellerName = stringResource(thread.sellerRes)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(
            title = sellerName,
            subtitle = stringResource(R.string.messages_thread_title),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = PageGutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PreviewNotice(text = stringResource(R.string.messages_demo_notice))
            Bubble(text = stringResource(thread.previewRes), incoming = true)
            Bubble(text = stringResource(R.string.login_subtitle), incoming = false)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(horizontal = PageGutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(SquircleLg)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.messages_input_placeholder),
                    style = TextXs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(SquircleLg)
                    .background(VelnoxBrandSoft),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = VelnoxIcons.ArrowRight,
                    contentDescription = stringResource(R.string.messages_input_hint),
                    tint = VelnoxBrand,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun Bubble(text: String, incoming: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (incoming) Arrangement.Start else Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .clip(SquircleLg)
                .background(
                    if (incoming) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        VelnoxBrandSoft
                    }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = text,
                style = TextXs.copy(
                    fontWeight = if (incoming) FontWeight.Normal else FontWeight.Medium,
                ),
                color = if (incoming) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MessagesPreview() {
    VelnoxTheme(darkTheme = false) {
        MessagesScreen(onOpenThread = {}, onBrowseProducts = {})
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MessagesEmptyPreview() {
    VelnoxTheme(darkTheme = false) {
        MessagesScreen(onOpenThread = {}, onBrowseProducts = {}, threads = emptyList())
    }
}
