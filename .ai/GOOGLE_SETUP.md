# GOOGLE_SETUP.md — Google Sign-In configuration

> What must exist in Google Cloud Console for Velnox Mobile, what is known, and
> what is still missing. **Nothing here was invented**: every value marked
> "verified" was read from the build, the API or this repository. Anything marked
> "unknown" could not be inspected from this environment.

---

## 1. What the flow needs

```
Android app (Credential Manager)
   serverClientId = WEB client id
        │
        │  Google ID token (aud = WEB client id)
        ▼
POST /api/auth/mobile/google  { idToken }
        │  verifies: signature · iss · aud · exp · azp · email_verified · sub
        ▼
users + auth_identities  →  velnox_session
```

Two OAuth clients are involved and they are **not interchangeable**:

| Client | Used by | Why |
|---|---|---|
| **Web** client | the app, as `serverClientId`; the backend, as `aud` check | The ID token's `aud` is the Web client id. The backend compares it to `MOBILE_GOOGLE_CLIENT_ID ?? GOOGLE_CLIENT_ID`. |
| **Android** client | Google, to authorise the app | Bound to `package name` + signing certificate `SHA-1`. Without it Google returns `DEVELOPER_ERROR` and no token is issued. |

---

## 2. Status

| Item | Value | Status |
|---|---|---|
| Package name | `com.velnox.app` | **Verified** (`app/build.gradle.kts`) |
| Signing SHA-1 (CI debug) | `a94659e6d884960206997a4d960a905563568ffb` | **Verified** — printed by CI run 44278012706 |
| Signing SHA-256 (CI debug) | `d22a28cd7fd049d8fe34301e670f6344816e3d95ca0ed544fb3623655447f1e3` | **Verified** |
| Web client id | — | **Unknown.** The backend has one (`config` → `google:true`) but its value is not exposed anywhere I can read. |
| Android OAuth client | — | **Unknown.** Google Cloud Console cannot be inspected from here. |
| Backend `MOBILE_GOOGLE_CLIENT_ID` | present? | **Verified present** (`GET /api/auth/mobile/config` → `google: true`). The value is not returned by the deployed build yet. |
| Backend verification code | `verifyGoogleToken()` | **Verified** — signature (via Google tokeninfo), `iss`, `aud`, `exp`, `azp`, `email_verified`, `sub`. |
| Backend rejects bad tokens | live probe | **Verified** — a forged token returns `401 INVALID_GOOGLE_TOKEN`. |
| Authorized redirect URIs | n/a for native | Not required: Credential Manager issues the ID token directly, with no redirect. Redirect URIs belong to the **web** flow only. |

---

## 3. Required owner actions

### 3.1 Create the Android OAuth client

Google Cloud Console → **APIs & Services → Credentials → Create credentials →
OAuth client ID → Android**.

- Package name: `com.velnox.app`
- SHA-1: `a94659e6d884960206997a4d960a905563568ffb`

That fingerprint is the **debug** key, now cached by the workflow
(`.github/workflows/gradle-build.yml`, key `velnox-debug-keystore-v1`), so it no
longer changes between builds. Before that cache existed every runner generated a
fresh debug keystore, which is why no fingerprint could have been registered.

For a release build, add a **second** Android client with the release key's
SHA-1. Both may coexist in the same project.

If the keystore cache is ever cleared, the fingerprint changes and the OAuth
client must be updated.

### 3.2 Confirm or create the Web OAuth client

The app needs the **Web** client id. Two ways to supply it:

1. **Merge [velnox-marketplace#16](https://github.com/EnJirad/velnox-marketplace/pull/16)**,
   which makes `GET /api/auth/mobile/config` return `googleClientId`. The app
   reads it at runtime and no APK rebuild is needed when it changes. *(Recommended.)*
2. Build with the value baked in:
   `./gradlew :app:assembleDebug -PvelnoxGoogleClientId=<web-client-id>.apps.googleusercontent.com`

Until one of these is done the app reports Google Sign-In as **not configured**
and disables the button. It does not open a sheet that would fail.

### 3.3 Confirm the server's audience matches

`MOBILE_GOOGLE_CLIENT_ID` (or `GOOGLE_CLIENT_ID`) on Render must be the **same
Web client id** the app uses. If they differ, every token is rejected with
`INVALID_GOOGLE_TOKEN` — the failure looks like a client bug but is a
configuration mismatch.

### 3.4 OAuth consent screen

The project must have a configured consent screen. In **Testing** mode only
accounts listed under *Test users* can sign in; for anyone else the flow fails
with `access_denied`. Publish the app, or add test users.

---

## 4. What the app does with each outcome

| Outcome | UI |
|---|---|
| `Token` | Sends it to `/api/auth/mobile/google`; session only after a `200` |
| `Cancelled` | Nothing — the user closed the sheet |
| `NoAccount` | "No Google account was found on this device" |
| `NotConfigured` | "Google Sign-In is not configured for this build"; button disables |
| `Network` | "Couldn't reach the server" |
| `Failed` | Generic retry message |
| `409 LINK_REQUIRED` | Switches to password sign-in: the account exists and must prove ownership |

---

## 5. Account linking rules

- A Google identity that is **already linked** → normal session.
- A Google email matching an **existing account** → `409 LINK_REQUIRED`. Nothing
  is merged. The user signs in with their password, then calls
  `POST /api/auth/mobile/link` (authenticated) to attach Google.
- Linking requires the session (proof of ownership) **and** a matching email
  (`409 EMAIL_MISMATCH`) **and** an unclaimed identity (`409 IDENTITY_CONFLICT`).
- `UNIQUE(provider, provider_id)` prevents duplicate Google mappings.

This behaviour ships in PR #16. Until it is merged, the deployed backend still
auto-links a verified Google email, so **the safe-linking rules are not live yet**.
