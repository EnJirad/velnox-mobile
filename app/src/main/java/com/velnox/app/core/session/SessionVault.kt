package com.velnox.app.core.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore-backed storage for the session token.
 *
 * Why not DataStore/SharedPreferences: those are plain files inside the app's
 * private directory, readable from a rooted device or a full backup, and easy to
 * leak through a bug. The token here is encrypted with an AES-256-GCM key that
 * lives in the Android Keystore and never enters the app process, so the
 * ciphertext on disk is useless without the device.
 *
 * Layout of the stored file (`filesDir/session/session.bin`):
 *
 * ```
 * [1 byte version][12 byte GCM IV][ciphertext]
 * ```
 *
 * There is no separate "clear text" copy and no fallback to weaker storage: if
 * the Keystore refuses to produce a key, [write] throws and the caller treats the
 * result as "not signed in" rather than downgrading.
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
            if (version != FORMAT_VERSION_BYTE) return null

            val iv = bytes.copyOfRange(VERSION_BYTES, VERSION_BYTES + IV_BYTES)
            val ciphertext = bytes.copyOfRange(VERSION_BYTES + IV_BYTES, bytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                existingKey() ?: return null,
                GCMParameterSpec(TAG_BITS, iv),
            )
            val plaintext = cipher.doFinal(ciphertext)

            val token = String(plaintext, Charsets.UTF_8)
            if (token.isEmpty()) null else StoredSession(token)
        } catch (_: Exception) {
            // A key that was invalidated (lock screen change, restore to a new
            // device) or a corrupted file both mean the same thing: no session.
            // Drop the useless bytes so the next launch does not retry them.
            files.clear()
            null
        }
    }

    override suspend fun write(session: StoredSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, createKey())
        val ciphertext = cipher.doFinal(session.token.toByteArray(Charsets.UTF_8))

        val payload = ByteArray(VERSION_BYTES + IV_BYTES + ciphertext.size)
        payload[0] = FORMAT_VERSION_BYTE
        cipher.iv.copyInto(payload, VERSION_BYTES)
        ciphertext.copyInto(payload, VERSION_BYTES + IV_BYTES)

        // Throws when the file cannot be made durable, so a caller never reports
        // a session that was not actually saved.
        files.writeBytes(payload)
    }

    override suspend fun clear() {
        try {
            files.clear()
        } catch (_: Exception) {
            // A failed delete cannot make the token readable; read() discards
            // anything it cannot decrypt.
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
                // No user-authentication requirement: the session must restore on
                // a cold start without a biometric prompt, which is the same
                // trade-off every "stay signed in" app makes.
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
        const val FORMAT_VERSION = 1

        /** Same value as [FORMAT_VERSION], typed for the on-disk byte compare. */
        val FORMAT_VERSION_BYTE: Byte = FORMAT_VERSION.toByte()
        const val DIRECTORY = "session"
    }
}
