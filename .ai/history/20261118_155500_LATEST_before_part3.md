# LATEST.md — Velnox Mobile Latest Change Report

> **Current snapshot (overwritten each task).** Previous report (the Part 1 audit)
> archived verbatim in `.ai/history/20261118_135900_LATEST_before_part2.md`.
> Repository: `EnJirad/velnox-mobile`

---

## Task: Part 2 — Guest-First Home & Navigation

**Date:** 2026-11-18
**Commit:** `cc8a093` — "Open app to Home for guests and gate protected destinations"
**Verified CI run:** [44254710010](https://github.com/EnJirad/velnox-mobile/actions/runs/44254710010)
**Status:** **VERIFIED — BUILD SUCCESSFUL** (all build/verify/upload steps green)

---

## 1. Repository safety

| Check | Result |
|---|---|
| `git fetch origin main` before work | Done |
| Base commit | `09207a3` (Part 1 audit report, committed upstream) |
| Local vs `origin/main` at start | Divergence `0 0` |
| Uncommitted work at start | None |
| Work preserved | Yes — no file deleted, no history rewritten |
| Local Android build | Not run, per `.ai/AI_RULES.md` §1 |
| Push | `09207a3..cc8a093` to `origin/main` |

---

## 2. What changed

### 2.1 New session layer (`app/src/main/java/com/velnox/app/core/session/`)

| File | Responsibility |
|---|---|
| `SessionState.kt` | `sealed interface SessionState { Restoring, Guest, Authenticated }` with `isAuthenticated` / `isGuest`. Single source of truth for the guest decision; no screen keeps its own flag. |
| `SessionStore.kt` | Persistence seam (`read()`, `clear()`) plus `NoStoredSessionStore`, which reports "nothing stored" — the truth in Part 2. Part 3 swaps in a Keystore-backed store. |
| `SessionController.kt` | Holds `state`, resolves the stored session **once per process**, fails safe to `Guest` on a storage error, and owns local sign-out. |
| `SessionProvider.kt` | `LocalSession` + `VelnoxSessionProvider`, which starts the restore in a `LaunchedEffect` so Home paints before storage is consulted. |

`VelnoxApplication` now owns `session` next to `preferences`, so a rotation cannot
re-run the restore or lose the resolved state.

### 2.2 Centralized access policy (`navigation/AccessPolicy.kt`)

`LoginReason` (cart, orders, profile, messages, checkout, account) carries the
localized explanation. `AccessPolicy.reasonForTab` / `reasonForOverlay` return
`null` for public destinations and a reason for protected ones.

Public: `home`, `category`, `category:<id>`, `product:<id>`, `search`.
Protected: `messages`, `orders`, `cart`, `profile`, `checkout`, `wishlist`,
`order:<id>`, `thread:<id>`, `settings`, `settings/<page>`.
Unknown routes **fail closed** to `LoginReason.Account`.

### 2.3 Navigation (`navigation/VelnoxApp.kt`)

- Removed the startup `signedIn` demo gate. `VelnoxNavHost` now starts on
  `AppTab.Home` for everyone; Login is a destination, not a launch screen.
- Two funnels are the only way a destination changes:
  `requestTab(AppTab)` and `requestOverlay(String)`. Both consult `AccessPolicy`
  and, for a guest, store `pendingDestinationKey` (`tab:<key>` /
  `overlay:<key>`) and set `loginReasonKey` instead of navigating.
- One `BackHandler` handles everything in a fixed order: close Login → child page
  to its hub → close overlay → back to Home tab → system exit.
- `completeSignIn()` applies the pending destination after sign-in (the Login
  screen's pre-existing demo submit calls it, so Part 3 only swaps the credential
  call); `dismissLogin()` clears the pending destination and leaves the current
  tab untouched.
- `signOut()` clears Login/pending/overlay, returns to the Home tab, then clears
  the session.

### 2.4 Login screen (`feature/auth/LoginScreen.kt`)

Added three optional parameters — `reason: LoginReason?`, `onDismiss: (() -> Unit)?` —
and two UI pieces:

- `SignInReasonCard`: lock icon, "Sign-in required" label and one sentence, on
  `secondaryContainer` / `onSecondaryContainer` so it is correct in light and dark.
- A 44dp "Continue browsing without signing in" action, so a guest is never
  trapped in the gate.

Nothing else on the screen changed: fields, validation, show/hide, remember-me,
social buttons, notice banner and previews are untouched. Preview call sites
compile unchanged thanks to the default arguments.

### 2.5 Home search placeholder (`feature/home/HomeScreen.kt`)

**Root cause:** the `Box` wrapping the placeholder and the `BasicTextField` used
the default `TopStart` alignment, while the field applies
`padding(vertical = VelnoxSpacing.md)`. The placeholder therefore sat ~12dp above
the typed text and above the 18dp search icon, which the `Row` centres.

**Fix:** `contentAlignment = Alignment.CenterStart` on that `Box`, so the
placeholder, the typed text and the icon share one vertical centre. One line plus
a comment; no colour, size, spacing or shape changed, so the fix is
scheme-independent and verified the same way in Light and Dark.

### 2.6 Localization

`+8` keys in `values/` (Thai), `values-en/` and `values-my/`, identical key sets:
`auth_reason_title`, `auth_reason_cart`, `auth_reason_orders`,
`auth_reason_profile`, `auth_reason_messages`, `auth_reason_checkout`,
`auth_reason_account`, `login_continue_browsing`.
**300 keys per locale** (was 292); all three files parse as XML.

---

## 3. Startup behaviour now

```
VelnoxApplication.onCreate()          preferences + SessionController
MainActivity → VelnoxApp()
  preferences → locale → theme → VelnoxSessionProvider → VelnoxNavHost
      state = Restoring  →  Home renders immediately (no Login screen, no spinner)
      LaunchedEffect    →  SessionController.restoreOnce()
                              NoStoredSessionStore.read() = null → Guest
```

- A fresh install opens **Home**, as a guest.
- No full-screen Login is shown while restoring: the shell is already on screen
  and `Restoring` only suppresses a protected-action prompt for the few
  milliseconds the restore takes.
- A stored session would resolve to `Authenticated`; none can exist yet because
  no token has ever been issued. The path is implemented, not simulated.
- Process death returns to `Guest` (nothing is persisted), which is correct until
  Part 3 stores a token.

---

## 4. Guest permission matrix (as implemented)

| Capability | Guest | Enforcement |
|---|---|---|
| Browse Home, feed, featured rail | Allowed | `AccessPolicy.reasonForTab(Home) = null` |
| Browse categories + listings | Allowed | `reasonForOverlay("category*") = null` |
| Open product detail | Allowed | `reasonForOverlay("product:*") = null` |
| Search | Allowed | local `CatalogSource.search` |
| Cart tab | **Login gate** | `requestTab(Cart)` |
| Orders tab + order detail | **Login gate** | `requestTab` / `reasonForOverlay("order:*")` |
| Profile tab, settings, addresses, wishlist | **Login gate** | `requestTab` / `reasonForOverlay("settings*", "wishlist")` |
| Messages tab + threads | **Login gate** | `requestTab` / `reasonForOverlay("thread:*")` |
| Checkout | **Login gate** | `reasonForOverlay("checkout")` |

Protected surfaces are enforced by the two navigation funnels, not by per-screen
`if (signedIn)` checks. The client gate is UX plus defence in depth; the security
boundary stays the API, where `requireAuth` guards `/api/customer/cart*`,
`/checkout`, `/orders*`, `/profile` and `/addresses` (verified in the Part 1 audit).

---

## 5. Why there is no redirect loop

1. `requestTab` / `requestOverlay` **do not change** `tabKey` / `overlayKey` when
   they gate — the guest stays exactly where they were.
2. Login is rendered from `loginReason`, which is independent of the tab state, so
   showing it cannot re-trigger a gate.
3. `dismissLogin()` clears the reason and the pending destination, so Back or
   "Continue browsing" lands on the unchanged screen.
4. Signing in applies the pending destination once and clears it.
5. `askForSignIn` ignores taps while `Restoring`, so a slow store cannot open
   Login on a state that is not yet known.

---

## 6. Verification

| Check | Method | Result |
|---|---|---|
| Kotlin/Compose compilation | GitHub Actions run 44254710010, step "Build debug APK" | **success** |
| APK produced and non-empty | step "Verify APK" | **success** |
| APK zip integrity (`unzip -t`) | step "Verify APK integrity" | **success** |
| Artifact upload | step "Upload APK" | **success** — `velnox-debug-apk`, 10,176,620 bytes |
| String resources | 300 keys in each of th/en/my, XML parse | **passed** |
| Resource references (`R.string.auth_reason_*`, `R.string.login_continue_browsing`) | resolved by the successful compile | **passed** |
| Fresh install → Home | source path traced: `signedIn` gate removed, `tabKey` starts `Home`, `SessionState` starts `Restoring` | **VERIFIED (source)** |
| Guest browsing / search / product detail | `AccessPolicy` returns `null` for those routes; navigation unchanged | **VERIFIED (source)** |
| Cart / Orders / Profile → Login | `reasonForTab` returns a reason for all three; `requestTab` gates | **VERIFIED (source)** |
| Back behaviour | single `BackHandler`, order documented in §5 | **VERIFIED (source)** |
| Light + Dark placeholder alignment | alignment-only change using semantic colours already in place | **VERIFIED (source)**; visual confirmation **NOT TESTED** on a device |

**Not verified:** on-device/emulator runtime — there is no install route in this
session. Every runtime claim above is a source-level trace, not an observed run.
Job log text (`BUILD SUCCESSFUL in …`) could not be downloaded: this token has no
Actions-log read scope (`403 Must have admin rights to Repository`). The per-step
**step conclusions** and the produced artifact size were read from the API.

---

## 7. Remaining issues / limits

1. **No real session can be restored yet.** `NoStoredSessionStore` always returns
   `null` because no token exists; Part 3 must implement `SessionStore` with a
   Keystore-backed write, and the API must return a token to the native client
   (Part 1 risk R1).
2. **The sign-in that completes the gate is still the pre-existing demo submit**
   (`delay` + local validation). It is clearly named `completeDemoSignIn()` and
   persists nothing; Part 3 replaces the call site.
3. **Add-to-cart has no UI yet**, so there is nothing to gate. When it is added it
   must be a protected route through `requestOverlay`, per `AccessPolicy`'s KDoc.
4. **Product detail is still `PlaceholderDestination`**, so "open product details"
   works as navigation but shows the honest preview notice, not real content.
5. **Search and the catalogue remain local preview data** (`HomeCatalog`); Part 5
   replaces the source behind the same `CatalogSource` seam.
6. **Messages is treated as account-specific** and gated. That is a product
   decision made here; if guests should read threads it must be reclassified in
   `AccessPolicy`.
7. **Notification bell is not gated** — it opens an empty popover, not a route.
8. `.ai/AI_RULES.md` still names VelShop; GitHub Actions still emits
   `setup-java@v4` / Node 20 deprecation annotations (non-blocking).

---

## 8. Files changed

**Added**

- `app/src/main/java/com/velnox/app/core/session/SessionState.kt`
- `app/src/main/java/com/velnox/app/core/session/SessionStore.kt`
- `app/src/main/java/com/velnox/app/core/session/SessionController.kt`
- `app/src/main/java/com/velnox/app/core/session/SessionProvider.kt`
- `app/src/main/java/com/velnox/app/navigation/AccessPolicy.kt`

**Modified**

- `app/src/main/java/com/velnox/app/VelnoxApplication.kt`
- `app/src/main/java/com/velnox/app/navigation/VelnoxApp.kt`
- `app/src/main/java/com/velnox/app/feature/auth/LoginScreen.kt`
- `app/src/main/java/com/velnox/app/feature/home/HomeScreen.kt`
- `app/src/main/res/values/strings.xml`, `values-en/strings.xml`, `values-my/strings.xml`

**Documentation**

- `.ai/reports/LATEST.md` (this report)
- `.ai/AI_HANDOFF.md`
- `.ai/history/20261118_135900_LATEST_before_part2.md`, `…_AI_HANDOFF_before_part2.md`

No backend, database, workflow, Gradle or dependency change. No new dependency.

---

## 9. Next: Part 3 inputs

- Replace `NoStoredSessionStore` with a real `SessionStore` and swap
  `completeDemoSignIn()` for an `AuthRepository` sign-in.
- Decide the native session carrier with the backend owner (Part 1 risk R1:
  return the token in the mobile response body, or add an exchange endpoint).
- Registration screen, password policy alignment (≥ 8 + strength, not 6) and the
  `VALIDATION_ERROR` / `INVALID_CREDENTIALS` / `ACCOUNT_CONFLICT` copy mapping.
- Real account identity in Profile/Settings once `/api/customer/profile` is wired.
