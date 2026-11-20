package com.velnox.app.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressFormValidatorTest {

    @Test
    fun `validates 10-digit phone numbers with or without formatting`() {
        assertTrue(AddressFormValidator.isValidPhone("0812345678"))
        assertTrue(AddressFormValidator.isValidPhone("081-234-5678"))
        assertTrue(AddressFormValidator.isValidPhone("081 234 5678"))
        assertEquals("0812345678", AddressFormValidator.normalizePhone("081-234-5678"))

        assertFalse(AddressFormValidator.isValidPhone("081234567"))
        assertFalse(AddressFormValidator.isValidPhone("08123456789"))
        assertFalse(AddressFormValidator.isValidPhone(""))
    }

    @Test
    fun `validates 5-digit postal codes`() {
        assertTrue(AddressFormValidator.isValidPostalCode("10110"))
        assertTrue(AddressFormValidator.isValidPostalCode(" 50000 "))

        assertFalse(AddressFormValidator.isValidPostalCode("1011"))
        assertFalse(AddressFormValidator.isValidPostalCode("101100"))
        assertFalse(AddressFormValidator.isValidPostalCode("1011A"))
    }

    @Test
    fun `validate returns exact error state for missing, phone, and postal issues`() {
        assertEquals(
            AddressValidationResult.MissingRequired,
            AddressFormValidator.validate("", "0812345678", "99/1 Sukhumvit", "Bangkok", "10110"),
        )
        assertEquals(
            AddressValidationResult.InvalidPhone,
            AddressFormValidator.validate("Somchai", "08123", "99/1 Sukhumvit", "Bangkok", "10110"),
        )
        assertEquals(
            AddressValidationResult.InvalidPostalCode,
            AddressFormValidator.validate("Somchai", "0812345678", "99/1 Sukhumvit", "Bangkok", "101"),
        )
        assertEquals(
            AddressValidationResult.Valid,
            AddressFormValidator.validate("Somchai", "081-234-5678", "99/1 Sukhumvit", "Bangkok", "10110"),
        )
    }

    @Test
    fun `UserAddress toInput forwards tag, line2, latitude and longitude`() {
        val addr = UserAddress(
            id = "a-1",
            recipientName = "Ann Suriya",
            phone = "0891234567",
            addressLine = "88 Rama 9 Rd",
            subDistrict = "Huai Khwang",
            district = "Huai Khwang",
            province = "Bangkok",
            postalCode = "10310",
            isDefault = true,
            label = AddressTags.OFFICE,
            addressLine2 = "Tower B Floor 14",
            latitude = 13.756301,
            longitude = 100.501802,
        )

        val input = addr.toInput()
        assertEquals(AddressTags.OFFICE, input.label)
        assertEquals("Tower B Floor 14", input.addressLine2)
        assertEquals(13.756301, input.latitude!!, 0.000001)
        assertEquals(100.501802, input.longitude!!, 0.000001)
    }
}
