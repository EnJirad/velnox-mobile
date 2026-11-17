# Velnox — Mobile (Android)

Android app for **Velnox**. First slice: the login screen, built strictly on
the Velnox Design System v2.2 (`.ai/VELNOX_DESIGN_THEME.md`).

## Stack (pinned)

| Component | Version |
|---|---|
| JDK | 17 (Temurin) |
| Android SDK | API 35, build-tools 35.0.0 + 34.0.0 |
| Gradle | 8.11.1 |
| AGP | 8.7.2 |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.10.01 |
| minSdk / targetSdk | 26 / 35 |

Application id `com.velnox.app`.

## Build the APK (GitHub Actions only)

The APK is built on `ubuntu-latest` by `.github/workflows/gradle-build.yml`.
Push to `main` (or run the workflow manually) and download the artifact
**`velnox-debug-apk`** from the run page.

Do not build locally on aarch64: the Kotlin/AGP toolchain needs a desktop JDK
and the wrapper jar approach fails there. CI is the build environment.

## Layout

```
app/src/main/java/com/velnox/app/
├── MainActivity.kt                 # edge-to-edge, VelnoxTheme { LoginScreen() }
├── feature/auth/LoginScreen.kt     # the only screen in this slice
└── ui/theme/                       # Color.kt, Shape.kt, Type.kt, Theme.kt
app/src/main/res/
├── values/strings.xml              # Thai (default)
├── values-en/strings.xml           # English
└── mipmap-anydpi-v26/              # launcher icon
```

## Design tokens used by the login screen

- Background `#f8fafc`, card `#ffffff`, text `#0f172a` (§2.1)
- Primary action dark `#0f172a`, brand emerald `#10b981` (§2.2, §2.3)
- Borders `#e2e8f0` (slate-200), error `#dc2626` (§2.4)
- Squircle radii 7 / 10 / 14 / 18 dp (§7.1)
- Type scale 11–30sp, 14sp body default (§8)

## State handled by the screen

- Empty / malformed email and short password show inline errors under the field
- Show/hide password toggle
- Remember-me toggle
- Submit runs a short simulated request (900 ms) then shows a success notice
- Social buttons, forgot password and sign-up report "coming in a later version"

All user-facing text lives in string resources (Thai + English), with no
hardcoded copy in composables.

## Next

- Real authentication API
- Registration and password reset screens
- Navigation host for the signed-in area
