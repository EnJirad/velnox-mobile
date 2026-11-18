package com.velnox.app.core.session

import java.io.File
import java.io.IOException

/**
 * The on-disk half of [SessionVault]: an all-or-nothing file replace.
 *
 * Kept separate from the Keystore crypto so it can run in a JVM unit test. That
 * matters: an earlier `SessionVault.write()` deleted its own temp file before
 * renaming it, so every save threw and the session was never persisted. The unit
 * tests at the time all used a fake in-memory store, so nothing caught it.
 *
 * Protocol:
 * 1. delete a temp file left behind by an interrupted write,
 * 2. write the new payload to `session.tmp`,
 * 3. delete the old `session.bin`,
 * 4. rename temp → target, so a reader never sees a half-written file.
 *
 * Never deletes the temp file after writing it; that is exactly the bug this
 * class exists to prevent.
 */
internal class SessionFileStore(
    private val directory: File,
    private val fileName: String = FILE_NAME,
    private val tempName: String = TEMP_NAME,
) {

    private val target: File get() = File(directory, fileName)
    private val temporary: File get() = File(directory, tempName)

    /** The raw stored bytes, or `null` when nothing usable is stored. */
    fun readBytes(): ByteArray? {
        if (!target.exists()) return null
        return try {
            val bytes = target.readBytes()
            // A file shorter than the version byte is not a session.
            if (bytes.size < VERSION_BYTES) null else bytes
        } catch (_: IOException) {
            null
        }
    }

    /**
     * Replace the stored bytes. Throws if the payload cannot be made durable, so
     * the caller can refuse to report a session that was not actually saved.
     */
    fun writeBytes(payload: ByteArray) {
        directory.mkdirs()

        // (1) Clear anything an interrupted previous write left behind.
        temporary.delete()

        // (2) Write the whole payload to the temp file and flush it to the device.
        temporary.outputStream().use { out ->
            out.write(payload)
            out.flush()
            out.fd.sync()
        }

        // (3) + (4) Swap it in. `renameTo` will not replace an existing file on
        // every filesystem, so the old target goes first.
        val wrote = temporary.length() == payload.size.toLong()
        if (!wrote) {
            temporary.delete()
            throw IllegalStateException("Could not persist the session")
        }

        target.delete()
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IllegalStateException("Could not persist the session")
        }
    }

    /** Remove everything this class owns. Idempotent. */
    fun clear() {
        target.delete()
        temporary.delete()
    }

    internal companion object {
        const val FILE_NAME = "session.bin"
        const val TEMP_NAME = "session.tmp"
        const val VERSION_BYTES = 1
    }
}
