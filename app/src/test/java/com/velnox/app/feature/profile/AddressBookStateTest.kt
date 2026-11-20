package com.velnox.app.feature.profile

import com.velnox.app.core.customer.AddressInput
import com.velnox.app.core.customer.CustomerAddress
import com.velnox.app.core.customer.CustomerError
import com.velnox.app.core.customer.CustomerRepository
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.FakeCustomerApi
import com.velnox.app.core.customer.LoadStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AddressBookStateTest {

    /** A tiny in-memory "server": saves and deletes change what the next GET returns. */
    private class ServerAddresses(initial: List<CustomerAddress>) : FakeCustomerApi() {
        val rows = initial.toMutableList()
        var saved: Triple<String?, AddressInput, Int>? = null
        var failNext: CustomerError? = null
        private var nextId = 100

        override suspend fun addresses(token: String): CustomerResult<List<CustomerAddress>> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it) }
            return CustomerResult.Ok(rows.toList())
        }

        override suspend fun saveAddress(
            token: String,
            addressId: String?,
            input: AddressInput,
        ): CustomerResult<String> {
            failNext?.let { failNext = null; return CustomerResult.Failed(it, "postalCode must be a 5-digit string") }
            val id = addressId ?: "srv-${nextId++}"
            if (input.isDefault) rows.replaceAll { it.copy(isDefault = false) }
            val row = CustomerAddress(
                id = id, label = input.label, recipientName = input.recipientName,
                phone = input.phone, addressLine = input.addressLine, addressLine2 = input.addressLine2,
                subDistrict = input.subDistrict, district = input.district, province = input.province,
                postalCode = input.postalCode, latitude = input.latitude, longitude = input.longitude,
                isDefault = input.isDefault,
            )
            val index = rows.indexOfFirst { it.id == id }
            if (index >= 0) rows[index] = row else rows.add(row)
            saved = Triple(addressId, input, rows.size)
            return CustomerResult.Ok(id)
        }

        override suspend fun deleteAddress(token: String, id: String): CustomerResult<Unit> {
            rows.removeAll { it.id == id }
            return CustomerResult.Ok(Unit)
        }
    }

    private fun row(id: String, default: Boolean = false) = CustomerAddress(
        id = id, label = "Home", recipientName = "Name $id", phone = "0812345678",
        addressLine = "99/1 Sukhumvit", addressLine2 = "Floor 3", subDistrict = "", district = "",
        province = "Bangkok", postalCode = "10110", latitude = 13.7, longitude = 100.5,
        isDefault = default,
    )

    private fun repo(server: ServerAddresses) = CustomerRepository(server, token = { "tok" })

    @Before
    fun setUp() = AddressBookState.reset()

    @After
    fun tearDown() = AddressBookState.reset()

    @Test
    fun `it starts empty - no seeded addresses`() {
        assertTrue(AddressBookState.addresses.isEmpty())
        assertNull(AddressBookState.defaultAddress)
        assertEquals(LoadStatus.Idle, AddressBookState.status)
    }

    @Test
    fun `load mirrors the server and picks the flagged default`() = runBlocking {
        val server = ServerAddresses(listOf(row("a1"), row("a2", default = true)))

        val result = AddressBookState.load(repo(server))

        assertTrue(result is CustomerResult.Ok)
        assertEquals(listOf("a1", "a2"), AddressBookState.addresses.map { it.id })
        assertEquals("a2", AddressBookState.defaultAddress?.id)
        assertEquals(LoadStatus.Ready, AddressBookState.status)
    }

    @Test
    fun `without a flagged default the first address is used`() = runBlocking {
        AddressBookState.load(repo(ServerAddresses(listOf(row("a1"), row("a2")))))

        assertEquals("a1", AddressBookState.defaultAddress?.id)
    }

    @Test
    fun `a failed load keeps what was shown and flags the failure`() = runBlocking {
        val server = ServerAddresses(listOf(row("a1")))
        AddressBookState.load(repo(server))
        server.failNext = CustomerError.Network

        val result = AddressBookState.load(repo(server))

        assertEquals(CustomerResult.Failed(CustomerError.Network), result)
        assertEquals(listOf("a1"), AddressBookState.addresses.map { it.id })
        assertEquals(LoadStatus.Failed, AddressBookState.status)
    }

    @Test
    fun `creating an address posts it without an id and shows the server's list`() = runBlocking {
        val server = ServerAddresses(listOf(row("a1", default = true)))
        AddressBookState.load(repo(server))

        val draft = UserAddress(
            id = "", recipientName = "Test User", phone = "0899999999", addressLine = "999 Rama 9",
            subDistrict = "", district = "", province = "Bangkok", postalCode = "10310", isDefault = true,
        )
        val result = AddressBookState.save(repo(server), draft, existing = false)

        assertTrue(result is CustomerResult.Ok)
        assertNull(server.saved?.first)
        assertEquals("srv-100", AddressBookState.defaultAddress?.id)
        assertFalse(AddressBookState.addresses.first { it.id == "a1" }.isDefault)
    }

    @Test
    fun `editing sends the address id and keeps the fields the app does not edit`() = runBlocking {
        val server = ServerAddresses(listOf(row("a1")))
        AddressBookState.load(repo(server))

        val edited = AddressBookState.addresses.first().copy(recipientName = "Updated Name")
        AddressBookState.save(repo(server), edited, existing = true)

        assertEquals("a1", server.saved?.first)
        val sent = server.saved!!.second
        assertEquals("Home", sent.label)
        assertEquals("Floor 3", sent.addressLine2)
        assertEquals(13.7, sent.latitude!!, 0.0)
        assertEquals("Updated Name", AddressBookState.addresses.single().recipientName)
    }

    @Test
    fun `a rejected save returns the server's reason and leaves the list alone`() = runBlocking {
        val server = ServerAddresses(listOf(row("a1")))
        AddressBookState.load(repo(server))
        server.failNext = CustomerError.Rejected

        val result = AddressBookState.save(repo(server), row("a1").toUserAddress(), existing = true)

        result as CustomerResult.Failed
        assertEquals("postalCode must be a 5-digit string", result.message)
        assertEquals(listOf("a1"), AddressBookState.addresses.map { it.id })
    }

    @Test
    fun `setDefault re-saves the address as the default`() = runBlocking {
        val server = ServerAddresses(listOf(row("a1", default = true), row("a2")))
        AddressBookState.load(repo(server))

        AddressBookState.setDefault(repo(server), AddressBookState.addresses.first { it.id == "a2" })

        assertEquals("a2", AddressBookState.defaultAddress?.id)
        assertFalse(AddressBookState.addresses.first { it.id == "a1" }.isDefault)
    }

    @Test
    fun `delete removes it on the server and re-reads the book`() = runBlocking {
        val server = ServerAddresses(listOf(row("a1", default = true), row("a2")))
        AddressBookState.load(repo(server))

        AddressBookState.delete(repo(server), "a1")

        assertEquals(listOf("a2"), AddressBookState.addresses.map { it.id })
        assertEquals("a2", AddressBookState.defaultAddress?.id)
    }

    @Test
    fun `clear empties the book when the session ends`() = runBlocking {
        AddressBookState.load(repo(ServerAddresses(listOf(row("a1")))))

        AddressBookState.clear()

        assertTrue(AddressBookState.addresses.isEmpty())
        assertEquals(LoadStatus.Idle, AddressBookState.status)
    }

    @Test
    fun `the full address text skips parts the server left empty`() {
        val address = row("a1").toUserAddress()

        assertEquals("99/1 Sukhumvit Floor 3 Bangkok 10110", address.fullAddressText)
        assertEquals(
            "99/1 Sukhumvit Floor 3 แขวงA เขตB Bangkok 10110",
            address.copy(subDistrict = "A", district = "B").fullAddressText,
        )
    }
}
