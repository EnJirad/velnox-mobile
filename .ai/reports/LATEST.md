# LATEST.md — Velnox Mobile Latest Change Report

> **Current snapshot (overwritten each task).** Archives in `.ai/history/`.
> Repository: `EnJirad/velnox-mobile`

---

## Task: Create the Velnox Android app and ship the login APK

**Date:** 2026-11-17
**Commit:** `45e60bd` (app) + workflow fix
**GitHub Actions Run:** 44098361945
**Status:** **VERIFIED — BUILD SUCCESSFUL**

---

## What Changed

| Area | Files | Description |
|------|-------|-------------|
| Gradle project | `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` | AGP 8.7.2, Kotlin 2.0.21, Compose compiler plugin, `rootProject.name = "Velnox"` |
| App module | `app/build.gradle.kts`, `app/proguard-rules.pro` | namespace / applicationId `com.velnox.app`, compileSdk 35, minSdk 26, targetSdk 35, Compose enabled, BOM 2024.10.01 |
| Manifest | `app/src/main/AndroidManifest.xml` | Single exported `MainActivity`, `Theme.Velnox`, packaging-style icon references, no permissions |
| Entry point | `app/src/main/java/com/velnox/app/MainActivity.kt` | `enableEdgeToEdge()`, `VelnoxTheme { LoginScreen() }` |
| Theme | `ui/theme/Color.kt`, `Shape.kt`, `Type.kt`, `Theme.kt` | Design-system tokens: §2 colours, §7.1 squircle radii 7/10/14/18, §8 type scale 11-30sp, light + dark schemes |
| Login screen | `feature/auth/LoginScreen.kt` | Email + password with inline validation, show/hide password, remember-me, dark primary submit with loading state, social row, sign-up row, notice banner, previews |
| Resources | `res/values/strings.xml` (Thai), `res/values-en/strings.xml`, `values/colors.xml`, `values/themes.xml`, `values-night/themes.xml` | All user-facing copy in string resources; no hardcoded text in composables |
| Icon | `res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `mipmap-anydpi-v26/*` | Adaptive launcher icon: dark tile, emerald V |
| CI | `.github/workflows/gradle-build.yml` | JDK 17, SDK 35 + build-tools 35.0.0/34.0.0, Gradle 8.11.1 installed directly, `./gradlew :app:assembleDebug`, APK verification + artifact upload |
| Docs | `README.md`, `.gitignore` | Stack, build route, layout, tokens, screen behaviour |
| Wrapper | `gradlew`, `gradlew.bat` | Delegating wrapper scripts; no `gradle-wrapper.jar` committed |

---

## Verified

| Check | Result |
|-------|--------|
| Workflow parses | **VERIFIED** — first run failed on a YAML plain-scalar colon; converted to block scalar |
| `assembleDebug` on CI | **VERIFIED** — `BUILD SUCCESSFUL in 2m 30s` |
| APK produced | **VERIFIED** — `app-debug.apk`, 9,527,422 bytes |
| APK integrity | **VERIFIED** — `unzip -t` OK |
| Artifact uploaded | **VERIFIED** — `velnox-debug-apk`, 9,097,188 bytes, not expired |
| Package identity | **VERIFIED** — `aapt2 dump badging`: `com.velnox.app`, versionCode 1, versionName 1.0.0 |
| SDK levels | **VERIFIED** — minSdk 26, targetSdk 35, compileSdk 35 |
| App label | **VERIFIED** — `Velnox` |
| Launcher activity | **VERIFIED** — `com.velnox.app.MainActivity` |
| Thai strings packaged | **VERIFIED** — `aapt2 dump strings` returns `ยินดีต้อนรับกลับ`, `เข้าสู่ระบบ` |
| On-device render | **NOT TESTED** — no privileged install shell available in this session |

---

## Notes for Next Agent

- **Do not** commit `gradle-wrapper.jar`. `gradlew` delegates to `GRADLE_HOME` (CI installs 8.11.1).
- **Do not** build locally on aarch64 with the packaged Gradle; CI is the build environment.
- **Do not** add `continue-on-error` to any step.
- Keep `build-tools;34.0.0` + `35.0.0` (AGP 8.7.2 requirement), and accept licences twice.
- Keep `@file:OptIn(ExperimentalMaterial3Api::class)` off unless a screen actually uses experimental M3 APIs.
- Keep the squircle tokens (`SquircleXs/Sm/Squircle/SquircleLg`); `rounded-full` only for real circles.
- Keep copy in `values/strings.xml` (Thai) + `values-en/strings.xml` (English).
- YAML pitfall found this session: a `run:` step written as a plain scalar that contains `word: word` inside quotes breaks parsing. Use `run: |`.

---

## Next

1. Real authentication API + token storage
2. Registration and password-reset screens
3. Navigation host for the signed-in area
4. UI tests for validation states
5. Dark-theme pass over the login screen (palette exists, screen not audited)
