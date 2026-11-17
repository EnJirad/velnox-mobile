# VelShop Mobile — Project Map

> Canonical repo layout. Keep in sync with actual source.

---

## Root

```
velshop-mobile/
├── .github/workflows/gradle-build.yml   # CI (source of truth for build)
├── .ai/                                  # AI collaboration layer
├── app/                                  # Main Android module
├── settings.gradle.kts
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew                               # delegates to GRADLE_HOME
├── gradle.properties
└── .ai/
```

---

## App Module (`app/`)

```
app/
├── build.gradle.kts                  # AGP 8.7.2, compose-bom 2024.10.01, Kotlin 2.0.21
├── proguard-rules.pro
├── gradle.properties                 # android.useAndroidX=true, org.gradle.jvmargs
├── src/main/
│   ├── AndroidManifest.xml           # package=com.velnox.velshop, minSdk=26, targetSdk=35
│   ├── java/com/velnox/velshop/
│   │   ├── MainActivity.kt           # ComponentActivity, setContent(VelShopTheme + VelShopNavHost)
│   │   ├── VelShopApplication.kt     # Application subclass (empty)
│   │   ├── navigation/
│   │   │   └── VelShopNavHost.kt     # rememberNavController + 15 routes
│   │   ├── feature/
│   │   │   ├── auth/AuthScreen.kt    # Login/Register, social (AccountCircle/Email)
│   │   │   ├── home/HomeScreen.kt    # TopAppBar, SearchBar, CategoryStrip, ProductRail ×2, NavigationBar
│   │   │   ├── search/SearchScreen.kt # TopAppBar TextField, results grid, recent + category chips
│   │   │   ├── categories/CategoriesScreen.kt # TopAppBar, SubCategory grid, product grid
│   │   │   ├── product/
│   │   │   │   ├── ProductDetailScreen.kt  # Gallery, qty, Add to Cart / Buy Now
│   │   │   │   └── ProductListingScreen.kt # Sort/filter bar, 2-col grid
│   │   │   ├── cart/CartScreen.kt    # Items, qty steppers, summary, checkout btn
│   │   │   ├── checkout/CheckoutScreen.kt # Address form, shipping, payment, summary, place order
│   │   │   ├── orders/
│   │   │   │   ├── OrdersScreen.kt   # List with status, pull-to-load, View Details
│   │   │   │   └── OrderDetailScreen.kt # Items, address, payment, tracking
│   │   │   ├── wishlist/WishlistScreen.kt # Items, remove, add-to-cart
│   │   │   ├── profile/ProfileScreen.kt # Not signed in / signed in, orders/settings links, sign out dialog
│   │   │   ├── settings/SettingsScreen.kt # Account, preferences, support, sign out
│   │   │   └── auth/AuthScreen.kt    # Login / Register, OutlinedTextField1, social btns, switch mode
│   │   ├── navigation/VelShopNavHost.kt
│   │   └── ui/theme/
│   │       ├── Theme.kt              # VelShopTheme(MaterialTheme wrapper, edge-to-edge)
│   │       ├── Color.kt              # Top-level velnox* vals (light palette)
│   │       └── Shape.kt              # Material3 Shapes() wrapper
│   ├── res/
│   │   ├── values/
│   │   │   ├── strings.xml           # All strings (escape & as &)
│   │   │   ├── colors.xml            # Full palette (light + dark overrides)
│   │   │   └── themes.xml            # Theme.VelShop parent = android:Theme.Material.Light.NoActionBar
│   │   ├── drawable/
│   │   │   ├── ic_launcher.xml
│   │   │   ├── ic_launcher_background.xml
│   │   │   ├── ic_launcher_foreground.xml
│   │   │   ├── ic_launcher_round.xml
│   │   │   └── placeholder_product.xml
│   │   └── xml/
│   │       ├── backup_rules.xml
│   │       └── data_extraction_rules.xml
│   ├── build.gradle.kts              # AGP 8.7.2, compose-bom 2024.10.01, Kotlin 2.0.21
│   ├── proguard-rules.pro
│   └── gradle.properties             # android.useAndroidX=true, org.gradle.jvmargs=-Xmx2048m
├── settings.gradle.kts
├── gradle/wrapper/gradle-wrapper.properties  # distributionUrl=gradle-8.11.1-all.zip
├── gradlew                               # delegates to GRADLE_HOME
├── gradle.properties                     # android.useAndroidX=true, org.gradle.jvmargs
├── .github/workflows/gradle-build.yml
└── .ai/
```

---

## Key Files (Purpose + Hot Paths)

| File | Purpose | Why It Matters |
|------|---------|----------------|
| `.github/workflows/gradle-build.yml` | **CI source of truth** | Only place build happens |
| `app/build.gradle.kts` | Dependency & plugin versions | Pin matrix here |
| `app/src/main/java/.../MainActivity.kt` | Entry point | Sets Compose theme + nav host |
| `app/src/main/java/.../navigation/VelShopNavHost.kt` | All 15 routes | Single source of navigation |
| `app/src/main/java/.../feature/*/Screen.kt` | 12 screens | Each self-contained; `@file:OptIn` |
| `app/src/main/java/.../ui/theme/Color.kt` | Top-level `velnox*` vals | All screens import these |
| `app/src/main/java/.../designsystem/VelnoxColors.kt` | Palette (light/dark) | Single source of color truth |
| `app/src/main/java/.../designsystem/VelnoxTypography.kt` | FontFamily + Typography | Recursion fix: `FontFamily.Default` explicit |
| `app/src/main/java/.../ui/theme/Theme.kt` | `VelShopTheme` + edge-to-edge | System bar colors from scheme |
| `.github/workflows/gradle-build.yml` | CI build | Only build env; no local build |
| `gradle.properties` | `android.useAndroidX=true` | Required for AndroidX |
| `gradlew` | Delegates to `$GRADLE_HOME` | No wrapper jar needed |

---

## Navigation Map (Routes)

| Route | Screen | Key Args |
|-------|--------|----------|
| `home` | HomeScreen | — |
| `search` | SearchScreen | — |
| `category/{categoryId}` | CategoriesScreen | `categoryId` |
| `products` | ProductListingScreen | — |
| `product/{productId}` | ProductDetailScreen | `productId` |
| `cart` | CartScreen | — |
| `checkout` | CheckoutScreen | — |
| `orders` | OrdersScreen | — |
| `order/{orderId}` | OrderDetailScreen | `orderId` |
| `wishlist` | WishlistScreen | — |
| `profile` | ProfileScreen | — |
| `settings` | SettingsScreen | — |
| `auth` | AuthScreen | — |

---

## Navigation Conventions

- All routes defined in `VelShopNavHost.kt` (single source).
- Arguments via `navArgument("id") { type = NavType.StringType }`.
- Navigation via `navController.navigate("route/$param")`.
- Back stack: `popUpTo("home") { inclusive = false }` for order placement.
- Bottom nav: `NavigationBar` (5 tabs: Home, Market, Orders, Cart, Profile).
- TopAppBar on each screen with back nav + actions.

---

## Color Palette (VelnoxColors.kt — Light)

| Token | Hex | Use |
|-------|-----|-----|
| `velnoxPrimary` | `#1E293B` | Primary actions, top bar, text primary |
| `velnoxPrimaryVariant` | `#0F172A` | Deep primary |
| `velnoxPrimaryContainer` | `#E2E8F0` | Light primary bg |
| `velnoxSecondary` | `#475569` | Secondary actions |
| `velnoxTertiary` | `#00BFA6` | **Accent (mint)** — CTAs, selected, badges |
| `velnoxTertiaryVariant` | `#009D8A` | Darker accent |
| `velnoxTertiaryContainer` | `#CCFBF5` | Light accent bg |
| `velnoxSuccess` | `#16A34A` | Positive (in stock, delivered) |
| `velnoxWarning` | `#F59E0B` | Warnings (low stock, processing) |
| `velnoxError` | `#DC2626` | Errors, cancelled, out of stock |
| `velnoxSurface` | `#FFFFFF` | Card/surface bg |
| `velnoxSurfaceVariant` | `#F1F5F9` | Secondary surface |
| `velnoxBackground` | `#FAFAFA` | Screen bg |
| `velnoxOnPrimary` | `#FFFFFF` | Text on primary |
| `velnoxOnSurface` | `#0F172A` | Primary text |
| `velnoxOnSurfaceVariant` | `#475569` | Secondary text |
| `velnoxTextPrimary` | `#0F172A` | Body text |
| `velnoxTextSecondary` | `#475569` | Caption text |
| `velnoxTextTertiary` | `#94A3B8` | Muted text |
| `velnoxBorder` | `#E2E8F0` | Dividers, outlines |
| `velnoxBorderStrong` | `#CBD5E1` | Stronger outlines |

Dark palette mirrors via `luminance()` check in `Theme.kt` (no explicit dark theme file).

---

## Shape System (VelnoxShapes.kt)

| Token | Value | Use |
|-------|-------|-----|
| `Badge` / `Chip` | `7.dp` | Badges, chips, small labels |
| `Small` / `SearchPill` / `SmallButton` | `10.dp` | Small controls, search fields |
| `Medium` / `Button` / `IconButton` / `Input` | `14.dp` | Default controls |
| `Large` / `Card` / `Sheet` / `Dialog` / `BottomSheet` | `18.dp` | Cards, sheets, media frames |
| `Circle` | `50.dp` | Avatars, status dots |
| `NavigationBar` / `ModalBottomSheet` | `top 18.dp` | Bottom nav / modal sheets |

---

## Dependency Matrix (Pinned)

| Group | Artifact | Version | Notes |
|-------|----------|---------|-------|
| Android | `androidx.core:core-ktx` | 1.15.0 | |
| Android | `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.7 | |
| Android | `androidx.activity:activity-compose` | 1.10.1 | |
| Android | `androidx.window:window` | 1.3.1 | Version 1.3.1 exists |
| Compose BOM | `androidx.compose:compose-bom` | 2024.10.01 | Single source |
| Compose | `androidx.compose.ui:ui` | (from BOM) | |
| Compose | `androidx.compose.ui:ui-graphics` | (from BOM) | |
| Compose | `androidx.compose.ui:ui-tooling-preview` | (from BOM) | |
| Material3 | `androidx.compose.material3:material3` | (from BOM) | |
| Material3 | `androidx.compose.material3:material3-window-size-class` | (from BOM) | |
| Compose | `androidx.compose.runtime:runtime-livedata` | (from BOM) | |
| Compose | `androidx.compose.runtime:runtime-rxjava3` | (from BOM) | |
| Navigation | `androidx.navigation:navigation-compose` | 2.8.7 | |
| ViewModel | `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.7 | |
| ViewModel | `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.8.7 | |
| Serialization | `kotlinx-serialization-json` | 1.7.1 | |
| Image | `io.coil-kt:coil-compose` | 2.7.0 | |
| Icons | `androidx.compose.material:material-icons-extended` | (from BOM) | |
| Testing | `junit` | 4.13.2 | |
| Testing | `androidx.test.ext:junit` | 1.2.1 | |
| Testing | `androidx.test.espresso:espresso-core` | 3.6.1 | |
| Testing | `androidx.compose.ui:ui-test-junit4` | (from BOM) | |

**Explicitly EXCLUDED**: `androidx.window:window` (version 1.3.1 doesn't exist in Maven central — build fails).

---

## Build Configuration (gradle.properties)

```properties
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```

---

## Build Pipeline (gradle-build.yml — Step by Step)

1. `actions/checkout@v4`
2. `actions/setup-java@v5` (Temurin 17)
3. **Manual Android SDK**: install cmdline-tools → `sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "build-tools;34.0.0"`; `yes | sdkmanager --licenses` (twice)
4. Install Gradle 8.11.1 to `/home/runner/gradle-8.11.1` → `GRADLE_HOME` + `PATH`
5. **No Gradle cache** (removed to avoid stale-caches)
6. `chmod +x gradlew`
6. `./gradlew :app:assembleDebug --stacktrace --refresh-dependencies` (env: `ANDROID_SDK_ROOT`, `ANDROID_HOME`, `GRADLE_HOME`)
7. Verify APK exists, non-empty, zip integrity
7. `actions/upload-artifact@v4` → `velshop-debug-apk` (30 days)

---

## Navigation Flow (User Perspective)

```
Launch → HomeScreen
  ├─ TopAppBar search → SearchScreen
  ├─ CategoryStrip → CategoriesScreen
  ├─ ProductRail → ProductDetailScreen
  └─ BottomNav:
      ├─ Home (home)
      ├─ Market (products)
      ├─ Orders (orders → OrderDetail)
      ├─ Cart (cart → CheckoutScreen → order placed → OrderDetail)
      └─ Profile (profile → Orders / Settings / Auth)

AuthScreen → onAuthSuccess → popBackStack("profile")
CheckoutScreen → onOrderPlaced → popUpTo("home") + navigate("order/$orderId")
ProfileScreen signed-in → Orders / Settings / SignOut
SettingsScreen → Auth (sign out) or Auth (sign in)
```

---

## Build Verification Checklist (Pre-Push)

- [ ] `git fetch origin && git rev-parse HEAD == git rev-parse origin/main`
- [ ] Push → GitHub Actions run starts
- [ ] Run logs show `BUILD SUCCESSFUL`
- [ ] APK size > 0 bytes
- [ ] `unzip -t app-debug.apk` → no errors
- [ ] Artifact `velshop-debug-apk` uploaded (30-day retention)
- [ ] No local `./gradlew assembleDebug` run (won't work on this device)
