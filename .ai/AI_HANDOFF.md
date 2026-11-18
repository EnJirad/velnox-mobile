# AI_HANDOFF.md — Velnox Mobile Current Snapshot

> **Current snapshot (not a log).** Supersedes the Phase 3 snapshot, archived
> verbatim in `.ai/history/20261118_135900_AI_HANDOFF_before_part2.md`.
> Statuses: **VERIFIED / UNVERIFIED / FAILED / BLOCKED / SKIPPED / NOT TESTED**.
> Repository: `EnJirad/velnox-mobile`

---

## Current State (2026-11-18, commit `cc8a093`, run 44254710010)

| Area | Status | Notes |
|------|--------|-------|
| Startup destination | **VERIFIED (source)** | Opens on Home for everyone; Login is no longer a launch screen |
| Session restore | **VERIFIED (source)** | `SessionController.restoreOnce()` runs once per process; `NoStoredSessionStore` truthfully reports no session, so the result is Guest |
| Session during restore | **VERIFIED (source)** | `Restoring` renders the shell; no full-screen Login, no spinner, protected taps ignored until resolved |
| Guest browsing | **VERIFIED (source)** | Home, categories, listings, product route, search |
| Guest gate | **VERIFIED (source)** | Cart / Orders / Profile / Messages / checkout / wishlist / settings / order + thread detail |
| Gate centralization | **VERIFIED (source)** | `AccessPolicy` + two funnels (`requestTab`, `requestOverlay`); no per-screen auth checks |
| Gate reason copy | **VERIFIED (compile)** | `LoginReason` card on `secondaryContainer`; localized in th/en/my |
| Gate escape | **VERIFIED (source)** | 44dp "continue browsing" + system Back; pending destination cleared, no loop |
| Pending destination | **VERIFIED (source)** | `tab:<key>` / `overlay:<key>` restored by `completeSignIn()` |
| Sign-in | **DEMO (unchanged)** | `LoginScreen`'s pre-existing simulated submit; `completeDemoSignIn()` persists nothing |
| Home search alignment | **VERIFIED (compile)** | Placeholder, typed text and icon share one vertical centre (was ~12dp high) |
| Localisation | **VERIFIED (packaged)** | 300 keys × th/en/my, identical key sets |
| CI build | **VERIFIED** | run 44254710010, all steps success; artifact 10,176,620 bytes |
| On-device runtime | **NOT TESTED** | No install route in this session; no device-level claim |

---

## Architecture

```
MainActivity
└── VelnoxApp (preferences → locale → theme → session)
    └── VelnoxSessionProvider          LocalSession (process-scoped controller)
        └── VelnoxNavHost              starts on AppTab.Home
            ├── requestTab / requestOverlay   ← the only destination funnels
            │     └── AccessPolicy            ← the only guest decision
            ├── Login gate                 (reason + pending destination)
            └── TabScaffold + five tabs
                └── Home / Messages / Orders / Cart / Profile
                    └── Overlay destinations
                        settings, settings/<page>, category, category:<id>,
                        order:<id>, thread:<id>, product:<id>, placeholders
```

- `core/session/` — `SessionState`, `SessionStore` (+ `NoStoredSessionStore`),
  `SessionController`, `SessionProvider`.
- `navigation/AccessPolicy.kt` — `LoginReason` + public/protected route tables.
- `HomeCatalog` still implements `CatalogSource`; a repository can replace the
  four read methods without touching Home.
- `CartState` remains hoisted in the nav host; `HomeNotifications.items` is empty.
- No Navigation Compose, backend client, database, auth SDK or new dependency.

---

## Decisions

1. **Guest-first, gate-on-demand.** Home is the launch destination; authentication
   is requested only when a protected destination is opened.
2. **One policy object.** `AccessPolicy` is the only place that knows what a guest
   may reach, and unknown routes fail closed to "sign-in required".
3. **Two funnels, no scattered checks.** `requestTab` and `requestOverlay` are the
   only ways a destination changes, so a new screen cannot quietly bypass the gate.
4. **Client gate is not the security boundary.** The API's `requireAuth` on
   `/api/customer/cart*`, `/checkout`, `/orders*`, `/profile`, `/addresses` remains
   the authority; the client gate is UX and defence in depth.
5. **Honest session, no fake auth.** `NoStoredSessionStore` returns `null` rather
   than inventing a session; the demo sign-in is named `completeDemoSignIn()` and
   persists nothing.
6. **Restore never blocks the UI.** The restore runs after the first frame, so
   Home paints immediately and Login cannot flash.
7. **Messages is account-specific.** Gated with the other account surfaces; change
   `AccessPolicy` if guests should read threads.
8. **Search fix is alignment-only.** `CenterStart` on the text box; no colour,
   size or spacing change, so light and dark behave identically.

---

## What changed this phase

- `core/session/SessionState.kt`, `SessionStore.kt`, `SessionController.kt`,
  `SessionProvider.kt` — new session layer
- `navigation/AccessPolicy.kt` — new centralized guest policy
- `navigation/VelnoxApp.kt` — guest-first host, two funnels, single Back handler,
  pending destination, sign-out reset
- `feature/auth/LoginScreen.kt` — optional `reason` card + 44dp dismiss action
- `feature/home/HomeScreen.kt` — search placeholder vertical centring
- `VelnoxApplication.kt` — owns `SessionController`
- `values/`, `values-en/`, `values-my/strings.xml` — +8 keys each (300 total)

Full evidence: `.ai/reports/LATEST.md`. Previous snapshots: `.ai/history/`.

---

## Known issues

- No session can be restored yet: nothing has ever been issued or persisted.
  Part 3 needs a Keystore-backed `SessionStore` **and** a native token from the API
  (Part 1 risk R1: mobile responses set a cookie but return no token).
- Sign-in is still the pre-existing demo submit; no credential check, no token.
- Add-to-cart has no UI; when added it must route through `requestOverlay`.
- Product detail is still `PlaceholderDestination`; search and the catalogue are
  local preview data.
- Runtime behaviour is verified by compilation and source tracing only; no
  device/emulator run.
- Notification bell is not gated (it opens an empty popover, not a route).
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
> **Last updated:** 2026-11-18 by the Part 2 guest-first navigation session.
