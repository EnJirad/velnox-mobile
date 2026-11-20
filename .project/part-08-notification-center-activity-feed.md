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
   - Distinct visual icons and badge tones per notification type (`Truck`, `Sparkles`, `ShieldCheck`, `Bell`).
   - Unread indicator dot with "Mark All as Read" header button.
   - Tap on notification routes to the relevant destination (e.g. order tracking `order:VLX-2401`, category `category:electronics`, security `settings/security`).
   - Clear all notifications with confirmation dialog.
2. **Top Bar & Icon Badges:**
   - Real unread notification count badge over the Bell icon in `HomeScreen`.
   - Direct navigation from bell tap to `NotificationCenterScreen`.
3. **Verification & Tests:**
   - `NotificationsStateTest`: tests initial state, category filtering, unread counting per category, markAsRead, markAllAsRead, deleteNotification, clearAll, addNotification.
   - `AccessPolicyTest`: verified public access to `"notifications"` overlay.
   - GitHub Actions CI build & verification.

## Status
- **Commit:** `3f605b8`
- **GitHub Actions Run:** [Run #44730363828](https://github.com/EnJirad/velnox-mobile/actions/runs/44730363828) (PASS)
- **APK Artifact:** `velnox-debug-apk` (ID: 14625483350, 12.5 MB)
- Implementation complete, unit tests passed, remotely verified on GitHub Actions CI.
