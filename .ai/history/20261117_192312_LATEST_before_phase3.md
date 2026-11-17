# LATEST.md — Velnox Mobile Latest Change Report

> **Current snapshot (overwritten each task).** Previous phase archived in `.ai/history/20261117_175235_*`.
> Repository: `EnJirad/velnox-mobile`

---

## Task: UI/UX Audit, Visual Refinement and Brand Identity Upgrade

**Date:** 2026-11-17
**Commits:** `0a496d7` (launcher), `ae00aa8` (Home), `6da1cca` (Profile),
`d56979c` (commerce screens), `b5c932a` (Settings), `56446f0` (login/shared)
**Verified CI run:** [44140832100](https://github.com/EnJirad/velnox-mobile/actions/runs/44140832100)
**Status:** **VERIFIED — BUILD SUCCESSFUL** (`BUILD SUCCESSFUL in 1m 51s`)

---

## 1. Repository safety

| Check | Result |
|---|---|
| `git fetch origin main` | Done before any edit |
| Local vs `origin/main` at start | `a166471` on both sides; divergence `0 0` |
| Uncommitted tracked work at start | None; only generated `.codegraph/` was untracked |
| `.codegraph/` | Added to local `.git/info/exclude` only; left on disk, never committed |
| Navigation, DataStore, i18n provider, CI workflow | Not changed in this phase |
| Local Android build | Not run, per `.ai/AI_RULES.md` |

The repository rules still use the older VelShop/velshop-mobile names. Source code and
the current workflow are authoritative; the rules document was left unchanged.

---

## 2. UI audit findings and fixes

| Area | Finding before this phase | Action |
|---|---|---|
| Launcher | Placeholder dark square with a simplified emerald "V"; both standard and round entries pointed at it | Rebuilt from the approved emerald logo pack SVG |
| Home | Category chips were a fixed-width `Row` (566dp for 7 items) and clipped at 320dp; selection was hardcoded to `"all"`; hero CTA used a non-existent category id | Switched to scrolling `LazyRow`, added saved selected-category state, corrected CTA id |
| Home | Icon button 40dp, hero CTA ~32dp, ad-hoc 6/12/16/20/24/28/32dp spacing, arbitrary product-art colours | 48dp icon target, 44dp CTA, `VelnoxSpacing` tokens, design-system slate/brand/warning/destructive tints |
| Profile | Edit-pencil target 32dp, add-address row below 48dp, secondary address mislabelled as "primary", long name could clip | 44dp pencil, 48dp row, real secondary-address string with default badge only on primary, ellipsis + weight for name |
| Messages | Disabled send control used emerald soft colours, implying an active action | Muted surface-variant background and foreground |
| Orders | Filter pills below 44dp, chevron without content description, hardcoded em-dash placeholders | 44dp centred pills, localized accessibility label, localized placeholder |
| Cart | Checkbox target 20dp, remove target ~32dp, disabled checkout used emerald soft, hardcoded em-dashes | 48dp checkbox/remove targets, neutral disabled state, localized placeholder |
| Settings | Current language/appearance rows had no selected cue; Privacy reused the Security shield; address rows and add action mixed in one card | Emerald selected icon tint, new Lucide-style `EyeOff` icon, separate add-address card |
| Settings detail | Hardcoded phone and password placeholders, hardcoded em-dash, fixed 46dp field | Localized placeholders in all three locales, 48dp minimum field height |
| Login/dialog | Primary/social buttons and dialog close had inconsistent heights (50/46/40dp); remember checkbox target 18dp | 48dp buttons and dialog close; 48dp touch wrapper around the 20dp checkbox |
| Shared components | Brand header and login used a text "V" rather than the approved mark; icon buttons 40dp; primary pill 42dp | Approved vector mark in `BrandMark`; 48dp icon buttons; 48dp primary pill |
| Previews | Mostly 390dp light-only | Added 320dp and dark previews across Home, Profile, Messages, Orders, Cart and Login |

No screen was redesigned for its own sake. Working navigation, preview-data boundaries and
existing component structure were preserved.

---

## 3. Components changed or reused

**Changed:** `BrandMark`, `SettingRow`, `PrimaryPill`, `IconActionButton`, `SectionHeader`
consumers, `VelnoxIcons` (one added `EyeOff` glyph).

**Reused:** `VelnoxSpacing`, `Squircle*` shapes, `Text*` type scale, `VelnoxBrand`,
`VelnoxPrimary`, `VelnoxBrandSoft*`, `VelnoxDestructive*`, `VelnoxWarning*`,
`SectionCard`, `CardDivider`, `EmptyState`, `PreviewNotice`, `StatusChip`, `StandaloneScreen`,
`ChoiceRow`, `ProductArtTile`, `TrustRow`, `PageGutter` and `ContentMaxWidth`.

No new dependency, no duplicate design system, no emoji icon, no new brand colour.

---

## 4. Logo conversion and launcher implementation

Source: `.ai/velnox-logo-pack/svg/app-icons/velnox-app-icon-emerald.svg` (untouched).

- The SVG contains a full-bleed `#10B981` rect and a white four-path symbol with
  `translate(130.41 227.1) scale(1.73)`.
- All four `d` attributes were copied verbatim into three VectorDrawables; a script confirmed
  `SVG paths: 4 / VectorDrawable paths: 4 / exact match: True`.
- `ic_launcher_background.xml`: full-bleed `#10B981` at the 108dp adaptive viewport.
- `ic_launcher_foreground.xml`: white symbol only. Raw bounding box
  `x 7.47..434.53`, `y 10..320.33` (427.06 × 310.33) was centred at (54,54) with
  `scale 0.11708` and `translate 28.125, 34.663`; result ≈50 × 36.33dp,
  diagonal ≈61.8dp, inside the 66dp adaptive safe circle.
- `ic_launcher_monochrome.xml`: same geometry for Android 13+ themed icons.
- `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` both reference the new
  background, foreground and monochrome layers; manifest references were already correct.
- `ic_velnox_mark.xml`: tight-viewport version of the same four paths for in-app branding;
  `BrandMark` now renders this vector in Home and Login instead of a text letter.
- XML well-formedness and path equality were checked locally. Standard, round, monochrome,
  foreground and in-app mark resources are all present in the downloaded APK.

---

## 5. Inspo MCP availability and actual research

**Available.** No paywall was encountered in this phase.

| Call | Result used |
|---|---|
| `search_screens(query="mobile marketplace ecommerce home screen category discovery product cards search clean white", device="mobile", limit=8)` | Real slugs returned: `amazon-com`, `fellowproducts-com`, `parachutehome-com`, `bandcamp-com`, `bose-com`, `columbia-com`, `italic-com`, `apple-com` |
| `find_components(type="pricing", device="mobile")` | Returned no usable component catalogue data; nothing was inferred from it |

Patterns adapted, never copied: search-first header with a compact category entry, clear section
titles with generous spacing between sections, card grids with restrained accent colour, one
primary CTA per hero block, and muted product-story surfaces. No brand identity, copy or pixel
layout from those sites was reproduced.

---

## 6. Localization and theme verification

| Check | Result |
|---|---|
| Key counts | 282 in `values`, `values-en`, `values-my` |
| Key-name hash | Identical (`7f62e02afa70b1ee9c60964a1d57c834`) across all three files |
| Raw Thai/Burmese literals in Kotlin | None found |
| Literal `text = "..."` user strings in Kotlin | None found |
| Theme modes | `ThemeMode.LIGHT`, `DARK`, `SYSTEM` unchanged; `VelnoxApp.kt` still resolves `SYSTEM` via `isSystemInDarkTheme()` |
| Language switching | `VelnoxLocaleProvider` and DataStore keys untouched; live localized-context approach unchanged |
| Diff scope | `VelnoxApp.kt`, `VelnoxBottomBar.kt`, `PlaceholderDestination.kt`, `core/preferences/*`, `core/i18n/*`, workflow: unchanged; only `VelnoxInfoDialog.kt` changed inside `navigation/` |
| Compile-time layout checks | New 320dp and dark previews added; compiled successfully in CI |

Runtime switching and device rendering were **not** exercised; this remains compile + resource verification only.

---

## 7. Build and APK

- GitHub Actions: [run 44140832100](https://github.com/EnJirad/velnox-mobile/actions/runs/44140832100)
- Job `Assemble debug APK`: **success**, `BUILD SUCCESSFUL in 1m 51s`
- Workflow log also confirmed `APK size: 10732671 bytes` and `APK integrity: OK`
- Artifact: `velnox-debug-apk`, downloaded and independently checked
- Local copy: 10,732,671 bytes
- SHA-256: `27621718ed67e32f7bd282b366b2f465d25f0dfa29101aa6d4c560669f2a4d2c`
- `unzip -t`: `No errors detected in compressed data`
- APK contains `res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`,
  `res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`,
  `ic_launcher_monochrome.xml`, `ic_velnox_mark.xml`

All phase commits had their own green CI run; no failed build was left in this sequence.

---

## 8. Git review

Fifteen product/resource files changed across six focused commits. The commit tool also removed
the stale tracked harness artifact `.harness/commit-msg.txt`; no product code was affected.
`.codegraph/` remained untracked and excluded locally.

---

## 9. Remaining issues / next steps

1. Runtime verification on a device or emulator: theme switching, language switching, Back chain,
   per-tab state, launcher mask appearance and 320dp clipping.
2. Search, category listing and product detail are still honest placeholder destinations.
3. `.ai/AI_RULES.md` and `.ai/context/project-map.md` still describe the older VelShop package
   and directory layout; they were not part of this task and should be updated deliberately.
4. GitHub Actions annotations remain for deprecated `setup-java@v4` and Node 20 actions; changing
   them was outside this UI phase.
5. No database, backend auth or payment code was added; the app remains ready for Phase 3.
