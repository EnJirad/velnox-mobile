package com.velnox.app.core.customer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Local persistent storage for customer addresses.
 *
 * Ensures that address management (add, edit, set default, delete, select at checkout)
 * functions reliably and persists across app restarts, even when the remote API
 * endpoint is unreachable, returning 404, or running offline.
 */
class LocalAddressStore(private val context: Context? = null) {

    private val memoryStore = linkedMapOf<String, CustomerAddress>()
    private val lock = Any()

    init {
        loadFromDisk()
    }

    fun getAll(): List<CustomerAddress> = synchronized(lock) {
        memoryStore.values.toList()
    }

    fun get(id: String): CustomerAddress? = synchronized(lock) {
        memoryStore[id]
    }

    fun save(address: CustomerAddress): String = synchronized(lock) {
        val id = if (address.id.isNotBlank()) {
            address.id
        } else {
            "addr_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        }
        val updated = address.copy(id = id)

        if (updated.isDefault) {
            val keys = memoryStore.keys.toList()
            for (k in keys) {
                val existing = memoryStore[k]
                if (existing != null && existing.isDefault && existing.id != id) {
                    memoryStore[k] = existing.copy(isDefault = false)
                }
            }
        } else if (memoryStore.isEmpty()) {
            // If it's the very first address, default to true
            memoryStore[id] = updated.copy(isDefault = true)
            persistToDisk()
            return id
        }

        memoryStore[id] = updated
        persistToDisk()
        id
    }

    fun delete(id: String) = synchronized(lock) {
        val removed = memoryStore.remove(id)
        if (removed != null && removed.isDefault && memoryStore.isNotEmpty()) {
            val firstKey = memoryStore.keys.first()
            val first = memoryStore[firstKey]
            if (first != null) {
                memoryStore[firstKey] = first.copy(isDefault = true)
            }
        }
        persistToDisk()
    }

    fun replaceAll(list: List<CustomerAddress>) = synchronized(lock) {
        memoryStore.clear()
        for (addr in list) {
            memoryStore[addr.id] = addr
        }
        persistToDisk()
    }

    private fun getStorageFile(): File? {
        val dir = context?.filesDir ?: return null
        return File(dir, "velnox_customer_addresses.json")
    }

    private fun loadFromDisk() {
        val file = getStorageFile() ?: return
        if (!file.exists()) return
        try {
            val content = file.readText()
            val array = JSONArray(content)
            synchronized(lock) {
                memoryStore.clear()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val addr = parseJsonAddress(obj)
                    if (addr != null) {
                        memoryStore[addr.id] = addr
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore parse errors on corrupted local cache
        }
    }

    private fun persistToDisk() {
        val file = getStorageFile() ?: return
        try {
            val array = JSONArray()
            for (addr in memoryStore.values) {
                val obj = JSONObject()
                    .put("id", addr.id)
                    .put("recipientName", addr.recipientName)
                    .put("phone", addr.phone)
                    .put("addressLine", addr.addressLine)
                    .put("subDistrict", addr.subDistrict)
                    .put("district", addr.district)
                    .put("province", addr.province)
                    .put("postalCode", addr.postalCode)
                    .put("isDefault", addr.isDefault)
                addr.label?.let { obj.put("label", it) }
                addr.addressLine2?.let { obj.put("addressLine2", it) }
                addr.latitude?.let { obj.put("latitude", it) }
                addr.longitude?.let { obj.put("longitude", it) }
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (_: Exception) {
            // Non-fatal if local write fails
        }
    }

    companion object {
        fun parseJsonAddress(obj: JSONObject): CustomerAddress? {
            val id = obj.optString("id").takeIf { it.isNotBlank() }
                ?: obj.optString("addressId").takeIf { it.isNotBlank() }
                ?: return null
            return CustomerAddress(
                id = id,
                label = obj.optString("label").takeIf { it.isNotBlank() },
                recipientName = obj.optString("recipientName").ifBlank { obj.optString("name") },
                phone = obj.optString("phone"),
                addressLine = obj.optString("addressLine").ifBlank { obj.optString("line1") },
                addressLine2 = obj.optString("addressLine2").takeIf { it.isNotBlank() },
                subDistrict = obj.optString("subDistrict").ifBlank { obj.optString("subdistrict") },
                district = obj.optString("district"),
                province = obj.optString("province"),
                postalCode = obj.optString("postalCode").ifBlank { obj.optString("postal_code") },
                latitude = if (obj.has("latitude") && !obj.isNull("latitude")) obj.optDouble("latitude") else null,
                longitude = if (obj.has("longitude") && !obj.isNull("longitude")) obj.optDouble("longitude") else null,
                isDefault = obj.optBoolean("isDefault", false),
            )
        }
    }
}
