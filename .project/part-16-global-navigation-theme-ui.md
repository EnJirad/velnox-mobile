# PART 16 — Global Navigation, Theme System & Mobile Insets Consistency

## Context & Scope
`VelnoxBottomBar.kt`, `VelnoxApp.kt`, and global theme files govern global navigation, status/navigation bars, touch targets, dialogs, and dark/light color schemes.

## Objectives
1. **Bottom Navigation Bar Polish:**
   - Polish `VelnoxBottomBar`:
     - 5 tabs (`Home`, `Market`, `Orders`, `Cart`, `Account`).
     - Selected squircle indicator with brand emerald accent.
     - Badge overlays (item count on Cart tab, unread dot on Orders/Messages).
     - Proper `navigationBarsPadding()` so gesture bar / navigation bar on Android devices doesn't overlap labels.
2. **Theme System & Contrast Polish:**
   - Verify Material 3 dark and light color tokens:
     - `surface`, `surfaceVariant`, `background`, `outline`, `outlineVariant`.
   - Ensure text contrast ratios meet WCAG AA standards in both themes.
   - Refine subtle squircle borders (`Squircle`, `SquircleSm`, `SquircleLg`).
3. **Safe Area & Inset Handling:**
   - Ensure `systemBarsPadding()`, `statusBarsPadding()`, and `imePadding()` are consistently implemented across all modal overlays and full-screen destinations.
   - Guard against top status bar clipping and bottom cutouts on diverse Android phone screens.
4. **Touch Targets & Accessibility:**
   - Ensure all interactive buttons, icons, chips, and list items have at least 44dp x 44dp (or 48dp x 48dp) touch targets.
   - Accessibility content descriptions on all icon buttons (`VelnoxIcons`).
5. **Final Round 1 Verification & APK Generation:**
   - Review entire application diff.
   - Push to GitHub and verify GitHub Actions green build.
   - Generate debug APK artifact and present Round 1 completion report.

## Verification Checklist
- [x] No navigation bar or status bar overlaps on any screen.
- [x] Light and dark themes display readable, high-contrast text and crisp card outlines.
- [x] Touch targets are easy to tap on physical devices and emulators.
- [x] Full remote CI verification passes and APK artifact is generated.
