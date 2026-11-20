# PART 14 — Profile, Account & Settings UI

## Context & Scope
`ProfileScreen.kt`, `SettingsScreen.kt`, and `SettingsDetailScreen.kt` handle user account overview, order shortcuts, app preferences (theme, language, notifications), and account security.

## Objectives
1. **Profile Header:**
   - User avatar (image or initial placeholder), full name, email, and membership tier pill (e.g. `Silver Member`).
   - Quick stats row: Total Orders, Saved Items / Wishlist, Messages, Vouchers.
2. **Order Shortcuts Grid:**
   - Visual icon grid for quick order tracking: `To Pay`, `To Ship`, `To Receive`, `To Review`, and `Returns/Refunds`.
   - Small badge counter for active orders.
3. **Account & Preferences Menus:**
   - Grouped card sections:
     - `Account`: Edit Profile, Shipping Addresses, Payment Methods.
     - `Preferences`: Language (`SettingsDetailScreen`), Appearance / Dark Theme, Notification Preferences.
     - `Support & About`: Help Center, Privacy Policy, Terms of Service, App Version.
     - `Session`: Sign Out button with confirmation dialog.
4. **Settings & Language Selection Polish:**
   - Ensure `SettingsDetailScreen.kt` displays all 3 supported languages (`ไทย`, `English`, `မြန်မာ`) using their native labels and radio choice rows.
   - Immediate language switching across all profile strings without app restart.

## Verification Checklist
- [x] User details and avatar render cleanly in both light and dark themes.
- [x] Language selection updates whole app locale immediately and persists to DataStore.
- [x] Sign out button prompts confirmation dialog and cleanly clears session.
- [x] Remote CI passes successfully.
