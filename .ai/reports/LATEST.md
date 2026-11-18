# LATEST.md — Velnox Mobile Latest Change Report

> **Current snapshot (overwritten each task).** Previous report (Part 2)
> archived verbatim in `.ai/history/20261118_155500_LATEST_before_part3.md`.
> Repository: `EnJirad/velnox-mobile`

---

## Task: Part 3 — Authentication Foundation & Secure Session Architecture

**Date:** 2026-11-18
**Commits:** `edc2b0e` (implementation), `169a0eb` (CI failure surfacing),
`8bea21f` (compile fixes)
**Verified CI run:** [44261281798](https://github.com/EnJirad/velnox-mobile/actions/runs/44261281798)
**Status:** **VERIFIED — unit tests pass, BUILD SUCCESSFUL**

---

## 1. Repository safety

| Check | Result |
|---|---|
| `git fetch origin main` before work | Done; local == remote (`3537116`) |
| Uncommitted work at start | None |
| Work preserved | Yes — nothing deleted, no history rewritten |
| Local Android build | Not run (`.ai/AI_RULES.md` §1) |
| Backend / database | Untouched; read-only verification only |

---

## 2. What was built

### 2.1 Authentication states (one source of truth)

`core/session/SessionState.kt`:

```kotlin
sealed interface SessionState {
    data object Loading : SessionState
    data object Guest : SessionState
    data class Authenticated(override val user: AuthUser) : SessionState
    data object ExpiredSession : SessionState
    val isAuthenticated: Boolean
    val user: AuthUser?
}
```

No screen keeps its own flag. `LocalSession.current.state` is the only read path,
and `SessionController` is the only writer.

### 2.2 Layered implementation

| File | Role |
|---|---|
| `core/auth/AuthModels.kt` | `AuthUser`, `AuthSession`, `AuthError` (10 cases), `ApiResult`, `SignInResult`, `RestoreOutcome` |
| `core/auth/Jwt.kt` | `exp` reader. Used **only** to skip a guaranteed-401 call; never for authorization |
| `core/auth/AuthApi.kt` | Interface (tests) |
| `core/auth/AuthRemoteDataSource.kt` | OkHttp + `org.json`; login, register, me, logout; error mapping; token capture |
| `core/auth/AuthRepository.kt` | The rules: what counts as signed in, restore matrix, sign-out ordering |
| `core/session/SessionStore.kt` | Persistence seam + `StoredSession` |
| `core/session/SessionVault.kt` | AES-256-GCM, Android Keystore key, atomic file write |
| `core/session/SessionManager.kt` | One in-memory token, expiry check, clear |
| `core/session/SessionController.kt` | The four states, `signIn`, `signOut`, `onUnauthorized`, notice |
| `core/session/SessionProvider.kt` | `LocalSession` + one-shot restore after first frame |
| `navigation/AccessPolicy.kt` | `isAllowed(state)`, `reasonForTab/Overlay`, `reasonFor(base, state)` |

### 2.3 Session transport — the Part 1 blocker, resolved without a backend change

Part 1 recorded that mobile responses set `velnox_session` but never return the
token, while `bearerOrCookie` accepts the same value as a Bearer token. Verified
again this session: `backend/routes/mobile-auth.ts` is **byte-identical** to the
Part 1 snapshot.

Implementation: `captureSession()` reads the token from `Set-Cookie` and replays it
as `Authorization: Bearer`. That is the path the backend's own comment describes
for native clients. If the server later adds `data.token`, only that one function
changes.

### 2.4 Secure storage

- AES-256-GCM, 256-bit key in `AndroidKeyStore` (alias `velnox.session.v1`,
  non-exportable, no user-auth requirement so a cold start can restore).
- File: `filesDir/session/session.bin`, layout `[version][12-byte IV][ciphertext]`,
  written to a temp file then renamed.
- Unreadable/invalidated key or corrupt file → `null` **and** the file is deleted:
  the app falls back to Guest rather than crashing or retrying dead bytes.
- `res/xml/backup_rules.xml` + `data_extraction_rules.xml` exclude `session/`, so
  neither cloud backup nor device transfer can carry a session off the device.
- Nothing is written to DataStore or SharedPreferences.

### 2.5 UI changes

- `LoginScreen` now takes `onSubmit`, `isSubmitting`, `errorRes`; the simulated
  700 ms delay and `LoginNotice.Success` are gone.
- Failures render in an `errorContainer` banner with a distinct message per case
  (credentials, offline, rate limited, disabled, validation, generic).
- Sign-in password rule relaxed to "not empty". Strength rules stay server-side:
  the old client `length < 6` rule could reject a valid legacy password, and
  duplicating the server pattern only invites drift.
- A 12dp top-end pill shows "Checking session…" during `Loading`, and
  "Session couldn't be verified (offline)" (tap to dismiss) after an unverified
  restore. Browsing is never blocked.
- `AccessPolicy` gained `LoginReason.Expired`, so a dead session says so instead
  of looking like a first visit.

### 2.6 Dependencies

| Added | Why |
|---|---|
| `com.squareup.okhttp3:okhttp:4.12.0` | The app had no HTTP client at all |
| `junit:junit:4.13.2`, `kotlinx-coroutines-core:1.8.1` (test only) | Unit tests |
| `buildConfigField API_BASE_URL` | One place for the API origin; not a secret |

No serialization plugin, no DI framework, no Navigation library, no security-crypto
library: `org.json` is platform, Keystore is platform, DataStore already existed.

---

## 3. Security properties (implemented, not claimed)

| Property | Where |
|---|---|
| A boolean cannot authenticate | `Authenticated` only from `me() == 200` or a sign-in response; `AccessPolicy.isAllowed` accepts only that state |
| Token never in plaintext | `SessionVault`; DataStore/SharedPreferences untouched |
| No secrets in the APK | Only the public API origin is compiled in |
| Network failure ≠ signed in | `Unverified` → `Guest`, token kept, no account |
| Invalid session dropped | `restore()` clears storage on 401 or passed `exp` |
| Server is the authority | `/api/auth/me` + existing `requireAuth` on every account route |
| Backup cannot leak a session | Backup rules exclude `session/` |
| Sign-out is local-first | Storage cleared before the revoke call |

---

## 4. Verification

### 4.1 CI

| Item | Result |
|---|---|
| Run | [44261281798](https://github.com/EnJirad/velnox-mobile/actions/runs/44261281798) on `8bea21f` |
| Run unit tests | **success** (1m 43s) |
| Build debug APK | **success** |
| Verify APK / integrity / upload | **success** |
| Artifacts | `velnox-unit-test-report` 19,823 B · `velnox-debug-apk` **10,564,666 B** |

The earlier run `44259933404` failed and `44260777809` failed again; the second
failure's annotations gave the two compile errors, both fixed in `8bea21f`. The
workflow now re-emits compiler/test failures as annotations and uploads the log,
because job logs are not readable with this token (`403 Must have admin rights`).

### 4.2 Unit tests — 41 tests, 5 classes

| Class | Tests | Covers |
|---|---|---|
| `JwtTest` | 5 | `exp` parsing, boundary at/after expiry, no-exp, malformed, non-numeric |
| `SessionManagerTest` | 6 | save/read/clear, unreadable store, failed clear still drops memory, expiry |
| `AuthRepositoryTest` | 12 | no token, valid, 401 → cleared, expired without a call, network/server keep token, sign-in success/failure/unstorable, sign-out ordering |
| `SessionControllerTest` | 13 | initial `Loading`, guest startup, valid restore, invalid, expired, offline notice, sign-in, failed sign-in, sign-out, 401 demotion, restart after sign-in, restart after sign-out, restore-once |
| `AccessPolicyTest` | 5 | public vs protected routes, fail-closed default, `isAllowed` matrix, expired reason |

This maps 1:1 onto the Part 3 test list: guest startup, valid restoration, invalid
session, expired session, logout, application restart, network unavailable.

### 4.3 Live backend contract (read-only probes)

| Probe | Response | Matches client mapping |
|---|---|---|
| `GET /api/auth/me` (no token) | `401 UNAUTHORIZED` | `AuthError.Unauthorized` → `ExpiredSession` |
| `POST /api/auth/mobile/login` (bad credentials) | `401 INVALID_CREDENTIALS` | `AuthError.InvalidCredentials` → banner |

No account was created, no row was written, no schema was touched.

### 4.4 Not verified

- **A successful end-to-end sign-in on a device.** No customer account exists in
  Neon (verified in Part 1: 3 users, all owner/seller/staff) and creating one is
  the owner's decision. The success path is covered by unit tests and the API
  contract, not by a live login.
- **Keystore behaviour on hardware.** `SessionVault` compiles and its logic is
  straightforward, but no instrumented test ran on a device.
- Visual confirmation of the loading/unverified pill and the error banner.

---

## 5. Files changed

**Added**

- `app/src/main/java/com/velnox/app/core/auth/{AuthModels,Jwt,AuthApi,AuthRemoteDataSource,AuthRepository}.kt`
- `app/src/main/java/com/velnox/app/core/session/{SessionManager,SessionVault}.kt`
- `app/src/main/res/xml/{backup_rules,data_extraction_rules}.xml`
- `app/src/test/java/com/velnox/app/core/auth/{JwtTest,AuthRepositoryTest}.kt`
- `app/src/test/java/com/velnox/app/core/session/{SessionManagerTest,SessionControllerTest}.kt`
- `app/src/test/java/com/velnox/app/navigation/AccessPolicyTest.kt`
- `.ai/ARCHITECTURE_AUTH.md`

**Modified**

- `app/src/main/java/com/velnox/app/{VelnoxApplication.kt,navigation/VelnoxApp.kt,navigation/AccessPolicy.kt}`
- `app/src/main/java/com/velnox/app/core/session/{SessionState,SessionStore,SessionController,SessionProvider}.kt`
- `app/src/main/java/com/velnox/app/feature/auth/LoginScreen.kt`
- `app/src/main/AndroidManifest.xml` (INTERNET, backup rules)
- `app/build.gradle.kts` (OkHttp, test deps, BuildConfig, testOptions)
- `app/src/main/res/values{,-en,-my}/strings.xml` (308 keys each)
- `.github/workflows/gradle-build.yml` (unit-test step, failure annotations, reports)
- `.ai/reports/LATEST.md`, `.ai/AI_HANDOFF.md`

**Removed**

- `NoStoredSessionStore` and the Part 2 demo sign-in (`completeDemoSignIn`).

---

## 6. Remaining issues

1. **No refresh token** — the backend has none. A 7-day JWT either validates or
   becomes `ExpiredSession`; there is no silent renewal. Adding it needs a backend
   endpoint (owner decision).
2. **Google and LINE are still unimplemented in the app** (Part 6). Server side is
   ready; `mobile/config` reports `google:true, line:false`.
3. **Registration has no screen** (Part 4). `AuthRepository.register` is
   implemented and tested but unreachable from UI.
4. **Profile still shows demo identity** — `Authenticated` carries the real
   account, but Profile/Settings do not render it yet.
5. **`onUnauthorized()` has no caller yet**, because no authenticated feature call
   exists; it is the seam Part 4/5 will use.
6. **No instrumented tests** — Keystore and Compose UI behaviour are unverified on
   a device.
7. `.ai/AI_RULES.md` still names VelShop; `setup-java@v4` / Node 20 deprecation
   annotations remain.
