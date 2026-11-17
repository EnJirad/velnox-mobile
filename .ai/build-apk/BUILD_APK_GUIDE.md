# BUILD_APK_GUIDE.md — VelShop Mobile APK Build Method

> **The reusable, step-by-step recipe to build the VelShop Debug APK on GitHub Actions.**
> This is the exact method that produced `velshop-debug-apk` (18.5 MB) on Run #21, commit `e813c92`.
> **No local build** — GitHub Actions is the only build environment.

---

## Prerequisites (Do Not Change Without Explicit Approval)

| Component | Version | Source |
|-----------|---------|--------|
| JDK | 17 (Temurin) | `actions/setup-java@v5` |
| Android SDK | API 35 | `platforms;android-35` + `build-tools;35.0.0` + `build-tools;34.0.0` |
| Gradle | 8.11.1 | Downloaded directly from services.gradle.org |
| AGP | 8.7.2 | `com.android.application` plugin in `app/build.gradle.kts` |
| Kotlin | 2.0.21 | `org.jetbrains.kotlin.android` plugin |
| Compose Compiler | 2.0.21 | Via `compose-bom:2024.10.01` |
| Compose BOM | 2024.10.01 | Single source of truth |

---

## Complete Working Workflow (Copy-Paste Ready)

Save as `.github/workflows/gradle-build.yml`:

```yaml
name: Build VelShop APK

on:
  push:
    branches: [ main ]

permissions:
  contents: read
  pages: write
  id-token: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v5
        with:
          distribution: 'temurin'
          java-version: 17

      - name: Set up Android SDK
        run: |
          mkdir -p /home/runner/android-sdk
          cd /home/runner/android-sdk
          wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
          unzip -q cmdline-tools.zip
          mkdir -p cmdline-tools/latest
          mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true
          export ANDROID_SDK_ROOT=/home/runner/android-sdk
          export ANDROID_HOME=/home/runner/android-sdk
          export PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin
          yes | sdkmanager --licenses > /dev/null 2>&1
          sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "build-tools;34.0.0" > /dev/null 2>&1
          yes | sdkmanager --licenses > /dev/null 2>&1
          echo "ANDROID_SDK_ROOT=/home/runner/android-sdk" >> $GITHUB_ENV
          echo "ANDROID_HOME=/home/runner/android-sdk" >> $GITHUB_ENV

      - name: Install Gradle 8.11.1
        run: |
          wget -q https://services.gradle.org/distributions/gradle-8.11.1-bin.zip -O gradle-bin.zip
          unzip -q gradle-bin.zip -d /home/runner
          echo "GRADLE_HOME=/home/runner/gradle-8.11.1" >> $GITHUB_ENV
          echo "/home/runner/gradle-8.11.1/bin" >> $GITHUB_PATH

      - name: Make Gradle wrapper executable
        run: chmod +x gradlew

      - name: Build debug APK
        run: ./gradlew :app:assembleDebug --stacktrace --refresh-dependencies
        env:
          ANDROID_SDK_ROOT: /home/runner/android-sdk
          ANDROID_HOME: /home/runner/android-sdk
          GRADLE_HOME: /home/runner/gradle-8.11.1

      - name: Verify APK exists and is non-empty
        run: |
          APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
          if [ ! -f "$APK_PATH" ]; then
            echo "ERROR: APK not found at $APK_PATH"
            exit 1
          fi
          APK_SIZE=$(stat -c%s "$APK_PATH")
          if [ "$APK_SIZE" -eq 0 ]; then
            echo "ERROR: APK is empty"
            exit 1
          fi
          echo "APK size: $APK_SIZE bytes"
          echo "APK_PATH=$APK_PATH" >> $GITHUB_ENV

      - name: Verify APK archive integrity
        run: |
          unzip -t "${{ env.APK_PATH }}" > /dev/null

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: velshop-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30
```

---

## Key Hardening (What Makes It Work)

| Gotcha | Solution | Why |
|--------|----------|-----|
| `sdkmanager --licenses` needs interactive input | `yes \| sdkmanager --licenses` (twice) | Non-interactive acceptance |
| YAML `env:` doesn't expand `$HOME` | Hardcode `/home/runner/...` | YAML env doesn't expand shell vars |
| Gradle wrapper jar missing full distro | Install Gradle 8.11.1 directly to `/home/runner/gradle-8.11.1` | Wrapper jar on this device lacks `libnative-platform.so` for aarch64 |
| Build-tools 35.0.0 + 34.0.0 | Install both (`build-tools;35.0.0` + `build-tools;34.0.0`) | AGP 8.7.2 requires 34+35 |
| License acceptance | Run `yes | sdkmanager --licenses` **twice** | Once before install, once after |
| No local Gradle cache | Remove `actions/cache` step | Stale caches caused dependency resolution failures |
| No local build | Remove local `./gradlew` attempts | `libnative-platform.so` missing on aarch64 |
| `./gradlew` on this device | Fails with `libnative-platform.so` | Use GitHub Actions only |
| `gradle-build.yml` uses `./gradlew` not `gradle` | Wrapper script delegates to `$GRADLE_HOME/bin/gradle` | Installed Gradle 8.11.1 on PATH |

---

## Exact Working Workflow (Current as of Run #21)

```yaml
# See .github/workflows/gradle-build.yml in repo root
# This is the exact file that produced Run #21 SUCCESS
```

Copy from `.github/workflows/gradle-build.yml` — it's the canonical working version.

---

## Step-by-Step Execution (What Happens in CI)

| Step | Command | Expected Output |
|------|---------|-----------------|
| 1. Checkout | `actions/checkout@v4` | Repository cloned |
| 2. JDK 17 | `actions/setup-java@v5` | `java -version` → 17 |
| 3. Android SDK | Manual `sdkmanager` | `sdkmanager --list` shows 35, 34 |
| 4. Licenses | `yes \| sdkmanager --licenses` (×2) | All licenses accepted |
| 5. Gradle 8.11.1 | `wget .../gradle-8.11.1-bin.zip` | `/home/runner/gradle-8.11.1/bin/gradle --version` → 8.11.1 |
| 5. Cache | **Removed** (stale cache caused issues) | — |
| 6. `chmod +x gradlew` | Wrapper executable | `./gradlew --version` → delegates to 8.11.1 |
| 7. Build | `./gradlew :app:assembleDebug --stacktrace --refresh-dependencies` | **`BUILD SUCCESSFUL in 2m 31s`** |
| 8. Verify APK | `stat -c%s app-debug.apk` | Size > 0 (e.g., 18,495,781) |
| 9. Verify integrity | `unzip -t app-debug.apk` | `No errors detected` |
| 9. Upload | `actions/upload-artifact@v4` | `velshop-debug-apk` (30-day retention) |

---

## Verification Checklist (Post-Run)

| Check | Command | Expected |
|-------|---------|----------|
| Build success | Log contains `BUILD SUCCESSFUL` | ✅ |
| APK exists | `ls -la app/build/outputs/apk/debug/app-debug.apk` | File exists |
| APK non-empty | `stat -c%s app-debug.apk` | Size > 0 (e.g., 18,495,781) |
| APK integrity | `unzip -t app-debug.apk` | `No errors detected` |
| Artifact uploaded | GitHub Actions artifact list | `velshop-debug-apk` present |

---

## Troubleshooting Quick Reference

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| `libnative-platform.so` missing | Local build attempt | **Don't build locally** — use GitHub Actions only |
| `Could not find method filledButtonColors()` | Material3 1.3 removed it | Use `ButtonDefaults.buttonColors()` |
| `FilledButton` unresolved | Removed in Material3 1.3 | Use `Button` + `ButtonDefaults.buttonColors()` |
| `ButtonDefaults.filledButtonColors` unresolved | API removed | Use `ButtonDefaults.buttonColors()` |
| `outlinedButtonColors(borderColor=...)` | Overload doesn't exist | Drop `borderColor`; use defaults |
| `KeyboardOptions` / `KeyboardType` unresolved | Removed in material3 1.3 | Remove `keyboardOptions` from all TextFields |
| `TextFieldDefaults.textFieldColors()` unresolved | Colors block breaks compile | Remove `colors` arg from TextField/OutlinedTextField |
| `TextButtonDefaults.textButtonColors()` unresolved | Import missing | `import androidx.compose.material3.TextButtonDefaults` |
| `FontFamily.Default` recursion | Type inference loop | Use `androidx.compose.ui.text.font.FontFamily.Default` explicitly |
| `LazyHorizontalGrid(columns=...)` | Param is `rows`, not `columns` | Use `rows = GridCells.Fixed(n)` |
| `Modifier.fillMaxHeight(56.dp)` | Takes fraction, not dp | Use `.height(56.dp)` |
| `PaddingValues(horizontal, vertical, bottom)` | Overload doesn't exist | Use `PaddingValues(start, top, end, bottom)` |
| `MaterialTheme.velnox*` unresolved in composable | Extension not in scope | Use top-level `velnoxPrimary` etc. from `Color.kt` |
| `FontFamily.Default` recursion | Type inference loop | Use `androidx.compose.ui.text.font.FontFamily.Default` |
| `MaterialTheme.velnox*` unresolved | Extension not in scope | Use top-level vals from `Color.kt` |
| `android.useAndroidX=true` missing | `gradle.properties` missing | Create `gradle.properties` with `android.useAndroidX=true` |
| `androidx.window:window:1.3.1` not found | Version doesn't exist | Remove `androidx.window:window` dep |
| `libnative-platform.so` missing | Local Gradle build | **Don't build locally** — use GitHub Actions |

---

## Quick Rebuild Checklist (Copy-Paste)

```bash
# 1. Ensure remote is in sync
git fetch origin
git rev-parse HEAD
git rev-parse origin/main
# If different → git pull --ff-only

# 2. Push to trigger CI
git push origin main

# 3. Watch GitHub Actions
# https://github.com/EnJirad/velshop-mobile/actions

# 4. Wait for "BUILD SUCCESSFUL" in logs
# 5. Verify artifact: velshop-debug-apk (18.5 MB)
# 6. Download from Actions → Artifacts → velshop-debug-apk
```

---

## Verification Checklist (Post-Run)

- [ ] `BUILD SUCCESSFUL` in logs
- [ ] APK exists: `app/build/outputs/apk/debug/app-debug.apk`
- [ ] APK size > 0 (e.g., 18,495,781 bytes)
- [ ] `unzip -t app-debug.apk` → `No errors detected`
- [ ] Artifact `velshop-debug-apk` uploaded (30-day retention)
- [ ] No `continue-on-error: true` in workflow
- [ ] No local build attempted
- [ ] Commit SHA matches GitHub run HEAD

---

## File Reference

- **Working workflow**: `.github/workflows/gradle-build.yml`
- **Build guide (this file)**: `.ai/build-apk/BUILD_APK_GUIDE.md`
- **APK artifact**: `velshop-debug-apk` (Actions → Artifacts)
- **Run #21 log**: https://github.com/EnJirad/velshop-mobile/actions/runs/43776495591

---

## Maintenance Notes

- **Do not** upgrade AGP / Kotlin / Compose without full regression test.
- **Do not** add `continue-on-error: true` to any step.
- **Do not** add Gradle cache step back (caused stale dependency resolution).
- **Do not** use `gradle` command directly — use `./gradlew` (delegates to installed Gradle).
- **Do not** attempt local `./gradlew assembleDebug` on this device (aarch64 `libnative-platform.so` missing).
- **Do not** add `build-tools;36.0.0` until AGP supports it.
- **Do** keep `build-tools;34.0.0` + `build-tools;35.0.0` (AGP 8.7.2 requirement).
- **Do** run `yes | sdkmanager --licenses` **twice** (pre + post install).
- **Do** use `/home/runner/...` absolute paths in YAML `env:` (no `$HOME` expansion).

---

## Version History

| Date | Commit | Changes |
|------|--------|---------|
| 2026-11-15 | `e813c92` | Fixed OutlinedTextField signatures, PaddingValues overload, VelnoxTypography recursion, AuthScreen brand icons, @file:OptIn on all screens, PaddingValues overload fix, VDivider scope, Theme luminance, top-level velnox vals, Product data class clash, LazyHorizontalGrid rows param, fillMaxHeight → height, SettingsRow Card(onClick) nullability, drop androidx.window, drop KSP plugin, fix gradle.properties, workflow absolute paths, gradlew delegates to installed Gradle, no cache, --refresh-dependencies |

---

## Quick Copy-Paste (Minimal Working Config)

```yaml
# .github/workflows/gradle-build.yml (minimal working)
name: Build VelShop APK
on:
  push:
    branches: [ main ]
permissions:
  contents: read
  pages: write
  id-token: write
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v5
        with: { distribution: 'temurin', java-version: 17 }
      - name: Set up Android SDK
        run: |
          mkdir -p /home/runner/android-sdk
          cd /home/runner/android-sdk
          wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
          unzip -q cmdline-tools.zip
          mkdir -p cmdline-tools/latest
          mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true
          export ANDROID_SDK_ROOT=/home/runner/android-sdk
          export ANDROID_HOME=/home/runner/android-sdk
          export PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin
          yes | sdkmanager --licenses > /dev/null 2>&1
          sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "build-tools;34.0.0" > /dev/null 2>&1
          yes | sdkmanager --licenses > /dev/null 2>&1
          echo "ANDROID_SDK_ROOT=/home/runner/android-sdk" >> $GITHUB_ENV
          echo "ANDROID_HOME=/home/runner/android-sdk" >> $GITHUB_ENV
      - name: Install Gradle 8.11.1
        run: |
          wget -q https://services.gradle.org/distributions/gradle-8.11.1-bin.zip -O gradle-bin.zip
          unzip -q gradle-bin.zip -d /home/runner
          echo "GRADLE_HOME=/home/runner/gradle-8.11.1" >> $GITHUB_ENV
          echo "/home/runner/gradle-8.11.1/bin" >> $GITHUB_PATH
      - name: Make Gradle wrapper executable
        run: chmod +x gradlew
      - name: Build debug APK
        run: ./gradlew :app:assembleDebug --stacktrace --refresh-dependencies
        env:
          ANDROID_SDK_ROOT: /home/runner/android-sdk
          ANDROID_HOME: /home/runner/android-sdk
          GRADLE_HOME: /home/runner/gradle-8.11.1
      - name: Verify APK
        run: |
          APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
          if [ ! -f "$APK_PATH" ]; then echo "ERROR: APK not found"; exit 1; fi
          APK_SIZE=$(stat -c%s "$APK_PATH")
          if [ "$APK_SIZE" -eq 0 ]; then echo "ERROR: APK empty"; exit 1; fi
          echo "APK size: $APK_SIZE bytes"
          echo "APK_PATH=$APK_PATH" >> $GITHUB_ENV
      - name: Verify APK integrity
        run: unzip -t "${{ env.APK_PATH }}" > /dev/null
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: velshop-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30
```

---

## Support

- **GitHub Actions logs**: https://github.com/EnJirad/velshop-mobile/actions
- **Artifact download**: Actions → `velshop-debug-apk` (30-day retention)
- **Issues**: File in repo if build breaks after changes

---

## TL;DR (One-Liner)

> **Push to `main` → GitHub Actions installs JDK17 + Android SDK 35 + Gradle 8.11.1 → runs `./gradlew :app:assembleDebug --stacktrace --refresh-dependencies` → uploads `velshop-debug-apk` (18.5 MB). No local build.**
