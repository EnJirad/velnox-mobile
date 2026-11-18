package com.velnox.app.core.session

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * The file protocol [SessionVault] depends on.
 *
 * Two real defects are covered here:
 *
 * 1. `SessionVault.write()` used to call the "clear everything" helper after
 *    writing its temp file, which deleted that temp file, so `renameTo` always
 *    failed and **every** session save threw. Signing in succeeded, the token was
 *    never persisted, and the app silently fell back to signed-out on the next
 *    launch.
 * 2. The repair then did `target.delete()` followed by `temporary.renameTo(target)`.
 *    Between those calls no session file existed at all, so a crash or a failing
 *    rename destroyed the last valid session.
 *
 * Both survived because every session test used a fake in-memory `SessionStore`,
 * which has no files. These tests use a real directory and the production
 * [SessionFileStore], so the ordering cannot regress unnoticed.
 */
class SessionFileStoreTest {

    private val previous = "previous.header.payload.signature".toByteArray(Charsets.UTF_8)
    private val replacement = "replacement.header.payload.signature".toByteArray(Charsets.UTF_8)

    private fun tempDir(): File = Files.createTempDirectory("velnox-vault").toFile()

    // -- creation and replacement -------------------------------------------

    @Test
    fun `the first write creates the session file and nothing else`() {
        val dir = tempDir()
        val store = SessionFileStore(dir)

        store.writeBytes(previous)

        assertBytes(previous, store.readBytes())
        assertTrue(File(dir, SessionFileStore.FILE_NAME).exists())
        assertFalse(File(dir, SessionFileStore.TEMP_NAME).exists())
        assertFalse(File(dir, SessionFileStore.BACKUP_NAME).exists())
    }

    @Test
    fun `write then read round-trips the payload`() {
        val dir = tempDir()
        val store = SessionFileStore(dir)

        store.writeBytes(previous)

        assertBytes(previous, store.readBytes())
    }

    @Test
    fun `replacing an existing session keeps only the new payload`() {
        val dir = tempDir()
        val store = SessionFileStore(dir)
        store.writeBytes(previous)

        store.writeBytes(replacement)

        assertBytes(replacement, store.readBytes())
        // A completed replacement must not leave the old copy or the temp file.
        assertFalse(File(dir, SessionFileStore.TEMP_NAME).exists())
        assertFalse(File(dir, SessionFileStore.BACKUP_NAME).exists())
    }

    @Test
    fun `a completed write leaves no temp file behind`() {
        val dir = tempDir()
        val store = SessionFileStore(dir)

        store.writeBytes(previous)

        assertTrue(File(dir, SessionFileStore.FILE_NAME).exists())
        assertFalse(
            "session.tmp must not survive: deleting it after writing is the bug that broke persistence",
            File(dir, SessionFileStore.TEMP_NAME).exists(),
        )
    }

    @Test
    fun `a second store instance reads what the first wrote`() {
        val dir = tempDir()
        SessionFileStore(dir).writeBytes(previous)

        // What a cold start does: a fresh SessionVault over the same directory.
        assertBytes(previous, SessionFileStore(dir).readBytes())
    }

    // -- failure paths -------------------------------------------------------

    @Test
    fun `a failed temp-file write keeps the previous session`() {
        val dir = tempDir()
        val store = SessionFileStore(dir)
        store.writeBytes(previous)

        // A non-empty directory where session.tmp should be: it cannot be
        // deleted, and FileOutputStream cannot open it.
        val blockedTemp = File(dir, SessionFileStore.TEMP_NAME)
        assertTrue(blockedTemp.mkdirs())
        File(blockedTemp, "keep").writeText("x")

        assertThrows(IOException::class.java) { store.writeBytes(replacement) }

        assertBytes(previous, store.readBytes())
        assertTrue(File(dir, SessionFileStore.FILE_NAME).exists())
    }

    @Test
    fun `a failed replacement keeps the previous session`() {
        val dir = tempDir()
        SessionFileStore(dir).writeBytes(previous)

        val store = SessionFileStore(
            dir,
            replacer = ScriptedReplacer(atomicSupported = false, failTemporaryToTarget = true),
        )

        assertThrows(IOException::class.java) { store.writeBytes(replacement) }

        // The rollback put the old file back; the user stays signed in.
        assertBytes(previous, store.readBytes())
        assertTrue(File(dir, SessionFileStore.FILE_NAME).exists())
        assertFalse(File(dir, SessionFileStore.TEMP_NAME).exists())
        assertFalse(File(dir, SessionFileStore.BACKUP_NAME).exists())
    }

    @Test
    fun `a replacement that cannot roll back still leaves the session recoverable`() {
        val dir = tempDir()
        SessionFileStore(dir).writeBytes(previous)

        val store = SessionFileStore(
            dir,
            replacer = ScriptedReplacer(
                atomicSupported = false,
                failTemporaryToTarget = true,
                failRollback = true,
            ),
        )

        assertThrows(IOException::class.java) { store.writeBytes(replacement) }

        // Worst case: no target and no way to restore it right now. The backup
        // is still on disk, which is exactly what the next launch reads.
        assertFalse(File(dir, SessionFileStore.FILE_NAME).exists())
        assertTrue(File(dir, SessionFileStore.BACKUP_NAME).exists())

        val nextLaunch = SessionFileStore(dir)
        assertBytes(previous, nextLaunch.readBytes())
        assertTrue(File(dir, SessionFileStore.FILE_NAME).exists())
        assertFalse(File(dir, SessionFileStore.BACKUP_NAME).exists())
    }

    @Test
    fun `a directory that cannot be created reports failure instead of pretending`() {
        // A regular file where the directory should be: mkdirs() fails and the
        // write must throw, because the caller relies on that to avoid reporting
        // a session it did not store.
        val blocker = File(Files.createTempDirectory("velnox-blocked").toFile(), "not-a-dir")
        blocker.writeText("x")
        val store = SessionFileStore(File(blocker, "session"))

        assertThrows(Exception::class.java) { store.writeBytes(previous) }
    }

    // -- recovery ------------------------------------------------------------

    @Test
    fun `an interrupted replacement is recovered from the backup`() {
        val dir = tempDir()
        // The exact on-disk state of a crash between "move the old file aside"
        // and "move the new file in": no target, a complete backup, a partial temp.
        File(dir, SessionFileStore.BACKUP_NAME).writeBytes(previous)
        File(dir, SessionFileStore.TEMP_NAME).writeBytes(replacement.copyOfRange(0, 3))

        val store = SessionFileStore(dir)

        assertBytes(previous, store.readBytes())
        assertTrue(File(dir, SessionFileStore.FILE_NAME).exists())
        assertFalse(File(dir, SessionFileStore.BACKUP_NAME).exists())

        // And the store is usable afterwards.
        store.writeBytes(replacement)
        assertBytes(replacement, store.readBytes())
    }

    @Test
    fun `a stale temp file from an interrupted write does not block the next one`() {
        val dir = tempDir()
        File(dir, SessionFileStore.TEMP_NAME).writeText("left over after a crash")
        val store = SessionFileStore(dir)

        store.writeBytes(previous)

        assertBytes(previous, store.readBytes())
        assertFalse(File(dir, SessionFileStore.TEMP_NAME).exists())
    }

    @Test
    fun `a stale temp file never replaces a valid session`() {
        val dir = tempDir()
        SessionFileStore(dir).writeBytes(previous)
        File(dir, SessionFileStore.TEMP_NAME).writeBytes(replacement)

        assertBytes(previous, SessionFileStore(dir).readBytes())
    }

    // -- corrupt files -------------------------------------------------------

    @Test
    fun `a zero-length file reads as null rather than an empty session`() {
        val dir = tempDir()
        File(dir, SessionFileStore.FILE_NAME).writeBytes(ByteArray(0))

        assertNull(SessionFileStore(dir).readBytes())
    }

    @Test
    fun `a corrupt session file falls back to the backup`() {
        val dir = tempDir()
        File(dir, SessionFileStore.FILE_NAME).writeBytes(ByteArray(0))
        File(dir, SessionFileStore.BACKUP_NAME).writeBytes(previous)

        val store = SessionFileStore(dir)

        assertBytes(previous, store.readBytes())
        // Restored, so the next read does not need the backup again.
        assertBytes(previous, store.readBytes())
    }

    @Test
    fun `an unreadable directory reports no session instead of throwing`() {
        val missing = File(tempDir(), "never-created")

        assertNull(SessionFileStore(missing).readBytes())
    }

    // -- clearing ------------------------------------------------------------

    @Test
    fun `clear removes the session, temp and backup files and is idempotent`() {
        val dir = tempDir()
        val store = SessionFileStore(dir)
        store.writeBytes(previous)
        File(dir, SessionFileStore.TEMP_NAME).writeText("stale")
        File(dir, SessionFileStore.BACKUP_NAME).writeBytes(previous)

        store.clear()
        store.clear()

        assertNull(store.readBytes())
        assertFalse(File(dir, SessionFileStore.FILE_NAME).exists())
        assertFalse(File(dir, SessionFileStore.TEMP_NAME).exists())
        assertFalse(File(dir, SessionFileStore.BACKUP_NAME).exists())
    }

    @Test
    fun `clear works when nothing was ever stored`() {
        val dir = tempDir()

        SessionFileStore(dir).clear()

        assertNull(SessionFileStore(dir).readBytes())
    }

    // -- the atomic path -----------------------------------------------------

    @Test
    fun `an atomic replace is used when the filesystem supports it`() {
        val dir = tempDir()
        SessionFileStore(dir).writeBytes(previous)
        val replacer = ScriptedReplacer(atomicSupported = true)

        SessionFileStore(dir, replacer = replacer).writeBytes(replacement)

        assertEquals("the atomic path must be tried first", 1, replacer.atomicAttempts)
        assertEquals("no backup is needed when the swap is atomic", 0, replacer.renames)
        assertBytes(replacement, SessionFileStore(dir).readBytes())
        assertFalse(File(dir, SessionFileStore.BACKUP_NAME).exists())
    }

    @Test
    fun `the store is usable from a suspend context like the vault`() = runBlocking {
        val dir = tempDir()
        val store = SessionFileStore(dir)

        // Mirrors SessionVault.write/read, which are suspend functions.
        store.writeBytes(previous)
        assertEquals(previous.size, store.readBytes()?.size)
    }

    private fun assertBytes(expected: ByteArray, actual: ByteArray?) {
        assertEquals(expected.toList(), actual?.toList())
    }

    /**
     * A [FileReplacer] that can fail at one chosen step of the swap, so the
     * rollback paths are exercised rather than trusted.
     */
    private class ScriptedReplacer(
        private val atomicSupported: Boolean,
        private val failTemporaryToTarget: Boolean = false,
        private val failRollback: Boolean = false,
    ) : FileReplacer {

        var atomicAttempts = 0
            private set
        var renames = 0
            private set

        override fun atomicReplace(source: File, destination: File): Boolean {
            atomicAttempts++
            if (!atomicSupported) return false
            return NioFileReplacer.atomicReplace(source, destination)
        }

        override fun renameReplace(source: File, destination: File): Boolean {
            renames++
            if (failTemporaryToTarget &&
                source.name == SessionFileStore.TEMP_NAME &&
                destination.name == SessionFileStore.FILE_NAME
            ) {
                return false
            }
            if (failRollback &&
                source.name == SessionFileStore.BACKUP_NAME &&
                destination.name == SessionFileStore.FILE_NAME
            ) {
                return false
            }
            return NioFileReplacer.renameReplace(source, destination)
        }
    }
}
