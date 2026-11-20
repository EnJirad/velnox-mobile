@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.messages

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.velnox.app.R
import com.velnox.app.VelnoxApplication
import com.velnox.app.core.catalog.PriceFormat
import com.velnox.app.core.customer.CustomerConversation
import com.velnox.app.core.customer.CustomerMessage
import com.velnox.app.core.customer.CustomerRepository
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.LoadStatus
import com.velnox.app.core.customer.userMessage
import com.velnox.app.ui.components.BrandBadge
import com.velnox.app.ui.components.EmptyState
import com.velnox.app.ui.components.IconActionButton
import com.velnox.app.ui.components.InitialsAvatar
import com.velnox.app.ui.components.PageGutter
import com.velnox.app.ui.components.TopBar
import com.velnox.app.ui.components.velnoxClickable
import com.velnox.app.ui.icons.VelnoxIcons
import com.velnox.app.ui.theme.Circle
import com.velnox.app.ui.theme.Squircle
import com.velnox.app.ui.theme.SquircleLg
import com.velnox.app.ui.theme.SquircleSm
import com.velnox.app.ui.theme.Text2xs
import com.velnox.app.ui.theme.TextXs
import com.velnox.app.ui.theme.VelnoxBrand
import com.velnox.app.ui.theme.VelnoxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Messages inbox backed by `GET /api/customer/conversations` and
 * `POST /api/customer/support/conversation`.
 */
@Composable
fun MessagesScreen(
    onOpenThread: (String) -> Unit,
    onBrowseProducts: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val repository: CustomerRepository? = remember(context) {
        (context.applicationContext as? VelnoxApplication)?.customer
    }
    val scope = rememberCoroutineScope()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var actionError by remember { mutableStateOf<String?>(null) }
    var openingSupport by remember { mutableStateOf(false) }

    LaunchedEffect(repository) {
        if (repository != null) {
            MessagesState.loadConversations(repository)
        }
    }

    val supportLabel = stringResource(R.string.messages_support_title)
    val threads = MessagesState.conversations
    val filteredThreads = remember(threads.toList(), searchQuery, supportLabel) {
        if (searchQuery.isBlank()) {
            threads.toList()
        } else {
            val q = searchQuery.trim().lowercase()
            threads.filter { conv ->
                val title = conv.displayTitle(supportLabel).lowercase()
                val preview = conv.lastMessage.orEmpty().lowercase()
                title.contains(q) || preview.contains(q)
            }
        }
    }

    fun openSupport() {
        if (repository == null || openingSupport) return
        openingSupport = true
        actionError = null
        scope.launch {
            when (val res = MessagesState.openSupportConversation(repository)) {
                is CustomerResult.Ok -> onOpenThread(res.value.id)
                is CustomerResult.Failed -> actionError = res.userMessage(context)
            }
            openingSupport = false
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
            actions = {
                if (repository != null) {
                    Text(
                        text = stringResource(R.string.messages_contact_support),
                        style = Text2xs.copy(fontWeight = FontWeight.SemiBold),
                        color = VelnoxBrand,
                        modifier = Modifier
                            .clip(SquircleSm)
                            .velnoxClickable(role = Role.Button, enabled = !openingSupport) { openSupport() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            },
        )

        if (actionError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = 4.dp)
                    .clip(Squircle)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = actionError.orEmpty(),
                    style = Text2xs,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        if (threads.isEmpty()) {
            when (MessagesState.status) {
                LoadStatus.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.customer_loading),
                            style = TextXs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                LoadStatus.Failed -> {
                    EmptyState(
                        icon = VelnoxIcons.MessageCircle,
                        title = stringResource(R.string.messages_load_failed),
                        body = stringResource(R.string.customer_error_network),
                        actionLabel = stringResource(R.string.common_retry),
                        onAction = {
                            if (repository != null) {
                                scope.launch { MessagesState.loadConversations(repository) }
                            }
                        },
                    )
                }

                LoadStatus.Idle, LoadStatus.Ready -> {
                    EmptyState(
                        icon = VelnoxIcons.MessageCircle,
                        title = stringResource(R.string.messages_empty_title),
                        body = stringResource(R.string.messages_empty_body),
                        actionLabel = stringResource(R.string.messages_contact_support),
                        onAction = {
                            if (repository != null) openSupport() else onBrowseProducts()
                        },
                    )
                }
            }
            return@Column
        }

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
            items(filteredThreads, key = { it.id }) { thread ->
                ThreadRow(
                    thread = thread,
                    supportLabel = supportLabel,
                    onClick = { onOpenThread(thread.id) },
                )
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
private fun ThreadRow(
    thread: CustomerConversation,
    supportLabel: String,
    onClick: () -> Unit,
) {
    val title = thread.displayTitle(supportLabel)
    val initials = remember(title) { initialsOf(title) }
    val locale = LocalConfiguration.current.locales[0]
    val timeText = remember(thread.lastMessageAt, locale) {
        formatChatTimestamp(thread.lastMessageAt, locale)
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
        InitialsAvatar(initials = initials, size = 44, brand = thread.unreadCount > 0 || thread.isSupport)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (thread.isSupport) {
                    Spacer(Modifier.width(4.dp))
                    BrandBadge(text = stringResource(R.string.profile_verified_badge))
                }
                Spacer(Modifier.weight(1f))
                if (timeText.isNotEmpty()) {
                    Text(
                        text = timeText,
                        style = Text2xs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = thread.lastMessage ?: stringResource(R.string.messages_empty_chat),
                    style = TextXs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (thread.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(Circle)
                            .background(VelnoxBrand)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (thread.unreadCount > 99) "99+" else thread.unreadCount.toString(),
                            style = Text2xs.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Real server-backed conversation screen (`GET/POST /api/customer/conversations/:id/messages`)
 * with 2.5s live periodic polling, optimistic outgoing messages, and modern chat bubbles.
 */
@Composable
fun ConversationScreen(
    threadId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProduct: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val repository: CustomerRepository? = remember(context) {
        (context.applicationContext as? VelnoxApplication)?.customer
    }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val locale = LocalConfiguration.current.locales[0]
    val listState = rememberLazyListState()

    val supportLabel = stringResource(R.string.messages_support_title)
    val thread = MessagesState.conversations.firstOrNull { it.id == threadId }
    val title = thread?.displayTitle(supportLabel) ?: supportLabel
    val initials = remember(title) { initialsOf(title) }

    val messages = remember(threadId) { mutableStateListOf<CustomerMessage>() }
    var loading by remember(threadId) { mutableStateOf(true) }
    var loadError by remember(threadId) { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }

    var inputText by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var lastScrolledCount by remember(threadId) { mutableIntStateOf(0) }

    // Initial load + real-time live polling loop every 2.5s while conversation is open.
    LaunchedEffect(threadId, repository, reloadTick) {
        if (repository == null) {
            loading = false
            return@LaunchedEffect
        }
        loading = messages.isEmpty()
        loadError = null
        if (MessagesState.conversations.none { it.id == threadId }) {
            MessagesState.loadConversations(repository)
        }
        when (val res = MessagesState.loadMessages(repository, threadId)) {
            is CustomerResult.Ok -> {
                val merged = MessagesState.mergePolledMessages(messages.toList(), res.value)
                messages.clear()
                messages.addAll(merged)
                loadError = null
                MessagesState.markRead(repository, threadId)
            }

            is CustomerResult.Failed -> {
                if (messages.isEmpty()) {
                    loadError = res.userMessage(context)
                }
            }
        }
        loading = false

        // Periodic silent live polling loop — cancels automatically on screen exit.
        while (isActive) {
            delay(MessagesState.CHAT_POLL_INTERVAL_MS)
            when (val polled = MessagesState.loadMessages(repository, threadId)) {
                is CustomerResult.Ok -> {
                    val snapshot = messages.toList()
                    val hasNewIncoming = MessagesState.hasNewIncomingMessages(snapshot, polled.value)
                    val merged = MessagesState.mergePolledMessages(snapshot, polled.value)
                    if (merged != snapshot) {
                        messages.clear()
                        messages.addAll(merged)
                    }
                    if (hasNewIncoming) {
                        MessagesState.markRead(repository, threadId)
                    }
                }
                is CustomerResult.Failed -> {
                    // Silent failure during background polling; keep existing messages intact.
                }
            }
        }
    }

    // Smooth auto-scroll to latest message whenever new messages arrive or are sent.
    LaunchedEffect(messages.size, messages.lastOrNull()?.id) {
        if (messages.isNotEmpty() && messages.size != lastScrolledCount) {
            val wasInitial = lastScrolledCount == 0
            lastScrolledCount = messages.size
            if (wasInitial) {
                listState.scrollToItem(messages.lastIndex)
            } else {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    fun submitMessage() {
        val text = inputText.trim()
        if (text.isEmpty() || sending || repository == null) return
        sending = true
        sendError = null
        val clientId = UUID.randomUUID().toString()
        val pendingId = "pending-$clientId"
        val optimistic = CustomerMessage(
            id = pendingId,
            text = text,
            fromCustomer = true,
            createdAt = System.currentTimeMillis(),
            isPending = true,
        )
        // Optimistically clear input and append bubble immediately.
        inputText = ""
        messages.add(optimistic)

        scope.launch {
            listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0))
            when (val res = MessagesState.sendMessage(repository, threadId, text, clientId)) {
                is CustomerResult.Ok -> {
                    val idx = messages.indexOfFirst { it.id == pendingId }
                    val alreadyMerged = messages.any { it.id == res.value.id }
                    if (idx >= 0) {
                        if (alreadyMerged) {
                            messages.removeAt(idx)
                        } else {
                            messages[idx] = res.value
                        }
                    } else if (!alreadyMerged) {
                        messages.add(res.value)
                    }
                    focusManager.clearFocus()
                }

                is CustomerResult.Failed -> {
                    messages.removeAll { it.id == pendingId }
                    if (inputText.isEmpty()) inputText = text
                    sendError = res.userMessage(context)
                }
            }
            sending = false
        }
    }

    val inputInteractionSource = remember { MutableInteractionSource() }
    val inputFocused by inputInteractionSource.collectIsFocusedAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Refined Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconActionButton(
                    icon = VelnoxIcons.ChevronLeft,
                    contentDescription = stringResource(R.string.common_back),
                    onClick = onBack,
                )
                Spacer(Modifier.width(4.dp))
                Box(contentAlignment = Alignment.BottomEnd) {
                    InitialsAvatar(initials = initials, size = 40, brand = true)
                    if (thread?.participantOnline == true) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(Circle)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(1.5.dp)
                                .clip(Circle)
                                .background(VelnoxBrand),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (thread?.isSupport == true) {
                            Spacer(Modifier.width(6.dp))
                            BrandBadge(text = stringResource(R.string.profile_verified_badge))
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(Circle)
                                .background(
                                    if (thread?.participantOnline == true) VelnoxBrand
                                    else VelnoxBrand.copy(alpha = 0.6f),
                                ),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (thread?.participantOnline == true) {
                                stringResource(R.string.messages_online)
                            } else {
                                stringResource(R.string.messages_live_connected)
                            },
                            style = Text2xs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }

        // Pinned Product Card — only shown when the server returned a real linked product.
        if (thread?.productId != null && !thread.productName.isNullOrBlank()) {
            PinnedProductCard(
                name = thread.productName,
                price = thread.productPrice,
                imageUrl = thread.productImageUrl,
                locale = locale,
                onClick = { onOpenProduct?.invoke("product:${thread.productId}") },
                modifier = Modifier.padding(horizontal = PageGutter, vertical = 8.dp),
            )
        }

        // Chat Messages Area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && messages.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.customer_loading),
                            style = TextXs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                loadError != null && messages.isEmpty() -> {
                    EmptyState(
                        icon = VelnoxIcons.MessageCircle,
                        title = stringResource(R.string.messages_load_failed),
                        body = loadError.orEmpty(),
                        actionLabel = stringResource(R.string.common_retry),
                        onAction = { reloadTick++ },
                    )
                }

                messages.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(PageGutter),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.messages_empty_chat),
                            style = TextXs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = PageGutter),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            val timeLabel = formatChatTimestamp(msg.createdAt, locale)
                            if (msg.isSystem) {
                                SystemBubble(text = msg.text)
                            } else {
                                Bubble(
                                    text = msg.text,
                                    incoming = !msg.fromCustomer,
                                    timestamp = timeLabel,
                                    isPending = msg.isPending,
                                    isRead = msg.isRead,
                                    sellerInitials = initials,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (sendError != null) {
            Text(
                text = sendError.orEmpty(),
                style = Text2xs,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = PageGutter, vertical = 4.dp),
            )
        }

        // Sleek Message Input Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = PageGutter, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val inputBorder = if (inputFocused) VelnoxBrand else MaterialTheme.colorScheme.outline
                BasicTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        sendError = null
                    },
                    enabled = !sending,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(VelnoxBrand),
                    interactionSource = inputInteractionSource,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitMessage() }),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, inputBorder, RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.messages_input_placeholder),
                                    style = TextXs,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                Spacer(Modifier.width(10.dp))

                val canSend = inputText.isNotBlank() && !sending && repository != null
                val sendBgModifier = if (canSend) {
                    Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(VelnoxBrand, Color(0xFF059669)),
                        ),
                    )
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(Circle)
                        .then(sendBgModifier)
                        .velnoxClickable(
                            role = Role.Button,
                            enabled = canSend,
                            onClick = { submitMessage() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VelnoxIcons.Send,
                        contentDescription = stringResource(R.string.messages_send),
                        tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedProductCard(
    name: String,
    price: Double?,
    imageUrl: String?,
    locale: Locale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Squircle)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, Squircle)
            .velnoxClickable(role = Role.Button, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(SquircleSm)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = VelnoxIcons.ShoppingBag,
                    contentDescription = null,
                    tint = VelnoxBrand,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.messages_product_inquiry),
                style = Text2xs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = name,
                style = TextXs.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (price != null) {
                Text(
                    text = PriceFormat.format(price, "THB", locale) ?: "฿$price",
                    style = TextXs.copy(fontWeight = FontWeight.Bold),
                    color = VelnoxBrand,
                )
            }
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
private fun SystemBubble(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(Squircle)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = text,
                style = Text2xs,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Bubble(
    text: String,
    incoming: Boolean,
    timestamp: String,
    isPending: Boolean = false,
    isRead: Boolean = false,
    sellerInitials: String = "",
) {
    val bubbleShape = if (incoming) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    }

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
                Spacer(Modifier.width(8.dp))
            }
            if (incoming) {
                Surface(
                    shape = bubbleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .border(1.dp, MaterialTheme.colorScheme.outline, bubbleShape),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = text,
                            style = TextXs.copy(lineHeight = MaterialTheme.typography.bodyMedium.lineHeight),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (timestamp.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = timestamp,
                                style = Text2xs,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                val outgoingGradient = Brush.linearGradient(
                    colors = if (isPending) {
                        listOf(VelnoxBrand.copy(alpha = 0.78f), Color(0xFF059669).copy(alpha = 0.78f))
                    } else {
                        listOf(VelnoxBrand, Color(0xFF059669))
                    },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .shadow(2.dp, bubbleShape)
                        .clip(bubbleShape)
                        .background(outgoingGradient)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = text,
                            style = TextXs.copy(
                                fontWeight = FontWeight.Medium,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                            ),
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                        ) {
                            if (timestamp.isNotEmpty()) {
                                Text(
                                    text = timestamp,
                                    style = Text2xs,
                                    color = Color.White.copy(alpha = 0.8f),
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            val statusTick = when {
                                isPending -> "✓"
                                else -> "✓✓"
                            }
                            Text(
                                text = statusTick,
                                style = Text2xs.copy(fontWeight = FontWeight.Bold),
                                color = when {
                                    isPending -> Color.White.copy(alpha = 0.65f)
                                    isRead -> Color(0xFFD1FAE5)
                                    else -> Color.White.copy(alpha = 0.9f)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun CustomerConversation.displayTitle(supportLabel: String): String = when {
    isSupport -> supportLabel
    !shopName.isNullOrBlank() -> shopName
    !participantName.isNullOrBlank() -> participantName
    else -> supportLabel
}

private fun initialsOf(name: String): String =
    name.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
        .ifEmpty { "V" }

private fun formatChatTimestamp(millis: Long?, locale: Locale): String {
    if (millis == null || millis <= 0L) return ""
    return SimpleDateFormat("HH:mm", locale).format(Date(millis))
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MessagesPreview() {
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
