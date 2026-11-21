package com.velnox.app.feature.messages

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.velnox.app.core.customer.CustomerConversation
import com.velnox.app.core.customer.CustomerMessage
import com.velnox.app.core.customer.CustomerRemoteDataSource
import com.velnox.app.core.customer.CustomerRepository
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.LoadStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
        val existing = conversations.firstOrNull { it.isSupport }
        if (existing != null) {
            return CustomerResult.Ok(existing)
        }
        if (conversations.isEmpty()) {
            loadConversations(repository)
            val found = conversations.firstOrNull { it.isSupport }
            if (found != null) {
                return CustomerResult.Ok(found)
            }
        }
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
        val existing = conversations.firstOrNull { it.shopId == shopId }
        if (existing != null && productId == null) {
            return CustomerResult.Ok(existing)
        }
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
     * at their exact chronological position so a poll firing mid-send never flickers
     * or drops a bubble.
     *
     * Guarantees a strictly interleaved, chronologically ascending timeline.
     */
    fun mergePolledMessages(
        current: List<CustomerMessage>,
        remote: List<CustomerMessage>,
    ): List<CustomerMessage> {
        val remoteIds = remote.mapTo(HashSet(remote.size)) { it.id }
        val remoteClientIds = remote.mapNotNullTo(HashSet()) { it.clientId }
        val remoteCustomerTexts = remote
            .asSequence()
            .filter { it.fromCustomer }
            .map { it.text.trim() }
            .toHashSet()

        val now = System.currentTimeMillis()
        val remainingLocal = current.filter { msg ->
            val notInRemote = msg.id !in remoteIds && (msg.clientId == null || msg.clientId !in remoteClientIds)
            if (!notInRemote) return@filter false
            if (msg.isPending) {
                msg.text.trim() !in remoteCustomerTexts
            } else {
                // Keep recently confirmed local messages for 15s to bridge any replication or poll latency
                msg.fromCustomer && (msg.createdAt == null || (now - msg.createdAt) < 15_000L)
            }
        }
        val combined = if (remainingLocal.isEmpty()) remote else remote + remainingLocal
        return CustomerRemoteDataSource.sortMessagesChronologically(combined.distinctBy { it.id })
    }

    /** True when [remote] contains an incoming message not yet present in [current]. */
    fun hasNewIncomingMessages(
        current: List<CustomerMessage>,
        remote: List<CustomerMessage>,
    ): Boolean {
        val existingIds = current.mapTo(HashSet(current.size)) { it.id }
        return remote.any { !it.fromCustomer && !it.isSystem && it.id !in existingIds }
    }

    // Thread ID -> in-memory cached message list
    private val threadMessages = mutableMapOf<String, List<CustomerMessage>>()

    /**
     * Get cached messages for a thread (in-memory first, falling back to disk cache).
     * Guarantees instantaneous loading when re-entering chat.
     */
    fun getCachedMessages(threadId: String, context: Context? = null): List<CustomerMessage> {
        threadMessages[threadId]?.let { if (it.isNotEmpty()) return it }
        if (context != null) {
            val fromDisk = loadFromDisk(context, threadId)
            if (fromDisk.isNotEmpty()) {
                threadMessages[threadId] = fromDisk
                return fromDisk
            }
        }
        return emptyList()
    }

    /**
     * Update cached messages for a thread in-memory and write to disk cache.
     */
    fun setCachedMessages(threadId: String, messages: List<CustomerMessage>, context: Context? = null) {
        val sorted = CustomerRemoteDataSource.sortMessagesChronologically(messages.distinctBy { it.id })
        threadMessages[threadId] = sorted
        if (context != null) {
            saveToDisk(context, threadId, sorted)
        }
    }

    private fun cacheFile(context: Context, threadId: String): File {
        val dir = File(context.filesDir, "chat_cache").apply { if (!exists()) mkdirs() }
        val safeId = threadId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(dir, "msg_$safeId.json")
    }

    private fun saveToDisk(context: Context, threadId: String, messages: List<CustomerMessage>) {
        try {
            val file = cacheFile(context, threadId)
            val arr = JSONArray()
            for (msg in messages) {
                val obj = JSONObject()
                    .put("id", msg.id)
                    .put("text", msg.text)
                    .put("fromCustomer", msg.fromCustomer)
                    .put("createdAt", msg.createdAt ?: JSONObject.NULL)
                    .put("isSystem", msg.isSystem)
                    .put("isRead", msg.isRead)
                    .put("isPending", msg.isPending)
                    .put("clientId", msg.clientId ?: JSONObject.NULL)
                arr.put(obj)
            }
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(arr.toString())
            if (file.exists()) file.delete()
            temp.renameTo(file)
        } catch (_: Exception) {
            // Best effort, ignore errors
        }
    }

    private fun loadFromDisk(context: Context, threadId: String): List<CustomerMessage> {
        try {
            val file = cacheFile(context, threadId)
            if (!file.exists()) return emptyList()
            val text = file.readText()
            val arr = JSONArray(text)
            val list = ArrayList<CustomerMessage>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val body = obj.optString("text")
                if (id.isNullOrBlank() || body.isNullOrBlank()) continue
                list.add(
                    CustomerMessage(
                        id = id,
                        text = body,
                        fromCustomer = obj.optBoolean("fromCustomer", true),
                        createdAt = if (obj.isNull("createdAt")) null else obj.optLong("createdAt"),
                        isSystem = obj.optBoolean("isSystem", false),
                        isRead = obj.optBoolean("isRead", false),
                        isPending = obj.optBoolean("isPending", false),
                        clientId = if (obj.isNull("clientId")) null else obj.optString("clientId"),
                    )
                )
            }
            return CustomerRemoteDataSource.sortMessagesChronologically(list)
        } catch (_: Exception) {
            return emptyList()
        }
    }

    fun clear() {
        conversations.clear()
        threadMessages.clear()
        status = LoadStatus.Idle
    }

    /** Test seam: put conversations into a known state without the network. */
    fun reset(initial: List<CustomerConversation> = emptyList()) {
        conversations.clear()
        threadMessages.clear()
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
