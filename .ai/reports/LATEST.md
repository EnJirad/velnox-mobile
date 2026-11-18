# LATEST.md — Velnox Mobile Latest Change Report

> **Current snapshot (overwritten each task).** Previous report (Part 3)
> archived verbatim in `.ai/history/20261118_164500_LATEST_before_part4.md`.
> Repository: `EnJirad/velnox-mobile`

---

## Task: Part 4 — Email Registration & Login

**Date:** 2026-11-18
**Commits:** `d523138` (feature), `6677ade` (duplicate resource fix),
`55ee97c` (test expectation + CI diagnostics)
**Verified CI run:** [44269003139](https://github.com/EnJirad/velnox-mobile/actions/runs/44269003139) — **success**
**Backend PR:** [velnox-marketplace#15](https://github.com/EnJirad/velnox-marketplace/pull/15) — open, **not merged, not deployed**
**Status:** **VERIFIED** — 57 unit tests green, APK built, real registration and login exercised against the live API

---

## 1. Repository safety

| Check | Result |
|---|---|
| `git fetch origin main` before work | Done; local == remote (`df35203`) |
| Uncommitted work at start | None |
| Work preserved | Yes — nothing deleted, no history rewritten |
| Local Android build | Not run (`.ai/AI_RULES.md` §1) |
| Database | Read-only queries only. **One test customer row was created by the required live test** (§6.3) |

---

## 2. Backend inspection (before writing anything)

Re-read `backend/routes/mobile-auth.ts` at `4b6b09d` and diffed it against the
Part 1/Part 3 snapshot: **byte-identical**. So the mobile surface is still exactly
register / login / google / line / config.

| Capability | Before Part 4 | Action |
|---|---|---|
| Register | `POST /api/auth/mobile/register` | **Reused** |
| Login | `POST /api/auth/mobile/login` | **Reused** |
| Logout | `POST /api/auth/logout` (Bearer) | **Reused** |
| Session restoration | `GET /api/auth/me` | **Reused** |
| Token refresh | none | **Not added** — no refresh token exists; documented |
| Forgot password | **missing** | **Added** (PR #15) |
| Password reset | **missing** | **Added** (PR #15) |
| Password hashing | `lib/password.ts`, scrypt N=16384 r=8 p=1 | **Reused unchanged** |
| Rate limiting | `middleware/rate-limit.ts` | **Reused**; two new limiters |
| Mail transport | **none in the repo** | New `sendResetEmail` via Resend HTTP, no new dependency |

Only the admin route `/api/admin/employees/:userId/reset-password` existed, and it
is staff-only (`requireAuth` + RBAC), so it is not a customer recovery path.

---

## 3. What was built (Android)

### 3.1 Login (`feature/auth/LoginScreen.kt`)

- Email + password, visibility toggle, inline validation, loading state,
  per-case error banner.
- **Forgot password** entry point and **Sign up** entry point.
- **Google** and **LINE** entry points. Both are inert by design and say
  "coming soon" instead of starting a flow that cannot finish. Facebook was
  removed: there is no backend counterpart.
- The screen never decides that sign-in succeeded — it reports
  `onSubmit(email, password)` and renders `isSubmitting` / `errorRes` from the
  caller. Only the API answer can change the session state.

### 3.2 Registration (`feature/auth/RegisterScreen.kt`, new)

- Full name, email, password, confirm password.
- 4-bar strength meter with a localized label, plus the server's actual rule
  spelled out ("at least 8 characters with letters and numbers").
- **Required consent** checkbox; the button stays disabled until the form is
  valid *and* consent is given, because pressing it creates the account.
- Back to sign-in, and the same provider entry points.

### 3.3 Recovery (`feature/auth/ForgotPasswordScreen.kt`, new)

- **Forgot password**: one email field, then a neutral
  "if that address has an account, a reset link is on its way".
- **Reset password**: new password + confirmation, and a paste-a-code field when
  no token was supplied. A successful reset signs the user out locally, because
  the old session must not outlive a password change.

### 3.4 Shared components (`feature/auth/AuthComponents.kt`, new)

`FieldGroup`, `VelnoxTextField`, `PasswordVisibilityToggle`, `ConsentToggle`,
`PrimaryButton`, `OrDivider`, `SocialButton`, `ErrorBanner`, `NoticeBanner`,
`SuccessBanner`, `PasswordStrengthMeter`, `AuthScreenFrame`, `AuthBrandHeader`,
`AuthFooter`. One copy each, so the three screens cannot drift.

### 3.5 Rules (`feature/auth/CredentialRules.kt`, new)

Email shape, name 1–120, password 8–128 with a letter and a digit, confirm match,
and the strength estimate. These mirror the API; they do not replace it.

### 3.6 Session integration

- `SessionController.register()` and `resetPassword()` added.
- `AuthRepository.register()` was already implemented in Part 3 but unreachable;
  it is now wired. `requestPasswordReset` / `resetPassword` added.
- The gate is **one destination with four steps** (`AuthStep`: login, register,
  forgot, reset), so the reason card and the pending destination survive moving
  between them. Back inside the gate returns to sign-in before it leaves the gate.
- After sign-in **or** registration the originally requested destination is
  applied. Home is untouched.

### 3.7 New error cases

`AuthError.EmailNotConfigured` (503) and `AuthError.InvalidResetToken` (400), each
with its own message. A 404 (endpoint not deployed) maps to `Unknown`, so the app
reports a generic failure rather than a false success.

---

## 4. Endpoints and schemas

### 4.1 Existing, reused (verified live)

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/api/auth/mobile/register` | `{name, email, password}` | `201 {success, data:{user:{id,email,name,role}}}` + `Set-Cookie: velnox_session` |
| POST | `/api/auth/mobile/login` | `{email, password}` | `200` same shape + cookie |
| GET | `/api/auth/me` | `Authorization: Bearer <token>` | `200 {success, data:{user:{…}}}` |
| POST | `/api/auth/logout` | Bearer | `200 {success, data:{success:true}}` |

Errors: `{success:false, error:{code, message}}` — `EMAIL_EXISTS` (409),
`INVALID_CREDENTIALS` (401), `VALIDATION_ERROR` (400), `UNAUTHORIZED` (401).

### 4.2 Added (PR #15, not deployed)

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/api/auth/forgot-password` | `{email}` | `200 {success, data:{requested:true}}` · `503 EMAIL_NOT_CONFIGURED` · `400 VALIDATION_ERROR` |
| POST | `/api/auth/reset-password` | `{token, password}` | `200 {success, data:{reset:true}}` · `400 INVALID_RESET_TOKEN` · `400 VALIDATION_ERROR` |

`GET /api/auth/mobile/config` gains `email: boolean` so the app can hide recovery
when no transport exists.

**Security properties of the new endpoints**

- Tokens are 32 random bytes; only the **SHA-256** is stored, so a database dump
  cannot be replayed.
- One live token per account; a second request invalidates the previous link.
- 30-minute expiry; consumption is guarded by `used_at` inside a transaction, so
  two concurrent requests cannot both succeed.
- Uniform answers for known/unknown addresses, with the unknown branch doing the
  same work (a short delay) so timing does not leak either.
- Rate limits: 3 per 15 min (forgot), 5 per 15 min (reset), per IP.
- No token is ever logged, returned in a body, or written in clear.

### 4.3 Database dependencies

`password_reset_tokens` — added to `db/schema.sql`:

```
id UUID PK · user_id UUID FK users ON DELETE CASCADE · token_hash TEXT UNIQUE
expires_at TIMESTAMPTZ · used_at TIMESTAMPTZ NULL · created_at TIMESTAMPTZ
indexes: (user_id), (expires_at)
```

Existing tables used unchanged: `users`, `auth_identities`, `revoked_tokens`,
`customer_profiles`. **No migration was run**; the PR does not apply it.

---

## 5. Password security

| Requirement | Status |
|---|---|
| Argon2id or a suitably configured algorithm | **scrypt N=16384, r=8, p=1, 64-byte key, 32-byte random salt** — already in `lib/password.ts`, reused rather than replaced. Argon2id would need a new native dependency on Render; scrypt is memory-hard and OWASP-acceptable at these parameters. |
| Never store plaintext | Verified: the new test account has `$scrypt$…` in `users.password_hash`. |
| Never return hashes to Android | `sendSession` returns `{id,email,name,role}` only; `/api/auth/me` returns profile fields and permissions, no hash. |
| Rate limiting | Existing per-IP limiters on register (5/min) and login (8/min); new 3/15min and 5/15min on recovery. |
| Backend input validation | `zod` schemas on every route; verified live (weak password → 400). |
| Generic errors | Login returns one `INVALID_CREDENTIALS` for missing account, missing password auth and wrong password; forgot-password is uniform. |

---

## 6. Test results

### 6.1 Unit tests — 57, all passing (CI)

| Class | Tests | Covers |
|---|---|---|
| `JwtTest` | 5 | `exp` parsing, boundaries, malformed |
| `SessionManagerTest` | 6 | save/read/clear, unreadable store, failed clear |
| `AuthRepositoryTest` | 18 | restore matrix, sign-in, **register**, **forgot**, **reset**, sign-out |
| `SessionControllerTest` | 16 | four states, restart, offline, **register**, **duplicate email**, **reset → Guest** |
| `AccessPolicyTest` | 5 | public/protected, fail-closed, expired reason |
| `CredentialRulesTest` | 7 | email, password policy, confirm, name, strength |

### 6.2 CI

| Item | Result |
|---|---|
| Run | 44269003139 on `55ee97c` |
| Run unit tests | **success** |
| Build debug APK | **success** |
| Verify / integrity / upload | **success** |
| Artifacts | `velnox-debug-apk` **10,637,196 B** · `velnox-unit-test-report` 23,718 B |

Two earlier runs failed and both were real defects, not flakes:
`44267358538` — `mergeDebugResources` (duplicate `login_continue_line`);
`44267996969` — one wrong test expectation. Both fixed; see §8.

### 6.3 Real backend tests (production API, not simulated)

A customer account was created because the task requires a real registration:

`qa.part4.20261118@example.com` · password `VelnoxQA2026x` · user id
`29c031fc-5abd-4d2f-b9e0-20c6c281aaa2`

| # | Test | Result |
|---|---|---|
| 1 | Register new account | **201**, user returned, `Set-Cookie: velnox_session` present |
| 2 | Login with those credentials | **200** + cookie; token captured |
| 3 | `GET /api/auth/me` with Bearer | **200**, real profile (`role: customer`, `status: active`) |
| 4 | Duplicate registration | **409** `EMAIL_EXISTS` |
| 5 | Wrong password | **401** `INVALID_CREDENTIALS` |
| 6 | Weak password on register (`short`) | **400** `VALIDATION_ERROR` |
| 7 | Letters-only password on register | **400** `VALIDATION_ERROR` |
| 8 | Logout with Bearer | **200** |
| 9 | `/api/auth/me` after logout | **401** `UNAUTHORIZED` — "Session revoked" |
| 10 | Row in Neon | `role=customer`, `status=active`, `password_hash` starts `$scrypt$` |
| 11 | `POST /api/auth/forgot-password` | **404** — expected: PR #15 not deployed |

Tests 1–10 are genuine round trips against `velnox-api.onrender.com`. Nothing was
mocked, and the Bearer path the app uses was proven end to end (3 and 9).

**Cleanup for the owner:** the test account above can be deleted whenever you
like (`DELETE FROM users WHERE email = 'qa.part4.20261118@example.com'` cascades
to its profile). It holds no orders or addresses.

### 6.4 Not verified

- No device/emulator run: the screens compile and are previewed, but layout and
  keyboard behaviour are unverified on hardware.
- The forgot/reset flow has **never executed end to end**, because the endpoints
  are not deployed and no mail transport exists. Its client half is unit-tested;
  its server half is typechecked only.

---

## 7. Files changed

**Added (Android)**

- `feature/auth/AuthComponents.kt`, `CredentialRules.kt`, `RegisterScreen.kt`,
  `ForgotPasswordScreen.kt`
- `app/src/test/.../feature/auth/CredentialRulesTest.kt`

**Modified (Android)**

- `feature/auth/LoginScreen.kt` (rebuilt on the shared components)
- `core/auth/{AuthApi,AuthModels,AuthRemoteDataSource,AuthRepository}.kt`
- `core/session/SessionController.kt`
- `navigation/{AccessPolicy,VelnoxApp}.kt`
- `res/values{,-en,-my}/strings.xml` — **357 keys each**, identical sets
- `app/src/test/.../{AuthRepositoryTest,SessionControllerTest}.kt`

**Modified (CI)**

- `.github/workflows/gradle-build.yml` — failing tests are now named from JUnit XML

**Backend (separate repo, PR #15)**

- `backend/routes/mobile-auth.ts`, `db/schema.sql`, `docs/API.md`

**Documentation**

- `.ai/reports/LATEST.md`, `.ai/AI_HANDOFF.md`, `.ai/ARCHITECTURE_AUTH.md`

---

## 8. Defects found and fixed during this part

1. **Duplicate string resource.** Adding the LINE entry point both renamed the
   Facebook string and inserted a new `login_continue_line`, so AAPT failed with
   `mergeDebugResources`. My local checks missed it because they compared *sets*
   of keys, not multiplicities; the checker now looks for duplicates too.
2. **Wrong test expectation.** `PasswordStrength.of("Password1")` is `Good`
   (score 3), not `Fair`. The implementation was right; the test was wrong.
3. **CI could not name a failing test.** The annotation step only grepped Gradle
   console lines, which the runner prefixes, so a test failure appeared as
   "exit code 1" and nothing else. It now parses the JUnit XML and emits one
   annotation per failure with class, name and message.

---

## 9. Remaining issues

1. **PR #15 is not merged or deployed**, so forgot/reset return 404 today. The app
   shows a generic failure; it never claims success.
2. **No mail transport** (`RESEND_API_KEY`, `MAIL_FROM`). Even after deploy, the
   endpoint answers `503 EMAIL_NOT_CONFIGURED` until those are set.
3. **`password_reset_tokens` must be created** before the endpoints work.
4. **Existing JWTs survive a password reset** — there is no session-version
   mechanism, so sessions end at their 7-day expiry. Documented in the PR.
5. **No refresh token** exists server-side.
6. **Google and LINE are entry points only** (Part 6). `mobile/config` reports
   `line:false`.
7. **Profile and Settings still show demo identity**, though
   `SessionState.Authenticated.user` now carries the real account.
8. **No device verification** of the new screens.
9. `rememberMe` is still a local toggle; it does not change the 7-day server
   session lifetime.
