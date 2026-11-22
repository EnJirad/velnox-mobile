@file:OptIn(ExperimentalMaterial3Api::class)

package com.velnox.app.feature.messages

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.velnox.app.core.customer.CustomerRemoteDataSource
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
import java.util.Calendar
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
    val lifecycleOwner = LocalLifecycleOwner.current

    // Automatically clear focus when window/app loses focus or pauses, preventing unwanted keyboard auto-popup upon return.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                focusManager.clearFocus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val locale = LocalConfiguration.current.locales[0]
    val listState = rememberLazyListState()

    val supportLabel = stringResource(R.string.messages_support_title)
    val thread = MessagesState.conversations.firstOrNull { it.id == threadId }
    val title = thread?.displayTitle(supportLabel) ?: supportLabel
    val initials = remember(title) { initialsOf(title) }

    val initialCached = remember(threadId) {
        MessagesState.getCachedMessages(threadId, context)
    }
    val messages = remember(threadId) {
        mutableStateListOf<CustomerMessage>().apply { addAll(initialCached) }
    }
    var loading by remember(threadId) { mutableStateOf(initialCached.isEmpty()) }
    var loadError by remember(threadId) { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }

    var inputText by rememberSaveable { mutableStateOf("") }
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
                MessagesState.setCachedMessages(threadId, merged, context)
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
                        MessagesState.setCachedMessages(threadId, merged, context)
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

    val inputInteractionSource = remember { MutableInteractionSource() }
    val inputFocused by inputInteractionSource.collectIsFocusedAsState()

    // Smooth auto-scroll to latest message whenever new messages arrive, are sent, or input is focused.
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

    LaunchedEffect(inputFocused) {
        if (inputFocused && messages.isNotEmpty()) {
            delay(150)
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun submitMessage() {
        val text = inputText.trim()
        if (text.isEmpty() || repository == null) return
        sendError = null
        val clientId = UUID.randomUUID().toString()
        val pendingId = "pending-$clientId"
        val optimistic = CustomerMessage(
            id = pendingId,
            text = text,
            fromCustomer = true,
            createdAt = System.currentTimeMillis(),
            isPending = true,
            clientId = clientId,
        )
        // Optimistically clear input immediately so the user can continue typing seamlessly without lockout
        inputText = ""
        val updated = CustomerRemoteDataSource.sortMessagesChronologically(messages.toList() + optimistic)
        messages.clear()
        messages.addAll(updated)
        MessagesState.setCachedMessages(threadId, updated, context)

        // Asynchronous non-blocking message dispatcher
        scope.launch {
            try {
                when (val res = MessagesState.sendMessage(repository, threadId, text, clientId)) {
                    is CustomerResult.Ok -> {
                        val remaining = messages.filterNot {
                            it.id == pendingId || (it.clientId != null && it.clientId == clientId) || it.id == res.value.id
                        }
                        val sorted = CustomerRemoteDataSource.sortMessagesChronologically(remaining + res.value)
                        messages.clear()
                        messages.addAll(sorted)
                        MessagesState.setCachedMessages(threadId, sorted, context)
                    }

                    is CustomerResult.Failed -> {
                        // Mark optimistic message as failed with retry affordance instead of dropping or blocking UI
                        val idx = messages.indexOfFirst {
                            it.id == pendingId || (it.clientId != null && it.clientId == clientId)
                        }
                        if (idx >= 0) {
                            messages[idx] = messages[idx].copy(
                                isPending = false,
                                isFailed = true,
                                failureReason = res.userMessage(context),
                            )
                            MessagesState.setCachedMessages(threadId, messages.toList(), context)
                        }
                    }
                }
            } catch (e: Exception) {
                val idx = messages.indexOfFirst {
                    it.id == pendingId || (it.clientId != null && it.clientId == clientId)
                }
                if (idx >= 0) {
                    messages[idx] = messages[idx].copy(
                        isPending = false,
                        isFailed = true,
                        failureReason = e.message,
                    )
                    MessagesState.setCachedMessages(threadId, messages.toList(), context)
                }
            }
        }
    }

    fun retryMessage(msg: CustomerMessage) {
        if (repository == null) return
        val clientId = msg.clientId ?: UUID.randomUUID().toString()
        val text = msg.text
        val idx = messages.indexOfFirst { it.id == msg.id }
        if (idx >= 0) {
            messages[idx] = messages[idx].copy(
                isPending = true,
                isFailed = false,
                failureReason = null,
                clientId = clientId,
            )
            MessagesState.setCachedMessages(threadId, messages.toList(), context)
        }
        scope.launch {
            try {
                when (val res = MessagesState.sendMessage(repository, threadId, text, clientId)) {
                    is CustomerResult.Ok -> {
                        val remaining = messages.filterNot {
                            it.id == msg.id || (it.clientId != null && it.clientId == clientId) || it.id == res.value.id
                        }
                        val sorted = CustomerRemoteDataSource.sortMessagesChronologically(remaining + res.value)
                        messages.clear()
                        messages.addAll(sorted)
                        MessagesState.setCachedMessages(threadId, sorted, context)
                    }

                    is CustomerResult.Failed -> {
                        val currIdx = messages.indexOfFirst {
                            it.id == msg.id || (it.clientId != null && it.clientId == clientId)
                        }
                        if (currIdx >= 0) {
                            messages[currIdx] = messages[currIdx].copy(
                                isPending = false,
                                isFailed = true,
                                failureReason = res.userMessage(context),
                            )
                            MessagesState.setCachedMessages(threadId, messages.toList(), context)
                        }
                    }
                }
            } catch (e: Exception) {
                val currIdx = messages.indexOfFirst {
                    it.id == msg.id || (it.clientId != null && it.clientId == clientId)
                }
                if (currIdx >= 0) {
                    messages[currIdx] = messages[currIdx].copy(
                        isPending = false,
                        isFailed = true,
                        failureReason = e.message,
                    )
                    MessagesState.setCachedMessages(threadId, messages.toList(), context)
                }
            }
        }
    }

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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = PageGutter, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(Circle)
                                .background(VelnoxBrand.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = VelnoxIcons.MessageCircle,
                                contentDescription = null,
                                tint = VelnoxBrand,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = if (thread?.isSupport == true) {
                                stringResource(R.string.messages_support_title)
                            } else {
                                stringResource(R.string.messages_empty_chat)
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (thread?.isSupport == true) {
                                stringResource(R.string.messages_support_prompt)
                            } else {
                                stringResource(R.string.messages_chat_prompt)
                            },
                            style = Text2xs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(18.dp))
                        // Quick suggestion pills
                        val suggestions = if (thread?.isSupport == true) {
                            listOf(
                                stringResource(R.string.messages_suggestion_help),
                                stringResource(R.string.messages_suggestion_delivery),
                            )
                        } else {
                            listOf(
                                stringResource(R.string.messages_suggestion_stock),
                                stringResource(R.string.messages_suggestion_delivery),
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            suggestions.forEach { prompt ->
                                Surface(
                                    shape = SquircleSm,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                    modifier = Modifier.velnoxClickable(role = Role.Button) {
                                        inputText = prompt
                                    },
                                ) {
                                    Text(
                                        text = prompt,
                                        style = TextXs.copy(fontWeight = FontWeight.Medium),
                                        color = VelnoxBrand,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = PageGutter),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
                    ) {
                        itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                            val prev = messages.getOrNull(index - 1)
                            val next = messages.getOrNull(index + 1)

                            val showDateHeader = shouldShowDateHeader(prev, msg)
                            if (showDateHeader) {
                                DateSeparator(
                                    timestamp = msg.createdAt,
                                    locale = locale,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                )
                            }

                            if (msg.isSystem) {
                                SystemBubble(text = msg.text)
                            } else {
                                val isIncoming = !msg.fromCustomer

                                val isSameAsPrev = prev != null &&
                                    !prev.isSystem &&
                                    prev.fromCustomer == msg.fromCustomer &&
                                    msg.createdAt != null &&
                                    prev.createdAt != null &&
                                    Math.abs(msg.createdAt - prev.createdAt) < 60_000L &&
                                    !showDateHeader

                                val nextShowsDate = next != null && shouldShowDateHeader(msg, next)
                                val isSameAsNext = next != null &&
                                    !next.isSystem &&
                                    next.fromCustomer == msg.fromCustomer &&
                                    msg.createdAt != null &&
                                    next.createdAt != null &&
                                    Math.abs(next.createdAt - msg.createdAt) < 60_000L &&
                                    !nextShowsDate

                                val isSolo = !isSameAsPrev && !isSameAsNext
                                val isFirstInGroup = !isSameAsPrev && isSameAsNext
                                val isMiddleInGroup = isSameAsPrev && isSameAsNext
                                val isLastInGroup = isSameAsPrev && !isSameAsNext

                                val topPadding = if (isFirstInGroup || isSolo) 8.dp else 2.dp

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = topPadding),
                                ) {
                                    InstagramBubble(
                                        text = msg.text,
                                        incoming = isIncoming,
                                        isSolo = isSolo,
                                        isFirstInGroup = isFirstInGroup,
                                        isMiddleInGroup = isMiddleInGroup,
                                        isLastInGroup = isLastInGroup,
                                        timestamp = if (isLastInGroup || isSolo) formatChatTimestamp(msg.createdAt, locale) else "",
                                        isPending = msg.isPending,
                                        isRead = msg.isRead,
                                        isFailed = msg.isFailed,
                                        onRetry = if (msg.isFailed) { { retryMessage(msg) } } else null,
                                        sellerInitials = initials,
                                    )
                                }
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

                val canSend = inputText.isNotBlank() && repository != null
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
private fun InstagramBubble(
    text: String,
    incoming: Boolean,
    isSolo: Boolean,
    isFirstInGroup: Boolean,
    isMiddleInGroup: Boolean,
    isLastInGroup: Boolean,
    timestamp: String,
    isPending: Boolean = false,
    isRead: Boolean = false,
    isFailed: Boolean = false,
    onRetry: (() -> Unit)? = null,
    sellerInitials: String = "",
) {
    val bubbleShape = getInstagramBubbleShape(
        incoming = incoming,
        isSolo = isSolo,
        isFirstInGroup = isFirstInGroup,
        isMiddleInGroup = isMiddleInGroup,
        isLastInGroup = isLastInGroup,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (incoming) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (incoming) {
            // Instagram Direct: avatar ONLY on the bottom-most message of a cluster
            if (isLastInGroup || isSolo) {
                InitialsAvatar(initials = sellerInitials, size = 28, brand = false)
            } else {
                Spacer(Modifier.width(28.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        if (incoming) {
            Surface(
                shape = bubbleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier
                    .widthIn(min = 40.dp, max = 280.dp)
                    .border(
                        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        shape = bubbleShape,
                    ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                    Text(
                        text = text,
                        style = TextXs.copy(lineHeight = MaterialTheme.typography.bodyMedium.lineHeight),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (timestamp.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = timestamp,
                            style = Text2xs,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        } else {
            val outgoingGradient = Brush.linearGradient(
                colors = when {
                    isFailed -> listOf(Color(0xFFDC2626), Color(0xFFB91C1C))
                    isPending -> listOf(VelnoxBrand.copy(alpha = 0.78f), Color(0xFF059669).copy(alpha = 0.78f))
                    else -> listOf(VelnoxBrand, Color(0xFF059669))
                },
            )
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 40.dp, max = 280.dp)
                        .shadow(0.5.dp, bubbleShape)
                        .clip(bubbleShape)
                        .background(outgoingGradient)
                        .then(
                            if (isFailed && onRetry != null) {
                                Modifier.velnoxClickable(role = Role.Button, onClick = onRetry)
                            } else Modifier
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = text,
                            style = TextXs.copy(
                                fontWeight = FontWeight.Normal,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                            ),
                            color = Color.White,
                        )
                        if (timestamp.isNotEmpty() || isPending || isFailed) {
                            Spacer(Modifier.height(3.dp))
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
                                    Spacer(Modifier.width(4.dp))
                                }
                                val statusTick = when {
                                    isFailed -> "!"
                                    isPending -> "✓"
                                    else -> "✓✓"
                                }
                                Text(
                                    text = statusTick,
                                    style = Text2xs.copy(fontWeight = FontWeight.Bold),
                                    color = when {
                                        isFailed -> Color(0xFFFEE2E2)
                                        isPending -> Color.White.copy(alpha = 0.65f)
                                        isRead -> Color(0xFFD1FAE5)
                                        else -> Color.White.copy(alpha = 0.9f)
                                    },
                                )
                            }
                        }
                    }
                }
                if (isFailed && onRetry != null) {
                    Spacer(Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier
                            .clip(SquircleSm)
                            .velnoxClickable(role = Role.Button, onClick = onRetry)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Icon(
                            imageVector = VelnoxIcons.RotateCcw,
                            contentDescription = stringResource(R.string.common_retry),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.messages_send_failed),
                            style = Text2xs.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

private fun getInstagramBubbleShape(
    incoming: Boolean,
    isSolo: Boolean,
    isFirstInGroup: Boolean,
    isMiddleInGroup: Boolean,
    isLastInGroup: Boolean,
): Shape {
    val rLarge = 18.dp
    val rSmall = 4.dp
    return if (incoming) {
        when {
            isSolo -> RoundedCornerShape(topStart = rLarge, topEnd = rLarge, bottomEnd = rLarge, bottomStart = rSmall)
            isFirstInGroup -> RoundedCornerShape(topStart = rLarge, topEnd = rLarge, bottomEnd = rLarge, bottomStart = rSmall)
            isMiddleInGroup -> RoundedCornerShape(topStart = rSmall, topEnd = rLarge, bottomEnd = rLarge, bottomStart = rSmall)
            isLastInGroup -> RoundedCornerShape(topStart = rSmall, topEnd = rLarge, bottomEnd = rLarge, bottomStart = rLarge)
            else -> RoundedCornerShape(rLarge)
        }
    } else {
        when {
            isSolo -> RoundedCornerShape(topStart = rLarge, topEnd = rLarge, bottomStart = rLarge, bottomEnd = rSmall)
            isFirstInGroup -> RoundedCornerShape(topStart = rLarge, topEnd = rLarge, bottomStart = rLarge, bottomEnd = rSmall)
            isMiddleInGroup -> RoundedCornerShape(topStart = rLarge, topEnd = rSmall, bottomStart = rLarge, bottomEnd = rSmall)
            isLastInGroup -> RoundedCornerShape(topStart = rLarge, topEnd = rSmall, bottomStart = rLarge, bottomEnd = rLarge)
            else -> RoundedCornerShape(rLarge)
        }
    }
}

private fun shouldShowDateHeader(prev: CustomerMessage?, current: CustomerMessage): Boolean {
    if (prev == null) return current.createdAt != null && current.createdAt > 0L
    val tPrev = prev.createdAt ?: return true
    val tCurr = current.createdAt ?: return false
    if (tCurr <= 0L || tPrev <= 0L) return false
    val diff = Math.abs(tCurr - tPrev)
    if (diff >= 15 * 60 * 1000L) return true
    val cal1 = Calendar.getInstance().apply { timeInMillis = tPrev }
    val cal2 = Calendar.getInstance().apply { timeInMillis = tCurr }
    return cal1.get(Calendar.DAY_OF_YEAR) != cal2.get(Calendar.DAY_OF_YEAR) ||
        cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR)
}

@Composable
private fun DateSeparator(
    timestamp: Long?,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    if (timestamp == null || timestamp <= 0L) return
    val context = LocalContext.current
    val text = remember(timestamp, locale) {
        formatDateSeparatorText(context, timestamp, locale)
    }
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Text2xs.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier
                .clip(Circle)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun formatDateSeparatorText(context: Context, millis: Long, locale: Locale): String {
    val calMsg = Calendar.getInstance().apply { timeInMillis = millis }
    val calNow = Calendar.getInstance()
    val isToday = calMsg.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
        calMsg.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
    calNow.add(Calendar.DAY_OF_YEAR, -1)
    val isYesterday = calMsg.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
        calMsg.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)

    val timeStr = SimpleDateFormat("HH:mm", locale).format(Date(millis))
    return when {
        isToday -> "${context.getString(R.string.messages_today)} $timeStr"
        isYesterday -> "${context.getString(R.string.messages_yesterday)} $timeStr"
        else -> {
            val dateStr = SimpleDateFormat("d MMM", locale).format(Date(millis))
            "$dateStr, $timeStr"
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
