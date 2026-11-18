# LATEST.md — Velnox Mobile Latest Change Report

> **Current snapshot (overwritten each task).** Previous phase archived in
> `.ai/history/20261117_192312_*`.
> Repository: `EnJirad/velnox-mobile`

---

## Task: Phase 3 — Emerald Identity, Home Experience & UX Fixes

**Date:** 2026-11-17
**Commits:** `2a7e887` (Home/navigation/theme), `fa22249` (compile fix),
`9e3ae6f` (featured rail filtering)
**Verified CI run:** [44157230905](https://github.com/EnJirad/velnox-mobile/actions/runs/44157230905)
**Status:** **VERIFIED — BUILD SUCCESSFUL** (`BUILD SUCCESSFUL in 2m 23s`)

---

## 1. Repository safety

| Check | Result |
|---|---|
| `git fetch origin main` | Done before edits |
| Local vs `origin/main` at start | `bb825a5` on both sides; divergence `0 0` |
| Uncommitted work at start | None |
| Prior successful CI | Run 44142464456 (before this phase) |
| Local Android build | Not run, per `.ai/AI_RULES.md` |
| Navigation architecture, DataStore keys, package identity | Preserved |

---

## 2. Dark Mode root cause and fix

**Root cause:** static Light-mode tokens were used for text on dynamic surfaces.
The selected bottom-tab label used `VelnoxForeground` (`#0F172A`) while the dark
surface is `#0B1220`, so the label disappeared. The same class of bug existed in
Profile statistics, Messages bubbles, Settings dialog buttons and several
emerald-soft pairs.

**Fix:** all user-facing text/icons now resolve through the active scheme:

- Bottom tab selected label → `colorScheme.onSurface`; unselected →
  `colorScheme.onSurfaceVariant`.
- Profile statistic value → `onSurface`; add-address text and default badge →
  `onSecondaryContainer` on `secondaryContainer`.
- Messages outgoing bubble → `secondaryContainer` background with
  `onSecondaryContainer` text (previously text and background were the same light
  emerald).
- Settings sign-out dialog → `colorScheme.error` / `colorScheme.primary`.
- Login errors, focus borders and success notice → `error`, `secondary` and
  `onSecondaryContainer`.
- `SettingRow`, `ChoiceRow`, `StatusChip`, `InitialsAvatar`, `BrandBadge`,
  `PreviewNotice`, `PrimaryPill`, `VelnoxInfoDialog` → semantic roles.
- `BrandMark` derives Light/Dark from the **applied** `colorScheme.surface`
  luminance, not `isSystemInDarkTheme()`, so a manual Light choice on a dark-system
  phone still shows the slate logo.

---

## 3. Emerald identity

Used only existing tokens (`VelnoxBrand`, `secondaryContainer`,
`onSecondaryContainer`):

- Selected bottom-tab indicator: emerald tint (`VelnoxBrand.copy(alpha = 0.16f)`)
  with emerald icon.
- Selected Popular Categories chip: emerald container, emerald border and icon.
- Search icon and focus border: emerald.
- Orders filter pills: selected state is emerald instead of slate.
- Category hub icon tiles, featured tags, message bubbles, success notice and
  notification empty-state tile: emerald container roles.
- Add to Cart / checkout remains dark primary; destructive remains red; warning
  remains amber. No new brand colour was introduced.

---

## 4. Fixed Home toolbar

`HomeScreen` is now a `Column`:

```
HomeToolbar (fixed, statusBarsPadding, surface, bottom divider)
└── BoxWithConstraints → LazyColumn (one scrolling system)
```

The toolbar is `[BrandMark] [editable search field] [notification bell]`.
Greeting, location row and the separate search row were removed. The search
field is the only search entry point on Home.

---

## 5. Sticky category discovery

Feed order:

1. Seasonal collection banner
2. **Sticky** Popular Categories header (`LazyColumn.stickyHeader`)
3. Trust row
4. Featured rail (filtered by the active category)
5. Complete local product grid

The sticky header is opaque, has a 2dp shadow and a bottom divider, and pins to the
bottom edge of the fixed toolbar. Scrolling back to the banner returns it to its
natural position. The duplicate "Shop by Category" rail was deleted.

The header title has an emerald right-arrow that opens the dedicated category
screen. The category chip row scrolls horizontally; the selected chip filters the
feed in place.

---

## 6. Dedicated Category screen and listing

New `feature/home/CategoryScreen.kt`:

- `category` overlay → category hub with every local category and a product count.
- `category:<id>` overlay → filtered product listing with the selected chip,
  product grid and an empty state.
- Listing Back arrow → category hub; hub Back arrow → Home.
- System Back follows the same chain via the `category:` branch in `VelnoxApp`.
- All labels come from the three locale files; no hardcoded UI text.

---

## 7. Local search

`HomeCatalog` now exposes a replaceable `CatalogSource` interface:

```kotlin
fun categories(): List<Category>
fun products(): List<ProductPreview>
fun productsIn(categoryId: String): List<ProductPreview>
fun search(query: String, context: Context): List<ProductPreview>
```

Search resolves `name`, `store`, qualitative tag and category labels through the
localized `Context` supplied by `VelnoxLocaleProvider`, then does case-insensitive
substring matching. It works in Thai, English and Burmese and filters the same
lazy feed in place.

**Limitation:** this is local preview data only. There is no server-wide search,
no Neon/API connection and no paging. A future repository can implement the same
four methods without rewriting the Home UI.

---

## 8. Lazy product feed

- One `LazyColumn` owns vertical scrolling; there is no nested vertical list.
- Product rows are `items(products.chunked(columns), key = row.joinToString("|") { id }, contentType = "productRow")`.
- Product ids are unique across the complete catalogue; the previous `p1b`/`p6b`
  duplicates were removed.
- Off-screen rows leave composition through Compose's lazy layout; there is no
  manual deletion, fading or artificial paging.
- Horizontal featured/category rails are `LazyRow`s with stable ids and content
  types; they do not fight vertical gestures.
- 320dp → 2 columns, <600dp → 3, wider → 4.

---

## 9. Notification popover

- Bell opens an M3 `DropdownMenu` anchored to the bell, below the toolbar.
- Outside tap and system Back dismiss it; the user stays on Home.
- Empty state: "No notifications yet" plus an honest local-data note.
- `HomeNotifications.items` is an empty list; no fake orders, payments, shipping
  or security events were invented.
- Unread dot is rendered only when `notifications.any { it.unread }`; with the
  empty source it is absent.

---

## 10. Navigation and interaction fixes

| Fix | Detail |
|---|---|
| Profile Sign Out removed | Duplicate `DangerRow` and `onSignOut` wiring deleted; Settings sign-out dialog and session reset untouched |
| Settings child Back | `SettingsDetailScreen` now receives `onBack = { overlayKey = "settings" }`; every child arrow returns to the hub, and system Back matches |
| Settings hub Back | Still `closeOverlay()` → Profile (or the originating tab) |
| Tap effects | New `VelnoxInteractions.kt` provides `velnoxClickable` / `velnoxToggleable` with remembered `MutableInteractionSource`, `indication = null`, preserved `Role`, enabled state and semantics; applied across all custom controls |
| Animated colour | Removed the login field's `animateColorAsState`; focus/error borders switch directly |
| Preserved | Switch behaviour, loading spinner, keyboard actions, disabled states, TalkBack roles, 44–48dp targets |

---

## 11. Theme-aware brand mark

Source assets (untouched):

- `.ai/velnox-logo-pack/svg/mark/velnox-mark-slate.svg`
- `.ai/velnox-logo-pack/svg/mark/velnox-mark-white.svg`

Converted to `res/drawable/ic_velnox_mark_slate.xml` and
`ic_velnox_mark_white.xml`. A script confirmed all four `d` paths are byte-for-byte
identical to the SVGs and the fills are `#0F172A` / `#FFFFFF`.
`BrandMark` picks the drawable from `colorScheme.surface.luminance()`, so Light,
Dark and System all switch immediately without an OS-theme check.

---

## 12. Localization and accessibility

| Check | Result |
|---|---|
| String counts | 292 in `values`, `values-en`, `values-my` |
| Key-name hash | Identical (`f0f855027049a802c4a050a26881762d`) |
| Raw Thai/Myanmar literals in Kotlin | None |
| Literal `text = "..."` user strings | None |
| New keys | search clear/results/empty, category open/empty, all products, notification empty |
| Search logic check | Thai `หูฟังไร้สาย`, English `ANC`, Burmese `ANC` all resolve to product `p2`; `electronics` maps to `p2`/`p4` |
| Icon descriptions | Search clear, category arrow, notification bell, selected states |
| Touch targets | Toolbar controls 44dp; category chips 44dp; product rows/cards full-width |

Runtime language/theme switching remains **NOT TESTED** on a device.

---

## 13. Build and APK

- Final GitHub Actions: [run 44157230905](https://github.com/EnJirad/velnox-mobile/actions/runs/44157230905)
- `BUILD SUCCESSFUL in 2m 23s`; `APK size: 10773857 bytes`; `APK integrity: OK`
- Artifact downloaded: `velnox-debug-apk`
- APK bytes: **10,773,857**
- SHA-256: `cc4ddbde961045d9e9ddc788669b657c7460ce692419ab76e61e4abbec7663d2`
- `unzip -t`: `No errors detected in compressed data`
- APK contains `ic_velnox_mark_slate.xml`, `ic_velnox_mark_white.xml`,
  `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`

An intermediate build (`2a7e887`, run 44155550773) failed on two compile errors:
an unnecessary `stickyHeader` import and a `MaterialTheme` call inside a `Canvas`
draw scope. Both were fixed in `fa22249`; the final run is green.

---

## 14. Remaining limitations / next steps

1. Runtime verification on a device/emulator: sticky-header pinning, search input,
   popover dismissal, Back chains, theme and language switching, 320dp layout.
2. Search is local preview data only; no backend, paging or server-wide results.
3. Notifications have no source yet; only the empty state is implemented.
4. Category listing composes the small local catalogue in a scrollable column; a
   backend catalogue will need lazy paging there.
5. Product detail and checkout remain honest placeholder destinations.
6. `.ai/AI_RULES.md` still names VelShop/velshop-mobile; source and workflow remain
   authoritative.
7. GitHub Actions still emits `setup-java@v4` / Node 20 deprecation annotations.
