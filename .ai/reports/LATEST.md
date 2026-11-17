# LATEST.md — Velnox Mobile Latest Change Report

> **Current snapshot (overwritten each task).** Archives in `.ai/history/`.
> Repository: `EnJirad/velnox-mobile`

---

## Task: Phase 1 — Home + Login UI (frontend only)

**Date:** 2026-11-17
**Commit:** `72feb92` (feature) + `f31a1d3` (build fix)
**GitHub Actions Run:** 44110918805
**Status:** **VERIFIED — BUILD SUCCESSFUL**

---

## Reference research (honest record)

| Source | Result |
|--------|--------|
| Mobbin MCP (`search_screens`, `search_flows`, `list_collections`) | **BLOCKED** — every call returned `Mobbin MCP requires a paid plan`. No Mobbin data was used. |
| Web (Baymard search-bar UI, Rigby marketplace UX, Eleken search examples, LOW/CODE marketplace guide) | **USED** — see the principles applied below |

Principles taken from the web references:

1. Search must be prominent with an obvious action, and scoped filters belong next to it.
2. Repeated UI patterns across listing/checkout reduce cognitive load → shared components.
3. Content priority on a marketplace home: location/identity → search → categories → promotion → curated rails.
4. Quick, touch-friendly tiles; trust signals near the top; consistent naming.

Velnox-specific decisions (not copied from any reference): squircle geometry
throughout, dark primary + emerald accent, vector-only product art, 2/3/4-column
responsive grid, and a 560dp content cap for tablets.

---

## What Changed

| Area | Files | Description |
|------|-------|-------------|
| Icons | `ui/icons/VelnoxIcons.kt` | 22 Lucide-derived `ImageVector` glyphs (24dp viewport, 2dp stroke). No icon font, no emoji |
| Components | `ui/components/VelnoxComponents.kt` | `BrandMark`, `SectionHeader`, `BrandBadge`, `TrustRow`, `PageColumn`, spacing scale |
| Home | `feature/home/HomeScreen.kt`, `HomeCatalog.kt` | Header, search + filter, trust row, category chips, hero, category rail, responsive grid, new-arrivals rail, bottom nav |
| Login | `feature/auth/LoginScreen.kt` | Restyled to match Home; validation, show/hide, remember-me kept; now calls `onSignedIn` |
| Navigation | `navigation/VelnoxApp.kt`, `VelnoxInfoDialog.kt` | Typed `AppDestination` (Login/Home), Crossfade transition, Back from Home → Login, styled placeholder dialog |
| Entry | `MainActivity.kt` | Renders `VelnoxApp()` |
| Strings | `res/values/strings.xml`, `res/values-en/strings.xml` | 85 strings each, Thai and English in sync |
| Docs | `README.md` (unchanged stack), this report, `AI_HANDOFF.md` | — |

### Stack

Not changed: AGP 8.7.2, Kotlin 2.0.21, Compose BOM 2024.10.01, Gradle 8.11.1,
compileSdk/targetSdk 35, minSdk 26. No new Gradle dependencies were added.

---

## Verified

| Check | Result |
|-------|--------|
| Workflow unchanged | **VERIFIED** — `gradle-build.yml` untouched this phase |
| CI build | **VERIFIED** — run 44110918805, `BUILD SUCCESSFUL in 2m 29s` |
| APK produced | **VERIFIED** — 9,669,400 bytes |
| APK integrity | **VERIFIED** — `unzip -t` passed |
| Artifact uploaded | **VERIFIED** — `velnox-debug-apk`, 9,224,459 bytes |
| Package identity | **VERIFIED** — `com.velnox.app`, `Velnox`, launcher `MainActivity`, minSdk 26 / target 35 |
| Thai + English packaged | **VERIFIED** — `aapt2 dump strings` returns Home and Login copy in both locales |
| First build attempt | **FAILED** — scripted edit left a duplicated `HomeScreen` body (`Syntax error: Expecting a top level declaration`). Fixed in the next commit; second run green |
| Local compile | **BLOCKED** — the packaged Gradle 9.9.1 launcher jar is missing on this device |
| On-device render | **NOT TESTED** — no Shizuku / root install route in this session |

---

## Notes for Next Agent

- **Mobbin MCP is paywalled** for this account; do not claim Mobbin research. Report the block,
  then either ask the user or use web references.
- Home content width: the feed is capped at `ContentMaxWidth` (560dp) and centred; the bottom bar spans the window.
- Column counts come from `BoxWithConstraints`: `<400dp` → 2, `<600dp` → 3, else 4.
- Any screen-level `stringResource` / `MaterialTheme` call must be inside `@Composable`.
- Add new icons to `VelnoxIcons` as Lucide strokes; never emoji, never an icon font.
- Product data is UI preview only: names, stores and qualitative tags. **No prices, stock, orders or money.**
- Phase 2 seam: `AppRouting` (start destination) + `SignedInStateHolder` + `LoginScreen(onSignedIn)`.

---

## Next

1. Real auth: replace the 700 ms demo delay with the API call and resolve `AppRouting` from stored state
2. Search screen and category listing backed by the same catalog
3. Product detail from the grid tap
4. UI tests for validation, navigation and the 320dp layout
