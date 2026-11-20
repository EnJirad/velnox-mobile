package com.velnox.app.feature.messages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.velnox.app.core.customer.CustomerConversation
import com.velnox.app.core.customer.CustomerMessage
import com.velnox.app.core.customer.CustomerRepository
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.LoadStatus

/**
 * Shared server-backed messaging state across Messages inbox, Conversation
 * detail, Profile stats, and Contact Support.
 *
 * Starts empty (zero mock/demo threads) and mirrors `/api/customer/conversations`.
 */
object MessagesState {

    val conversations = mutableStateListOf<CustomerConversation>()

    var status by mutableStateOf(LoadStatus.Idle)
        private set

    val totalUnreadCount: Int
        get() = conversations.sumOf { it.unreadCount }

    /** Fetch all conversations via `GET /api/customer/conversations`. */
    suspend fun loadConversations(repository: CustomerRepository): CustomerResult<Unit> {
        status = LoadStatus.Loading
        return when (val result = repository.call { api, token -> api.conversations(token) }) {
            is CustomerResult.Ok -> {
                conversations.clear()
                conversations.addAll(result.value)
                status = LoadStatus.Ready
                CustomerResult.Ok(Unit)
            }

            is CustomerResult.Failed -> {
                status = LoadStatus.Failed
                result
            }
        }
    }

    /** Open or reuse the customer's Velnox Support conversation. */
    suspend fun openSupportConversation(
        repository: CustomerRepository,
    ): CustomerResult<CustomerConversation> {
        val result = repository.call { api, token -> api.startSupportConversation(token) }
        if (result is CustomerResult.Ok) {
            upsert(result.value)
        }
        return result
    }

    /** Open or reuse a seller shop conversation. */
    suspend fun openShopConversation(
        repository: CustomerRepository,
        shopId: String,
        productId: String? = null,
    ): CustomerResult<CustomerConversation> {
        val result = repository.call { api, token -> api.startConversation(token, shopId, productId) }
        if (result is CustomerResult.Ok) {
            upsert(result.value)
        }
        return result
    }

    /** Load messages for a thread via `GET /api/customer/conversations/:id/messages`. */
    suspend fun loadMessages(
        repository: CustomerRepository,
        conversationId: String,
    ): CustomerResult<List<CustomerMessage>> =
        repository.call { api, token -> api.messages(token, conversationId) }

    /** Send a message via `POST /api/customer/conversations/:id/messages` and update preview. */
    suspend fun sendMessage(
        repository: CustomerRepository,
        conversationId: String,
        text: String,
        clientId: String? = null,
    ): CustomerResult<CustomerMessage> {
        val result = repository.call { api, token -> api.sendMessage(token, conversationId, text, clientId) }
        if (result is CustomerResult.Ok) {
            val idx = conversations.indexOfFirst { it.id == conversationId }
            if (idx >= 0) {
                val updated = conversations[idx].copy(
                    lastMessage = result.value.text,
                    lastMessageAt = result.value.createdAt ?: System.currentTimeMillis(),
                )
                conversations.removeAt(idx)
                conversations.add(0, updated)
            }
        }
        return result
    }

    /** Mark incoming messages read via `POST /api/customer/conversations/:id/read`. */
    suspend fun markRead(
        repository: CustomerRepository,
        conversationId: String,
    ): CustomerResult<Unit> {
        val result = repository.call { api, token -> api.markConversationRead(token, conversationId) }
        if (result is CustomerResult.Ok) {
            val idx = conversations.indexOfFirst { it.id == conversationId }
            if (idx >= 0 && conversations[idx].unreadCount > 0) {
                conversations[idx] = conversations[idx].copy(unreadCount = 0)
            }
        }
        return result
    }

    /** Polling interval for active conversations (2.5 seconds). */
    const val CHAT_POLL_INTERVAL_MS = 2_500L

    /**
     * Silent in-place merge for live chat polling.
     *
     * Server-confirmed [remote] messages are authoritative; any local optimistic
     * (`isPending == true`) messages not yet reflected in [remote] are preserved
     * at the tail so a poll firing mid-send never flickers or drops a bubble.
     */
    fun mergePolledMessages(
        current: List<CustomerMessage>,
        remote: List<CustomerMessage>,
    ): List<CustomerMessage> {
        val remoteIds = remote.mapTo(HashSet(remote.size)) { it.id }
        val remoteCustomerTexts = remote
            .asSequence()
            .filter { it.fromCustomer }
            .map { it.text.trim() }
            .toHashSet()
        val remainingPending = current.filter { msg ->
            msg.isPending &&
                msg.id !in remoteIds &&
                msg.text.trim() !in remoteCustomerTexts
        }
        return if (remainingPending.isEmpty()) remote else remote + remainingPending
    }

    /** True when [remote] contains an incoming message not yet present in [current]. */
    fun hasNewIncomingMessages(
        current: List<CustomerMessage>,
        remote: List<CustomerMessage>,
    ): Boolean {
        val existingIds = current.mapTo(HashSet(current.size)) { it.id }
        return remote.any { !it.fromCustomer && !it.isSystem && it.id !in existingIds }
    }

    fun clear() {
        conversations.clear()
        status = LoadStatus.Idle
    }

    /** Test seam: put conversations into a known state without the network. */
    fun reset(initial: List<CustomerConversation> = emptyList()) {
        conversations.clear()
        conversations.addAll(initial)
        status = if (initial.isEmpty()) LoadStatus.Idle else LoadStatus.Ready
    }

    private fun upsert(conversation: CustomerConversation) {
        val idx = conversations.indexOfFirst { it.id == conversation.id }
        if (idx >= 0) {
            conversations[idx] = conversation
        } else {
            conversations.add(0, conversation)
        }
        status = LoadStatus.Ready
    }
}
