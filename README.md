# Velnox — Mobile (Android)

Android app for **Velnox**. Phase 1 ships the Home and Login UI on top of the
Velnox Design System v2.2 (`.ai/VELNOX_DESIGN_THEME.md`). Frontend only: no
backend, no authentication, no database.

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

Push to `main` (or run the workflow manually). The workflow builds
`app-debug.apk` and uploads the artifact **`velnox-debug-apk`**.

Do not build locally on aarch64; CI is the build environment.

## Screens

| Route | Screen |
|-------|--------|
| `login` | Login — validation, show/hide password, remember me, demo submit |
| `home` | Home — header, search, categories, hero, product grid, bottom nav |

Cold start lands on Login. Completing the demo flow opens Home; Back returns to
Login. Placeholder destinations open a styled dialog.

## Layout

```
app/src/main/java/com/velnox/app/
├── MainActivity.kt                 # edge-to-edge, VelnoxApp()
├── navigation/
│   ├── VelnoxApp.kt                # typed destinations, demo auth gate
│   └── VelnoxInfoDialog.kt         # placeholder destination dialog
├── feature/auth/LoginScreen.kt
├── feature/home/
│   ├── HomeScreen.kt               # feed, responsive grid, bottom nav
│   └── HomeCatalog.kt              # UI preview data only
└── ui/
    ├── components/VelnoxComponents.kt
    ├── icons/VelnoxIcons.kt        # Lucide-derived ImageVectors
    └── theme/                      # Color, Shape, Type, Theme
app/src/main/res/
├── values/strings.xml              # Thai (default)
└── values-en/strings.xml           # English
```

## Design rules applied

- Surfaces `#f8fafc` / `#ffffff`, text `#0f172a`, brand emerald `#10b981`, borders slate-200
- Dark primary action, emerald accent and success, red destructive, amber warning
- Squircle radii 7 / 10 / 14 / 18 dp; circles only for dots, spinners, avatars
- Type scale 11–30 sp, 14 sp body default
- Icons are Lucide vector geometry — never emoji, never an icon font

## Responsive behaviour

- Feed width capped at 560 dp and centred; bottom bar spans the window
- Product grid: `<400dp` → 2 columns, `<600dp` → 3, otherwise 4
- Login caps its form at 480 dp and tightens gutters below 360 dp

## Preview data

`HomeCatalog` exists only to give the layout realistic proportions: product
names, store names and qualitative tags such as "New". There are no prices,
stock levels, orders or payment values anywhere in the app.

## Next (Phase 2)

- Real authentication API and persisted session
- Resolve `AppRouting` from auth state instead of the fixed start destination
- Search and category screens, product detail
