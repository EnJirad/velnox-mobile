# AI_HANDOFF.md — Velnox Mobile Current Snapshot

> **Current snapshot (not a log).** Supersedes the Phase 2/UI-audit snapshots,
> archived verbatim in `.ai/history/`.
> Statuses: **VERIFIED / UNVERIFIED / FAILED / BLOCKED / SKIPPED / NOT TESTED**.
> Repository: `EnJirad/velnox-mobile`

---

## Current State (2026-11-17, run 44157230905)

| Area | Status | Notes |
|------|--------|-------|
| Five-tab navigation | **VERIFIED (source unchanged)** | Home, Messages, Orders, Cart, Profile; per-tab `SaveableStateHolder` |
| Home fixed toolbar | **VERIFIED (compile)** | Logo, editable search, notification bell; status-bar inset; never scrolls |
| Home sticky categories | **VERIFIED (compile)** | Seasonal banner → sticky Popular Categories → featured → full feed; one `LazyColumn` |
| Home search | **VERIFIED (compile + static logic)** | Localized local search over name/store/tag/category; clear action and empty state |
| Category destination | **VERIFIED (compile)** | `category` hub and `category:<id>` filtered listing; Back chain to Home |
| Product feed | **VERIFIED (compile)** | Lazy rows, stable product ids, content types, no nested vertical list |
| Notification popover | **VERIFIED (compile)** | Anchored `DropdownMenu`, outside/back dismiss, honest empty state, no fake data |
| Profile Sign Out | **VERIFIED (source)** | Removed from Profile; Settings sign-out confirmation and reset preserved |
| Settings child Back | **VERIFIED (source)** | Arrow and system Back return to Settings hub; hub Back returns to Profile/tab |
| Dark Mode | **VERIFIED (compile)** | Selected tab, Profile, Messages, Settings, dialogs and emerald pairs use semantic roles |
| Brand mark | **VERIFIED (APK/source)** | Slate SVG for Light, white SVG for Dark, chosen from the applied scheme |
| Localisation | **VERIFIED (packaged)** | 292 keys × th/en/my, identical key sets, no raw UI literals in Kotlin |
| Theme modes | **VERIFIED (source)** | Light / Dark / System still resolve through `ThemeMode` + DataStore |
| Tap effects | **VERIFIED (source)** | Shared no-indication clickable/toggleable across custom controls |
| CI build | **VERIFIED** | `BUILD SUCCESSFUL in 2m 23s`, run 44157230905 |
| APK artifact | **VERIFIED** | 10,773,857 bytes; SHA-256 `cc4ddbde961045d9e9ddc788669b657c7460ce692419ab76e61e4abbec7663d2`; `unzip -t` clean |
| On-device runtime | **NOT TESTED** | No install route in this session; no device-level claim |

---

## Architecture

```
MainActivity
└── VelnoxApp (preferences → locale → theme)
    ├── signedIn seam (demo, Phase 3 backend later)
    └── VelnoxBottomBar + five tabs
        └── Home / Messages / Orders / Cart / Profile
            ├── Home: fixed toolbar + one LazyColumn
            │   ├── seasonal banner
            │   ├── sticky Popular Categories
            │   ├── featured rail
            │   └── complete product rows
            └── Overlay destinations
                settings, settings/<page>, category, category:<id>,
                order/<id>, thread/<id>, product:<id>, placeholders
```

- `HomeCatalog` implements `CatalogSource`; a future repository can replace the
  four read methods without changing Home.
- `HomeNotifications.items` is empty; no notification backend exists.
- `CartState` remains hoisted in the nav host.
- No Navigation Compose, database, backend auth, payment or new dependency.

---

## Decisions

1. **One vertical lazy system.** `LazyColumn` owns Home; `stickyHeader` provides
   the category pin. Featured/category rails are horizontal `LazyRow`s only.
2. **Stable local ids.** `p1`–`p8` are unique; the old `p1b`/`p6b` duplicates were
   removed. Rows use the joined product ids as their lazy key.
3. **Search is honest.** Localized local substring search only; the UI states the
   preview nature and the `CatalogSource` seam is ready for an API.
4. **Theme-aware branding without OS checks.** `BrandMark` uses
   `colorScheme.surface.luminance()`, so manual Light/Dark choices win over the
   system setting.
5. **Navigation stays one-level with derived parents.** `settings/<page>` →
   `settings`, `category:<id>` → `category`; no new navigation library.
6. **No fake notifications.** The popover ships with an empty state and an empty
   model, not invented order or payment events.

---

## What changed this phase

- `feature/home/HomeScreen.kt` — fixed toolbar, sticky categories, search, lazy feed
- `feature/home/HomeCatalog.kt` — `CatalogSource`, unique products, localized search
- `feature/home/CategoryScreen.kt` — category hub + filtered listing
- `feature/home/HomeNotifications.kt` — notification model with empty source
- `ui/components/VelnoxInteractions.kt` — no-indication clickable/toggleable
- `ui/components/VelnoxComponents.kt` — theme-aware `BrandMark`, theme-safe badges
- `ui/components/VelnoxScaffolds.kt` — semantic selected/danger/status roles
- `navigation/VelnoxApp.kt` — category routes, Settings child Back, Profile sign-out wiring removed
- `navigation/VelnoxBottomBar.kt` — readable selected label, emerald indicator
- `feature/profile/ProfileScreen.kt` — Sign Out removed, theme-safe stats/address
- `feature/settings/SettingsScreen.kt`, `SettingsDetailScreen.kt` — dialog/selected/switch/saved-state colours
- `feature/messages/MessagesScreen.kt` — readable outgoing bubble
- `feature/orders/OrdersScreen.kt` — emerald selected filters
- `feature/cart/CartScreen.kt` — semantic primary/selection colours
- `feature/auth/LoginScreen.kt` — semantic errors/focus/primary, no colour animation
- `navigation/VelnoxInfoDialog.kt` — semantic primary button
- `res/drawable/ic_velnox_mark_slate.xml`, `ic_velnox_mark_white.xml`
- `values/`, `values-en/`, `values-my/strings.xml` — +10 keys each (292 total)

Full evidence: `.ai/reports/LATEST.md`. Previous snapshots: `.ai/history/`.

---

## Known issues

- Runtime behaviour is verified only by compilation, static logic checks and APK
  inspection; no device/emulator run.
- Search is local preview data; no server-wide search or paging.
- Notifications have no source; only the empty state exists.
- Category listing is a scrollable column over the small local catalogue; a real
  backend will need lazy paging there.
- Product detail and checkout remain placeholder destinations.
- `.ai/AI_RULES.md` and `.ai/context/project-map.md` still use older VelShop names.
- GitHub Actions emits `setup-java@v4` / Node 20 deprecation annotations.

---

## Next Agent Startup Order

1. `.ai/AI_HANDOFF.md` ← **YOU ARE HERE**
2. `.ai/AI_RULES.md` (stale names; verify against source)
3. `.ai/VELNOX_DESIGN_THEME.md`
4. `.ai/reports/LATEST.md`
5. `git fetch origin && git rev-parse HEAD && git rev-parse origin/main`

---

## Secrets / Credentials

No secrets in `.ai/`. No tokens, keystores or passwords in this repository.

---

## Signature

> This handoff supersedes the previous snapshots.
> **Last updated:** 2026-11-17 by the Phase 3 Home/brand/UX session.
