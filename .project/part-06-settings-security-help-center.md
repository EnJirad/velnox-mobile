# PART 06 — Settings Hub Refinement, Security & Help Center

## Context & Objectives
Ensure every setting and customer service option in `SettingsScreen` and `SettingsDetailScreen` operates meaningfully without dead clicks (`onClick = {}`), providing a polished experience.

## Key Deliverables
1. **Password & Security Enhancements:**
   - Implement functional Password Change dialog/screen:
     - Old password, new password, confirm new password.
     - Strength meter & validation (min 8 characters, numbers, letters).
     - Success feedback and state update.
   - Two-Factor Authentication (2FA) toggle with persistent preference state.
   - Active Login Sessions list displaying device model, login location, and "Log out of other sessions" action.
2. **Help Center & FAQ Accordion:**
   - Expandable FAQ accordion sections covering:
     - Orders & Payments (How to pay with PromptPay, order cancellation window)
     - Shipping & Delivery (Tracking updates, delivery times)
     - Returns & Refunds (Return policy within 7 days, refund timeline)
   - Search bar within Help Center to filter FAQ articles.
3. **Contact Support Channels:**
   - Direct click-to-email (`mailto:support@velnox.app`), click-to-call support hotline, and live chat shortcut.
4. **Verification & Tests:**
   - Test password validation rules and FAQ search/expansion.
   - Remote GitHub Actions CI verification.

## Implementation Summary
- **Password Strength & Validation:** Implemented `PasswordStrength` enum, `calculatePasswordStrength`, multi-field form (current, new, confirm), dynamic strength label, and input validation.
- **Security Hub & Active Sessions:** Interactive 2FA switch toggle, active session card with device model/location/online status, and functional "Log out of other sessions" action.
- **Help Center & Expandable FAQ:** Added search filter across 6 comprehensive e-commerce FAQ articles (Orders, Shipping, Returns) with animated expandable/collapsible accordion.
- **Support Contact Channels:** Wired Email to `ACTION_SENDTO` (`mailto:`), Phone to `ACTION_DIAL` (`tel:`), and Live Chat to interactive in-app customer support chat dialog.
- **Unit Tests:** Added `SettingsSecurityTest.kt` validating strength scoring, password validation rules, and keyword filtering.
