# LATEST.md — Velnox Mobile Latest Change Report

> **Current snapshot (overwritten each task).** Previous phase archived in
> `.ai/history/20261118_063000_LATEST_before_part1_audit.md`.
> Repository: `EnJirad/velnox-mobile` · backend `EnJirad/velnox-marketplace` ·
> database Neon project `Velnox`

---

## Task: Part 1 — Project Audit & Architecture Assessment (Guest Shopping + Authentication)

**Date:** 2026-11-18
**Scope:** Inspection only. No application code, backend code, schema, migration,
workflow or configuration file was modified. The only write in this task is this
report.
**Status:** **COMPLETE (audit)**. Part 2 not started; awaiting owner approval.

---

## 0. Repository safety

| Check | Result |
|---|---|
| `git fetch origin main` | Done before inspection |
| Local `HEAD` | `0db3cd36104f82a3c7645ff84d2a2e75889629cf` |
| `origin/main` | `0db3cd36104f82a3c7645ff84d2a2e75889629cf` (identical, divergence `0 0`) |
| Uncommitted changes at start | **None** (`git status --porcelain` empty) |
| Uncommitted changes created | None (no source edit) |
| Local Android build | Not run (`.ai/AI_RULES.md` §1 — Actions only) |
| Last CI on `0db3cd3` | GitHub Actions run `44233078867` — **success** |
| Previous report archived | `.ai/history/20261118_063000_LATEST_before_part1_audit.md` (verbatim copy of the Phase 3 report from `HEAD`) |

> ⚠️ **History note for Part 2.** Commit `7aac8a6` ("…implement guest-first startup
> with auth flow") added `app/src/main/java/com/velnox/app/AuthState.kt` and a
> guest-first `VelnoxNavHost`. The next commit, `0db3cd3` ("Restore tree to
> 2d05f56"), deleted `AuthState.kt` and restored the login-gated nav host **but
> kept `7aac8a6`'s search-placeholder change in `HomeScreen.kt`**. So the current
> tree is *not* byte-identical to `2d05f56`, and no guest-first code is live.
> Treat `git show 7aac8a6` as prior thinking, not as current source.

---

## 1. Current Android architecture

### 1.1 Build and package

| Item | Value |
|---|---|
| Application id / namespace | `com.velnox.app` (package `com.velnox.app`) |
| `compileSdk` / `targetSdk` / `minSdk` | 35 / 35 / 26 |
| AGP / Kotlin / Compose BOM | 8.7.2 / 2.0.21 / 2024.10.01 |
| Java / JVM target | 17 / 17 |
| Build command | `./gradlew :app:assembleDebug` (GitHub Actions only) |
| Modules | `:app` only (`settings.gradle.kts`) |

`app/build.gradle.kts` dependencies (complete list):

- `androidx.core:core-ktx:1.13.1`
- `androidx.lifecycle:lifecycle-runtime-ktx:2.8.6`
- `androidx.activity:activity-compose:1.9.3`
- `androidx.datastore:datastore-preferences:1.1.1`
- Compose BOM `2024.10.01`: `ui`, `ui-graphics`, `ui-tooling-preview`, `animation`, `material3`
- `debugImplementation ui-tooling`

**Absent on purpose / not yet added:** navigation-compose, ViewModel, Hilt/Koin,
Retrofit/OkHttp/Ktor, kotlinx-serialization/Moshi/Gson, DataStore Proto,
`androidx.security:security-crypto` (encrypted prefs), Credential Manager,
Google Play Services Auth (`googleid`), LINE SDK, coil/image loading, WorkManager,
Firebase. There is **no `INTERNET` permission** in `AndroidManifest.xml` and
**no network security config**, no `networkSecurityConfig`, no `usesCleartextTraffic`.

### 1.2 Startup flow (exact)

```
Process start
  → VelnoxApplication.onCreate()            creates one process-scoped
                                            PreferencesRepository (DataStore "velnox_preferences")
  → MainActivity.onCreate(): enableEdgeToEdge(); setContent { VelnoxApp() }
  → VelnoxApp()                              VelnoxPreferencesProvider
                                               → LocalPreferences.current
                                               → VelnoxLocaleProvider(language)
                                               → VelnoxTheme(darkTheme from ThemeMode)
                                               → VelnoxNavHost()
```

`PreferencesRepository.preferences` is a `Flow<UserPreferences>` with
`collectAsState(initial = UserPreferences())`. **There is no splash/hold state**:
the first frame is composed with defaults (device locale, `ThemeMode.SYSTEM`), so a
stored dark theme or non-device language paints one frame late. A cold start has
no I/O beyond reading DataStore asynchronously.

Stored keys (`PreferencesRepository.Keys`): `language`, `theme_mode`,
`notify_order_updates`, `notify_promotions`, `notify_security`. **No auth key of
any kind.**

### 1.3 Navigation architecture — and exactly how Home vs Login is decided

`app/src/main/java/com/velnox/app/navigation/VelnoxApp.kt` — `VelnoxNavHost()`:

```kotlin
var signedIn by rememberSaveable { mutableStateOf(false) }   // line ~73
var tabKey by rememberSaveable { mutableStateOf(AppTab.Home.key) }
var overlayKey by rememberSaveable { mutableStateOf<String?>(null) }
```

```kotlin
Crossfade(targetState = signedIn, ...) { isSignedIn ->
    if (!isSignedIn) LoginScreen(onSignedIn = { signedIn = true; tabKey = Home; overlayKey = null })
    else            TabScaffold(...)  // five tabs, overlays, settings, etc.
}
```

**Answer to "how does the app decide Home or Login?"** — The decision is a *single
in-memory Compose boolean*:

- It is initialised to `false` on every cold start (comment at line 63–70 states
  this explicitly: "Phase 2 has no authentication: `signedIn` is a demo gate that
  starts false and is never persisted, so a cold start always lands on Login").
- It is flipped to `true` only by `LoginScreen`'s `onSignedIn` callback, which
  currently fires after a `delay(700)` + `delay(450)` and a local
  `login_demo_hint`-labelled form validation. No server call, no credential check.
- It resets to `false` from Settings → Sign out (`SettingsScreen.onSignOut`).
- `rememberSaveable` means it survives rotation/process-restoration of the same
  task, but **not** a fresh launch.
- Nothing is read from disk, from a token store or from a network call at
  startup. A network call at startup does not exist anywhere in the app.

Consequence: **the app currently starts on Login and cannot be used as a guest.**

Navigation is a hand-rolled string-keyed host, not Navigation-Compose:

| State | Type | Meaning |
|---|---|---|
| `tabKey` | `AppTab.key` string | `home` / `messages` / `orders` / `cart` / `profile` |
| `overlayKey` | `String?` | `null` = tab view; otherwise `settings`, `settings/<page>`, `category`,
`category:<id>`, `product:<id>`, `order:<id>`, `thread:<id>`, `wishlist`, `checkout`,
`search`, `notifications` |
| `signedIn` | `Boolean` | login gate (see above) |
| `cart` | `remember { CartState() }` | demo cart, **not** `rememberSaveable`, lost on process death |
| `tabStateHolder` | `rememberSaveableStateHolder()` | per-tab `SaveableStateProvider(tab.key)` |

Back handling is one `BackHandler(enabled = signedIn && (overlayKey != null || tab != AppTab.Home))`
that unwinds `settings/<page>` → `settings`, `category:<id>` → `category`, other
overlays → `null`, non-Home tab → Home, and then lets the system exit.

Tab content is wired in `TabContent()` (lines 190–230). Overlay content is resolved
in a `when`/`else` chain (lines 149–182); anything unmatched falls through to
`PlaceholderDestination`, an honest "preview" screen — `product:`, `wishlist`,
`checkout`, `search`, `notifications` all land there today.

### 1.4 Home screen and catalogue

- `feature/home/HomeScreen.kt` (1037 lines): fixed toolbar (logo, search field,
  notification bell) → seasonal banner → `stickyHeader` popular categories →
  trust row → featured rail → full feed, in **one `LazyColumn`** with stable keys
  and content types.
- `feature/home/HomeCatalog.kt`: `CatalogSource` interface
  (`categories()`, `products()`, `productsIn(categoryId)`, `search(query, context)`)
  implemented by `object HomeCatalog` over **8 hard-coded `ProductPreview` items**
  and 6 categories. `ProductPreview` carries `@StringRes name/store/tag`, flat
  vector art (`ProductArt`), and `categoryIds`. **No price, stock, image URL,
  shop, variant or id beyond `p1..p8`.**
- `HomeScreen(catalog: CatalogSource = HomeCatalog, …)` already takes the source as
  a parameter — this is the intended seam for a repository-backed implementation.
- Category hub + listings: `feature/home/CategoryScreen.kt` (307 lines).
- Notifications: `feature/home/HomeNotifications.kt` — an **empty list by design**,
  so the popover shows an honest empty state.

### 1.5 Search

Local only. `HomeScreen` holds `var query by rememberSaveable` and
`var selectedCategory`; results = `catalog.search(query, context)` memoized on
`(query, context, catalog)`, matched against localized name/store/tag/category.
There is **no server search, no debounce, no paging, no error state** because there
is no I/O. The backend already exposes a tokenized search
(`GET /api/products/catalog?q=`) — see §2.5.

### 1.6 Cart

- `feature/cart/CartState.kt`: a plain class holding
  `mutableStateListOf(CartItem)` pre-seeded with **three demo lines** (`p2`, `p3`,
  `p6`) and `increase/decrease/toggleSelection/remove/toggleAll`.
- `CartItem` deliberately has **no price field**; `feature/cart/CartScreen.kt`
  renders placeholder amounts and a `checkout` route that resolves to
  `PlaceholderDestination`.
- Cart state is `remember { CartState() }` in the nav host: survives tab switches,
  dies with the process. Not tied to any user, session or server.

### 1.7 Orders, Messages, Profile, Settings

- `feature/orders/OrdersScreen.kt` (404 lines): `enum OrderStatus` (Pending,
  Processing, Shipped, Delivered, Completed, Cancelled, Returned) with `StatusTone`,
  `OrdersFilter`, `data class DemoOrder`; detail + timeline on demo data.
- `feature/messages/MessagesScreen.kt` (319 lines): `DemoThread` + `ThreadTime`,
  conversation screen on demo data.
- `feature/profile/ProfileScreen.kt` (394 lines): demo identity
  (`profile_demo_name`, `profile_demo_handle`, `profile_demo_email`), two demo
  addresses, three statistics. Sign out is **not** here; it lives in Settings.
- `feature/settings/SettingsScreen.kt` + `SettingsDetailScreen.kt` (643 lines):
  language, appearance, notifications, account pages (`EditProfile`,
  `PersonalInfo`, `Addresses`, `About`, security), and **Sign out** with a
  confirmation dialog (`settings_sign_out_demo` is an honest demo label).
  Edit-profile and address forms write to in-memory `rememberSaveable` only.

### 1.8 Authentication-related code that exists today

| Item | Location | Reality |
|---|---|---|
| Login screen | `feature/auth/LoginScreen.kt` (735 lines) | UI complete: email + password, inline validation, show/hide, remember-me, "Success"/"ComingSoon" notice banner, previews at 390/320dp and dark |
| Sign-in action | `LoginScreen.submit()` | `scope.launch { delay(700); notice = Success; delay(450); onSignedIn() }` — **simulated**, comment says "Phase 2 replaces this with the real sign-in call" |
| Validation | `emailErrorRes`, `passwordErrorRes` | Email regex; password `length < 6` (**backend requires ≥ 8 + strength rules — mismatch**) |
| Social buttons | `SocialButton("G")`, `SocialButton("f")` | Google **and Facebook**; both only set `LoginNotice.ComingSoon`. There is no LINE button and no Google/LINE SDK |
| Registration | `SignUpRow(...)` | Renders "no account? Sign up" with an `onClick` that only shows `ComingSoon`. **No registration screen exists** |
| Session state | `VelnoxNavHost.signedIn` | demo boolean (see §1.3) |
| Sign-out | `SettingsScreen` → `onSignOut` | flips the boolean; no token to clear |
| Any HTTP client | — | **none** |
| Any token storage | — | **none** (not even an unused key) |
| Any Google/LINE SDK or intent filter | — | **none** in `AndroidManifest.xml` |

### 1.9 Networking configuration

There is none. No base URL constant, no `INTERNET` permission, no client, no
interceptor, no error mapping, no retry policy, no certificate pinning. Part 2+
creates this layer from scratch.

### 1.10 Secure storage and session handling

Only `DataStore Preferences` (`velnox_preferences`) for language/theme/notification
flags. No `EncryptedSharedPreferences`, no Keystore usage, no token, no user id.
`android:allowBackup="true"` with no `dataExtractionRules`/`fullBackupContent`
(see §7).

### 1.11 Localization

- `core/i18n/VelnoxLocaleProvider.kt` supplies a `createConfigurationContext`
  with the selected `Locale` through `LocalContext`/`LocalConfiguration` — a
  language switch recomposes the whole tree without recreating the activity.
- `AppLanguage`: `TH("th")`, `EN("en")`, `MY("my")`; `fromTag` falls back to the
  device locale, then Thai.
- Resource folders: `values/` (Thai, default), `values-en/`, `values-my/`,
  `values-night/` (themes only). **292 strings in each of the three languages**
  (verified by count).
- Server payloads (product names, categories, shops) are **not localized**; every
  product returned by `/api/products/catalog` is Thai text. Part 5+ must decide how
  backend content is localized (the backend README documents an existing I18N doc,
  but the API returns single-language fields today).

### 1.12 GitHub Actions

`.github/workflows/gradle-build.yml` — triggers on push/PR to `main` and
`workflow_dispatch`; JDK 17 Temurin; Android SDK cmdline-tools `11076708` →
`platforms;android-35`, `build-tools;35.0.0` and `34.0.0`; Gradle **8.11.1**
installed outside the wrapper; runs `./gradlew :app:assembleDebug --stacktrace
--refresh-dependencies`; verifies the APK exists/non-empty and `unzip -t`; uploads
`velnox-debug-apk` for 30 days. No unit tests, no lint, no signing, no versioning.
`setup-java@v4`/Node-20 deprecation annotations remain (known, non-blocking).

---

## 2. Current backend architecture (Velnox Express)

**Location:** `EnJirad/velnox-marketplace`, `backend/` (TypeScript, Express).
**Why this is "the existing Velnox Express backend":** `.env.example` in that
repository already contains a *native-app* section (`MOBILE_GOOGLE_CLIENT_ID`,
`LINE_CHANNEL_ID`, with comments about the APK and "never ship the secret in the
APK"), and `backend/routes/mobile-auth.ts` was written for the mobile client.
No Express backend exists inside `velnox-mobile`.

**Deployment:** Render service `velnox-api` (workspace `My Workspace`,
`tea-d4fln60gjchc73e5sn5g`), region Ohio, free plan, `bun install` /
`bun run api:start`, auto-deploy from `main`, public URL
`https://velnox-api.onrender.com`. Live probe returned
`GET /api/auth/mobile/config → 200 {"success":true,"data":{"google":true,"line":false}}`.

**Stack (from `backend/package.json`, `server.ts`):** Express + `cors` + `helmet`
+ `cookie-parser`, `jsonwebtoken`, `zod`, `pg` pool (`backend/db/index.ts`,
`query()` + `getClient()`), `bcrypt`-free `crypto.scrypt` password hashing,
Cloudflare Turnstile verification, `tsx` runtime.

**Order of middleware in `server.ts`:** `helmet()` → `trust proxy = 1` →
`cookieParser()` → Stripe raw-body matcher (**before** `express.json`) →
`express.json({limit:"1mb"})` → CORS → origin guard → security rate limiter →
diagnostics gate → route setup (`setupGoogleAuth(app)`, `setupMobileAuth(app)`,
`setupRoutes`, seller/order/product/wholesale/upload/etc.) → `errorHandler`.

### 2.1 Authentication endpoints (verified in source)

`backend/routes/mobile-auth.ts` — the native client surface:

| Method | Path | Gates | Success body |
|---|---|---|---|
| POST | `/api/auth/mobile/register` | `mobile-register` limiter (5/min/IP) | `setSessionCookie` + `201 {success, data.user{id,email,name,role}}` |
| POST | `/api/auth/mobile/login` | `mobile-login` limiter (8/min/IP) | `200` same shape |
| POST | `/api/auth/mobile/google` | `mobile-social` limiter (10/min/IP) | `200` same shape |
| POST | `/api/auth/mobile/line` | `mobile-social` limiter | `200` same shape |
| GET | `/api/auth/mobile/config` | none | `{google:boolean, line:boolean}` |

`backend/routes/auth.ts` — web/privileged surface:

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/member-login` | VelCenter staff password login (Turnstile-gated) |
| POST | `/api/auth/password-login` | Customer/seller password login, **Turnstile fail-closed** |
| POST | `/api/auth/password-register` | Customer registration, Turnstile-gated |
| POST | `/api/auth/change-password` | Authenticated |
| POST | `/api/auth/preauth` | Mints a one-time pre-auth proof cookie after Turnstile (for the browser OAuth gate) |
| GET | `/auth/google` | Browser OAuth start; mints `state` in a signed httpOnly cookie |
| GET | `/auth/google/callback` | Validates `state` **before** code exchange; resolves identity; sets session; redirects |
| GET | `/api/auth/me` | `bearerOrCookie`; returns cached profile (30 s TTL) |
| POST | `/api/auth/logout` | `bearerOrCookie`; inserts `jti` into `revoked_tokens`; clears cookie |

Register/login also create a `customer_profiles` row. Responses are
`{success, data}` or `{success:false, error:{code,message}}`.

**Live verification:** `POST /api/auth/mobile/login` with a synthetic
non-existent address returned `401 {"code":"INVALID_CREDENTIALS"}` (generic
message → no enumeration). No account was created.

### 2.2 Authentication middleware

`backend/middleware/auth.ts`:

- `sessionToken(req)` = `bearerOrCookie(req)`: `req.cookies.velnox_session`
  **or** `Authorization: Bearer <token>` (identical opaque JWT value).
- `requireAuth`: verifies JWT with `JWT_SECRET`, then checks `revoked_tokens` by
  `jti` (DB error is *not* masked as 401); sets `req.user = {userId, email, jti}`,
  otherwise `401 UNAUTHORIZED`.
- `optionalAuth`: same but invalid/revoked/missing tokens are treated as
  anonymous. **This is the existing guest seam** — `GET /api/products`,
  `/api/products/:id/reviews` already use it.

`backend/middleware/seller.ts` (role/permission checks), `rate-limit.ts`
(in-memory + IP keys), `origin-guard.ts`, `diag-guard.ts`, `error.ts`.

### 2.3 User management and password handling

- `users` is the single account table for customers, sellers, staff and owner —
  differentiated by `role` (`customer`, `seller`, `staff`, `owner`) and
  `status` (`active`, `suspended`, `banned`).
- `backend/lib/password.ts`: `scrypt` with `N=16384, r=8, p=1, keylen=64`,
  32-byte random salt, stored as `$scrypt$N$r$p$salt$hex`, verified with
  `timingSafeEqual`; `isPasswordHashFormat()` distinguishes password accounts from
  OAuth-only accounts.
- **Password policy differs between surfaces:** `mobile-auth.ts` requires ≥ 8
  chars + `PASSWORD_RE` strength, while the app's own UI (`LoginScreen`) enforces
  ≥ 6. `auth.ts` web routes use their own checks.

### 2.4 JWT / session architecture

- `createSessionToken(userId, email)`: HS256 `jsonwebtoken`, payload
  `{userId, email, jti: crypto.randomUUID()}`, `expiresIn: "7d"`.
- `setSessionCookie`: name **`velnox_session`**, `httpOnly: true`,
  `secure: NODE_ENV === "production"`, **`sameSite: "none"`**, `maxAge` 7 days,
  `path: "/"`.
- Revocation: `revoked_tokens(token_id=UNIQUE, user_id FK, expires_at, revoked_at)`
  checked by `requireAuth` and by `/api/auth/me`; lazy cleanup every 5 min;
  logout also severs live WebSocket connections.
- The response body **never includes the token**; the cookie is the only carrier.
  The bearer branch exists but is currently unreachable — see §7 risk R1.

### 2.5 Products, cart and order endpoints

| Area | Endpoints (verified) | Gate |
|---|---|---|
| Catalogue | `GET /api/products` (`optionalAuth`), `GET /api/products/catalog`, `GET /api/products/:productId`, `GET /api/products/:productId/reviews` | public (guest-readable) |
| Cart | `GET /api/customer/cart`, `POST /api/customer/cart/add`, `PUT /api/customer/cart/item/:cartItemId`, `DELETE /api/customer/cart/item/:cartItemId` | **`requireAuth` (12 occurrences in `cart.ts`, `optionalAuth` 0)** |
| Wishlist | `GET /api/customer/wishlist`, `POST /api/customer/wishlist/toggle` | `requireAuth` |
| Checkout | `POST /api/customer/checkout` | `requireAuth`; server-side pricing; refuses client-supplied money fields |
| Orders | `GET /api/customer/orders`, `GET /api/customer/orders/:orderId`, `PATCH /api/customer/orders/:orderId/cancel`, `POST /api/customer/reorder` | `requireAuth` |
| Profile | `GET /api/customer/profile`, `PUT /api/customer/profile`, `GET /api/customer/account-summary` | `requireAuth` |
| Addresses | `GET/POST /api/customer/addresses`, `DELETE /api/customer/addresses/:id` | `requireAuth` |

`GET /api/products/catalog` is a real, paged, tokenized search:
`q` (≤ 6 tokens, AND pass then OR recall pass, relevance-ranked), `category`
(slug or UUID → slug), `shopId`, `minPrice`, `maxPrice`, `inStock`, `minRating`,
`onSale`, `sortBy`, `limit` (default 50, max 200), `offset`. It only returns
`p.status='published'` from `sh.status='active'` shops, and returns variants,
images, `featuredVariant`, `totalAvailableStock`. Live probe with `limit=1`
returned one real product with 11 variants — this is what Home should render
instead of `HomeCatalog`.

### 2.6 Authorization and ownership checks

- Every cart/order/address/profile handler scopes by `req.user!.userId`; order
  detail and cancel look up `WHERE id = $1 AND user_id = $2`; checkout rebuilds the
  address snapshot server-side from an owned `addresses` row and rejects any
  client-supplied amount (`CLIENT_AMOUNT_NOT_ACCEPTED`).
- `ensureCart(userId)` (`cart.ts` ~line 231) does `SELECT … WHERE user_id=$1` then
  `INSERT INTO carts (user_id)`; the unique index on `carts.user_id` plus that
  pattern is the only race guard.
- Seller and center surfaces are guarded by `middleware/seller.ts` +
  `lib/permissions.ts`; guests and customers never pass them.

### 2.7 CORS, cookies and the mobile app

- `cors({ origin: allOrigins, credentials: true })` where `allOrigins` merges
  `CORS_ORIGINS`, the `VITE_*_URL` production origins and localhost dev origins.
- `createOriginGuard`: rejects state-changing requests whose `Origin` header is not
  allowlisted (403 `FORBIDDEN`), **but accepts requests with no `Origin` header**
  — the comment names the mobile app explicitly.
- Native OkHttp sends no `Origin` → the guard passes. Native TLS means OkHttp/HttpURLConnection
  will store `set-cookie` with `SameSite=None; Secure` normally, and subsequent
  requests send it. `Authorization: Bearer` is an accepted alternative in
  `bearerOrCookie`.

---

## 3. Current Neon schema overview

**Project:** `Velnox` (`tiny-darkness-71996293`), Neon PG **18**,
`aws-ap-southeast-1`, single branch **`production`** (`br-tiny-forest-azau4yf3`,
primary/default). Logical size ≈ 37.9 MB. Inspected **read-only** through
`information_schema`/`pg_catalog` only. Nothing was created, altered or dropped.

**74 tables** (`public`). No views. Source-of-truth DDL lives in the marketplace
repo: `db/schema.sql` (1 779 lines) plus `db/run-sqleditor.sql` and
`db/verify-reconciler.sh` (reconciler, not migrations).

### 3.1 Account / identity / session

| Table | Key columns (verified from the live DB) |
|---|---|
| `users` | `id uuid PK default uuid_generate_v4()`, `email text NOT NULL UNIQUE`, `name text NOT NULL DEFAULT ''`, `avatar`, `cover_url`, `phone`, `role text DEFAULT 'customer'`, `status text DEFAULT 'active'`, `department`, `password_hash text NULL`, `must_change_password bool DEFAULT false`, `created_at`, `updated_at`. Indexes: `users_pkey`, `users_email_key` (unique), `idx_users_email`. |
| `auth_identities` | `id uuid PK`, `user_id uuid NOT NULL FK→users ON DELETE CASCADE`, `provider varchar(50) NOT NULL`, `provider_id varchar(255) NOT NULL`, `email text NOT NULL`, `created_at`. **`UNIQUE (provider, provider_id)`** (`auth_identities_provider_provider_id_key`), `idx_auth_identities_provider`, `idx_auth_identities_email`. |
| `customer_profiles` | `user_id uuid NOT NULL UNIQUE FK→users CASCADE`, `date_of_birth`, `gender`, `preferences jsonb`, timestamps. |
| `revoked_tokens` | `id`, `token_id text NOT NULL UNIQUE`, `user_id uuid NOT NULL FK→users CASCADE`, `expires_at NOT NULL`, `revoked_at NOT NULL`; indexes on `token_id`, `user_id`, `expires_at`. |
| `preauth_nonces` | `nonce_hash PK`, `expires_at` (+ index). Used by the browser pre-auth proof, not by native mobile. |

There is **no sessions table** — sessions are stateless JWTs plus this revocation
list. There is **no guest/anonymous account table** and **no device table**.

### 3.2 Commerce tables relevant to Parts 2–8

| Table | Notable constraints |
|---|---|
| `carts` | `id uuid PK`, **`user_id uuid NOT NULL` with `carts_user_id_key` UNIQUE**, timestamps. One cart per user, and a cart **cannot exist without a user**. |
| `cart_items` | `cart_id NOT NULL FK→carts CASCADE`, `product_id FK→products CASCADE`, `variant_id FK→product_variants SET NULL` (nullable), `quantity NOT NULL`, unique index `idx_cart_items_unique (cart_id, product_id, COALESCE(variant_id, '000…0'))`. |
| `orders` | `user_id uuid NOT NULL`, `order_number` (unique where not null), status CHECK list, money columns, `shipping_address_id`, `shipping_address jsonb`, `checkout_group_id` (nullable), timestamps. **`user_id` is NOT NULL — no guest order is representable today.** |
| `products`, `product_variants`, `product_images`, `product_option_groups/values`, `inventory`, `shops`, `sellers`, `categories` | Catalogue side; `orders`/`cart_items` reference `products` (and `product_variants`). |
| `checkout_groups`, `payments`, `payment_attempts`, `payment_events`, `refunds`, `shipments`, `shipping_quotes`, `notifications`, `customer_wishlist`, `addresses`, `conversations`, `chat_messages`, `audit_logs`, … | Full commerce/ops surface, all keyed by `user_id` for customer data. |

### 3.3 Existing data (counts only — no PII read)

`users` = 3 · `auth_identities` = 1 (provider `google`) · `carts` = 1.
Roles present: `owner`, `seller`, `staff` — **no `customer` account exists yet**.
Two users have `password_hash` in `$scrypt$` format (both non-customer); one user
is Google-linked (the `owner`, created 2026-11-11). No `customer_profiles` rows
for 2 of 3 users. `schema_migrations` exists but is **empty** (`NULL` aggregate),
so migrations have been applied by running `db/schema.sql` / the reconciler, not
by a numbered migration runner.

---

## 4. Existing authentication capabilities (reusable as-is)

| Capability | Evidence | Reuse verdict |
|---|---|---|
| Mobile email/password register | `POST /api/auth/mobile/register` | **Reuse** (payload `{name,email,password}` → cookie + user) |
| Mobile email/password login | `POST /api/auth/mobile/login` | **Reuse** (verified live on Render) |
| Server-side scrypt hashing | `lib/password.ts` | **Reuse** |
| Identity resolution + email linking | `resolveIdentity()` in `mobile-auth.ts`, transactional, `FOR UPDATE`, `23505` guard | **Reuse**; conflicts surface as `409 ACCOUNT_CONFLICT` |
| Google ID-token verification | `POST /api/auth/mobile/google` (tokeninfo, `aud`=`MOBILE_GOOGLE_CLIENT_ID` ?? `GOOGLE_CLIENT_ID`, `iss`, `exp`, `azp`, `email_verified`) | **Reuse**; server side is production-ready |
| LINE ID-token verification | `POST /api/auth/mobile/line` (LINE verify endpoint, `iss`, `aud`=LINE_CHANNEL_ID, `exp`, `iat` skew, `nonce`, `email` scope required) | **Reuse**; needs owner credentials (§9) |
| Provider availability probe | `GET /api/auth/mobile/config` | **Reuse** for honest UI gating. Live: `google:true, line:false` |
| JWT session 7 days + `jti` revocation | `createSessionToken`, `revoked_tokens` | **Reuse** |
| `Authorization: Bearer` acceptance | `bearerOrCookie` in `auth.ts` / `middleware/auth.ts` | **Reuse** (but see R1) |
| Logout / revoke | `POST /api/auth/logout` with bearer | **Reuse** |
| Current user | `GET /api/auth/me`; richer profile at `GET /api/customer/profile` | **Reuse** `profile` for display |
| Guest-readable catalogue | `/api/products`, `/api/products/catalog`, `/api/products/:id` (no auth) | **Reuse** for guest shopping |
| `optionalAuth` seam | `middleware/auth.ts` | **Reuse** for any future merge endpoint |
| Rate limiting | `middleware/rate-limit.ts` + per-route limiters | **Reuse** |
| Turnstile protection | web/desktop credential routes fail closed | **Keep web-only**; not needed by native |
| Password policy | `PASSWORD_RE`, ≥ 8 chars | **Align the app to it** |
| Audit log | `audit_logs` + `lib/audit-log.ts` | **Reuse** where the mobile flow is added |

---

## 5. Missing authentication capabilities

### Android (all missing)

1. HTTP client + serialization + `INTERNET` permission + base-URL config.
2. Repository/model layer for auth, catalogue, cart, orders, profile.
3. ViewModels (or equivalent state holders) with loading/error/retry states.
4. Persistent session store (tokens/user) with an encrypted store + Keystore.
5. Startup session resolution (silent restore → `GET /api/auth/me` →
   Guest / Authenticated), including offline behaviour.
6. Guest-first navigation: Home/catalogue/search for guests; a gated
   destination set and a login gate that returns to the attempted action.
7. Registration screen (does not exist).
8. Google sign-in: Play Services `googleid` + Credential Manager, ID token → `/api/auth/mobile/google`.
9. LINE Login: LINE SDK for Android, or an in-app browser OAuth with PKCE to
   obtain an ID token → `/api/auth/mobile/line`.
10. Forgot-password UI (no backend endpoint exists either — see below).
11. Auth-aware Sign out (revoke + clear), account display from the real profile,
    localized error mapping for the backend error codes.
12. Logout-everywhere / device management ("Signed-in devices" is a dead row today).

### Backend (missing or insufficient for a mobile client)

1. **No token in any mobile response body.** The only session carrier is the
   `velnox_session` cookie, while there is no endpoint that returns it to a native
   client. A native app therefore cannot persist a session without cookie handling
   or the `Cookie` request header being synthesized manually.
2. **No refresh-token endpoint** and no sliding renewal. `expiresIn: "7d"` is the
   only lifetime; silent re-auth is impossible without re-entering credentials.
3. **No logout-all / per-device session list** (only per-`jti` revocation).
4. **No server-side guest cart or guest→user cart merge.**
   `/api/customer/cart*` and `/api/customer/checkout` are `requireAuth`, and
   `carts.user_id` is NOT NULL UNIQUE; the only guest cart concept is
   `packages/shared/src/lib/guest-cart.ts` (web `localStorage` only).
5. **No guest order path** — `orders.user_id` NOT NULL.
6. **No forgot-password / password reset** endpoint (only authenticated
   `change-password`).
7. **No mobile test coverage** — `backend/tests/` has no `mobile-auth` test.
8. **No `docs/AUTHENTICATION.md` coverage of the mobile flow** — the doc still
   describes the cookie name `session_token`, `SameSite=strict`, and a Google
   redirect flow, all of which contradict `routes/auth.ts`
   (`velnox_session`, `SameSite=None`, plus `/api/auth/mobile/*`).
9. `GOOGLE_REDIRECT_URI` is documented in the repository README's Render env list
   but is not present in `.env.example` (the browser callback needs it).

---

## 6. Google and LINE integration status

| | Google | LINE |
|---|---|---|
| Backend require | `MOBILE_GOOGLE_CLIENT_ID` ?? `GOOGLE_CLIENT_ID` | `LINE_CHANNEL_ID` |
| Verification method | `oauth2.googleapis.com/tokeninfo` (aud/iss/exp/azp/email_verified) | `api.line.me/oauth2/v2.1/verify` (iss/aud/exp/iat skew/nonce + `email` claim required) |
| Backend configured? | **Yes** (live `/api/auth/mobile/config` → `google:true`) | **No** (live → `line:false`; `LINE_CHANNEL_ID` unset on Render) |
| App SDK present? | **No** | **No** |
| App button present? | Yes — "Google" → `ComingSoon` | **No button** (app shows Google + Facebook) |
| End-to-end works? | Server-side ready; **client side is zero** | **Blocked on owner credentials** |

Extra facts:
- `GOOGLE_CLIENT_SECRET` is set for the **browser** flow only; the mobile path
  needs no secret. The ID token from Play Services is minted by Google against an
  Android OAuth client whose **`aud` equals the Web client ID** — so
  `MOBILE_GOOGLE_CLIENT_ID` must be the **Web** client id, and the Android client
  (package `com.velnox.app` + the signing SHA-1) must exist in the same project.
- LINE requires the **`email` scope** enabled on the LINE Login channel
  (`LINE_EMAIL_SCOPE_REQUIRED` otherwise) and a channel whose callback URL matches
  whatever mechanism Part 6 uses. There is no web LINE login in the backend at all.
- The "Facebook" button in `LoginScreen` has **no backend counterpart**; it should
  be removed or explicitly labelled unavailable (design decision for Part 3).

---

## 7. Security risks and architectural conflicts

| # | Risk / conflict | Evidence | Sev | Suggested handling (Part 2+) |
|---|---|---|---|---|
| R1 | **No native token transport.** `sendSession()` only sets the cookie; `bearerOrCookie` can read a bearer but nothing ever emits one. The cookie is `SameSite=None; Secure`, valid only over HTTPS and stored by the HTTP client. | `routes/auth.ts` `sendSession`/`setSessionCookie`; `routes/mobile-auth.ts` `sendSession` | **High** | Add `token` + `expiresAt` to the mobile response bodies (mobile routes only), or add an exchange endpoint. Must not change web responses. |
| R2 | **Same-origin cookie semantics are implicit.** If cookie handling differs between OkHttp and the WebView/Play Services flow, sessions silently break; no `Origin` header means the origin guard cannot help. | `origin-guard.ts` (no Origin accepted) | Med | Decide and document one carrier (Bearer preferred for native) and test on device. |
| R3 | **Token storage location unknown today** — no encrypted store, `allowBackup="true"` with no backup rules, so a token written into DataStore could be backed up to Drive. | `AndroidManifest.xml`, `PreferencesRepository.kt` | **High** if naively implemented | `androidx.security:security-crypto` (or Keystore-encrypted DataStore) + `dataExtractionRules` excluding the auth file. |
| R4 | **Client password policy < server policy** (6 vs 8 + strength). A 6-char password registers a generically-failing form. | `LoginScreen.kt` line ~705 vs `mobile-auth.ts` `PASSWORD_RE` | Med | Mirror the backend rule in the UI and map `VALIDATION_ERROR` back to fields. |
| R5 | **Guest cart vs server cart.** `carts.user_id` is NOT NULL UNIQUE and every cart route is `requireAuth`; a guest cart must live on device and be merged in on sign-in. | live schema + `cart.ts` `ensureCart` | **High** (design) | Part 4: local guest cart (validated, quantity-clamped) + explicit merge call after auth. Merge must re-price server-side; never trust client prices. |
| R6 | **Guest orders are not representable.** `orders.user_id` NOT NULL. | live schema | Med | Part 8 decision: require sign-in at checkout, or add a guest column/flow (a migration — needs owner approval). |
| R7 | **Account linking is asymmetric.** Google links by verified email; LINE never auto-links (`409 ACCOUNT_CONFLICT`). An existing password account signing in with LINE gets a dead end with no recovery UI. | `resolveIdentity()` in `mobile-auth.ts` | Med | Part 6: explicit "you already have an account, sign in then link" flow; the server needs a link-after-login endpoint. |
| R8 | **`/api/auth/me` cache staleness.** 30 s profile cache skips the revocation check. | `routes/auth.ts` ~880–910 | Low | Accept for profile display; do not use it to decide sensitive actions. |
| R9 | **Fire-and-forget revocation writes on logout and login** (best-effort `try/catch`, `ON CONFLICT DO NOTHING`). | `routes/auth.ts` logout block | Low | Accept; client should treat logout as local-first + best-effort revoke. |
| R10 | **Registration is data-minimal only.** Creates `users` + `customer_profiles`; no email verification, no phone verification, no terms acceptance record. | `mobile-auth.ts` register handler | Med | Part 3/7: decide whether email verification is in scope (needs SMTP provider = owner action). |
| R11 | **No forgot-password flow** anywhere (client or server). | absence in code | Med | Part 7 scope decision. |
| R12 | **Docs drift.** `docs/AUTHENTICATION.md` states `session_token` cookie + `SameSite=strict` and no mobile flow. | `docs/AUTHENTICATION.md` | Low | Update in the same PR that touches auth. |
| R13 | **Rate limits are in-memory** per instance; with one Render instance this is fine, but it is not shared or durable. | `middleware/rate-limit.ts` | Low | Note when scaling; do not rely on it as the only abuse control. |
| R14 | **Static demo data masquerades as product state.** `HomeCatalog` has no prices/stock while `CartState` is pre-seeded with three lines; wiring real APIs must remove the seeded cart to avoid phantom items. | `HomeCatalog.kt`, `CartState.kt` | Med | Part 3/4: seed from API, start carts empty. |
| R15 | **Localization of server content.** API returns single-language Thai fields; app has th/en/my. | live catalogue probe | Low | Part 5: decide (translate fields, or ship `Accept-Language`). |
| R16 | **`allowBackup`/exported activity.** `MainActivity` is `exported="true"` (required for LAUNCHER) with no deep-link filters yet; adding OAuth redirect `intent-filter`s later widens the attack surface. | `AndroidManifest.xml` | Low | Part 5/6: use Play Services (no redirect) and/or App Links verification. |
| R17 | **Backend and app share ad-hoc demo gates.** `signedIn` + `settings_sign_out_demo` + `login_demo_hint` strings are user-visible and must be replaced, not kept alongside real auth. | `strings.xml` 292 keys | Med | Part 2/3: replace demo strings in all three locales. |
| R18 | **No automated tests on the mobile repo** (CI builds only) and no mobile backend tests. | workflow + `backend/tests` | Med | Add at least unit tests for parsing/session repo in Part 2/4. |

**Architectural conflicts to resolve up front (Part 2 decisions):**

1. **Session carrier:** cookie vs Bearer for native. Bearer is the clean answer,
   but it requires a *backend* change and therefore owner approval to ship.
2. **Guest identity model:** purely device-local (no Neon change, recommended for
   Parts 2–5) vs server-side anonymous accounts (a schema change).
3. **Navigation rewrite:** `VelnoxNavHost` must become auth-aware and
   guest-first. Prior attempt `7aac8a6` is a useful sketch; its `AuthState` enum +
   `pendingTabKey` gate pattern can be reused, but it must be backed by a real
   session repository with async resolution (and therefore a brief
   "resolving session" state, which `7aac8a6` lacked).
4. **Facebook** must be removed or explicitly marked unavailable; the design
   system says do not invent brand colours, so use text-only neutral treatment.

---

## 8. Recommended implementation sequence for Parts 2–8

**Architecture (recommended, no schema change required):**

```
Compose UI (existing screens, made state-driven)
   └─ ViewModel / state holder per feature
        └─ Repository (AuthRepository, CatalogRepository, CartRepository, OrderRepository)
             └─ ApiClient (OkHttp + kotlinx.serialization, Bearer + cookie fallback)
                  └─ https://velnox-api.onrender.com   (existing Express API)
                       └─ Neon PostgreSQL  (existing schema; reuse users/auth_identities/revoked_tokens)
```

Guest state is **device-local**; the server only ever sees authenticated users,
so no Neon migration is needed until Part 8 decides on guest orders.

| Part | Goal | Reuse | Build new | Exit criteria |
|---|---|---|---|---|
| **2** | Networking foundation + session layer | Backend `/mobile/config`, `/api/auth/me`, `/api/customer/profile`; `bearerOrCookie` | `INTERNET` perm, OkHttp + serialization, base-URL config, `ApiResult` error mapping for `{success,error{code,message}}`, `SessionStore` (Keystore-backed), `AuthRepository`, startup resolution Guest/Authenticated/Online-Unknown, `AuthState` sealed type | Cold start resolves state from storage; a stored token survives restart; invalid/revoked token → Guest without crashing |
| **3** | Guest-first shell | Existing tabs/overlays, `PlaceholderDestination` | Auth-aware `VelnoxNavHost` (guest → Home/Messages-read-only?/Cart-local/Profile → Sign in), login gate that remembers the attempted destination, remove demo gate + demo strings (th/en/my), Profile/Settings read real profile, Sign out revokes | Guest can browse Home, category, search, product; any protected action opens Login and returns to that action after sign-in |
| **4** | Auth end-to-end (email + password) | `mobile/register`, `mobile/login`, `logout`, scrypt, revocation | Registration screen, real login wiring, password policy alignment, error mapping, guest-cart → server-cart merge after sign-in, remove `CartState` seed | Register → login → cart persists server-side; logout revokes and returns to Guest |
| **5** | Catalogue from API | `/api/products/catalog` (q, category, paging), `/api/products/:id` | `CatalogRepository`, paging on Home/category/search, image loading, loading/empty/error states, localization decision for content | Home/search/category show real products with prices and stock; no `HomeCatalog` in the render path |
| **6** | Google + LINE sign-in | `mobile/google`, `mobile/line`, `mobile/config` | Play Services `googleid` (ID token), LINE SDK or browser-PKCE → ID token, honest "not configured" state from `/mobile/config`, account-conflict copy | Google works end-to-end on a device; LINE shows its real availability; conflict path is explained |
| **7** | Hardening & account security | `revoked_tokens`, `audit_logs`, rate limiters | Token refresh or silent re-auth, logout-everywhere, forgot-password decision, backup rules, security review of token storage, tests | No token in plain storage; 401 recovery is automatic; documented threat model |
| **8** | Orders / commerce completion | `/api/customer/cart`, `checkout`, `orders`, `addresses` | Real cart/checkout/orders screens, guest-orders decision (may require owner-approved migration), payment state display | An end-to-end order can be placed and listed |

Ordering rules: Part 2 must not ship any UI change; Part 3 must not depend on a
token transport that Part 2 has not verified on a device; the Bearer transport
change (R1) is a backend edit and must be approved before Part 3's offline session
restore can be trusted.

---

## 9. Configuration or credentials that require owner action

Nothing in this list can be produced by an agent. Each item states why it is
needed and which part needs it. **No values were read, copied or written.**

### A. Blocks Part 6 (social sign-in)

1. **Google Cloud Console**
   - An **Android OAuth client** for package `com.velnox.app` with the SHA-1 of
     the app's signing certificate (debug AND the future release key). Without it,
     Play Services returns no ID token.
   - The **Web client ID** that must equal the `aud` of the token. Set it on Render
     as `MOBILE_GOOGLE_CLIENT_ID` (recommended) or keep `GOOGLE_CLIENT_ID`.
   - Owner confirms which project owns the existing `GOOGLE_CLIENT_ID`.
2. **LINE Developers Console**
   - A LINE Login channel: `LINE_CHANNEL_ID` (public) on Render.
   - **`email` scope enabled** and, if the browser/PKCE route is chosen, the correct
     callback URL and `LINE_CHANNEL_SECRET` handling (secret stays server-side and
     is **not** needed if only ID tokens are verified — confirm with the owner).
   - Part 6 also needs the channel's `botPrompt`/scope choices decided.

### B. Blocks Part 2–3 (session transport)

3. **Approval for a backend change**: include the session token in the mobile
   response body (e.g. `data.token`) and/or add an exchange endpoint. This is the
   only way to implement robust native session persistence without relying on
   cookie behaviour. Changes live in `EnJirad/velnox-marketplace`
   `backend/routes/mobile-auth.ts` + `backend/routes/auth.ts`.

### C. Deployment facts needed by the app

4. **Production base URL confirmation**: `https://velnox-api.onrender.com`
   (verified live). Decide whether it is hard-coded per build type or injected via
   `BuildConfig`/`local.properties` (the latter needs a CI secret only if the URL
   is private — it is not).
5. **CI**: no new secret is strictly required for a public API URL. If a
   `API_BASE_URL` GitHub Actions secret or `local.properties` entry is chosen, the
   owner must add it to the repository settings.

### D. Neon

6. **None required for Parts 2–7** (the existing `users`, `auth_identities`,
   `revoked_tokens`, `customer_profiles` tables are sufficient).
7. **Part 8 only**: if guest orders are required, owner approval for a migration
   (`orders.user_id` nullable or a `guest_*` column, plus `carts` guest strategy).
   No migration was prepared, proposed as applied, or run in this task.
8. Optional hygiene: the Neon `schema_migrations` table is **empty**. The owner
   should confirm whether a real migration runner is wanted before any future
   schema change.

### E. Product decisions only the owner can make

9. Whether registration requires email verification (needs an SMTP/email provider
   — no email provider is configured in `.env.example`).
10. Whether "forgot password" is in scope for this release.
11. Whether Facebook sign-in is dropped (currently a dead button).
12. Whether server content is localized for `en`/`my` or stays Thai.

---

## 10. Verification summary

| Check | Method | Result |
|---|---|---|
| Remote sync | `git fetch` + `rev-parse` | `0db3cd3` on both sides |
| Working tree preserved | `git status --porcelain` before/after | clean before; only this report written |
| Android claims | Source read of every Kotlin file listed in §1 (+ string counts, manifest, gradle, workflow) | Verified by inspection; **no device run** |
| Mobile auth endpoints | Source read `routes/mobile-auth.ts`, `routes/auth.ts`, `middleware/auth.ts` | Verified |
| Live API | `GET /api/auth/mobile/config` → 200 `{google:true,line:false}`; `POST /api/auth/mobile/login` bad creds → 401 `INVALID_CREDENTIALS`; `GET /api/products/catalog?limit=1` → 200 real product | Verified against production |
| Neon schema | `information_schema` / `pg_indexes` / `pg_constraint` read-only queries on `production` | Verified; **no writes** |
| Backend deployment | Render `list_services` → `velnox-api`, `https://velnox-api.onrender.com` | Verified |

**Not verified (honest gaps):** runtime behaviour on a device, the browser OAuth
redirect chain, whether OkHttp actually persists the `SameSite=None` cookie in a
release build, LINE behaviour (unconfigured), and any Neon row contents beyond
aggregate counts and column metadata.

---

## 11. State after this task

- Application code: **unchanged**.
- Backend code: **unchanged** (read from GitHub only; nothing pushed).
- Database: **unchanged** (read-only queries).
- Workflow / config: **unchanged**.
- Only file written: `.ai/reports/LATEST.md` (this report).

**Part 2 has not started. Awaiting approval.**
