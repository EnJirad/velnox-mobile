package com.velnox.app.core.session

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The on-disk half of [SessionVault]: replacing the session file without ever
 * losing the previous one.
 *
 * Kept separate from the Keystore crypto so it can run in a JVM unit test. That
 * matters: an earlier `SessionVault.write()` deleted its own temp file before
 * renaming it, so every save threw and the session was never persisted. The unit
 * tests at the time all used a fake in-memory store, so nothing caught it.
 *
 * A second defect then remained: the fixed code did `target.delete()` and only
 * afterwards `temporary.renameTo(target)`. Between those two calls there is no
 * session on disk at all, so a crash or a failing rename destroyed the last
 * valid session — the user was silently signed out.
 *
 * ## Replacement protocol
 *
 * 1. Recover: if the previous replacement died between "set the old file aside"
 *    and "move the new file in", put the backup back ([recoverIfNeeded]).
 * 2. Write the whole payload to `session.tmp` and `fsync` it. The target is not
 *    touched, so any failure here leaves the old session intact.
 * 3. Commit with a single atomic `rename(2)`/`Files.move(ATOMIC_MOVE)`, which is
 *    available on every Android version this app supports (`minSdk` 26). A
 *    rename either replaces the target completely or changes nothing; there is
 *    no window where the file is absent.
 * 4. If the filesystem refuses an atomic move, fall back to backup-and-rollback:
 *    move `session.bin` → `session.bak`, move `session.tmp` → `session.bin`, and
 *    on failure move the backup back. If even the restore fails, the backup
 *    stays on disk and step 1 recovers it on the next read.
 *
 * The temp file is never deleted after a successful write, and the backup is
 * only deleted once the new file is in place — those two deletions are exactly
 * the bugs this class exists to prevent.
 */
internal class SessionFileStore(
    private val directory: File,
    private val fileName: String = FILE_NAME,
    private val tempName: String = TEMP_NAME,
    private val backupName: String = BACKUP_NAME,
    private val replacer: FileReplacer = NioFileReplacer,
) {

    private val target: File get() = File(directory, fileName)
    private val temporary: File get() = File(directory, tempName)
    private val backup: File get() = File(directory, backupName)

    /** The raw stored bytes, or `null` when nothing usable is stored. */
    fun readBytes(): ByteArray? {
        recoverIfNeeded()

        readCandidate(target)?.let { return it }

        // The target is missing or unusable. When a replacement was interrupted,
        // the backup still holds the last session that was known-good, so use it
        // instead of reporting "signed out".
        val recovered = readCandidate(backup) ?: return null
        try {
            replacer.renameReplace(backup, target)
        } catch (_: Exception) {
            // Leaving the backup in place is fine: the next read tries again.
        }
        return recovered
    }

    /**
     * Replace the stored bytes. Throws if the payload cannot be made durable, so
     * the caller can refuse to report a session that was not actually saved. On
     * any failure the previously stored session is still readable.
     */
    fun writeBytes(payload: ByteArray) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Could not create the session directory: $directory")
        }

        // Finish or undo whatever an interrupted write left behind before this
        // write decides what "the previous session" is.
        recoverIfNeeded()

        // A stale temp file is never a session: it may be half written.
        deleteQuietly(temporary)

        writeTemporary(payload)
        commitTemporary()
    }

    /** Remove everything this class owns. Idempotent. */
    fun clear() {
        deleteQuietly(target)
        deleteQuietly(temporary)
        deleteQuietly(backup)
    }

    /**
     * Restore a backup left behind by an interrupted replacement. A backup only
     * exists while a swap is in flight, so a valid target makes it disposable.
     */
    private fun recoverIfNeeded() {
        if (!target.exists() && backup.exists()) {
            try {
                replacer.renameReplace(backup, target)
            } catch (_: Exception) {
                // readBytes() still falls through to the backup itself.
            }
        }
        if (backup.exists() && readCandidate(target) != null) {
            deleteQuietly(backup)
        }
    }

    private fun writeTemporary(payload: ByteArray) {
        // A fresh stream: an existing `session.tmp` was deleted above, so this
        // cannot silently append to a previous attempt.
        val out = FileOutputStream(temporary)
        try {
            out.write(payload)
            out.flush()
            // Durable before it can become the session file.
            out.fd.sync()
        } catch (failure: IOException) {
            // The temp file is never the session, so dropping it is always safe.
            deleteQuietly(temporary)
            throw failure
        } finally {
            try {
                out.close()
            } catch (_: IOException) {
                // The write already failed or succeeded; close errors add nothing.
            }
        }

        if (temporary.length() != payload.size.toLong()) {
            deleteQuietly(temporary)
            throw IOException(
                "Wrote ${temporary.length()} of ${payload.size} bytes to the session temp file",
            )
        }
    }

    private fun commitTemporary() {
        // Fast path: one rename, no window in which the target does not exist.
        val replaced = try {
            replacer.atomicReplace(temporary, target)
        } catch (failure: IOException) {
            // A failed rename() leaves the target exactly as it was.
            deleteQuietly(temporary)
            throw failure
        }
        if (replaced) {
            // Now, and only now, the old copy is redundant.
            deleteQuietly(backup)
            return
        }

        commitWithBackup()
    }

    /**
     * Backup-and-rollback for filesystems that cannot do an atomic replace.
     * The previous session is moved aside rather than deleted, so it can always
     * be put back.
     */
    private fun commitWithBackup() {
        val hadPrevious = target.exists()
        if (hadPrevious) {
            if (!replacer.renameReplace(target, backup)) {
                // The target is untouched, so the old session is still there.
                deleteQuietly(temporary)
                throw IOException("Could not set the previous session file aside")
            }
        }

        if (replacer.renameReplace(temporary, target)) {
            deleteQuietly(backup)
            return
        }

        // The swap failed. Put the previous session back.
        val restored = !hadPrevious || replacer.renameReplace(backup, target)
        if (!restored) {
            // Keep the backup: readBytes() restores it on the next launch, so
            // the last valid session survives even this failure.
            throw IOException("Could not restore the previous session file")
        }

        deleteQuietly(temporary)
        throw IOException("Could not replace the session file")
    }

    private fun readCandidate(file: File): ByteArray? {
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            // A file shorter than the version byte is not a session.
            if (bytes.size < VERSION_BYTES) null else bytes
        } catch (_: IOException) {
            null
        }
    }

    private fun deleteQuietly(file: File) {
        try {
            file.delete()
        } catch (_: SecurityException) {
            // Nothing useful to do: a failed delete must not fake a bad write.
        }
    }

    internal companion object {
        const val FILE_NAME = "session.bin"
        const val TEMP_NAME = "session.tmp"
        const val BACKUP_NAME = "session.bak"
        const val VERSION_BYTES = 1
    }
}

/**
 * The two file operations [SessionFileStore] needs, behind an interface so the
 * rollback paths can be tested. Production uses [NioFileReplacer].
 */
internal interface FileReplacer {

    /**
     * Replace [destination] with [source] in one step. Returns `false` when the
     * filesystem cannot do that atomically, so the caller can fall back. A
     * thrown [IOException] means the attempt failed; the destination is then
     * exactly as it was.
     */
    fun atomicReplace(source: File, destination: File): Boolean

    /**
     * Move [source] over [destination], replacing it. Returns whether it worked
     * instead of throwing, because every caller has a defined fallback.
     */
    fun renameReplace(source: File, destination: File): Boolean
}

/** `java.nio.file` is present from API 26, which is this app's `minSdk`. */
internal object NioFileReplacer : FileReplacer {

    override fun atomicReplace(source: File, destination: File): Boolean =
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            true
        } catch (_: AtomicMoveNotSupportedException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        }

    override fun renameReplace(source: File, destination: File): Boolean =
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            true
        } catch (_: IOException) {
            false
        }
}
