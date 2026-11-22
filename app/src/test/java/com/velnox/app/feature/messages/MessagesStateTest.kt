package com.velnox.app.feature.messages

import com.velnox.app.core.customer.CustomerConversation
import com.velnox.app.core.customer.CustomerError
import com.velnox.app.core.customer.CustomerMessage
import com.velnox.app.core.customer.CustomerRepository
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.FakeCustomerApi
import com.velnox.app.core.customer.LoadStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MessagesStateTest {

    private class ServerChat(
        initialConversations: List<CustomerConversation> = emptyList(),
        initialMessages: Map<String, List<CustomerMessage>> = emptyMap(),
    ) : FakeCustomerApi() {
        val convs = initialConversations.toMutableList()
        val msgs = initialMessages.mapValues { it.value.toMutableList() }.toMutableMap()
        var failNext: CustomerError? = null
        var markedReadIds = mutableListOf<String>()
        private var nextMsgId = 100

        override suspend fun conversations(token: String): CustomerResult<List<CustomerConversation>> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it) }
            return CustomerResult.Ok(convs.toList())
        }

        override suspend fun startSupportConversation(token: String): CustomerResult<CustomerConversation> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it) }
            val existing = convs.firstOrNull { it.isSupport }
            if (existing != null) return CustomerResult.Ok(existing)
            val created = CustomerConversation(
                id = "sup-1",
                shopId = null,
                shopName = null,
                lastMessage = null,
                lastMessageAt = null,
                unreadCount = 0,
                isSupport = true,
            )
            convs.add(0, created)
            return CustomerResult.Ok(created)
        }

        override suspend fun messages(
            token: String,
            conversationId: String,
        ): CustomerResult<List<CustomerMessage>> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it) }
            return CustomerResult.Ok(msgs[conversationId]?.toList().orEmpty())
        }

        override suspend fun sendMessage(
            token: String,
            conversationId: String,
            text: String,
            clientId: String?,
        ): CustomerResult<CustomerMessage> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it, "Rate limited") }
            val message = CustomerMessage(
                id = "m-${nextMsgId++}",
                text = text,
                fromCustomer = true,
                createdAt = 1_767_323_600_000L,
            )
            msgs.getOrPut(conversationId) { mutableListOf() }.add(message)
            return CustomerResult.Ok(message)
        }

        override suspend fun markConversationRead(
            token: String,
            conversationId: String,
        ): CustomerResult<Unit> {
            markedReadIds += conversationId
            return CustomerResult.Ok(Unit)
        }
    }

    private fun repo(api: FakeCustomerApi) = CustomerRepository(api, token = { "tok" })

    @Before
    fun setUp() = MessagesState.reset()

    @After
    fun tearDown() = MessagesState.reset()

    @Test
    fun `starts empty with zero demo threads`() {
        assertTrue(MessagesState.conversations.isEmpty())
        assertEquals(0, MessagesState.totalUnreadCount)
        assertEquals(LoadStatus.Idle, MessagesState.status)
    }

    @Test
    fun `loadConversations mirrors server threads and tracks totalUnreadCount`() = runBlocking {
        val server = ServerChat(
            initialConversations = listOf(
                CustomerConversation("c1", "s1", "Lumen Shop", "Hello", 1000L, unreadCount = 2),
                CustomerConversation("c2", null, null, "Need help", 2000L, unreadCount = 1, isSupport = true),
            ),
        )

        val res = MessagesState.loadConversations(repo(server))

        assertTrue(res is CustomerResult.Ok)
        assertEquals(LoadStatus.Ready, MessagesState.status)
        assertEquals(2, MessagesState.conversations.size)
        assertEquals(3, MessagesState.totalUnreadCount)
    }

    @Test
    fun `openSupportConversation creates support thread and upserts it into list`() = runBlocking {
        val server = ServerChat()

        val res = MessagesState.openSupportConversation(repo(server))

        res as CustomerResult.Ok
        assertTrue(res.value.isSupport)
        assertEquals("sup-1", MessagesState.conversations.single().id)
    }

    @Test
    fun `sendMessage posts to server, updates conversation preview, and markRead clears unread`() = runBlocking {
        val server = ServerChat(
            initialConversations = listOf(
                CustomerConversation("c1", "s1", "Lumen Shop", "Old", 1000L, unreadCount = 2),
                CustomerConversation("c2", "s2", "Studio", "Latest", 2000L, unreadCount = 0),
            ),
            initialMessages = mapOf(
                "c1" to listOf(CustomerMessage("m1", "Old", fromCustomer = false, createdAt = 1000L)),
            ),
        )
        val r = repo(server)
        MessagesState.loadConversations(r)

        val loaded = MessagesState.loadMessages(r, "c1")
        loaded as CustomerResult.Ok
        assertEquals(1, loaded.value.size)

        assertTrue(MessagesState.markRead(r, "c1") is CustomerResult.Ok)
        assertEquals(listOf("c1"), server.markedReadIds)
        assertEquals(0, MessagesState.totalUnreadCount)

        val sent = MessagesState.sendMessage(r, "c1", "Hi, is this in stock?", clientId = "cid-1")
        sent as CustomerResult.Ok
        assertEquals("Hi, is this in stock?", sent.value.text)
        assertEquals("c1", MessagesState.conversations.first().id)
        assertEquals("Hi, is this in stock?", MessagesState.conversations.first().lastMessage)
    }

    @Test
    fun `failed sendMessage returns failure without mutating conversation preview`() = runBlocking {
        val server = ServerChat(
            initialConversations = listOf(
                CustomerConversation("c1", "s1", "Lumen Shop", "Old", 1000L, unreadCount = 0),
            ),
        )
        val r = repo(server)
        MessagesState.loadConversations(r)
        server.failNext = CustomerError.Rejected

        val res = MessagesState.sendMessage(r, "c1", "Blocked")

        res as CustomerResult.Failed
        assertEquals("Rate limited", res.message)
        assertEquals("Old", MessagesState.conversations.single().lastMessage)
    }

    @Test
    fun `mergePolledMessages keeps in-flight optimistic messages and drops confirmed ones`() {
        val existing = listOf(
            CustomerMessage("m1", "Hello", fromCustomer = false, createdAt = 1000L),
            CustomerMessage("pending-1", "On my way", fromCustomer = true, createdAt = 2000L, isPending = true),
            CustomerMessage("pending-2", "Second msg", fromCustomer = true, createdAt = 3000L, isPending = true),
        )
        val remote = listOf(
            CustomerMessage("m1", "Hello", fromCustomer = false, createdAt = 1000L),
            CustomerMessage("m2", "On my way", fromCustomer = true, createdAt = 2100L, isRead = true),
            CustomerMessage("m3", "Sure thing!", fromCustomer = false, createdAt = 2500L),
        )

        val merged = MessagesState.mergePolledMessages(existing, remote)

        assertEquals(listOf("m1", "m2", "m3", "pending-2"), merged.map { it.id })
        assertTrue(merged.last().isPending)
        assertTrue(MessagesState.hasNewIncomingMessages(existing, remote))
    }

    @Test
    fun `mergePolledMessages preserves failed retryable messages until confirmed`() {
        val existing = listOf(
            CustomerMessage("m1", "Hello", fromCustomer = false, createdAt = 1000L),
            CustomerMessage("pending-f", "Failed send", fromCustomer = true, createdAt = 2000L, isPending = false, isFailed = true, failureReason = "Network error"),
        )
        val remote = listOf(
            CustomerMessage("m1", "Hello", fromCustomer = false, createdAt = 1000L),
        )

        val merged = MessagesState.mergePolledMessages(existing, remote)

        assertEquals(2, merged.size)
        assertEquals("pending-f", merged[1].id)
        assertTrue(merged[1].isFailed)
        assertEquals("Network error", merged[1].failureReason)
    }

    @Test
    fun `continuous consecutive message sending works seamlessly without lockout`() = runBlocking {
        val server = ServerChat(
            initialConversations = listOf(
                CustomerConversation("c1", "s1", "Lumen Shop", "Hello", 1000L, unreadCount = 0),
            ),
        )
        val r = repo(server)
        MessagesState.loadConversations(r)

        val res1 = MessagesState.sendMessage(r, "c1", "First message", clientId = "cid-1")
        val res2 = MessagesState.sendMessage(r, "c1", "Second message immediately", clientId = "cid-2")
        val res3 = MessagesState.sendMessage(r, "c1", "Third message immediately", clientId = "cid-3")

        assertTrue(res1 is CustomerResult.Ok)
        assertTrue(res2 is CustomerResult.Ok)
        assertTrue(res3 is CustomerResult.Ok)
        assertEquals("Third message immediately", MessagesState.conversations.first().lastMessage)
    }
}
