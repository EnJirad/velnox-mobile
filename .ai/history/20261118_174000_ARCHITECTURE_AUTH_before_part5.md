# ARCHITECTURE_AUTH.md — Velnox Mobile Authentication

> Scope: how the Android app proves who the user is. Companion to
> `.ai/AI_HANDOFF.md` and `.ai/reports/LATEST.md`.
> Last updated: 2026-11-18 (Part 4).

---

## 1. Layers

```
UI (Compose)
  HomeScreen / CartScreen / SettingsScreen / LoginScreen …
        │  reads state, calls callbacks — never decides auth
        ▼
SessionController            ← state holder (core/session)
        │
        ▼
AuthRepository               ← rules: what counts as signed in (core/auth)
        │                     │
        ▼                     ▼
AuthRemoteDataSource     SessionManager            ← token owner (core/session)
   (core/auth, OkHttp)        │
        │                     ▼
        ▼                SessionVault               ← AES-GCM + Android Keystore
  Velnox Express API          (core/session)
  /api/auth/mobile/*
  /api/auth/me
        │
        ▼
  Neon PostgreSQL
  users · auth_identities · revoked_tokens
```

| Layer | File | Rule it owns |
|---|---|---|
| Data source | `core/auth/AuthRemoteDataSource.kt` | HTTP + JSON + error mapping. Never decides validity. |
| Repository | `core/auth/AuthRepository.kt` | Only `200` from the API makes a session; unreachable API is never success. |
| Session manager | `core/session/SessionManager.kt` | One in-memory token; `exp` read for pre-emptive expiry only. |
| Secure storage | `core/session/SessionVault.kt` | AES-256-GCM with a Keystore key; ciphertext on disk only. |
| State holder | `core/session/SessionController.kt` | The four states; the only writer of them. |
| Nav policy | `navigation/AccessPolicy.kt` | Which destinations need a session. |

---

## 2. State machine

```
                 restoreOnce()            me() == 200
   ┌─────────┐   ┌──────────┐   ┌──────────────────────┐
   │ Loading │──▶│  Guest   │   │    Authenticated     │
   └─────────┘   └──────────┘   └──────────────────────┘
        │             ▲                    │      │
        │ no token    │ signOut()          │      │ 401 on any call
        └─────────────┘                    │      ▼
                                           │  ┌────────────────┐
        me() 401 / exp passed              │  │ ExpiredSession │
        ───────────────────────────────────┴─▶└────────────────┘
        me() unreachable → Guest + SessionNotice.NetworkUnavailable
```

| State | Meaning | Account surfaces |
|---|---|---|
| `Loading` | A stored session is being resolved | Blocked (answer unknown) |
| `Guest` | No session | Blocked |
| `Authenticated(user)` | API confirmed the token | Allowed |
| `ExpiredSession` | Had a session, no longer valid; token cleared | Blocked, Login explains why |

`SessionNotice.NetworkUnavailable` is not a state: a token that could not be
verified leaves the app in `Guest` while the token stays on disk for the next
launch. It is never treated as authentication.

---

## 3. Token lifecycle

1. **Issue** — `POST /api/auth/mobile/login` (or `register`, `google`, `line`)
   answers `200 {success, data.user}` and sets
   `Set-Cookie: velnox_session=<jwt>; HttpOnly; Secure; SameSite=None; Max-Age=604800`.
2. **Capture** — `AuthRemoteDataSource.captureSession()` reads that header. The
   server never returns the token in the body, so this is the only carrier.
3. **Store** — `SessionManager.save()` encrypts with AES-256-GCM (key in
   AndroidKeystore, alias `velnox.session.v1`, non-exportable) and writes
   `[version][12-byte IV][ciphertext]` to `filesDir/session/session.bin` via a
   temp file + rename. Backup rules exclude `session/`.
4. **Use** — every authenticated call sends `Authorization: Bearer <token>`. The
   server accepts this (`bearerOrCookie`) and prefers the cookie only when both
   are present.
5. **Verify** — `GET /api/auth/me` checks signature, `jti` against
   `revoked_tokens`, and the `users` row. This is the only proof of identity.
6. **Expire** — the JWT carries `exp` (7 days). `Jwt.isExpired` lets the app skip
   a guaranteed-401 round trip; the API remains the authority.
7. **Revoke** — sign-out clears storage **first**, then calls
   `POST /api/auth/logout`, which inserts the `jti` into `revoked_tokens` and
   severs WebSocket connections. A failed revoke never blocks the local clear.

### Why the cookie value is the token

The mobile routes were written for a native client but only ever set the cookie;
no endpoint returns the token in a body, and there is no refresh endpoint. The
server's own comment describes the intended native path: *"native clients send
the same opaque value as a Bearer because a cookie set by a fetch response is not
reliably stored by a WebView/native HTTP stack."* The app therefore reads
`Set-Cookie` once and replays the value as a Bearer header. No cookie jar, no
WebView, no invented credential.

If the backend later returns `data.token`, only `captureSession()` changes.

---

## 4. Security rules (and where each is enforced)

| Rule | Enforcement |
|---|---|
| No client boolean is proof of authentication | `Authenticated` is only produced by `me() == 200` or a sign-in response; `AccessPolicy.isAllowed` accepts only that state |
| No plaintext token on disk | `SessionVault` (AES-256-GCM, Keystore key); nothing written to DataStore or SharedPreferences |
| No secrets in the APK | Only `BuildConfig.API_BASE_URL` (a public HTTPS origin). Provider secrets stay on Render. |
| Network failure never authenticates | `RestoreOutcome.Unverified` → `Guest`; token kept, account never produced |
| Expired/invalid session is dropped | `restore()` clears storage on `401` or a passed `exp` |
| Server remains the authority | All account surfaces are `requireAuth` in the API; the client gate is UX + defence in depth |
| No token in logs or UI | `StoredSession` is never printed; `SessionManager` exposes only `current()` to the repository |
| Backup cannot leak a session | `res/xml/backup_rules.xml`, `data_extraction_rules.xml` exclude `session/` |

---

## 5. Endpoints used

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/mobile/login` | none (IP rate limit 8/min) | email + password → session cookie |
| POST | `/api/auth/mobile/register` | none (5/min) | create customer → session cookie (UI in Part 4) |
| POST | `/api/auth/mobile/google` | none (10/min) | Google ID token → session (Part 6) |
| POST | `/api/auth/mobile/line` | none (10/min) | LINE ID token → session (Part 6) |
| GET | `/api/auth/mobile/config` | none | `{google, line, email}` availability probe |
| POST | `/api/auth/forgot-password` | none (3/15min) | uniform reset request — **PR #15, not deployed** |
| POST | `/api/auth/reset-password` | none (5/15min) | consume a reset token — **PR #15, not deployed** |
| GET | `/api/auth/me` | Bearer or cookie | validate session, return account |
| POST | `/api/auth/logout` | Bearer or cookie | revoke `jti`, clear cookie |

Not used by the app: `/api/auth/password-login` and `/api/auth/password-register`
(Turnstile-gated, browser only), `/api/auth/preauth`, `/auth/google` (browser
redirect flow).

---

## 6. Error mapping

| Server | Client | UI |
|---|---|---|
| 401 on sign-in | `InvalidCredentials` | "Email or password is incorrect" |
| 401 on `/me` | `Unauthorized` | → `ExpiredSession` |
| 409 `EMAIL_EXISTS` | `EmailExists` | Part 4 registration |
| 409 `ACCOUNT_CONFLICT` | `AccountConflict` | Part 6 linking |
| 503 `EMAIL_NOT_CONFIGURED` | `EmailNotConfigured` | "reset email is not available yet" |
| 400 `INVALID_RESET_TOKEN` | `InvalidResetToken` | "that link is invalid or expired" |
| 403 `ACCOUNT_DISABLED` | `AccountDisabled` | "This account is disabled" |
| 400 `VALIDATION_ERROR` | `Validation` | "Check your details" |
| 429 | `RateLimited` | "Too many attempts" |
| `IOException` | `Network` | "Couldn't reach the server" |
| 5xx / unusable body | `Server` | "Try again" |

---

## 7. The authentication gate

The gate is one destination with four steps (`AuthStep`), so the reason card and
the pending destination survive moving between them:

```
requestTab / requestOverlay (protected, not Authenticated)
        └─▶ Login ──┬─▶ Register ──┐
                    ├─▶ Forgot ──▶ Reset
                    └──────────────┴─▶ success ⇒ Authenticated ⇒ pending destination applied
```

Back inside the gate returns to sign-in before it leaves the gate, so a user who
opened Sign up by mistake does not lose the destination they were reaching for.

## 8. Not implemented yet

- **Google and LINE provider flows** (Part 6). Server side is ready; the app has
  no SDK and no ID-token source. The buttons exist and say so.
- **Password recovery delivery** — endpoints are in PR #15 (not deployed) and no
  mail transport is configured.
- **Refresh tokens** — the backend has none.
- **Refresh tokens** — the backend has none. Sessions are 7-day JWTs; the app
  reports `ExpiredSession` and asks the user to sign in again.
- **Logout-everywhere / device list** — no endpoint; `revoked_tokens` is per `jti`.
- **Profile data** — `Authenticated` carries the account from `/me`, but Profile
  and Settings still render their demo content until Part 4 wires
  `/api/customer/profile`.
