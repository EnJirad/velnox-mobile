# LATEST.md — Velnox Mobile Latest Change Report

> **Current snapshot (overwritten each task).** Previous report (Part 5.1)
> archived in `.ai/history/20261118_152833_LATEST_before_part5_1_1.md`.
> Repository: `EnJirad/velnox-mobile`

---

## Task: Part 5.1.1 — Secure SessionFileStore Replacement

**Date:** 2026-11-18
**Start commit:** `359f9f6` · **End commit (code + tests):** `3bc2f3c`
**Verified CI run:** [44335263114](https://github.com/EnJirad/velnox-mobile/actions/runs/44335263114) — **success**
**Status:** **DONE.** The previous valid session can no longer be lost by a failed
replacement. Tests were added for every path named in the task; the CI unit-test
step passed with 90 tests in 8 classes and 0 failures.

---

## 1. Repository and inspection

| Check | Result |
|---|---|
| `git fetch origin` | `HEAD` == `origin/main` == `359f9f6` at start |
| Uncommitted work at start | **None** (clean tree; nothing to preserve) |
| Docs read | `.ai/AI_HANDOFF.md`, `.ai/AI_RULES.md`, `.ai/VELNOX_DESIGN_THEME.md`, `.ai/reports/LATEST.md` |
| Source inspected | `SessionFileStore.kt`, `SessionVault.kt`, `SessionManager.kt`, `AuthRepository.persist`, `SessionFileStoreTest.kt`, backup rules, CI workflow |
| Assumed from earlier reports | Nothing. The description of `SessionFileStore` in the Part 5.1 report matched the code; the data-loss window it left open did **not** |
| Local Gradle build | Not run (`.ai/AI_RULES.md` §1: the on-device wrapper cannot load `libnative-platform` for aarch64). Unit tests were compiled and executed directly instead — see §5 |

---

## 2. The defect that was still open

Part 5.1 made persistence work again, but the swap itself was unsafe:

```kotlin
target.delete()                       // ← no session on disk from here…
if (!temporary.renameTo(target)) {     // ← …to here
    temporary.delete()
    throw IllegalStateException("Could not persist the session")
}
```

Two failure modes, both silent:

1. **Process death** between the delete and the rename (LMK under memory
   pressure, power loss, user force-stop) — the only copy of the session is gone.
2. **Failed rename** (ENOSPC, EIO, a locked or read-only filesystem): the old file
   is already deleted, so the throw reports failure *after* destroying the data.

Either way the user is signed out on the next launch with no session and no
explanation, and the app cannot re-derive it — the token exists only on disk.

---

## 3. The replacement strategy now in place

`SessionFileStore` keeps three names in `filesDir/session/`: `session.bin`
(target), `session.tmp`, `session.bak` (only while a swap is in flight).

1. **Recover first.** `recoverIfNeeded()` runs on every read *and* every write.
   If the target is missing but a backup exists, the backup is moved back. If the
   target is valid and a backup is left over, the backup is deleted.
2. **Write the payload to `session.tmp`** with `FileOutputStream`, `flush()` and
   `FileDescriptor.sync()`. The target is untouched, so every failure here
   (unopenable temp, short write, failed fsync) leaves the old session readable.
3. **Commit with one atomic move:**
   `Files.move(tmp, target, REPLACE_EXISTING, ATOMIC_MOVE)`. `rename(2)` either
   replaces the target completely or changes nothing — there is no interval in
   which the session file does not exist.
4. **Fallback when the filesystem refuses an atomic move**
   (`AtomicMoveNotSupportedException` / `UnsupportedOperationException`):
   move `session.bin` → `session.bak`, move `session.tmp` → `session.bin`, and
   delete the backup only after the new file is in place. If the second move
   fails, the backup is moved back and the write throws. If even the rollback
   fails, the backup is **kept on disk** and step 1 restores it on the next read.

A read that finds an unusable target (missing, zero-length, shorter than the
version byte) now falls through to the backup instead of reporting "signed out",
and restores it.

### Why `java.nio.file` is allowed here

`app/build.gradle.kts` sets `minSdk = 26`, and `java.nio.file` (`Files`,
`StandardCopyOption`, `AtomicMoveNotSupportedException`) exists from API 26. No
desugaring or API-level branch is needed, and no compile-time guard is required.
`NioFileReplacer` is still behind the small `FileReplacer` interface so the
fallback paths can be driven by a test double.

### What was deliberately not changed

- Encryption, key handling, `SessionVault`'s payload format, session validation
  and the app's file permissions. `SessionFileStore` is still `internal`; nothing
  new is exposed.
- Backup rules already exclude the whole `session/` directory
  (`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`), so the new
  `session.bak` cannot reach cloud backup or device transfer either.

---

## 4. Tests added

`app/src/test/java/com/velnox/app/core/session/SessionFileStoreTest.kt` — the
class grew from 6 to **19** tests, using a real directory and the production
class. New coverage required by the task:

| Requirement | Test |
|---|---|
| Initial session creation | `the first write creates the session file and nothing else` |
| Successful replacement | `replacing an existing session keeps only the new payload` |
| Failed temporary-file write | `a failed temp-file write keeps the previous session` (a non-empty directory where `session.tmp` belongs) |
| Failed replacement, previous preserved | `a failed replacement keeps the previous session` (scripted replacer fails `tmp → bin`; rollback restores) |
| Failed replacement, rollback also fails | `a replacement that cannot roll back still leaves the session recoverable` (backup survives; a fresh store reads it) |
| Recovery from an interrupted replacement | `an interrupted replacement is recovered from the backup` (no target, complete backup, partial temp — the exact on-disk state of a crash) |
| Corrupted session | `a zero-length file reads as null…`, `a corrupt session file falls back to the backup` |
| Temp and backup cleanup | `a completed write leaves no temp file behind`, `replacing… keeps only the new payload`, `clear removes the session, temp and backup files…` |
| Existing read / clear behaviour | `a second store instance reads what the first wrote`, `clear works when nothing was ever stored`, `an unreadable directory reports no session instead of throwing` |
| Atomic path is actually taken | `an atomic replace is used when the filesystem supports it` (asserts one atomic attempt, zero renames, no backup left) |
| Stale temp cannot win | `a stale temp file never replaces a valid session` |

Retained from Part 5.1, unchanged: the round-trip test, the "no temp file left
behind" regression guard, and the "directory cannot be created" failure test.

`writeBytes` now throws `IOException` where it used to throw
`IllegalStateException`. `AuthRepository.persist()` already catches
`Exception`, and `SessionManager.save()` still propagates as documented, so no
call site changed.

---

## 5. Test results

**CI (authoritative):** run [44335263114](https://github.com/EnJirad/velnox-mobile/actions/runs/44335263114) — **success**.

Every step passed, including:

- `Run unit tests` (`./gradlew :app:testDebugUnitTest`) — **success**
- `Build debug APK` (`./gradlew :app:assembleDebug`) — **success**
- `Verify APK`, `Verify APK integrity`, `Print APK signing fingerprints`,
  `Upload APK` — **success**

Counted from the run's own JUnit XML artifact, not from the log:

| Suite | Tests |
|---|---|
| `AuthRepositoryTest` | 22 |
| `GoogleClientIdSourceTest` | 7 |
| `JwtTest` | 5 |
| `SessionControllerTest` | 19 |
| `SessionFileStoreTest` | **19** |
| `SessionManagerTest` | 6 |
| `CredentialRulesTest` | 7 |
| `AccessPolicyTest` | 5 |
| **Total** | **90 tests, 8 suites, 0 failures, 0 errors, 0 skipped** |

**Local (supplementary):** Gradle cannot run on this device (`.ai/AI_RULES.md`
§1), so the two files were compiled with `kotlinc 2.4.21` against `junit 4.13.2`,
`hamcrest-core 1.3` and `kotlinx-coroutines-core-jvm 1.8.1`, and executed with
`org.junit.runner.JUnitCore`: **OK (19 tests)**. This is a JVM run of the same
sources that CI compiles with Gradle; it is not a Gradle run and not a device
run.

**Not performed:** instrumentation tests, an on-device install, a real
process-kill during a write, and a read-only/ENOSPC filesystem test on hardware.
The corresponding behaviour is covered by unit tests with a scripted
`FileReplacer`, which is not the same thing as a hardware test.

---

## 6. Limitations, stated plainly

1. **No directory `fsync`.** The payload is fsynced before the swap, and `rename`
   is ordered before the directory-entry update by the filesystem journal on
   ext4/f2fs, but the parent directory is not explicitly synced. A power loss
   could in theory lose the directory entry on a filesystem that does not order
   it. Doing this properly needs `android.system.Os.open`/`Os.fsync` on the
   directory, which is not available in a JVM unit test; it was left out rather
   than added untested.
2. **The atomic path is expected to be the only path on real devices.** Android
   internal storage supports `ATOMIC_MOVE`, so `session.bak` normally never
   exists. The backup path exists for filesystems that refuse it and is exercised
   only by unit tests.
3. **A failed write is still a failed write.** If both the swap and the rollback
   fail, the current process reports failure and the caller behaves as before; the
   session is recovered on the *next* read, not in the current process.
4. **`AuthRepository.persist()` clears storage after a failed save** (pre-existing,
   deliberate: "staying signed in for this process only would be a lie"). If a
   replacement fails catastrophically during a sign-in, that clear also removes
   the recovered backup. This is unchanged in this task because changing it alters
   sign-in semantics, which is outside the scope given. Recorded here so the next
   agent can decide.
5. **`recoverIfNeeded()` reads the ~1 KB target file** on every write to decide
   whether a leftover backup is disposable. Negligible, but it is a read on the
   write path.

---

## 7. Files changed

**Android — modified**

- `app/src/main/java/com/velnox/app/core/session/SessionFileStore.kt`
  (atomic replace, backup/rollback, recovery on read, `FileReplacer` seam)
- `app/src/test/java/com/velnox/app/core/session/SessionFileStoreTest.kt`
  (6 → 19 tests)

**Docs** — this file, `.ai/AI_HANDOFF.md`, plus archives in `.ai/history/`.

**Not touched:** Google OAuth, backend, Neon schema, production session data,
navigation, UI, string resources, Gradle files, CI workflow.

**No schema change. No migration. No dependency added.**

---

## 8. Next steps

1. Review this change (Part 5.1.1), then unpark Part 6.
2. Google Sign-In is still blocked on owner configuration only: Android OAuth
   client for `com.velnox.app`, a stable debug SHA-1, and the Web client id. See
   `.ai/GOOGLE_SETUP.md`.
3. If a stricter durability guarantee is wanted later, add directory `fsync`
   behind an interface that has a no-op JVM test implementation.
