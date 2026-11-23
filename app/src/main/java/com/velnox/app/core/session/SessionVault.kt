package com.velnox.app.core.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore-backed storage for the session token.
 *
 * Supports FORMAT_VERSION 2 (encrypted JSON containing accessToken, refreshToken,
 * expiresAtMillis, backendFlavor) with seamless backward compatibility for
 * FORMAT_VERSION 1 (raw token UTF-8).
 */
class SessionVault(private val context: Context) : SessionStore {

    // All file handling lives in SessionFileStore so its temp-file/rename
    // protocol can be unit-tested; this class only does Keystore crypto.
    private val files = SessionFileStore(File(context.filesDir, DIRECTORY))

    override suspend fun read(): StoredSession? {
        val bytes = files.readBytes() ?: return null
        if (bytes.size <= VERSION_BYTES + IV_BYTES) return null

        return try {
            val version = bytes[0]
            if (version != FORMAT_VERSION_V1_BYTE && version != FORMAT_VERSION_V2_BYTE) return null

            val iv = bytes.copyOfRange(VERSION_BYTES, VERSION_BYTES + IV_BYTES)
            val ciphertext = bytes.copyOfRange(VERSION_BYTES + IV_BYTES, bytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                existingKey() ?: return null,
                GCMParameterSpec(TAG_BITS, iv),
            )
            val plaintext = cipher.doFinal(ciphertext)

            if (version == FORMAT_VERSION_V1_BYTE) {
                val token = String(plaintext, Charsets.UTF_8)
                if (token.isEmpty()) null else StoredSession(token)
            } else {
                val jsonStr = String(plaintext, Charsets.UTF_8)
                if (jsonStr.isEmpty()) null else {
                    val json = JSONObject(jsonStr)
                    val token = json.optString("token")
                    if (token.isEmpty()) null else {
                        StoredSession(
                            token = token,
                            refreshToken = json.optString("refreshToken").takeIf { it.isNotEmpty() },
                            expiresAtMillis = if (json.has("expiresAtMillis") && !json.isNull("expiresAtMillis")) {
                                json.optLong("expiresAtMillis")
                            } else null,
                            backendFlavor = json.optString("backendFlavor").takeIf { it.isNotEmpty() },
                        )
                    }
                }
            }
        } catch (_: Exception) {
            files.clear()
            null
        }
    }

    override suspend fun write(session: StoredSession) {
        val json = JSONObject().apply {
            put("token", session.token)
            session.refreshToken?.let { put("refreshToken", it) }
            session.expiresAtMillis?.let { put("expiresAtMillis", it) }
            session.backendFlavor?.let { put("backendFlavor", it) }
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, createKey())
        val ciphertext = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))

        val payload = ByteArray(VERSION_BYTES + IV_BYTES + ciphertext.size)
        payload[0] = FORMAT_VERSION_V2_BYTE
        cipher.iv.copyInto(payload, VERSION_BYTES)
        ciphertext.copyInto(payload, VERSION_BYTES + IV_BYTES)

        files.writeBytes(payload)
    }

    override suspend fun clear() {
        try {
            files.clear()
        } catch (_: Exception) {
        }
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? {
        val store = keyStore()
        if (!store.containsAlias(KEY_ALIAS)) return null
        return (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun createKey(): SecretKey {
        existingKey()?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "velnox.session.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_BYTES = 12
        const val VERSION_BYTES = 1

        const val FORMAT_VERSION_V1 = 1
        const val FORMAT_VERSION_V2 = 2

        val FORMAT_VERSION_V1_BYTE: Byte = FORMAT_VERSION_V1.toByte()
        val FORMAT_VERSION_V2_BYTE: Byte = FORMAT_VERSION_V2.toByte()

        const val DIRECTORY = "session"
    }
}
