# AI_RULES.md — VelShop Mobile Core Rulebook

> **For AI agents working on VelShop mobile (EnJirad/velshop-mobile).**
> Canonical location: `.ai/AI_RULES.md`. Authority: this file → `.ai/AI_HANDOFF.md` → `.ai/context/project-map.md` → source code.
> When docs conflict with source, **source wins** — then update the docs.

---

## 0. Startup Synchronization — GitHub Remote Is the Source of Truth

> **The AI sandbox / local checkout is temporary and may be stale. The GitHub remote is authoritative.**

Before **any** repository-changing task, establish the real remote state:

```bash
git remote -v
git fetch origin
git status
git branch --show-current
git rev-parse HEAD                 # local SHA
git rev-parse origin/main          # remote SHA — authoritative
```

Compare and act:

| Case | Condition | Action |
|------|-----------|--------|
| In sync | local SHA == remote SHA | Proceed |
| Behind | `git log HEAD..origin/main` non-empty | `git pull --ff-only` before any edit |
| Ahead | `git log origin/main..HEAD` non-empty | Proceed; push per §14 |
| Diverged | Both non-empty | **STOP** — inspect divergence |

```text
GitHub remote (main)  →  AUTHORITATIVE SOURCE OF TRUTH
AI sandbox / local    →  TEMPORARY WORKSPACE
```

---

## 1. Build & APK — GitHub Actions Only

- **Never** build the APK locally. The Gradle wrapper jar on this device lacks `libnative-platform.so` for aarch64. Local builds are guaranteed to fail.
- The **only** build environment is GitHub Actions (ubuntu-latest).
- The workflow is `.github/workflows/gradle-build.yml`. It:
  - Installs JDK 17 (Temurin)
  - Installs Android SDK API 35 + build-tools 35.0.0 + 34.0.0 via `sdkmanager` (accepts licenses)
  - Installs Gradle 8.11.1 directly to `/home/runner/gradle-8.11.1` (no wrapper jar)
  - Uses `gradlew` that delegates to `/home/runner/gradle-8.11.1/bin/gradle`
  - Runs: `./gradlew :app:assembleDebug --stacktrace --refresh-dependencies`
  - Verifies APK exists, non-empty, zip integrity, then uploads `velshop-debug-apk`
- **Never** add `continue-on-error: true` or hide failures.
- **Never** run local `./gradlew assembleDebug` or `./gradlew build` — they will fail with `libnative-platform.so` missing.
- Working build reference: Run #21, commit `e813c92`, artifact `velshop-debug-apk` (18.5 MB).

---

## 2. Version Matrix (Pinned — Do Not Change Without Explicit Approval)

| Component | Version | Notes |
|-----------|---------|-------|
| JDK | 17 (Temurin) | GitHub Actions `actions/setup-java@v5` |
| Android SDK | API 35 | `platforms;android-35` + `build-tools;35.0.0` + `build-tools;34.0.0` |
| Gradle | 8.11.1 | Installed directly to `/home/runner/gradle-8.11.1` |
| AGP | 8.7.2 | `com.android.application` plugin |
| Kotlin | 2.0.21 | `org.jetbrains.kotlin.android` plugin |
| Compose Compiler | 2.0.21 | Via `compose-bom:2024.10.01` |
| Compose BOM | 2024.10.01 | Single source of truth for Compose versions |

---

## 3. Known Compile Gotchas (Pre-Check Before Any Code Edit)

| Problem | Symptom | Fix / Avoidance |
|---------|---------|-----------------|
| `FilledButton` unresolved | `Unresolved reference 'FilledButton'` | Use `Button` instead (`material3` 1.3.0 removed `FilledButton`) |
| `ButtonDefaults.filledButtonColors` | Unresolved | Use `ButtonDefaults.buttonColors()` |
| `ButtonDefaults.outlinedButtonColors` with `borderColor` | Overload doesn't exist | Drop `borderColor`; use default outline |
| `BorderStroke` import | Unresolved / not in material3 | Remove; let `Surface`/`Card` use default border |
| `KeyboardOptions` / `KeyboardType` | Unresolved | Remove `keyboardOptions` param from `TextField`/`OutlinedTextField` calls; delete imports |
| `TextFieldDefaults.textFieldColors` / `outlinedTextFieldColors` | Unresolved / blocks compile | Remove `colors` arg entirely; use default |
| `TextButtonDefaults.textButtonColors()` | Unresolved | Use `TextButtonDefaults.textButtonColors()` only if truly needed; import it |
| `FilledButton` colors | `filledButtonColors()` missing | Use `Button` with `ButtonDefaults.buttonColors()` |
| `BadgeBox` / `Badge` | Unresolved / experimental | Use plain `Icon` + `Text`; no badge wrapper |
| `Chip` / `ChipDefaults` | Unresolved | Replace with `Surface + Text` |
| `LazyHorizontalGrid(columns = GridCells.Fixed(n))` | Wrong param | Use `rows = GridCells.Fixed(n)` (rows, not columns) |
| `Modifier.fillMaxHeight(56.dp)` | Crashes — takes `Float` fraction, not dp | Use `.height(56.dp)` instead |
| `PaddingValues(horizontal, vertical, bottom)` | Overload doesn't exist | Use `PaddingValues(start, top, end, bottom)` |
| `FontFamily.Default` | Recursive type error | Use `androidx.compose.ui.text.font.FontFamily.Default` explicitly |
| `MaterialTheme.velnox*` extension | Unresolved in composables | Use top-level `velnoxPrimary`, `velnoxSurface`, etc. from `.ai/Color.kt` (top-level vals, not extensions) |
| `filledButtonColors()` / `outlinedButtonColors()` with `borderColor` | Overload missing | Drop `borderColor`; use defaults |
| `TextButtonDefaults.textButtonColors()` | Must import explicitly | `import androidx.compose.material3.TextButtonDefaults` |
| `KeyboardOptions` / `KeyboardType` | Unresolved | Remove from all `TextField`/`OutlinedTextField` calls; delete imports |
| `TextFieldDefaults.textFieldColors()` | Blocks compile | Remove `colors` arg from `TextField`/`OutlinedTextField`; use defaults |
| `Chip` / `ChipDefaults` | Unresolved / experimental | Use `Surface + Text` |
| `BadgeBox` / `Badge` | Experimental / unresolved | Remove badge wrappers; plain `Icon` |
| `PaddingValues(horizontal, vertical, bottom)` | Overload doesn't exist | Use `PaddingValues(start, top, end, bottom)` |
| `Modifier.fillMaxHeight(dp)` | Takes fraction, not dp | Use `.height(dp)` instead |
| `LazyHorizontalGrid(columns = ...)` | Wrong param name | Use `rows = GridCells.Fixed(n)` |

---

## 4. Compose / Kotlin Conventions

- `@file:OptIn(ExperimentalMaterial3Api::class)` at top of **every** screen file using TopAppBar / TextFieldDefaults / TopAppBarDefaults.
- Import `androidx.compose.material3.ExperimentalMaterial3Api` in each screen file.
- All `velnox*` colors are **top-level vals** in `.ai/Color.kt` (not `MaterialTheme` extensions). Use bare `velnoxPrimary`, `velnoxSurface`, etc.
- No `androidx.window:window` (version 1.3.1 doesn't exist in Maven).
- No `androidx.compose.material3.FilledButton` — use `Button`.
- No `androidx.compose.material3.FilledButton` — use `Button`.
- Use `ButtonDefaults.buttonColors()` not `filledButtonColors()`.
- `OutlinedButton` — drop `borderColor`; use default outline.
- `OutlinedTextField` — remove `keyboardOptions`, drop `colors` arg entirely.
- `OutlinedTextField` / `TextField` — no `keyboardOptions`, no `colors` arg, no `TextFieldDefaults` colors.
- `Button` — `colors = ButtonDefaults.buttonColors(containerColor = velnoxTertiary, contentColor = velnoxOnTertiary)`.
- `OutlinedButton` — drop `borderColor`; use default.
- `@file:OptIn(ExperimentalMaterial3Api::class)` + `import androidx.compose.material3.ExperimentalMaterial3Api` in **every** screen file.

---

## 5. Resources & XML

- `strings.xml` — escape `&` as `&` (e.g., "Help & Support").
- `AndroidManifest.xml` — launcher icons: `@drawable/ic_launcher` / `@drawable/ic_launcher_round` (files live in `res/drawable/`, not `mipmap/`).
- `placeholder_product.xml` — `VectorDrawable` with `<path>` only; no `<rect>` with `android:width`/`height` attributes.

---

## 6. Git & Commit Discipline

- **Remote is truth** — sync before every edit (`git pull --ff-only`).
- Commit messages: imperative, one-line summary; why > what.
  - Good: "Fix OutlinedTextField signatures and invalid PaddingValues overload"
  - Bad: "Fix bugs"
- Never force-push to `main` unless recovering from a diverged state with explicit approval.
- If behind → `git pull --ff-only` **before** editing.
- If diverged → **STOP**, inspect, decide.

---

## 7. Testing & Verification

| Artifact | Verification Step | Success Criteria |
|----------|-------------------|------------------|
| APK | `./gradlew :app:assembleDebug --stacktrace` exits 0 | `BUILD SUCCESSFUL` in log |
| APK file | `ls -la app/build/outputs/apk/debug/app-debug.apk` | Exists, size > 0 |
| APK integrity | `unzip -t app-debug.apk` | `No errors detected` |
| Artifact | `actions/upload-artifact@v4` uploads `velshop-debug-apk` | Downloadable from run page |

**Never claim success without log proof.** "BUILD SUCCESSFUL" must be in the log.

---

## 8. Reporting (Mandatory)

- **After every task**, update `.ai/reports/LATEST.md`:
  - What changed (files, why)
  - Build result (log link, success/fail)
  - Remaining problems / next steps
  - Verification commands run
- `.ai/reports/LATEST.md` is the **current snapshot** (overwritten each task).
- `.ai/history/` — append a `YYYYMMDD_HHMMSS_topic.md` entry; never delete or rewrite old entries.
- **Never claim success without the log line** "BUILD SUCCESSFUL".

---

## 9. Project Structure (Canonical Map)

```
velshop-mobile/
├── .github/workflows/gradle-build.yml   # CI (source of truth for build)
├── .ai/                                  # AI collaboration layer (this folder)
│   ├── README.md
│   ├── AI_RULES.md
│   ├── AI_HANDOFF.md
│   ├── context/project-map.md
│   ├── build-apk/
│   │   ├── BUILD_APK_GUIDE.md
│   │   └── gradle-build.yml              # working workflow copy
│   ├── reports/LATEST.md
│   └── history/
├── app/
│   ├── build.gradle.kts                  # AGP 8.7.2, compose-bom 2024.10.01, Kotlin 2.0.21
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/velnox/velshop/
│   │   │   ├── MainActivity.kt
│   │   │   ├── VelShopApplication.kt
│   │   │   ├── navigation/VelShopNavHost.kt
│   │   │   ├── feature/                  # 12 screens
│   │   │   │   ├── home/HomeScreen.kt
│   │   │   │   ├── search/SearchScreen.kt
│   │   │   │   ├── categories/CategoriesScreen.kt
│   │   │   │   ├── product/ProductDetailScreen.kt
│   │   │   │   ├── product/ProductListingScreen.kt
│   │   │   │   ├── cart/CartScreen.kt
│   │   │   │   ├── checkout/CheckoutScreen.kt
│   │   │   │   ├── orders/OrdersScreen.kt
│   │   │   │   ├── wishlist/WishlistScreen.kt
│   │   │   │   ├── profile/ProfileScreen.kt
│   │   │   │   ├── settings/SettingsScreen.kt
│   │   │   │   └── auth/AuthScreen.kt
│   │   │   └── ui/theme/                 # Color.kt, Shape.kt, Theme.kt
│   │   └── res/                          # drawable, values, xml
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── gradle.properties                 # android.useAndroidX=true
├── settings.gradle.kts
├── gradle/wrapper/gradle-wrapper.properties  # 8.11.1
├── gradlew                                 # delegates to GRADLE_HOME
├── gradle.properties                       # android.useAndroidX=true
├── .github/workflows/gradle-build.yml
└── .ai/
```

---

## 10. Handoff Protocol (Summary)

`.ai/AI_HANDOFF.md` is the **current snapshot** (not a log): what works, what's broken, decisions, next steps.
- Use statuses: **VERIFIED / UNVERIFIED / FAILED / BLOCKED / SKIPPED / NOT TESTED**
- Never fabricate history. Supersede in `.ai/AI_HANDOFF.md`; keep original verbatim.
- Secrets: never write tokens/keys in `.ai/`. Record *presence* only ("test-mode key present").
- Next agent startup order:
  1. `.ai/office/QUICK_START.md` (if exists) → 2. `.ai/AI_HANDOFF.md` → 3. `.ai/context/project-map.md` → 4. Source code.

---

## 11. Secrets

`.ai/` is committed and public. **Never** write API keys, tokens, passwords, connection strings, cookies, session values, or customer data into `.ai/`. Record *presence and shape* only ("test-mode key present", "webhook secret configured"), never values.

---

## 12. Summary — Before You Edit, Say This

> "I have synced with `origin/main`. I understand the 21 compile gotchas. I will not build locally. I will update `.ai/reports/LATEST.md` when done."
