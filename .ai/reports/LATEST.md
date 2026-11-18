# LATEST.md — Velnox Mobile Latest Change Report

> **Current snapshot (overwritten each task).** Previous report (Part 4)
> archived verbatim in `.ai/history/20261118_174000_LATEST_before_part5.md`.
> Repository: `EnJirad/velnox-mobile`

---

## Task: Part 5 — Google Sign-In Integration

**Date:** 2026-11-18
**Commits:** `81e0b97` (feature), `a3c7b36` (compile fixes), `29b700a` (test
fakes), `751eb2b` (fingerprint annotation), plus docs
**Verified CI run:** [44278012706](https://github.com/EnJirad/velnox-mobile/actions/runs/44278012706) — **success**
**Backend PR:** [velnox-marketplace#16](https://github.com/EnJirad/velnox-marketplace/pull/16) — open, **not merged**
**Status:** **PARTIAL** — implementation complete and compiling; the real OAuth
flow **cannot be executed** from this environment. Exact blockers in §2 and §7.

---

## 1. Provider configuration — what could and could not be inspected

| Item | Finding | How |
|---|---|---|
| Package name | `com.velnox.app` | `app/build.gradle.kts` |
| Signing certificate SHA-1 | **`a94659e6d884960206997a4d960a905563568ffb`** | Printed by CI (`apksigner verify --print-certs`) |
| Signing certificate SHA-256 | `d22a28cd7fd049d8fe34301e670f6344816e3d95ca0ed544fb3623655447f1e3` | same |
| Web/server OAuth client id | **UNKNOWN** | The backend has one (`config` → `google:true`) but never returns the value in the deployed build |
| Android OAuth client | **UNKNOWN** | Google Cloud Console is not reachable from this environment; no API access to it |
| Backend verification requirement | `aud` must equal `MOBILE_GOOGLE_CLIENT_ID ?? GOOGLE_CLIENT_ID` | `backend/routes/mobile-auth.ts` |
| Authorized redirect URIs | **not required for native** | Credential Manager returns the ID token directly; redirects belong to the web flow |

I did not invent any credential. The two unknown values are the reason the flow
cannot run yet; §7 lists exactly what the owner must do.

### 1.1 A blocker that was fixed rather than reported

The workflow had no signing configuration, so every fresh GitHub runner generated
its own debug keystore. That makes the SHA-1 **different on every build**, so no
single fingerprint could ever be registered in Google Cloud Console.

Fixed: the workflow now caches `~/.android/debug.keystore` under the immutable key
`velnox-debug-keystore-v1`, and prints the fingerprint of the built APK. From now
on the debug SHA-1 is stable — the value above.

---

## 2. What was implemented

### 2.1 Android — the supported flow

`core/auth/CredentialManagerGoogleSignIn.kt` uses **Credential Manager** with
`GetGoogleIdOption`. `GoogleSignInClient` is deprecated and is not used.

| Case | Handling |
|---|---|
| Success | Returns the raw ID token; decides nothing else |
| User cancellation | `GetCredentialCancellationException` → silent |
| No account on device | `NoCredentialException` → its own message |
| Misconfigured build / package+SHA-1 not registered | `GetCredentialProviderConfigurationException` → "not configured", button disables |
| Interrupted | own message |
| Unsupported device | treated as not configured |
| Malformed token | `GoogleIdTokenParsingException` → generic failure |
| Network | offline message |

`setFilterByAuthorizedAccounts(false)` so a first-time user sees their accounts;
`setAutoSelectEnabled(false)` so nobody is signed in without choosing.

### 2.2 Client id resolution

`core/auth/GoogleClientIdSource.kt` resolves the **Web** client id:

1. `-PvelnoxGoogleClientId=…` baked into `BuildConfig` (wins), else
2. `GET /api/auth/mobile/config` → `googleClientId` (PR #16).

It must be the Web client: the ID token's `aud` carries it, and the backend
compares `aud` against its own value. No value → the UI says unavailable.

### 2.3 Backend integration

`AuthApi.googleSignIn(idToken)` → `POST /api/auth/mobile/google`. **Only the token
is sent**; no email, name or user id from the client. The existing
`verifyGoogleToken()` already checks signature (via Google tokeninfo), issuer,
audience, expiry, `azp`, `email_verified` and subject — unchanged and still
server-side.

### 2.4 Account linking — no silent merge

The deployed backend auto-linked a verified Google email to an existing account.
That is a silent merge: a verified address proves control of the mailbox, not that
the signer is the person who created the password account, and attaching hands
over its orders, addresses and history.

PR #16 changes `resolveIdentity` to return `link_required` (`409 LINK_REQUIRED`)
and adds `POST /api/auth/mobile/link` (`requireAuth`):

- holding a session for the account **is** the proof of ownership,
- the Google email must equal the account email (`409 EMAIL_MISMATCH`),
- an identity already bound elsewhere is refused (`409 IDENTITY_CONFLICT`),
- a repeat call is idempotent,
- `UNIQUE(provider, provider_id)` remains the guard against duplicate mappings.

LINE is unchanged: unverified email stays a plain conflict.

### 2.5 Session integration

`AuthRepository.signInWithGoogle` and `SessionController.signInWithGoogle` reuse
the Part 3 architecture exactly: `Authenticated` only after the API returns a
session, `SessionVault` storage, `/api/auth/me` restoration, and the same
`signOut()`. The nav gate is untouched; `LINK_REQUIRED` switches the gate to the
password step instead of dead-ending.

---

## 3. Files changed

**Added**

- `core/auth/GoogleSignInProvider.kt`, `CredentialManagerGoogleSignIn.kt`,
  `GoogleClientIdSource.kt`
- `app/src/test/.../GoogleClientIdSourceTest.kt`
- `.ai/GOOGLE_SETUP.md`

**Modified**

- `app/build.gradle.kts` — three Credential Manager/Google artifacts,
  `GOOGLE_WEB_CLIENT_ID` BuildConfig field
- `core/auth/{AuthApi,AuthModels,AuthRemoteDataSource,AuthRepository}.kt`
- `core/session/SessionController.kt`, `VelnoxApplication.kt`
- `feature/auth/LoginScreen.kt`, `navigation/VelnoxApp.kt`
- `res/values{,-en,-my}/strings.xml` — 361 keys each
- `app/src/test/.../{AuthRepositoryTest,SessionControllerTest}.kt`
- `.github/workflows/gradle-build.yml` — keystore cache + fingerprint step

**Backend (PR #16)**

- `backend/routes/mobile-auth.ts`, `docs/API.md`

---

## 4. Test results

### 4.1 Unit tests — 71, all passing (CI)

14 new: `GoogleClientIdSourceTest` (7 — build value wins, remote fallback,
caching, unconfigured, blank, missing field, invalidate), `AuthRepositoryTest`
(4 — token only, LinkRequired stores nothing, rejected token stores nothing,
provider unavailable), `SessionControllerTest` (3 — Guest→Authenticated,
LinkRequired stays Guest, Google session survives restart).

### 4.2 CI

| Item | Result |
|---|---|
| Run | 44278012706 on `751eb2b` |
| Restore the debug signing key | **success** |
| Run unit tests | **success** |
| Build / verify / integrity / upload | **success** |
| Print APK signing fingerprints | **success** |

Three runs failed first, all real defects of mine and all fixed:
`44274992238` (missing `LaunchedEffect` import, `NotConfigured` needing `is`),
`44276106016` (test fakes missing the new interface members, illegal `:` in a
test name). `44277127872` and `44278012706` are green.

### 4.3 Live backend probes

| Probe | Result | Meaning |
|---|---|---|
| `GET /api/auth/mobile/config` | `{"google":true,"line":false}` | The server has a Google client id configured |
| Same, `googleClientId` field | **absent** | PR #16 not deployed — the app therefore reports "not configured" |
| `POST /api/auth/mobile/google` with a forged token | **401** `INVALID_GOOGLE_TOKEN` | The endpoint is live and refuses untrusted input |
| `POST /api/auth/mobile/link` | **404** | PR #16 not deployed |

### 4.4 The real OAuth flow — NOT TESTED

**I could not run it, and I will not claim otherwise.** Running it needs all three
of: a device or emulator with Google Play services and a signed-in Google account;
the app installed; and an Android OAuth client registered for this package and
SHA-1. This environment has no device route, and the OAuth client does not exist
yet. The button being present is not evidence that the flow works.

---

## 5. Security properties

| Property | Where |
|---|---|
| Client never supplies identity | Only `{idToken}` is posted; identity comes from verified claims |
| Signature, issuer, audience, expiry, subject checked | `verifyGoogleToken()` server-side |
| Client-side flag is not proof | `Authenticated` only after a `200` from the API |
| No silent merge | `LINK_REQUIRED` + authenticated link endpoint (PR #16) |
| Duplicate Google mappings prevented | `UNIQUE(provider, provider_id)` + explicit guards |
| No secret in the APK | Only the public client id; `GOOGLE_CLIENT_SECRET` stays server-side |
| Token stored encrypted | Part 3 `SessionVault` (Keystore AES-GCM) |

---

## 6. Defects found and fixed during this part

1. **Debug SHA-1 changed on every build** — no signing config and no keystore
   cache. Fixed by caching the debug keystore; fingerprint is now stable.
2. **Missing `LaunchedEffect` import** — the availability probe did not compile.
3. **`NotConfigured` is a data class** — `when` branches needed `is`.
4. **Test fakes not updated** for the widened `AuthApi`.
5. **Illegal `:` in a test name** — rejected by the JVM method-name rule.

---

## 7. Exact blockers (owner action)

1. **Create the Android OAuth client** — Google Cloud Console → Credentials →
   Android client, package `com.velnox.app`, SHA-1
   `a94659e6d884960206997a4d960a905563568ffb`.
2. **Supply the Web client id** — either merge PR #16 (the app then reads it from
   `config`) or build with `-PvelnoxGoogleClientId=<web-client-id>`.
3. **Confirm `MOBILE_GOOGLE_CLIENT_ID` on Render equals that same Web client id**,
   or every token is rejected with `INVALID_GOOGLE_TOKEN`.
4. **Configure the OAuth consent screen** — in Testing mode only listed test users
   can sign in.
5. **Merge PR #16** to make `LINK_REQUIRED` and `/api/auth/mobile/link` live.
   Until then the deployed backend still auto-links.

Detailed steps: `.ai/GOOGLE_SETUP.md`.

---

## 8. Remaining issues

1. Google Sign-In is **not usable end to end** until blockers 1–3 are resolved.
2. The app currently shows "not configured" because `config` does not yet return
   `googleClientId` — correct behaviour, not a bug.
3. No device verification of the Credential Manager sheet, or of the new
   messages, on hardware.
4. LINE is still an entry point only (its own part).
5. Existing JWTs still survive a password reset (Part 4 note, unchanged).
