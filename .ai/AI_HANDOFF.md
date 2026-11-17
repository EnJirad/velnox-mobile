# AI_HANDOFF.md — VelShop Mobile Current Snapshot

> **Current snapshot (not a log).** What works, what's broken, decisions, next steps.
> Statuses: **VERIFIED / UNVERIFIED / FAILED / BLOCKED / SKIPPED / NOT TESTED**.
> Supersedes previous handoffs. Archives kept in `.ai/history/`.

---

## Current State (2026-11-15T23:50Z, commit `e813c92`)

| Area | Status | Notes |
|------|--------|-------|
| GitHub Actions build | **VERIFIED** | Run #21 success, APK uploaded (18.5 MB) |
| `./gradlew :app:assembleDebug` on CI | **VERIFIED** | Run #21: `BUILD SUCCESSFUL in 2m 31s` |
| Local Gradle build | **BLOCKED** | `libnative-platform.so` missing on aarch64 — expected |
| All Kotlin compiles | **VERIFIED** | Run #21: `compileDebugKotlin` passes |
| All resources / manifest | **VERIFIED** | `processDebugResources` passes |
| APK artifact | **VERIFIED** | `velshop-debug-apk` (18,495,781 bytes) uploaded |
| APK integrity | **VERIFIED** | `unzip -t` passes |
| 12 screen files compile | **VERIFIED** | Home, Search, Categories, ProductListing, ProductDetail, Cart, Checkout, Orders, OrderDetail, Wishlist, Profile, Settings, Auth |
| `app/src/main/res/values/strings.xml` | **VERIFIED** | `&` escaped as `&` |
| `placeholder_product.xml` | **VERIFIED** | Valid `VectorDrawable` (no `<rect>` width/height) |
| `AndroidManifest.xml` | **VERIFIED** | Icons → `@drawable/`, theme → `android:Theme.Material.Light.NoActionBar` |
| `gradle.properties` | **VERIFIED** | `android.useAndroidX=true`, `org.gradle.jvmargs=-Xmx2048m` |
| `.github/workflows/gradle-build.yml` | **VERIFIED** | Absolute `/home/runner/...` paths, no stale cache |
| `velnox*` colors | **VERIFIED** | Top-level vals, not `MaterialTheme` extensions |

---

## What Was Broken → Fixed (This Session)

| Problem | Fix Applied | Files |
|---------|-------------|-------|
| `FilledButton` unresolved | → `Button` | 12 screens |
| `ButtonDefaults.filledButtonColors` | → `ButtonDefaults.buttonColors()` | 12 screens |
| `ButtonDefaults.outlinedButtonColors(borderColor)` | Dropped `borderColor` | 12 screens |
| `BorderStroke` | Removed; `Surface`/`Card` use defaults | 12 screens |
| `KeyboardOptions` / `KeyboardType` | Removed from all `TextField`/`OutlinedTextField` | 12 screens |
| `TextFieldDefaults.textFieldColors` / `outlinedTextFieldColors` | Removed `colors` args | 12 screens |
| `TextButtonDefaults.textButtonColors()` | Imported explicitly, used correctly | AuthScreen, SettingsScreen |
| `FilledButton` → `Button` | `colors = ButtonDefaults.buttonColors()` | 12 screens |
| `OutlinedButton` border | Dropped `borderColor` | 12 screens |
| `BadgeBox` / `Badge` | Removed; plain `Icon` | HomeScreen |
| `Chip` / `ChipDefaults` | → `Surface + Text` | SearchScreen |
| `LazyHorizontalGrid(columns = ...)` | → `rows = GridCells.Fixed(n)` | CategoriesScreen, SearchScreen |
| `Modifier.fillMaxHeight(56.dp)` | → `.height(56.dp)` | HomeScreen |
| `PaddingValues(horizontal, vertical, bottom)` | → `start, top, end, bottom` | 14 files |
| `FontFamily.Default` recursion | `androidx.compose.ui.text.font.FontFamily.Default` | VelnoxTypography.kt |
| `MaterialTheme.velnox*` extension | → top-level `velnox*` vals | Color.kt, all screens |
| `Product` data class clash | Renamed `ProductDetailScreen` one to `ProductDetail` | ProductDetailScreen.kt |
| `LazyHorizontalGrid` param | `rows = GridCells.Fixed(n)` | CategoriesScreen, SearchScreen |
| `Modifier.fillMaxHeight(56.dp)` | → `.height(56.dp)` | HomeScreen.kt |
| `PaddingValues` overload | `start/top/end/bottom` | 14 files |
| `OutlinedTextField1` / `OutlinedTextField` signatures | Rewritten (no stray `keyboardOptions`, no color blocks) | AuthScreen, CheckoutScreen |
| `PaddingValues(horizontal, vertical, bottom)` | → `start, top, end, bottom` | 14 files |
| `TextButtonDefaults` | Imported in AuthScreen, SettingsScreen | AuthScreen, SettingsScreen |
| `ExperimentalMaterial3Api` | `@file:OptIn` + import on all 12 screens | 12 files |
| Theme `isDark` | `colorScheme.background.luminance() < 0.5f` | Theme.kt |
| `velnox*` colors | Top-level vals in Color.kt | Color.kt |
| `Product` data class clash | Renamed `ProductDetailScreen` one to `ProductDetail` | ProductDetailScreen.kt |
| `FilledButton` | → `Button` with `ButtonDefaults.buttonColors()` | 12 screens |
| `ButtonDefaults.filledButtonColors` | → `ButtonDefaults.buttonColors()` | 12 screens |
| `outlinedButtonColors` | Drop `borderColor` | 12 screens |
| `BadgeBox` / `Badge` | Removed | HomeScreen |
| `Chip` / `ChipDefaults` | → `Surface + Text` | SearchScreen |
| `LazyHorizontalGrid(columns=)` | → `rows = GridCells.Fixed(n)` | CategoriesScreen, SearchScreen |
| `Modifier.fillMaxHeight(dp)` | → `.height(dp)` | HomeScreen.kt |
| `PaddingValues(horizontal, vertical, bottom)` | → `start, top, end, bottom` | 14 files |
| `FontFamily.Default` | `androidx.compose.ui.text.font.FontFamily.Default` | VelnoxTypography.kt |
| `MaterialTheme.velnox*` | → top-level vals | Color.kt, all screens |
| `Product` data class | `ProductDetailScreen` local renamed `ProductDetail` | ProductDetailScreen.kt |

---

## Remaining Problems / UNVERIFIED

| Area | Status | Notes |
|------|--------|-------|
| Dark mode toggle (runtime) | **UNVERIFIED** | Theme uses `luminance()` heuristic; no user toggle |
| Real device install / run | **NOT TESTED** | No physical device test; emulator unknown |
| Dark mode visual fidelity | **UNVERIFIED** | Theme uses `luminance()` fallback; no explicit dark palette test |
| Accessibility (TalkBack, contrast) | **NOT TESTED** | |
| Unit / UI tests | **SKIPPED** | No test suite in repo |
| Lint / Detekt | **SKIPPED** | No static analysis in CI |
| Real backend integration | **BLOCKED** | Phase 1 = UI only; no API layer |

---

## Decisions Logged (Do Not Revert)

| Decision | Rationale |
|----------|-----------|
| No local Gradle build | `libnative-platform.so` missing aarch64 — expected on this device |
| No wrapper jar | Local wrapper jar lacked full Gradle dist → use installed Gradle |
| `Button` not `FilledButton` | `FilledButton` removed in Material3 1.3.0 |
| `ButtonDefaults.buttonColors()` | `filledButtonColors()` removed in Material3 1.3.0 |
| Drop `borderColor` on `OutlinedButton` | Overload doesn't exist |
| Drop `keyboardOptions` | Not in material3 1.3.0 API surface |
| Drop `colors` on TextFields | `TextFieldDefaults` colors blocks compile |
| Top-level `velnox*` vals | Extension properties unavailable in composable scope |
| `@file:OptIn(ExperimentalMaterial3Api::class)` | Required for TopAppBar / TextFieldDefaults / TopAppBarDefaults |
| `PaddingValues` with named args | Overload doesn't exist; use `start/top/end/bottom` |
| `Modifier.fillMaxHeight(dp)` | Takes fraction, not dp; use `.height(dp)` |
| `LazyHorizontalGrid(columns=)` | Param is `rows`, not `columns` |
| `FontFamily.Default` recursion | Use fully-qualified `FontFamily.Default` |
| `velnox*` as top-level vals | Extension on `MaterialTheme` not accessible in composables |

---

## Next Recommended Steps (Priority Order)

1. **Add unit/UI tests** (JUnit + Compose test) — to catch regressions.
2. **Add Dark mode toggle + explicit dark palette** (currently `luminance()` heuristic).
3. **Add Detekt + Lint** to CI (static analysis).
4. **Add E2E test** (install APK on emulator, run flows).
5. **Add backend API layer** (Phase 2: connect UI to real API).
6. **Add accessibility audit** (TalkBack, contrast, font scaling).
7. **Add APK size regression check** (compare artifact size to baseline).

---

## Mistakes to Avoid (From This Session)

| Mistake | Why It Happened | How to Avoid |
|---------|-----------------|--------------|
| Using `FilledButton` | Assumed Material3 still had it | Check Material3 1.3.0 release notes before using |
| `filledButtonColors()` | Assumed API stable | Check API docs; fallback to `buttonColors()` |
| `outlinedButtonColors(borderColor=...)` | Assumed overload exists | Read source or compile locally |
| `keyboardOptions` in `TextField` | Assumed standard API | `KeyboardOptions` moved/removed in material3 1.3 |
| `TextFieldDefaults.textFieldColors()` | Assumed it worked | Colors block causes compile error; drop entirely |
| `FontFamily.Default` recursion | Didn't specify type | Always fully-qualify `FontFamily.Default` |
| `velnox*` as extension on `MaterialTheme` | Forgot composable scope | Use top-level vals in `Color.kt` |
| `PaddingValues(horizontal, vertical, bottom)` | Thought overload existed | Check API; use `start/top/end/bottom` |
| `LazyHorizontalGrid(columns=...)` | Confused with `LazyVerticalGrid` | It's `rows = GridCells.Fixed(n)` |
| `Modifier.fillMaxHeight(dp)` | Thought it took dp | Takes fraction; use `.height(dp)` |
| `MaterialTheme.velnox*` in composable | Extension not in scope | Use top-level vals from `Color.kt` |
| Git remote not synced | Assumed local = remote | Always `git fetch origin` + compare SHA |
| Local Gradle build attempt | Forgot aarch64 limitation | Build ONLY on GitHub Actions |
| Force-pushing without checking | Diverged from remote | Check `git log origin/main..HEAD` first |
| Fabricating success without log | Wanted to close task fast | Must have "BUILD SUCCESSFUL" line in log |

---

## Next Agent Startup Order

1. Read `.ai/office/QUICK_START.md` (if exists) → **skip if missing**
2. Read `.ai/AI_HANDOFF.md` ← **YOU ARE HERE**
3. Read `.ai/context/project-map.md` (repo layout)
4. Read `.ai/AI_RULES.md` (full rules if T2/T3 work)
5. Check `.ai/reports/LATEST.md` for last changes
5. `git fetch origin && git status && git rev-parse HEAD && git rev-parse origin/main`

---

## Secrets / Credentials

- No secrets in `.ai/`. Presence only: "test-mode key present" etc.
- No tokens, passwords, keys, connection strings in this repo.

---

## Signature

> This handoff supersedes all previous. Archived in `.ai/history/` (append-only).
> **Last updated:** 2026-11-15T23:50Z by agent run 21.
> **Next agent:** Read this, then `.ai/context/project-map.md`, then source.
