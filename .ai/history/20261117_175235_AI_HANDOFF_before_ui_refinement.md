# AI_HANDOFF.md — Velnox Mobile Current Snapshot

> **Current snapshot (not a log).** What works, what's broken, decisions, next steps.
> Statuses: **VERIFIED / UNVERIFIED / FAILED / BLOCKED / SKIPPED / NOT TESTED**.
> Supersedes all previous handoffs.
> Repository: `EnJirad/velnox-mobile`

---

## Current State (2026-11-17, run 44122098464)

| Area | Status | Notes |
|------|--------|-------|
| Five-tab navigation | **VERIFIED** | Home, Messages, Orders, Cart, Profile; shared Velnox bottom bar |
| Tab state preservation | **VERIFIED (compile)** | `SaveableStateHolder` per tab key; runtime not exercised |
| Back behaviour | **VERIFIED (compile)** | Settings child → hub → tab root → Home → system exit |
| Home | **VERIFIED** | Featured grid, popular rail, new-arrivals rail, search, hero, categories |
| Profile | **VERIFIED** | Header, stats, account rows, addresses, Settings icon top-right |
| Settings | **VERIFIED** | Hub + 13 child screens, every row opens a real destination |
| Messages | **VERIFIED** | List, empty state, conversation detail |
| Orders | **VERIFIED** | Six filters, status chips, order detail with timeline |
| Cart | **VERIFIED** | Selection, quantity, removal, summary (placeholders only) |
| Localisation | **VERIFIED (packaged)** | 277 keys × th/en/my, live switch via localized context |
| Theme | **VERIFIED (compile)** | Light / Dark / System via DataStore |
| Persistence | **VERIFIED (compile)** | DataStore preferences survive process death |
| CI build | **VERIFIED** | `BUILD SUCCESSFUL in 2m 28s` |
| APK artifact | **VERIFIED** | 10,109,686 bytes |
| Mobbin research | **BLOCKED** | Paid-plan gate on every Mobbin tool |
| On-device runtime | **NOT TESTED** | No install route (no Shizuku/root) in this session |

---

## Architecture

```
MainActivity
└── VelnoxApp                  preferences → locale → theme → nav host
    ├── VelnoxPreferencesProvider   DataStore state + setters
    ├── VelnoxLocaleProvider        localized context (live language)
    └── VelnoxNavHost               signedIn · tabKey · overlayKey
        ├── LoginScreen (signedIn = false)
        ├── TabScaffold             bottom bar + SaveableStateHolder
        │   └── Home / Messages / Orders / Cart / Profile
        └── Overlay destinations    settings, settings/<page>, order/<id>,
                                    thread/<id>, placeholder screens
```

- `CartState` is hoisted in the nav host, so the cart survives tab switches.
- Language and theme live in DataStore only; nothing is written to a server.
- `signedIn` is the single Phase 3 seam; there is no mock authentication.

---

## Decisions

1. **Navigation stays hand-written.** Typed tabs plus a string overlay key, no
   Navigation Compose — the only dependency added was DataStore.
2. **Live locale via context, not `AppCompatDelegate`.** Works on API 26+ and
   updates the UI on the same frame, without activity recreation.
3. **Preview data is value-free.** No prices, totals, payments or real accounts;
   every demo block carries a preview notice.
4. **Placeholder destinations are real screens** with localized titles, so no tap
   dead-ends.
5. **M3 bottom bar geometry, Velnox skin** — squircle indicator, emerald icon.

---

## Known issues

- Runtime behaviour (language/theme switch, Back chain, per-tab state) is
  verified only by compilation and APK inspection. It needs a device run.
- Unused string keys remain for future screens (not dead UI).

---

## Next Agent Startup Order

1. `.ai/AI_HANDOFF.md` ← **YOU ARE HERE**
2. `.ai/AI_RULES.md`
3. `.ai/VELNOX_DESIGN_THEME.md`
4. `.ai/reports/LATEST.md`
5. `git fetch origin && git rev-parse HEAD && git rev-parse origin/main`

---

## Secrets / Credentials

No secrets in `.ai/`. No tokens, keystores or passwords in this repository.

---

## Signature

> This handoff supersedes all previous.
> **Last updated:** 2026-11-17 by the Phase 2 session (core screens, navigation, settings).
