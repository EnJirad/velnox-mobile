# AI_HANDOFF.md — Velnox Mobile Current Snapshot

> **Current snapshot (not a log).** What works, what's broken, decisions, next steps.
> Statuses: **VERIFIED / UNVERIFIED / FAILED / BLOCKED / SKIPPED / NOT TESTED**.
> Supersedes the 2026-11-17 Phase 2 handoff, archived verbatim in `.ai/history/`.
> Repository: `EnJirad/velnox-mobile`

---

## Current State (2026-11-17, run 44140832100)

| Area | Status | Notes |
|------|--------|-------|
| Five-tab navigation | **VERIFIED (source unchanged)** | Home, Messages, Orders, Cart, Profile; shared bottom bar |
| Tab state preservation | **VERIFIED (source unchanged)** | `SaveableStateHolder` per tab key; runtime not exercised |
| Back behaviour | **VERIFIED (source unchanged)** | Settings child → hub → tab root → Home → system exit |
| Home | **VERIFIED (compile)** | Scrollable category row, saved selection, corrected hero CTA, token rhythm, 320dp/dark previews |
| Profile | **VERIFIED (compile)** | Larger edit/add targets, corrected default badge, truncated-safe name, 320dp/dark previews |
| Settings | **VERIFIED (compile)** | Hub + 13 child screens unchanged; selected language/appearance cues, distinct Privacy icon, separated add-address card |
| Messages / Orders / Cart | **VERIFIED (compile)** | Neutral disabled states, localized placeholders, 44–48dp targets, accessible chevron |
| Login / dialog / empty states | **VERIFIED (compile)** | 48dp primary/social/close targets, larger checkbox target, dark preview |
| Localisation | **VERIFIED (packaged)** | 282 keys × th/en/my, identical key sets, no raw UI literals in Kotlin |
| Theme | **VERIFIED (compile)** | Light / Dark / System logic unchanged; dark previews added |
| Persistence | **VERIFIED (source unchanged)** | DataStore preferences and locale provider untouched |
| Launcher branding | **VERIFIED (APK contents)** | Approved SVG paths in standard/round/monochrome layers and in-app `BrandMark` |
| CI build | **VERIFIED** | `BUILD SUCCESSFUL in 1m 51s`, run 44140832100 |
| APK artifact | **VERIFIED** | 10,732,671 bytes; SHA-256 `27621718ed67e32f7bd282b366b2f465d25f0dfa29101aa6d4c560669f2a4d2c`; `unzip -t` clean |
| Inspo MCP | **VERIFIED AVAILABLE** | Marketplace mobile references found; patterns adapted, no brand copied |
| On-device runtime | **NOT TESTED** | No install route in this session; no claim of device verification |

---

## Architecture

```
MainActivity
└── VelnoxApp (theme + locale providers)
    ├── signedIn seam (demo, Phase 3)
    └── VelnoxBottomBar + five tabs
        └── Home / Messages / Orders / Cart / Profile
            └── Overlay destinations: settings, settings/<page>, order/<id>,
                thread/<id>, placeholder screens
```

- `CartState` is hoisted in the nav host, so the cart survives tab switches.
- Language and theme live in DataStore only; nothing is written to a server.
- `signedIn` remains the single Phase 3 seam; there is no mock authentication.
- No Navigation Compose, database, backend auth, payment or new dependency was added.

---

## Decisions

1. **Brand source stays untouched.** The approved logo pack is read-only; Android resources are
   derived copies, never renames. The source SVG's four paths are byte-for-byte identical in the
   VectorDrawables.
2. **Adaptive safe zone wins over raw artwork size.** The white symbol is centred and scaled to
   ≈50 × 36.33dp inside the 108dp viewport, leaving it inside the 66dp safe circle.
3. **Existing architecture wins.** Navigation, Back chain, DataStore keys and localized-context
   switching were deliberately not touched.
4. **Visual fixes are local.** Touch targets, token use, empty/disabled states and hierarchy were
   corrected without changing screen contracts or introducing new components.
5. **Rules document is stale.** `.ai/AI_RULES.md` still names VelShop/velshop-mobile and an older
   artifact name. Source and current workflow were followed instead.

---

## What changed this phase

- `res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`,
  `ic_launcher_monochrome.xml`, `ic_velnox_mark.xml`
- `res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`
- `feature/home/HomeScreen.kt`, `feature/profile/ProfileScreen.kt`
- `feature/cart/CartScreen.kt`, `feature/messages/MessagesScreen.kt`,
  `feature/orders/OrdersScreen.kt`
- `feature/settings/SettingsScreen.kt`, `SettingsDetailScreen.kt`
- `feature/auth/LoginScreen.kt`, `navigation/VelnoxInfoDialog.kt`
- `ui/components/VelnoxComponents.kt`, `VelnoxScaffolds.kt`, `ui/icons/VelnoxIcons.kt`
- `values/strings.xml`, `values-en/strings.xml`, `values-my/strings.xml` (+5 keys each)

Full evidence: `.ai/reports/LATEST.md`. Previous snapshots: `.ai/history/`.

---

## Known issues

- Runtime behaviour is still verified only by compilation and APK inspection.
- Search, filters, product detail and checkout remain honest placeholder destinations.
- GitHub Actions emits deprecation annotations for `setup-java@v4` / Node 20 actions; not changed here.
- Unused legacy string keys remain for future screens.

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

> This handoff supersedes the previous Phase 2 snapshot.
> **Last updated:** 2026-11-17 by the UI audit and brand refinement session.
