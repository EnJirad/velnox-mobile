# PART 04 — Dynamic Order Management & Live Package Tracking

## Context & Objectives
Upgrade the Orders tab and Order Details screen from static demo items to a dynamic, interactive order lifecycle management hub supporting active order tracking, cancellation, re-ordering, and live timeline statuses.

## Key Deliverables
1. **Dynamic Orders State Management:**
   - Implement an observable `OrdersState` that stores active and completed orders, automatically reflecting orders created in Checkout.
   - Support category filter tabs: All, Pending Payment, Processing, To Ship, Delivered/Completed, Cancelled.
2. **Order Detail Screen Enhancements (`OrderDetailScreen.kt`):**
   - **Delivery Status Timeline:** Interactive multi-step timeline (Order Placed → Payment Confirmed → Seller Packed → Shipped / Courier In Transit → Out for Delivery → Delivered) with timestamps and courier notes.
   - **Recipient & Shipping Information:** Address card, courier name (e.g. Kerry / Flash / J&T Express), tracking number with copy-to-clipboard button.
   - **Order Action Area:**
     - For Pending/Processing orders: "Cancel Order" action with confirmation dialog.
     - For Completed orders: "Buy Again" button (re-adds items to cart) and "Rate & Review" action.
     - "Contact Seller" action opening direct chat with the shop seller.
3. **Synchronization with Profile Screen:**
   - Status badge counts in Profile screen (To Pay, To Ship, To Receive, To Review) reflect actual order counts.
4. **Verification & Tests:**
   - Unit tests for order status transitions, filtering, and re-order operations.
   - GitHub Actions CI verification.

## Verification Summary
- **Commit SHA:** `e0b9b0b`
- **GitHub Actions Run:** [#44697947576](https://github.com/EnJirad/velnox-mobile/actions/runs/44697947576)
- **Status:** PASS (Build debug APK, Run unit tests, APK integrity verification, Signing verification, Upload artifact all succeeded)
- **Artifact:** `velnox-debug-apk` (ID: 14612070958)
