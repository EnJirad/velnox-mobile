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
