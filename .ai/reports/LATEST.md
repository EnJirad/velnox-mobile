# LATEST.md — Velnox Mobile Latest Change Report

> **Current snapshot (overwritten each task).** Archives in `.ai/history/`.
> Repository: `EnJirad/velnox-mobile`

---

## Task: Phase 2 — Core Screens, Navigation & Settings

**Date:** 2026-11-17
**Commits:** `82c900f` (feature), `c9b0d5e` (compile fixes), `d3d2abc` (preview polish)
**Verified CI run:** 44122098464
**Status:** **VERIFIED — BUILD SUCCESSFUL**

---

## Reference research (honest record)

| Source | Result |
|--------|--------|
| Mobbin MCP (`search_screens`) | **BLOCKED** — `Mobbin MCP requires a paid plan`. No Mobbin data used. |
| Material 3 navigation bar guidelines | **USED** — bar height, icon-over-label structure, selected-item state |
| Phase 1 web references (Baymard, Rigby, Eleken, LOW/CODE) | **CARRIED OVER** — discovery order and repeated-component rules |

The bottom bar keeps M3 geometry but replaces the default pill indicator with a
Velnox squircle + emerald icon tint, per the design system.

---

## What Changed

| Area | Files | Description |
|------|-------|-------------|
| Navigation | `navigation/VelnoxApp.kt`, `VelnoxBottomBar.kt`, `PlaceholderDestination.kt` | Five-tab shell, per-tab state preservation, Back chain, overlay destinations |
| Preferences | `core/preferences/*` | DataStore repository, `AppLanguage`, `ThemeMode`, `PreferencesState`, provider |
| Localisation | `core/i18n/VelnoxLocaleProvider.kt` | Live locale switch through a localized context |
| Locale data | `res/values/`, `res/values-en/`, `res/values-my/` | 277 string keys per language, in sync |
| Home | `feature/home/*` | Featured grid + popular and new-arrivals rails |
| Profile | `feature/profile/ProfileScreen.kt` | Header, stats, account rows, addresses, Settings icon |
| Settings | `feature/settings/SettingsScreen.kt`, `SettingsDetailScreen.kt` | Hub + 13 child screens |
| Messages | `feature/messages/MessagesScreen.kt` | List, empty state, conversation detail |
| Orders | `feature/orders/OrdersScreen.kt` | Six filters, status chips, detail with timeline |
| Cart | `feature/cart/CartState.kt`, `CartScreen.kt` | Selection, quantity, removal, summary |
| Components | `ui/components/VelnoxScaffolds.kt`, `ui/icons/VelnoxIcons.kt` | Shared scaffolds and 14 new Lucide glyphs |
| App shell | `VelnoxApplication.kt`, `MainActivity.kt`, `AndroidManifest.xml` | Process-scoped preferences, theme + locale chain |
| Gradle | `app/build.gradle.kts` | **Only** `androidx.datastore:datastore-preferences:1.1.1` added |

### Stack

Kotlin 2.0.21, Compose BOM 2024.10.01, AGP 8.7.2, Gradle 8.11.1, SDK 26/35 —
unchanged. The workflow was not modified.

---

## Verified

| Check | Result |
|-------|--------|
| CI build | **VERIFIED** — run 44122098464, `BUILD SUCCESSFUL in 2m 28s` |
| APK produced | **VERIFIED** — 10,711,441 bytes |
| APK integrity | **VERIFIED** — `unzip -t` passed |
| Artifact uploaded | **VERIFIED** — `velnox-debug-apk`, 10,109,686 bytes |
| Package identity | **VERIFIED** — `com.velnox.app`, `Velnox`, `MainActivity`, minSdk 26 / target 35 |
| Three locales packaged | **VERIFIED** — `aapt2 dump configurations` lists `th`, `en`, `my` |
| Thai / English / Burmese strings | **VERIFIED** — `aapt2 dump strings` returns UI copy for all three |
| First phase-2 build | **FAILED** — 4 compile errors (`LocalResources`, missing `ImageVector` import, `remember(named key)`, 2 missing colour imports). Fixed in `c9b0d5e`; next run green |
| Runtime language switch | **NOT TESTED** — no install route in this session; verified by compile + APK contents only |
| Runtime theme switch / persistence | **NOT TESTED** — same reason |
| Navigation + Back on device | **NOT TESTED** — same reason |

---

## Screens shipped

Home · Messages (+ conversation detail) · Orders (+ order detail) · Cart ·
Profile · Settings hub with 13 children:
Edit profile, Personal information, Addresses, Language, Appearance,
Notifications, Password, Account security, Help center, Contact support,
Terms, Privacy, About.

Standalone screens (Settings and children, order detail, conversation) hide the
bottom bar. Login never shows it.

---

## Notes for Next Agent

- **Do not** claim Mobbin research; the account is paywalled.
- `LocalResources` is not available in Compose 1.7 — localise via
  `LocalContext` + `LocalConfiguration` (`core/i18n`).
- `remember` takes vararg keys positionally; `remember(x = value)` does not compile.
- Every string must exist in `values`, `values-en` and `values-my` (277 keys each).
- Preview data must stay free of prices, totals, payments and real accounts.
- Keep `signedIn` in `VelnoxNavHost` as the only auth seam for Phase 3.
- Add icons to `VelnoxIcons` as Lucide strokes; no emoji, no icon font.

---

## Next

1. Runtime verification on a device or emulator: language switch, theme switch,
   Back chain, per-tab state
2. Search, category listing and product detail screens
3. UI tests for validation, filters and the 320dp layout
4. Phase 3: real auth resolving `signedIn` from a repository
