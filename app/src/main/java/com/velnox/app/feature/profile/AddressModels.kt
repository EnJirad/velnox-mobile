package com.velnox.app.feature.profile

import androidx.compose.runtime.mutableStateListOf

/**
 * A user's shipping/delivery address.
 */
data class UserAddress(
    val id: String,
    val recipientName: String,
    val phone: String,
    val addressLine: String,
    val subDistrict: String,
    val district: String,
    val province: String,
    val postalCode: String,
    val isDefault: Boolean = false,
) {
    val fullAddressText: String
        get() = "$addressLine แขวง$subDistrict เขต$district $province $postalCode"

    val shortSummary: String
        get() = "$recipientName · $phone\n$fullAddressText"
}

/**
 * Shared state for the user's shipping addresses, accessible across Settings, Profile, and Checkout.
 */
object AddressBookState {

    val addresses = mutableStateListOf(
        UserAddress(
            id = "addr-1",
            recipientName = "สมชาย ใจดี",
            phone = "081-234-5678",
            addressLine = "123/45 ถนนสุขุมวิท",
            subDistrict = "คลองเตย",
            district = "คลองเตย",
            province = "กรุงเทพมหานคร",
            postalCode = "10110",
            isDefault = true,
        ),
        UserAddress(
            id = "addr-2",
            recipientName = "สมชาย ใจดี (ที่ทำงาน)",
            phone = "081-234-5678",
            addressLine = "88 อาคารเอ็มโพเรียม ชั้น 15 ถนนสุขุมวิท",
            subDistrict = "คลองตัน",
            district = "คลองเตย",
            province = "กรุงเทพมหานคร",
            postalCode = "10110",
            isDefault = false,
        ),
    )

    val defaultAddress: UserAddress?
        get() = addresses.firstOrNull { it.isDefault } ?: addresses.firstOrNull()

    fun add(address: UserAddress) {
        if (address.isDefault) {
            addresses.replaceAll { it.copy(isDefault = false) }
        }
        addresses.add(0, address)
    }

    fun update(address: UserAddress) {
        if (address.isDefault) {
            addresses.replaceAll { it.copy(isDefault = false) }
        }
        val idx = addresses.indexOfFirst { it.id == address.id }
        if (idx >= 0) {
            addresses[idx] = address
        }
    }

    fun delete(id: String) {
        addresses.removeAll { it.id == id }
        if (addresses.none { it.isDefault } && addresses.isNotEmpty()) {
            addresses[0] = addresses[0].copy(isDefault = true)
        }
    }

    fun setDefault(id: String) {
        addresses.replaceAll { it.copy(isDefault = it.id == id) }
    }

    fun reset(initialList: List<UserAddress>? = null) {
        addresses.clear()
        if (initialList != null) {
            addresses.addAll(initialList)
        } else {
            addresses.addAll(
                listOf(
                    UserAddress(
                        id = "addr-1",
                        recipientName = "สมชาย ใจดี",
                        phone = "081-234-5678",
                        addressLine = "123/45 ถนนสุขุมวิท",
                        subDistrict = "คลองเตย",
                        district = "คลองเตย",
                        province = "กรุงเทพมหานคร",
                        postalCode = "10110",
                        isDefault = true,
                    ),
                    UserAddress(
                        id = "addr-2",
                        recipientName = "สมชาย ใจดี (ที่ทำงาน)",
                        phone = "081-234-5678",
                        addressLine = "88 อาคารเอ็มโพเรียม ชั้น 15 ถนนสุขุมวิท",
                        subDistrict = "คลองตัน",
                        district = "คลองเตย",
                        province = "กรุงเทพมหานคร",
                        postalCode = "10110",
                        isDefault = false,
                    ),
                )
            )
        }
    }
}
