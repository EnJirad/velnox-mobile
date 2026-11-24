package com.velnox.app.core.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAddressStoreTest {

    @Test
    fun `saving addresses and switching defaults works in memory`() {
        val store = LocalAddressStore(context = null)

        val addr1 = CustomerAddress(
            id = "a1",
            label = "Home",
            recipientName = "Somchai",
            phone = "0812345678",
            addressLine = "123 Sukhumvit",
            addressLine2 = "Room 4B",
            subDistrict = "Khlong Toei",
            district = "Khlong Toei",
            province = "Bangkok",
            postalCode = "10110",
            latitude = 13.72,
            longitude = 100.55,
            isDefault = true,
        )

        val id1 = store.save(addr1)
        assertEquals("a1", id1)
        assertEquals(1, store.getAll().size)
        assertTrue(store.get("a1")?.isDefault == true)

        val addr2 = CustomerAddress(
            id = "a2",
            label = "Office",
            recipientName = "Somchai",
            phone = "0812345678",
            addressLine = "456 Silom",
            addressLine2 = null,
            subDistrict = "Silom",
            district = "Bang Rak",
            province = "Bangkok",
            postalCode = "10500",
            latitude = 13.73,
            longitude = 100.53,
            isDefault = true, // should unset addr1's isDefault
        )

        val id2 = store.save(addr2)
        assertEquals("a2", id2)
        assertEquals(2, store.getAll().size)
        assertTrue(store.get("a2")?.isDefault == true)
        assertFalse(store.get("a1")?.isDefault == true)

        // Delete addr2 -> addr1 becomes default
        store.delete("a2")
        assertEquals(1, store.getAll().size)
        assertNull(store.get("a2"))
        assertNotNull(store.get("a1"))
        assertTrue(store.get("a1")?.isDefault == true)
    }
}
