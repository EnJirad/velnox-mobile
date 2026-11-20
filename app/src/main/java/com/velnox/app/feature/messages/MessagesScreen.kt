@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.messages

import com.velnox.app.ui.components.velnoxClickable

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.velnox.app.R
import com.velnox.app.ui.components.BrandBadge
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.IconActionButton
import com.velnox.app.ui.components.InitialsAvatar
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.PreviewNotice
import com.velnox.app.ui.components.TopBar
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Circle
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxTheme

/** One sample conversation. Preview content only. */
data class DemoThread(
    val id: String,
    @StringRes val sellerRes: Int,
    @StringRes val previewRes: Int,
    val timeLabel: ThreadTime,
    val unread: Boolean,
)

data class ChatMessage(
    val id: String,
    val text: String,
    val isIncoming: Boolean,
    val timestamp: String,
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
 * Supports filtering threads by store name and message preview.
 */
@Composable
fun MessagesScreen(
    onOpenThread: (String) -> Unit,
    onBrowseProducts: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    threads: List<DemoThread> = MessagesDemo.threads,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    val filteredThreads = remember(threads, searchQuery, context) {
        if (searchQuery.isBlank()) {
            threads
        } else {
            val query = searchQuery.trim().lowercase()
            threads.filter { thread ->
                context.getString(thread.sellerRes).lowercase().contains(query) ||
                    context.getString(thread.previewRes).lowercase().contains(query)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(
            title = stringResource(R.string.messages_title),
            onBack = onBack,
        )

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

        // Search Bar
        MessagesSearchField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onClear = { searchQuery = "" },
            modifier = Modifier.padding(horizontal = PageGutter, vertical = 6.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = PageGutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                PreviewNotice(text = stringResource(R.string.messages_demo_notice))
                Spacer(Modifier.height(4.dp))
            }
            items(filteredThreads, key = { it.id }) { thread ->
                ThreadRow(thread = thread, onClick = { onOpenThread(thread.id) })
            }
        }
    }
}

@Composable
private fun MessagesSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) VelnoxBrand else MaterialTheme.colorScheme.outline

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, borderColor, Squircle)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = VelnoxIcons.Search,
            contentDescription = null,
            tint = VelnoxBrand,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.messages_search_placeholder),
                    style = TextXs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(VelnoxBrand),
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = VelnoxIcons.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
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
            .velnoxClickable(role = Role.Button, onClick = onClick)
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
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(4.dp))
                BrandBadge(text = stringResource(R.string.profile_verified_badge))
                Spacer(Modifier.weight(1f))
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
 * Conversation detail with enriched seller header, pinned product reference card,
 * and responsive chat input with software keyboard safety (`imePadding`).
 */
@Composable
fun ConversationScreen(
    threadId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thread = MessagesDemo.threads.firstOrNull { it.id == threadId } ?: MessagesDemo.threads.first()
    val sellerName = stringResource(thread.sellerRes)
    val initials = remember(sellerName) {
        sellerName.trim().split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
    }

    val initialSellerMsg = stringResource(thread.previewRes)
    val initialUserMsg = stringResource(R.string.messages_preview_2)

    val messages = remember(threadId) {
        mutableStateListOf(
            ChatMessage("1", initialSellerMsg, isIncoming = true, timestamp = "10:30 AM"),
            ChatMessage("2", initialUserMsg, isIncoming = false, timestamp = "10:32 AM"),
            ChatMessage("3", "Yes, we currently have this item in stock and ready to ship!", isIncoming = true, timestamp = "10:35 AM"),
        )
    }

    var inputText by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Enriched Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconActionButton(
                icon = VelnoxIcons.ChevronLeft,
                contentDescription = stringResource(R.string.common_back),
                onClick = onBack,
            )
            Spacer(Modifier.width(6.dp))
            InitialsAvatar(initials = initials, size = 38, brand = true)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sellerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
                    BrandBadge(text = stringResource(R.string.profile_verified_badge))
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(Circle)
                            .background(Color(0xFF10B981)),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.messages_online),
                        style = Text2xs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Pinned Product Reference Snippet
        PinnedProductCard(
            modifier = Modifier.padding(horizontal = PageGutter, vertical = 8.dp),
        )

        // Chat messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = PageGutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            item {
                PreviewNotice(text = stringResource(R.string.messages_demo_notice))
                Spacer(Modifier.height(4.dp))
            }
            items(messages, key = { it.id }) { msg ->
                Bubble(
                    text = msg.text,
                    incoming = msg.isIncoming,
                    timestamp = msg.timestamp,
                    sellerInitials = initials,
                )
            }
        }

        // Message Input Bar with imePadding and navigationBarsPadding
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = PageGutter, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(SquircleSm)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VelnoxIcons.Plus,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(SquircleLg)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = stringResource(R.string.messages_input_placeholder),
                        style = TextXs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(VelnoxBrand),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                messages.add(
                                    ChatMessage(
                                        id = System.currentTimeMillis().toString(),
                                        text = inputText.trim(),
                                        isIncoming = false,
                                        timestamp = "Just now",
                                    )
                                )
                                inputText = ""
                                focusManager.clearFocus()
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.width(8.dp))

            val canSend = inputText.isNotBlank()
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(SquircleSm)
                    .background(if (canSend) VelnoxBrand else MaterialTheme.colorScheme.surfaceVariant)
                    .velnoxClickable(
                        role = Role.Button,
                        enabled = canSend,
                        onClick = {
                            if (canSend) {
                                messages.add(
                                    ChatMessage(
                                        id = System.currentTimeMillis().toString(),
                                        text = inputText.trim(),
                                        isIncoming = false,
                                        timestamp = "Just now",
                                    )
                                )
                                inputText = ""
                                focusManager.clearFocus()
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VelnoxIcons.ArrowRight,
                    contentDescription = stringResource(R.string.messages_send),
                    tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun PinnedProductCard(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, Squircle)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(SquircleSm)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = VelnoxIcons.ShoppingBag,
                contentDescription = null,
                tint = VelnoxBrand,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.messages_product_inquiry),
                style = Text2xs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.messages_pinned_product_name),
                style = TextXs.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.messages_pinned_product_price),
                style = TextXs.copy(fontWeight = FontWeight.Bold),
                color = VelnoxBrand,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(SquircleSm)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.messages_view_product),
                style = Text2xs.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun Bubble(
    text: String,
    incoming: Boolean,
    timestamp: String,
    sellerInitials: String = "",
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (incoming) Alignment.Start else Alignment.End,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (incoming) Arrangement.Start else Arrangement.End,
        ) {
            if (incoming) {
                InitialsAvatar(initials = sellerInitials, size = 28, brand = false)
                Spacer(Modifier.width(6.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .clip(SquircleLg)
                    .background(
                        if (incoming) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            VelnoxBrand
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = text,
                    style = TextXs.copy(
                        fontWeight = if (incoming) FontWeight.Normal else FontWeight.Medium,
                    ),
                    color = if (incoming) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        Color.White
                    },
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = timestamp,
            style = Text2xs,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = if (incoming) 34.dp else 0.dp, end = 4.dp),
        )
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

@Preview(name = "Messages 320dp", showBackground = true, widthDp = 320, heightDp = 720)
@Composable
private fun MessagesCompactPreview() {
    VelnoxTheme(darkTheme = false) {
        MessagesScreen(onOpenThread = {}, onBrowseProducts = {})
    }
}

@Preview(name = "Messages dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MessagesDarkPreview() {
    VelnoxTheme(darkTheme = true) {
        MessagesScreen(onOpenThread = {}, onBrowseProducts = {})
    }
}

@Preview(name = "Conversation Preview", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ConversationPreview() {
    VelnoxTheme(darkTheme = false) {
        ConversationScreen(threadId = "t1", onBack = {})
    }
}
