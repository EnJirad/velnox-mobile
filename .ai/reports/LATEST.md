# LATEST.md — Velnox Mobile Latest Change Report

> **Current snapshot (overwritten each task).** Previous report (Part 5.1.1)
> archived in `.ai/history/20261118_163429_LATEST_before_part5_2_part6.md`.
> Repository: `EnJirad/velnox-mobile`

---

## Task: Part 5.2 + Part 6 — Google Sign-In completion, real catalogue from Neon

**Date:** 2026-11-18
**Start commit:** `ee158af` · **End commit:** `ee115bc`
**Verified CI run:** [44347496595](https://github.com/EnJirad/velnox-mobile/actions/runs/44347496595) — **success**
**Status:** **Catalogue: DONE.** **Google Sign-In: BLOCKED on owner configuration**
(one repository secret + two Google Cloud items that cannot be inspected or
created from here).

Two deliverables, separate commits:

| Deliverable | Commits | CI |
|---|---|---|
| 5.2 signing-key verification | `fbbc625` | [44344651433](https://github.com/EnJirad/velnox-mobile/actions/runs/44344651433) — success |
| 6 real catalogue | `6959a42`, `096827c`, `5046a43`, `ee115bc` | [44347496595](https://github.com/EnJirad/velnox-mobile/actions/runs/44347496595) — success |

`096827c`, `5046a43` and `ee115bc` are one-line follow-ups for failures the first
catalogue push surfaced (see §7); they are part of deliverable 6.

---

# Deliverable 1 — Part 5.2, Google Sign-In

## 1. What is already implemented (verified in source, not in reports)

`core/auth/CredentialManagerGoogleSignIn` uses Credential Manager
(`GetGoogleIdOption`) and returns the raw ID token. `GoogleClientIdSource`
resolves the **Web** client id: `BuildConfig.GOOGLE_WEB_CLIENT_ID` first, then
`GET /api/auth/mobile/config`. `AuthRemoteDataSource` posts only the token to
`/api/auth/mobile/google` and maps `409 LINK_REQUIRED`,
`INVALID_GOOGLE_TOKEN`, `EMAIL_NOT_VERIFIED`, `GOOGLE_NOT_CONFIGURED`. A session
exists only after a `200` — the same rule as email sign-in.

No code change was needed in the app. What was missing was the ability to *prove*
the configuration, so this task changed the CI workflow instead.

## 2. Signing key — what the workflow does now

`.github/workflows/gradle-build.yml`:

- decodes `ANDROID_DEBUG_KEYSTORE_BASE64` into `~/.android/debug.keystore` with
  `umask 077` + `chmod 600` (a keystore is a credential);
- prints the pinned keystore's SHA-1 / SHA-256 **before** the build, so the
  fingerprint registered with Google can be compared with the one CI used;
- when the secret is absent, writes an **ACTION REQUIRED** block to the job
  summary with the exact `keytool` / `base64` commands and the Google Cloud step;
- never prints, generates into the repo, or commits a key.

## 3. Status of every configuration item

| Item | Status | Evidence |
|---|---|---|
| Package `com.velnox.app` | **VERIFIED** | `app/build.gradle.kts` |
| Credential Manager flow | **VERIFIED (source)** | `CredentialManagerGoogleSignIn` |
| Web client id from build | **NOT SET** | `GOOGLE_WEB_CLIENT_ID` empty in CI |
| Web client id from API | **BLOCKED** | live `/api/auth/mobile/config` = `{"google":true,"line":false}`; PR #16 adds `googleClientId` |
| `ANDROID_DEBUG_KEYSTORE_BASE64` | **MISSING** | step 6 of run 44344651433 wrote the ACTION REQUIRED block; SHA-1 differs each run |
| Debug SHA-1 stability | **FAILED (unstable)** | `d8a33542…` → `ce53e36d…` → `ee55aa8b…` across three runs |
| Android OAuth client | **UNKNOWN — owner** | Google Cloud cannot be inspected from here |
| Consent screen / test users | **UNKNOWN — owner** | same |
| Backend `MOBILE_GOOGLE_CLIENT_ID` | **UNKNOWN — owner** | Render env cannot be inspected from here |
| Real Google end-to-end | **BLOCKED** | needs the three owner items |

## 4. Owner actions, exactly

1. **Pin the signing key.** Create one debug keystore, base64 it, store it as the
   `ANDROID_DEBUG_KEYSTORE_BASE64` repository secret (exact commands are in the
   CI job summary). Until this is done the SHA-1 changes on every build and no
   Android OAuth client can ever match.
2. **Android OAuth client.** In the same Google Cloud project as the Web client,
   create an *Android* client for package `com.velnox.app` with the SHA-1 the next
   CI run prints, plus the SHA-1 of any keystore used for local builds.
3. **Publish the Web client id.** Either deploy PR #16 (the config endpoint then
   serves it, no APK rebuild) or build with `-PvelnoxGoogleClientId=<web id>`.
4. **Consent screen.** Publish the app, or add the accounts that will test it.
5. Confirm Render's `MOBILE_GOOGLE_CLIENT_ID` equals that Web client id.

## 5. Backend PR #16 — review before merge

**State:** open, not merged, `mergeable_state: unstable` (its CI is not green).
Base `4b6b09d`. **Not merged or deployed here.**

**Security review of the parts that matter:**

- Token verification is Google's `tokeninfo` endpoint (signature, `iss`, `exp`
  checked by Google) with a 5 s timeout, then local claim checks in
  `lib/google-identity.ts`: `aud === clientId`, issuer in
  `{https://accounts.google.com, accounts.google.com}`, `exp` in the future,
  `azp` equal to the client when present, `email_verified === "true"`, non-empty
  `sub`, email shape.
- `sub` is the identity key; the email is never used to pick an account.
- An existing account with the same verified email now gets `409 LINK_REQUIRED`
  instead of being silently attached. Linking needs an authenticated session
  **and** an equal email (`EMAIL_MISMATCH` otherwise) **and** an unclaimed
  identity (`IDENTITY_CONFLICT` otherwise).
- `UNIQUE(provider, provider_id)` remains the concurrency guard (`23505` →
  conflict).
- No schema change, no migration, no secret exposed; only the public client id
  is published by `/api/auth/mobile/config`.

**Recommendation:** safe to merge once its own CI is green, and consistent with
the app as written. Two caveats worth recording: verification depends on a
network call to Google on every sign-in (5 s timeout, `GOOGLE_UNAVAILABLE` on
failure), and it cannot be verified end-to-end until the three owner items in §4
exist.

---

# Deliverable 2 — Part 6, real catalogue from Neon

## 6. What the app reads (all already deployed)

| Purpose | Endpoint | Server rule |
|---|---|---|
| Listing / search / filter | `GET /api/products/catalog?q&category&limit&offset` | `p.status='published' AND sh.status='active'` |
| Product page | `GET /api/products/:id` | same predicate, else `404 NOT_FOUND` |
| Category tree | `GET /api/categories/tree` | `is_active` only, with published counts |

Discovered in `backend/routes/products.ts` (8 read routes exist; these three are
the public ones). Tables the backend touches for them, read from its own SQL —
**no direct connection to Neon was made or is possible from the app**:
`products`, `product_images`, `product_variants`, `categories`, `shops`,
`sellers`, `inventory`, `order_items`.

Fields displayed, all taken from the response: `id`, `name`, `price`,
`currency`, `primaryImage.url` (→ `displayUrl`), `images[]` (ordered,
`isPrimary` first), `description`, `shopName`, `currentStock` /
`totalAvailableStock`, `soldCount`, `unit`.

## 7. Live verification against the deployed API (2026-11-18)

| Check | Result |
|---|---|
| Published products in the deployed database | **2** |
| Both returned with `status=published` | yes |
| Detail for each id | `200`, price/stock match the list (`0 THB`/1117, `182 THB`/867) |
| Product images | 9 and 7 per product; R2 URL returns `HTTP 200`, 645 KB |
| Categories | 96 active, 15 top-level; **2** hold published products (`chargers-cables`, `mens-clothing`) |
| Search `q=charger` | 1 product |
| Search `q=zzzznotreal` | 0 products (empty state) |
| Filter `category=chargers-cables` | 1 product |
| Filter `category=books` (no products) | 0 products |
| Unknown product id | `404 NOT_FOUND` |

So the real catalogue is small but genuine. Nothing was inserted into production,
and the app shows an empty state rather than filler when a query has no results.

## 8. What changed in the app

**Added — `core/catalog/`**

- `CatalogModels.kt` — `CatalogProduct`, `ProductDetail`, `CatalogImage`,
  `CatalogCategory`, `CatalogPage`, `CatalogQuery`, `CatalogResult`, `CatalogError`.
- `CatalogApi.kt` — the three reads.
- `CatalogRemoteDataSource.kt` — OkHttp + `org.json` on the existing stack and
  base URL. Tolerant where the API is inconsistent (price is a JSON number on
  `/catalog` and `/detail`, a string on the legacy list route), strict where it
  matters (no `success:true` → error; malformed body → `Server`, never empty
  data).
- `CatalogRepository.kt` — in-memory page/product/category cache, `force` to
  bypass, failures never cached, a product that starts 404ing is evicted.
- `PriceFormat.kt` — the amount and currency from the database, `฿` for THB.

**Added — `feature/catalog/ProductDetailScreen.kt`** — gallery (first image main,
thumbnails in seller order), name, price, stock badge, shop, sold count,
description; loading, retry, and "product unavailable" states.

**Changed**

- `feature/home/HomeScreen.kt` — real feed with explicit `Loading`/`Content`/
  `Empty`/`Error` states, 300 ms debounced **server-side** search, category chips
  from the real tree (only categories with published products), load-more paging,
  real images (`AsyncImage`), price, shop and out-of-stock badge.
- `feature/home/CategoryScreen.kt` — real category hub with published-product
  counts, and a listing per category with empty/error states.
- `navigation/VelnoxApp.kt` — `product:<id>` now opens the detail screen;
  `"All"` opens the category hub.
- `VelnoxApplication.kt` — one `CatalogRepository` for the process.
- `app/build.gradle.kts` — `io.coil-kt:coil-compose:2.7.0` (image loading) and
  `org.json:json` for **tests only**.
- `strings.xml` ×3 — 14 new keys (error/empty/retry/stock/price labels), 16 demo
  product/category keys removed; 361 keys per language, identical sets.

**Removed** — `feature/home/HomeCatalog.kt` (8 invented products with invented
prices, stock, ratings and vector art) and every string that fed it.

**Deliberately kept** — the vector `ProductArt`/`ProductArtTile` used by the
Cart and Orders **demo** screens. Those screens are unrelated to the catalogue and
still show demo data; rewiring them needs backend cart/order work that is out of
this task's scope. Recorded here so it is not mistaken for finished work.

## 9. Tests

New: `CatalogRemoteDataSourceTest` (9), `CatalogRepositoryTest` (7),
`PriceFormatTest` (5) — 21 tests. The parsing tests run against **captured
responses** from the deployed API (`src/test/resources/*.json`, trimmed to one
product and two images, field names and types untouched).

CI run [44347496595](https://github.com/EnJirad/velnox-mobile/actions/runs/44347496595):

```
AuthRepositoryTest            22   SessionControllerTest 19
GoogleClientIdSourceTest       7   SessionFileStoreTest  19
JwtTest                        5   SessionManagerTest     6
CatalogRemoteDataSourceTest    9   CredentialRulesTest    7
CatalogRepositoryTest          7   AccessPolicyTest       5
PriceFormatTest                5
TOTAL 111 tests, 11 suites, 0 failures, 0 errors
```

All steps green: unit tests, `assembleDebug`, APK verify, APK integrity, signing
fingerprints, upload.

## 10. Failures hit and fixed on the way (all in CI, all fixed)

| Run | Failure | Fix |
|---|---|---|
| 44345615362 | AAPT: unescaped `'` in two English strings | `096827c` |
| 44346132312 | Kotlin: missing `rememberCoroutineScope` import; smart cast on a delegated property | `5046a43` |
| 44346763442 | 7 tests failed — the mockable `android.jar` ships stub `org.json` | `ee115bc` (test-only dependency) |

## 11. Not done, stated plainly

- **No device or instrumentation testing.** The APK builds and its unit tests
  pass; nobody has installed it. The Keystore, the image loading and the new
  screens are unverified on hardware.
- **Google Sign-In is not verified end-to-end** and cannot be until §4 is done.
- **Cart and Orders still use demo data** (pre-existing, out of scope).
- **No admin/seller product endpoints** are consumed; the app is read-only.
- The catalogue has 2 published products, so the grid, paging and "load more"
  are exercised structurally rather than with a large catalogue.

## 12. Next steps

1. Owner: the four Google items in §4. Nothing else blocks Google.
2. Review and (if CI allows) merge backend PR #16 — not merged here.
3. Part 6 leftovers: cart and orders on the real API, and a device pass over the
   new screens.
