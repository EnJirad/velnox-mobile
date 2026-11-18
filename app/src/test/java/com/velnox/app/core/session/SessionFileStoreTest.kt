package com.velnox.app.core.session

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The file protocol [SessionVault] depends on.
 *
 * This is the regression test for a real defect: `SessionVault.write()` used to
 * call the "clear everything" helper after writing its temp file, which deleted
 * that temp file, so `renameTo` always failed and **every** session save threw.
 * Signing in succeeded, the token was never persisted, and the app silently fell
 * back to signed-out on the next launch.
 *
 * It survived because every session test used a fake in-memory `SessionStore`,
 * which has no temp file to delete. These tests use a real directory and the
 * production [SessionFileStore], so the ordering cannot regress unnoticed.
 */
class SessionFileStoreTest {

    private fun tempDir(): File = Files.createTempDirectory("velnox-vault").toFile()

    private val token = "header.payload.signature".toByteArray(Charsets.UTF_8)

    @Test
    fun `write then read round-trips the payload`() {
        val dir = tempDir()
        val store = SessionFileStore(dir)

        store.writeBytes(token)

        assertArrayEquals(token, store.readBytes())
    }

    @Test
    fun `a completed write leaves no temp file behind`() {
        val dir = tempDir()
        val store = SessionFileStore(dir)

        store.writeBytes(token)

        assertTrue(File(dir, SessionFileStore.FILE_NAME).exists())
        assertFalse(
            "session.tmp must not survive: deleting it after writing is the bug that broke persistence",
            File(dir, SessionFileStore.TEMP_NAME).exists(),
        )
    }

    @Test
    fun `writing twice replaces the previous payload`() {
        val dir = tempDir()
        val store = SessionFileStore(dir)

        store.writeBytes("first".toByteArray())
        store.writeBytes("second".toByteArray())

        assertArrayEquals("second".toByteArray(), store.readBytes())
    }

    @Test
    fun `a stale temp file from an interrupted write does not block the next one`() {
        val dir = tempDir()
        File(dir, SessionFileStore.TEMP_NAME).writeText("left over after a crash")
        val store = SessionFileStore(dir)

        store.writeBytes(token)

        assertArrayEquals(token, store.readBytes())
        assertFalse(File(dir, SessionFileStore.TEMP_NAME).exists())
    }

    @Test
    fun `a missing file reads as null`() {
        assertNull(SessionFileStore(tempDir()).readBytes())
    }

    @Test
    fun `a zero-length file reads as null rather than an empty session`() {
        val dir = tempDir()
        File(dir, SessionFileStore.FILE_NAME).writeBytes(ByteArray(0))

        assertNull(SessionFileStore(dir).readBytes())
    }

    @Test
    fun `clear removes both files and is idempotent`() {
        val dir = tempDir()
        val store = SessionFileStore(dir)
        store.writeBytes(token)

        store.clear()
        store.clear()

        assertNull(store.readBytes())
        assertFalse(File(dir, SessionFileStore.FILE_NAME).exists())
        assertFalse(File(dir, SessionFileStore.TEMP_NAME).exists())
    }

    @Test
    fun `a directory that cannot be created reports failure instead of pretending`() {
        // A regular file where the directory should be: mkdirs() fails and the
        // write must throw, because the caller relies on that to avoid reporting
        // a session it did not store.
        val blocker = File(Files.createTempDirectory("velnox-blocked").toFile(), "not-a-dir")
        blocker.writeText("x")
        val store = SessionFileStore(File(blocker, "session"))

        assertThrows(Exception::class.java) { store.writeBytes(token) }
    }

    @Test
    fun `the store is usable from a suspend context like the vault`() = runBlocking {
        val dir = tempDir()
        val store = SessionFileStore(dir)

        // Mirrors SessionVault.write/read, which are suspend functions.
        store.writeBytes(token)
        assertEquals(token.size, store.readBytes()?.size)
    }

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray?) {
        assertEquals(expected.toList(), actual?.toList())
    }
}
