package com.velnox.app.feature.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.velnox.app.core.customer.AddressInput
import com.velnox.app.core.customer.CustomerAddress
import com.velnox.app.core.customer.CustomerRepository
import com.velnox.app.core.customer.CustomerResult
import com.velnox.app.core.customer.LoadStatus

/**
 * A user's shipping/delivery address, as stored on the server.
 *
 * [label], [addressLine2], [latitude] and [longitude] are not edited in the app;
 * they are carried so that saving an edit does not erase what the website set.
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
    val label: String? = null,
    val addressLine2: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    /** Non-empty parts only: the server does not require sub-district or district. */
    val fullAddressText: String
        get() = listOf(
            addressLine,
            addressLine2.orEmpty(),
            if (subDistrict.isBlank()) "" else "แขวง$subDistrict",
            if (district.isBlank()) "" else "เขต$district",
            province,
            postalCode,
        ).filter { it.isNotBlank() }.joinToString(" ")

    val shortSummary: String
        get() = "$recipientName · $phone\n$fullAddressText"
}

fun CustomerAddress.toUserAddress(): UserAddress = UserAddress(
    id = id,
    recipientName = recipientName,
    phone = phone,
    addressLine = addressLine,
    subDistrict = subDistrict,
    district = district,
    province = province,
    postalCode = postalCode,
    isDefault = isDefault,
    label = label,
    addressLine2 = addressLine2,
    latitude = latitude,
    longitude = longitude,
)

fun UserAddress.toInput(): AddressInput = AddressInput(
    label = label,
    recipientName = recipientName,
    phone = phone,
    addressLine = addressLine,
    addressLine2 = addressLine2,
    subDistrict = subDistrict,
    district = district,
    province = province,
    postalCode = postalCode,
    latitude = latitude,
    longitude = longitude,
    isDefault = isDefault,
)

/**
 * The signed-in user's address book, shared by Settings, Profile and Checkout.
 *
 * It is a mirror of `GET /api/customer/addresses` and nothing else: it starts
 * empty, every change goes to the server first, and the list shown afterwards is
 * the server's. [clear] drops it when the session ends so one account's
 * addresses are never visible to the next.
 */
object AddressBookState {

    val addresses = mutableStateListOf<UserAddress>()

    var status by mutableStateOf(LoadStatus.Idle)
        private set

    val defaultAddress: UserAddress?
        get() = addresses.firstOrNull { it.isDefault } ?: addresses.firstOrNull()

    /** Replace the list with the server's. A failure keeps what is shown and flags [status]. */
    suspend fun load(repository: CustomerRepository): CustomerResult<Unit> {
        status = LoadStatus.Loading
        return when (val result = repository.call { api, token -> api.addresses(token) }) {
            is CustomerResult.Ok -> {
                replaceAll(result.value.map { it.toUserAddress() })
                status = LoadStatus.Ready
                CustomerResult.Ok(Unit)
            }

            is CustomerResult.Failed -> {
                status = LoadStatus.Failed
                result
            }
        }
    }

    /** Create ([existing] = false) or overwrite an address, then re-read the book. */
    suspend fun save(
        repository: CustomerRepository,
        address: UserAddress,
        existing: Boolean,
    ): CustomerResult<Unit> {
        val saved = repository.call { api, token ->
            api.saveAddress(token, address.id.takeIf { existing }, address.toInput())
        }
        if (saved is CustomerResult.Failed) return saved
        return load(repository)
    }

    suspend fun setDefault(repository: CustomerRepository, address: UserAddress): CustomerResult<Unit> =
        save(repository, address.copy(isDefault = true), existing = true)

    suspend fun delete(repository: CustomerRepository, id: String): CustomerResult<Unit> {
        val deleted = repository.call { api, token -> api.deleteAddress(token, id) }
        if (deleted is CustomerResult.Failed) return deleted
        return load(repository)
    }

    fun clear() {
        addresses.clear()
        status = LoadStatus.Idle
    }

    /** Test seam: put the book into a known state without the network. */
    fun reset(initial: List<UserAddress> = emptyList()) {
        replaceAll(initial)
        status = if (initial.isEmpty()) LoadStatus.Idle else LoadStatus.Ready
    }

    private fun replaceAll(items: List<UserAddress>) {
        addresses.clear()
        addresses.addAll(items)
    }
}
