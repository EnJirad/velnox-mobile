# AI_HANDOFF.md — Velnox Mobile Current Snapshot

> **Current snapshot (not a log).** Supersedes the Part 5 snapshot, archived
> verbatim in `.ai/history/20261118_190000_AI_HANDOFF_before_part5_1.md`.
> Statuses: **VERIFIED / UNVERIFIED / FAILED / BLOCKED / SKIPPED / NOT TESTED**.
> Repository: `EnJirad/velnox-mobile`

---

## Current State (2026-11-18, commit `3bc2f3c`, run 44335263114)

| Area | Status | Notes |
|------|--------|-------|
| Startup destination | **VERIFIED (source)** | Opens on Home for everyone; Login is a gate, not a launch screen |
| Auth states | **VERIFIED (unit tests)** | `Loading` / `Guest` / `Authenticated` / `ExpiredSession`, one holder |
| Session restore | **VERIFIED (unit tests)** | `/api/auth/me` decides; token never trusted on its own |
| Session persistence | **VERIFIED (unit tests + CI)** | Part 5.1 fixed the write that deleted its own temp file; Part 5.1.1 made the replacement crash-safe (atomic move, backup/rollback, recovery on read) |
| Secure storage | **VERIFIED (compile)** | AES-256-GCM, Keystore key, `filesDir/session/session.bin`, excluded from backup |
| Expiry handling | **VERIFIED (unit tests)** | `exp` skips a guaranteed-401 call; API stays authoritative |
| Refresh tokens | **BLOCKED** | Backend has no refresh endpoint; 7-day JWT only |
| Sign-out | **VERIFIED (unit tests)** | Clears storage first, then revokes best-effort |
| Invalid session | **VERIFIED (unit tests)** | 401 → token cleared → `ExpiredSession` |
| Offline restore | **VERIFIED (unit tests)** | `Guest` + notice; token kept; never authenticated |
| Email sign-in | **VERIFIED (live)** | Real login against `velnox-api.onrender.com` returned 200 + session cookie; Bearer `/api/auth/me` returned the account |
| Registration | **VERIFIED (live)** | Real account created (201); duplicate → 409, weak password → 400 |
| Logout + revocation | **VERIFIED (live)** | Bearer logout 200, then `/api/auth/me` → 401 "Session revoked" |
| Forgot / reset password | **BLOCKED (not deployed)** | Backend PR #15 open; live API answers 404. Client half unit-tested |
| Google Sign-In | **IMPLEMENTED, BLOCKED** | Credential Manager flow compiles and is unit-tested; cannot run until the Android OAuth client exists and the Web client id is supplied. See `.ai/GOOGLE_SETUP.md` |
| Google account linking | **PR #16, not deployed** | `LINK_REQUIRED` + `POST /api/auth/mobile/link`. Deployed backend still auto-links |
| Google claim validation | **VERIFIED (17 backend tests)** | `lib/google-identity.ts`, pure and testable; not deployed |
| Profile shows real account | **VERIFIED (compile)** | `ProfileScreen(user = …)` from `SessionState`; demo identity removed |
| LINE | **ENTRY POINT ONLY** | Button says "coming soon"; `line:false` |
| Debug signing SHA-1 | **UNSTABLE** | Changes per build; keystore cache did not hold. Pin it with the `ANDROID_DEBUG_KEYSTORE_BASE64` secret |
| Login / Register UI | **VERIFIED (compile)** | Shared components; consent-gated register; strength meter |
| Guest gate | **VERIFIED (unit tests)** | `AccessPolicy`; unknown routes fail closed |
| Unit tests | **VERIFIED (CI)** | 90 tests, 8 classes, green in run 44335263114 (counted from the run's JUnit XML artifact) |
| APK | **VERIFIED (CI)** | built, verified, uploaded; signing fingerprints printed |
| Localisation | **VERIFIED (packaged)** | 361 keys × th/en/my, identical sets, no duplicates |
| On-device runtime | **NOT TESTED** | No install route; Keystore and UI unverified on hardware |

Architecture detail: **`.ai/ARCHITECTURE_AUTH.md`**. Evidence: `.ai/reports/LATEST.md`.

---

## Architecture

```
LoginScreen / any protected action
        │ (onSubmit, callbacks)
        ▼
SessionController  ── LocalSession ──▶  UI reads state
        │
        ▼
AuthRepository ──▶ AuthRemoteDataSource ──▶ https://velnox-api.onrender.com
        │                                        /api/auth/mobile/{login,register}
        │                                        /api/auth/me, /api/auth/logout
        ▼
SessionManager ──▶ SessionVault (AES-GCM + Android Keystore)
        │
        ▼
navigation/AccessPolicy  ← the only place that maps state → allowed destinations
```

Session transport: the API sets `velnox_session` as an httpOnly cookie and never
returns the token in a body, so the data source reads `Set-Cookie` and replays the
value as `Authorization: Bearer` (accepted by `bearerOrCookie`). No cookie jar, no
WebView, no invented token.

---

## Decisions

1. **Four states, one writer.** `SessionController` is the only thing that changes
   `SessionState`; screens read `LocalSession.current.state`.
2. **`Authenticated` requires a server answer.** `me() == 200` or a sign-in
   response. Nothing else — not a stored token, not a flag.
3. **Offline is not a session.** `Unverified` → `Guest` with the token kept for
   the next launch. `SessionNotice.NetworkUnavailable` is a notice, not a state.
4. **Keystore over `EncryptedSharedPreferences`.** The library is deprecated and
   would be the only new dependency for one file; a Keystore AES-GCM key plus a
   private file does the same job with platform APIs.
5. **OkHttp is the only new runtime dependency.** JSON uses platform `org.json`;
   no serialization plugin, no DI, no Navigation library.
6. **Password rules live server-side.** Sign-in only checks "not empty"; the old
   6-char client rule could reject a valid legacy password.
7. **Sign-out is local-first.** Storage is cleared before the revoke request, so a
   network failure cannot leave a usable token.
8. **Expiry is advisory.** `exp` avoids a wasted round trip; only the API may
   invalidate a session.

---

## What changed this phase

- New `core/auth/`: models, JWT reader, API interface, OkHttp data source,
  repository.
- New `core/session/`: `SessionManager`, `SessionVault`; `SessionState`,
  `SessionStore`, `SessionController`, `SessionProvider` rewritten for the four
  states.
- `navigation/AccessPolicy.kt`: `isAllowed`, `reasonFor`, `LoginReason.Expired`.
- `feature/auth/`: `AuthComponents.kt`, `CredentialRules.kt`, `RegisterScreen.kt`,
  `ForgotPasswordScreen.kt` (new); `LoginScreen.kt` rebuilt on the shared pieces.
- `navigation/VelnoxApp.kt`: real credential submit, registration, recovery, and a
  four-step gate (`AuthStep`) that keeps the reason and the pending destination.
- `AndroidManifest.xml`: `INTERNET`, backup rules. `app/build.gradle.kts`: OkHttp,
  test deps, `API_BASE_URL`, `buildConfig = true`.
- `.github/workflows/gradle-build.yml`: unit-test step, failure annotations, test
  report artifact.
- `strings.xml` ×3: 308 keys each.
- Docs: `.ai/ARCHITECTURE_AUTH.md`, `.ai/reports/LATEST.md`, this file.

---

## Part 5.1.1 changes (2026-11-18, `3bc2f3c`)

One module, one test file. No UI, no navigation, no backend, no schema.

- `core/session/SessionFileStore.kt`: a replaced session file is now swapped in
  with a single `Files.move(ATOMIC_MOVE)` (available from API 26, the app's
  `minSdk`). Filesystems that refuse an atomic move fall back to move-aside
  (`session.bak`) plus rollback; the backup is deleted only after the new file is
  in place and is recovered on the next read if the process died mid-swap. A
  corrupt or missing target falls back to the backup instead of reporting
  "signed out".
- `core/session/SessionFileStoreTest.kt`: 6 → 19 tests, covering first write,
  replacement, failed temp write, failed replacement with and without a working
  rollback, crash recovery, corrupt files and cleanup.
- The previous `target.delete()`-then-`renameTo` sequence is gone; it left a
  window with no session file on disk at all.

---

## Known issues

- **Password recovery is not live.** Backend PR
  [velnox-marketplace#15](https://github.com/EnJirad/velnox-marketplace/pull/15)
  adds `/api/auth/forgot-password` and `/api/auth/reset-password` but is not
  merged, and `password_reset_tokens` is not created. Until then those calls 404
  and the app reports a generic failure. A mail transport (`RESEND_API_KEY`,
  `MAIL_FROM`) is also required, or the endpoint answers 503.
- **A password reset does not invalidate existing JWTs** — no session-version
  mechanism; sessions end at 7-day expiry.
- **No refresh token** (no backend endpoint). Sessions last 7 days, then
  `ExpiredSession` → sign in again.
- **Google Sign-In cannot run yet.** Credential Manager flow is implemented, but
  the debug signing SHA-1 still changes per build, no Android OAuth client is
  registered for `com.velnox.app`, and the Web client id is unknown. The app shows
  "not configured" until all three exist. See `.ai/GOOGLE_SETUP.md`.
- **PR #16 is red in CI for pre-existing reasons**, not from its own change: none
  of the six failing offline test files reference the modified module, and
  `preauth-nonce` passes locally on `main`. Fixing them is outside Part 5.1 scope.
- **A session write is atomic, but not directory-synced.** The payload is
  fsynced before the rename and the filesystem journal orders the rename, but the
  parent directory is not explicitly fsynced; a power loss could in theory lose
  the directory entry on a filesystem that does not order it. Adding that needs
  `android.system.Os` and cannot be unit-tested on the JVM.
- **`AuthRepository.persist()` clears secure storage when a save throws**
  (pre-existing policy for a failed sign-in). If a replacement failed
  catastrophically during a sign-in, that clear also removes the recovered
  backup. Unchanged in Part 5.1.1 because it alters sign-in semantics.
- **LINE is still an entry point only.**
- **Profile/Settings still demo data**; the real account is in
  `SessionState.Authenticated.user` but unused.
- **`onUnauthorized()` has no caller** until an authenticated feature call exists.
- **No device verification** of Keystore, banners or the restore pill.
- `.ai/AI_RULES.md` and `.ai/context/project-map.md` still use older VelShop names.
- CI still emits `setup-java@v4` / Node 20 deprecation annotations.

---

## Next Agent Startup Order

1. `.ai/AI_HANDOFF.md` ← **YOU ARE HERE**
2. `.ai/GOOGLE_SETUP.md` (Google configuration and what is still missing)
3. `.ai/ARCHITECTURE_AUTH.md`
3. `.ai/AI_RULES.md` (stale names; verify against source)
4. `.ai/VELNOX_DESIGN_THEME.md`
5. `.ai/reports/LATEST.md`
6. `git fetch origin && git rev-parse HEAD && git rev-parse origin/main`

---

## Secrets / Credentials

No secrets in `.ai/`. No tokens, keystores or passwords in this repository.
The app embeds only the public API origin (`BuildConfig.API_BASE_URL`).

---

## Signature

> This handoff supersedes the previous snapshots.
> **Last updated:** 2026-11-18 by the Part 5.1.1 session-file replacement session.
