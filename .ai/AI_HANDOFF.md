# AI_HANDOFF.md — Velnox Mobile Current Snapshot

> **Current snapshot (not a log).** What works, what's broken, decisions, next steps.
> Statuses: **VERIFIED / UNVERIFIED / FAILED / BLOCKED / SKIPPED / NOT TESTED**.
> Supersedes all previous handoffs.
> Repository: `EnJirad/velnox-mobile`

---

## Current State (2026-11-17, run 44110918805)

| Area | Status | Notes |
|------|--------|-------|
| Home screen | **VERIFIED** | Header, search, trust row, category chips, hero, category rail, responsive grid, new-arrivals rail, bottom nav |
| Login screen | **VERIFIED** | Restyled to match Home; validation, show/hide, remember-me intact; submits to `onSignedIn` |
| Navigation | **VERIFIED** | Typed `AppDestination`; cold start → Login; demo flow → Home; Back from Home → Login |
| Icon set | **VERIFIED** | 22 Lucide-derived `ImageVector` glyphs, no icon font, no emoji |
| Shared components | **VERIFIED** | Brand mark, section header, badge, trust row, spacing scale |
| Strings | **VERIFIED** | 85 keys, Thai (`values/`) + English (`values-en/`) in sync, none hardcoded in composables |
| GitHub Actions build | **VERIFIED** | `BUILD SUCCESSFUL in 2m 29s` |
| APK artifact | **VERIFIED** | `velnox-debug-apk`, 9,224,459 bytes (APK 9,669,400 bytes) |
| APK identity | **VERIFIED** | `com.velnox.app`, label `Velnox`, launcher `MainActivity`, minSdk 26 / target 35 |
| Mobbin research | **BLOCKED** | Paid-plan gate on every Mobbin tool; web references used instead |
| Backend / auth | **SKIPPED** | No network calls, no API, no database, by instruction |
| On-device render | **NOT TESTED** | No install route in this session |

---

## Decisions

1. **No new Gradle dependency.** Navigation is a typed `AppDestination` state in
   `VelnoxApp`, not Navigation Compose, so the pinned stack and workflow stay untouched.
2. **Icons are code.** `VelnoxIcons` re-authors Lucide stroke geometry as `ImageVector`.
   Product art is drawn with `Canvas`; no photography, no icon font, no emoji.
3. **Preview data only.** `HomeCatalog` holds names, store names and qualitative tags.
   No prices, stock, order or payment data anywhere.
4. **Responsive by measurement.** `BoxWithConstraints` drives the grid (2/3/4 columns)
   and the feed is capped at 560dp and centred on tablets.
5. **Login owns no routing.** It calls `onSignedIn`; `VelnoxApp` decides where to go,
   which is where the Phase 2 auth gate will live.
6. **`rememberSaveable` uses a String route key**, because a sealed object cannot be
   written to the saved-state Bundle.

---

## Known issues

- None blocking. The first Phase 1 build failed from a duplicated function body produced
  by a scripted edit; fixed in `f31a1d3` and the next run was green.
- Unused icons (`ChevronLeft`, `ShoppingBag`, `ShoppingCart`, `Star`, `Tag`, `Wallet`) are
  library entries for upcoming screens, not dead UI.

---

## Screens

| Route key | Composable | Notes |
|-----------|-----------|-------|
| `login` | `feature/auth/LoginScreen.kt` | Start destination |
| `home` | `feature/home/HomeScreen.kt` | Reached through the demo flow |

Placeholder taps (search, filter, notifications, product, non-home tabs) open
`VelnoxInfoDialog` so no interaction silently does nothing.

---

## Next Agent Startup Order

1. `.ai/AI_HANDOFF.md` ← **YOU ARE HERE**
2. `.ai/AI_RULES.md`
3. `.ai/VELNOX_DESIGN_THEME.md` (UI source of truth)
4. `.ai/reports/LATEST.md`
5. `git fetch origin && git rev-parse HEAD && git rev-parse origin/main`
6. `.ai/context/project-map.md` — still describes `velshop-mobile`; update on first touch

---

## Secrets / Credentials

No secrets in `.ai/`. No tokens, keystores or passwords in this repository.

---

## Signature

> This handoff supersedes all previous.
> **Last updated:** 2026-11-17 by the Phase 1 session (Home + Login UI).
