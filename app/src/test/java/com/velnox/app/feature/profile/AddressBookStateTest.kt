package com.velnox.app.feature.profile

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AddressBookStateTest {

    @Before
    fun setUp() {
        AddressBookState.reset()
    }

    @After
    fun tearDown() {
        AddressBookState.reset()
    }

    @Test
    fun initialAddresses_hasDefaultAddress() {
        val defaultAddr = AddressBookState.defaultAddress
        assertNotNull(defaultAddr)
        assertEquals("addr-1", defaultAddr?.id)
        assertTrue(defaultAddr?.isDefault == true)
    }

    @Test
    fun addAddress_asDefault_clearsPreviousDefault() {
        val newAddr = UserAddress(
            id = "addr-new",
            recipientName = "Test User",
            phone = "089-999-9999",
            addressLine = "999 Rama 9",
            subDistrict = "Huai Khwang",
            district = "Huai Khwang",
            province = "Bangkok",
            postalCode = "10310",
            isDefault = true,
        )
        AddressBookState.add(newAddr)

        assertEquals("addr-new", AddressBookState.defaultAddress?.id)
        val oldDefault = AddressBookState.addresses.first { it.id == "addr-1" }
        assertFalse(oldDefault.isDefault)
    }

    @Test
    fun updateAddress_replacesExistingItem() {
        val updated = AddressBookState.addresses.first().copy(
            recipientName = "Updated Name",
            phone = "099-000-0000",
        )
        AddressBookState.update(updated)

        val retrieved = AddressBookState.addresses.first { it.id == updated.id }
        assertEquals("Updated Name", retrieved.recipientName)
        assertEquals("099-000-0000", retrieved.phone)
    }

    @Test
    fun deleteAddress_whenDefaultDeleted_promotesFirstRemainingToDefault() {
        val initialDefaultId = AddressBookState.defaultAddress?.id
        assertNotNull(initialDefaultId)

        AddressBookState.delete(initialDefaultId!!)

        assertFalse(AddressBookState.addresses.any { it.id == initialDefaultId })
        assertTrue(AddressBookState.addresses.isNotEmpty())
        assertTrue(AddressBookState.defaultAddress?.isDefault == true)
        assertEquals("addr-2", AddressBookState.defaultAddress?.id)
    }

    @Test
    fun setDefault_setsOnlyTargetAddressToDefault() {
        AddressBookState.setDefault("addr-2")

        val addr1 = AddressBookState.addresses.first { it.id == "addr-1" }
        val addr2 = AddressBookState.addresses.first { it.id == "addr-2" }

        assertFalse(addr1.isDefault)
        assertTrue(addr2.isDefault)
        assertEquals("addr-2", AddressBookState.defaultAddress?.id)
    }
}
