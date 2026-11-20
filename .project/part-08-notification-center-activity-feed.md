# PART 08 — Notification Center & Activity Feed

## Context & Objectives
Replace the empty notification popover and placeholder destination with an interactive Notification Center that keeps users informed about their orders, promotions, and system announcements.

## Key Deliverables
1. **Notification Center Screen (`NotificationCenterScreen.kt`):**
   - Category filtering tabs:
     - **All:** Chronological feed of all events.
     - **Orders:** Shipping status alerts, delivery confirmations, payment updates.
     - **Promotions:** Flash sale discounts, voucher drops, shop coupons.
     - **System:** Account security, policy updates.
   - Distinct visual icons and badge tones per notification type.
   - Unread indicator dot with "Mark All as Read" header button.
   - Tap on notification routes to the relevant destination (e.g. order tracking, voucher center, or product page).
2. **Top Bar & Icon Badges:**
   - Real unread notification count badge over the Bell icon in `HomeScreen` and other headers.
3. **Verification & Tests:**
   - Test mark-as-read logic and filtering.
   - GitHub Actions CI build & verification.
