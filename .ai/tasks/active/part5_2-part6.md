# Part 5.2 + Part 6 — execution checklist

> Working file for the Google Sign-In completion (5.2) and the real product
> catalogue (6). Statuses are the same ones the handoff uses:
> **VERIFIED / UNVERIFIED / FAILED / BLOCKED / SKIPPED / NOT TESTED**.
> Last updated: 2026-11-18, end commit `ee115bc`.

## Phase 0 — pre-work

- [x] `git fetch origin`; `HEAD` == `origin/main` == `ee158af` at start
- [x] Working tree clean; nothing to preserve
- [x] Read `AI_HANDOFF.md`, `AI_RULES.md`, `VELNOX_DESIGN_THEME.md`,
      `ARCHITECTURE_AUTH.md`, `GOOGLE_SETUP.md`, `reports/LATEST.md`
- [x] Inspect the Android source rather than trusting the reports
- [x] Inspect the backend repository and the deployed API
- [x] Checklist created (this file)

## Phase 5.2 — Google Sign-In

| Item | Status | Evidence |
|---|---|---|
| Credential Manager flow | **VERIFIED (source)** | `CredentialManagerGoogleSignIn` uses `GetGoogleIdOption`, posts only the ID token |
| Web client id resolution | **VERIFIED (source + unit test)** | build property first, then `GET /api/auth/mobile/config` (`GoogleClientIdSource`) |
| Config publishes `googleClientId` | **BLOCKED (PR #16 not deployed)** | live `config` returns `{"google":true,"line":false}` only |
| Backend audience check | **VERIFIED (source)** | `verifyGoogleToken` → Google `tokeninfo`, then `validateGoogleClaims` (`aud`, `iss`, `exp`, `azp`, `email_verified`, `sub`) |
| No silent account merge | **BLOCKED (PR #16 not deployed)** | code reviewed; live backend still auto-links |
| Stable debug signing key | **BLOCKED (owner secret missing)** | SHA-1 differs every run: `d8a33542…`, `ce53e36d…`, `ee55aa8b…` |
| Keystore decode hardening | **VERIFIED (CI)** | `umask 077`, `chmod 600`, fingerprints printed, ACTION REQUIRED summary when absent |
| Android OAuth client for `com.velnox.app` | **BLOCKED (owner)** | no way to inspect Google Cloud from here |
| OAuth consent screen / test users | **UNKNOWN (owner)** | not inspectable from this environment |
| Real Google end-to-end | **BLOCKED** | needs the three items above |

## Phase 6 — real catalogue

| Item | Status | Evidence |
|---|---|---|
| Backend discovery | **VERIFIED** | `backend/routes/products.ts`: `/api/products/catalog`, `/api/products/:id`, `/api/categories/tree` |
| Publication rules | **VERIFIED (source + live)** | `p.status='published' AND sh.status='active'` on both read routes |
| Mock product data removed | **VERIFIED** | `HomeCatalog.kt` deleted; no `ProductPreview`/`CatalogSource` references remain |
| API client + repository | **VERIFIED (unit tests)** | 21 new tests, real captured bodies |
| Home on real data | **VERIFIED (CI build + unit tests)**; device **NOT TESTED** | |
| Category browsing | **VERIFIED (CI build)**; device **NOT TESTED** | |
| Product detail | **VERIFIED (CI build)**; device **NOT TESTED** | |
| Search (server-side, debounced) | **VERIFIED (live API + CI)** | `q=charger` → 1 product |
| Images | **VERIFIED (live)** | R2 URL returns HTTP 200, 645 KB; Coil renders it |
| Empty / error states | **VERIFIED (code)**; live empty case **VERIFIED (API)** | `q=zzzznotreal` → 0 items |
| Guest browsing preserved | **VERIFIED (source)** | catalogue calls need no session |
| On-device run | **NOT TESTED** | no install route from this environment |
