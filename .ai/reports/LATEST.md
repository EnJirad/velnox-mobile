# LATEST.md — Velnox Mobile Latest Change Report

> **Current snapshot (overwritten each task).** Previous report (Part 5)
> archived in `.ai/history/20261118_190000_LATEST_before_part5_1.md`.
> Repository: `EnJirad/velnox-mobile`

---

## Task: Part 5.1 — Complete Google Sign-In & Real Neon Integration

**Date:** 2026-11-18
**Start commit:** `c016be9` · **End commit:** `2dfb21b`
**Verified CI run:** [44298776084](https://github.com/EnJirad/velnox-mobile/actions/runs/44298776084) — **success**
**Status:** **BLOCKED on owner configuration.** A real Google identity cannot
complete the flow, because no Android OAuth client exists and the Web client id
is not published. Everything that does not depend on those was fixed, tested and
verified — including a critical session-persistence bug that made **all** sign-in
non-persistent.

---

## 1. Repository and inspection

| Check | Result |
|---|---|
| `git fetch origin main` | local == remote (`c016be9`) |
| Uncommitted work at start | None |
| Inspection | `.ai/AI_HANDOFF.md`, `AI_RULES.md`, `VELNOX_DESIGN_THEME.md`, `ARCHITECTURE_AUTH.md`, `reports/LATEST.md`, `GOOGLE_SETUP.md` |
| Local Android build | Not run (`.ai/AI_RULES.md` §1) |
| Duplicate auth system created | **No** — extended the Part 3/5 architecture |

---

## 2. THE CRITICAL FINDING: session persistence never worked

> The task asked me to verify the "SessionVault persistence defect reported in the
> Part 4 remediation". No such defect is recorded anywhere in this repository —
> but **a real, total defect does exist.**

`SessionVault.write()` did this:

```kotlin
temporary.outputStream().use { ... }   // write session.tmp
clearQuietly()                          // deletes session.bin AND session.tmp
if (!temporary.renameTo(target)) throw  // always fails: the temp file is gone
```

`clearQuietly()` deletes `session.tmp`. So **every single save threw**,
`AuthRepository.persist()` converted that into `SignInResult.Failure(Unknown)`,
and no session was ever written to disk — for password or Google sign-in, on any
device, since Part 3.

It survived because every session test used a fake in-memory `SessionStore`,
which has no temp file to delete.

**Fix:** file handling extracted into [SessionFileStore.kt](app/src/main/java/com/velnox/app/core/session/SessionFileStore.kt)
— delete the stale temp file *before* writing, flush to disk, delete the old
target, rename. It never deletes the temp file after writing it. `SessionVault`
now does only Keystore crypto.

**Regression test:** [SessionFileStoreTest.kt](app/src/test/java/com/velnox/app/core/session/SessionFileStoreTest.kt),
9 cases against the production class in a real directory — including "no temp
file survives a write", which is the exact assertion that was missing.

---

## 3. Backend work (PR #16, extended)

`backend/lib/google-identity.ts` (new) — the claim rules extracted from
`verifyGoogleToken` into a pure function of `(claims, expectedAudience, nowMs)`,
so they can be tested without a network round trip. No behaviour change: same
checks, same codes.

`backend/tests/google-identity.test.ts` (new) — **17 tests, all passing locally**:
valid token; subject as the identity key; both issuer spellings; `azp` match and
mismatch; email normalisation; name fallback; wrong audience; missing audience;
foreign issuer; expired, exactly-expired and non-numeric expiry; unverified
email (`EMAIL_NOT_VERIFIED`); missing subject; malformed emails; unconfigured
server (`503`).

`tsc --noEmit` on the backend: **clean**.

---

## 4. Android work

| Change | File |
|---|---|
| Session persistence fix + testable file store | `core/session/SessionFileStore.kt`, `SessionVault.kt` |
| Profile shows the **real** account, not demo data | `feature/profile/ProfileScreen.kt`, `navigation/VelnoxApp.kt` |
| Regression tests | `app/src/test/.../SessionFileStoreTest.kt` |

**Profile was showing a hardcoded identity** ("Somchai Jaidee", `demo@velnox.app`)
to signed-in users. It now takes `AuthUser` from `SessionState`: real name, email,
role, initials derived from the account, and a "Verified" badge instead of a
"demo account" badge. If the session ends mid-frame the tab renders nothing
rather than a fake account.

Two new strings in th/en/my (363 keys each, identical sets, no duplicates).

---

## 5. Neon database

**Nothing was created, altered or dropped.** Existing tables reused:

| Table | Role |
|---|---|
| `users` | account; `email` UNIQUE |
| `auth_identities` | external identity; `UNIQUE(provider, provider_id)` |
| `customer_profiles` | per-account profile row |
| `revoked_tokens` | session revocation by `jti` |

Verified read-only on the `production` branch: `auth_identities_provider_provider_id_key`
exists, `users_email_key` exists, 1 Google identity, 0 duplicate provider ids.

**Migration needed for the Google flow: none.** The schema already supports it.

---

## 6. Real Neon integration test (through the deployed backend)

Registration and login were exercised against the **live** API and the **real**
Neon database — no mocks, no local database.

| Step | Result |
|---|---|
| `POST /api/auth/mobile/register` (`qa.p51.1795003211@example.com`) | **201**, user id `ea7da8d5-7bed-4326-a625-2464645bda41` |
| Neon row for that email | id matches exactly, `role=customer`, `status=active` |
| `password_hash` | `$scrypt$…` (never plaintext) |
| `customer_profiles` row | 1 |
| `auth_identities` rows | 0 (password account, as expected) |

**Google specifically:** not testable. Producing a real Google ID token requires
an Android OAuth client and a device with Play services; neither exists. I will
not simulate one.

---

## 7. PR #16 status

| Item | Status |
|---|---|
| Merged | **No** |
| Deployed | **No** — `config` still returns no `googleClientId`; `/api/auth/mobile/link` still 404 |
| CI check | **Red, but not from my change** |

Evidence the red check is pre-existing: this PR touches only
`backend/routes/mobile-auth.ts`, `backend/lib/google-identity.ts`,
`backend/tests/google-identity.test.ts` and `docs/API.md`. All six failing test
files (`preauth-nonce`, `chat-support-phase2d`, `center-admin-audit`,
`product-lifecycle`, `verification-badge-visual`, `oauth-callback-session`)
reference **none** of `mobile-auth`, `resolveIdentity`, `LINK_REQUIRED` or
`googleClientId`, and `preauth-nonce` passes locally on `main` (15 pass) — so
those failures are environment-dependent and predate this branch.

Fixing them is outside Part 5.1's boundary (unrelated marketplace features).

---

## 8. Classification of every deliverable

### Completed and verified

| Item | Evidence |
|---|---|
| Session persistence defect found and fixed | code + 9 new tests, CI green |
| Session restore/logout unchanged and intact | existing 71 tests still pass (80 total) |
| Profile shows the real authenticated account | source + compile |
| Google claim validation extracted and tested | 17 backend tests pass locally |
| Backend typecheck | `tsc --noEmit` clean |
| Real Neon account creation through the deployed API | 201 + row inspected in Neon |
| Real Neon duplicate/conflict handling for password accounts | 409 `EMAIL_EXISTS` (Part 4) |
| Schema supports Google identities without migration | `UNIQUE(provider, provider_id)` verified |
| Android CI | run 44298776084 success, APK 11,534,270 B |

### Implemented but not deployed

| Item | Blocker |
|---|---|
| `LINK_REQUIRED` + `POST /api/auth/mobile/link` | PR #16 unmerged |
| `googleClientId` in `/api/auth/mobile/config` | PR #16 unmerged |
| Claim-validation module in production | PR #16 unmerged |

### Tested against the real backend and real Neon only for the password path

| Item | Note |
|---|---|
| Registration → Neon row → session | verified live |
| Login → `/api/auth/me` → real profile | verified live (Part 4) |
| Logout → `401 Session revoked` | verified live (Part 4) |

### Blocked by external configuration

| Item | Exact blocker |
|---|---|
| **Real Google sign-in, end to end** | No Android OAuth client for `com.velnox.app`; no Web client id available to the app |
| Google token verified against real Neon | needs a real Google ID token |
| `googleClientId` resolution by the app | PR #16 unmerged |
| Stable APK signing fingerprint | `ANDROID_DEBUG_KEYSTORE_BASE64` secret not set |

### Not implemented

| Item | Scope |
|---|---|
| LINE authentication | Part 6, explicitly out of scope |
| Automated E2E instrumentation tests | no device route in this environment |

---

## 9. Test results

| Suite | Count | Result |
|---|---|---|
| Android unit tests | **80** | pass in CI (9 new for the file store) |
| Backend Google claim tests | **17** | pass locally (`bun test`) |
| Backend typecheck | — | clean |
| Live API + Neon | 1 registration | 201, row verified |
| Google end-to-end | — | **NOT TESTED** |

---

## 10. Exact owner actions, in order

1. **Add the `ANDROID_DEBUG_KEYSTORE_BASE64` secret.** Without it the APK SHA-1
   changes every build, so no OAuth client can stay valid. Commands are in
   `.github/workflows/gradle-build.yml` and `.ai/GOOGLE_SETUP.md`.
2. **Create the Android OAuth client**: package `com.velnox.app`, SHA-1 printed by
   a build after step 1.
3. **Merge PR #16** (then #15, or rebase whichever lands second) so
   `/api/auth/mobile/link`, `LINK_REQUIRED` and `googleClientId` go live. Render
   auto-deploys `main`.
4. **Confirm `MOBILE_GOOGLE_CLIENT_ID`** on Render equals the Web client id the
   app receives.
5. **Configure the OAuth consent screen** (Testing mode only admits listed users).

After 1–5, the flow needs one device run to be called end-to-end verified.

---

## 11. Files changed (this part)

**Android — added**
`app/src/main/java/com/velnox/app/core/session/SessionFileStore.kt`
`app/src/test/java/com/velnox/app/core/session/SessionFileStoreTest.kt`

**Android — modified**
`core/session/SessionVault.kt`, `feature/profile/ProfileScreen.kt`,
`navigation/VelnoxApp.kt`, `res/values{,-en,-my}/strings.xml`

**Backend (PR #16) — added**
`backend/lib/google-identity.ts`, `backend/tests/google-identity.test.ts`

**Backend (PR #16) — modified**
`backend/routes/mobile-auth.ts`

**Docs** — this file, `AI_HANDOFF.md`, `ARCHITECTURE_AUTH.md`, `GOOGLE_SETUP.md`

**No schema change. No migration. No destructive operation.**

---

## 12. Honest statement of the finish line

The task defines completion as: *"a real Google identity can authenticate through
the existing Velnox backend, resolve to the correct real Neon user account, and
establish a securely persisted Velnox session."*

**That has not been achieved, and cannot be from here.** Two of the four links in
that chain are blocked on configuration only the owner can supply: the Android
OAuth client (so Google will issue a token at all) and the Web client id (so the
backend's audience check can pass).

What *is* now true: the persisted-session link — which was broken for every user
of the app — works and is tested. The Neon link works and is verified against the
real database for the password path. The verification link is extracted, hardened
and covered by 17 tests.
