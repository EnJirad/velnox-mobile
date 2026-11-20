# PART 03 — Complete Checkout Flow & Multi-Shop Order Placement

## Context & Objectives
Replace the current placeholder destination for checkout with a production-grade e-commerce checkout screen. When users tap "Checkout" from Cart or "Buy Now" from Product Detail, they must enter a fully functional checkout review flow.

## Key Deliverables
1. **Checkout Screen (`CheckoutScreen.kt`):**
   - **Delivery Address Card:** Display active recipient name, phone, full address, and default badge. Provide an action to switch or edit shipping address.
   - **Order Items by Shop:** Render checked-out items grouped by store name, displaying variant thumbnail, variant description, unit price, quantity, and shop subtotal.
   - **Shipping Options per Shop:** Allow selecting shipping speed (Standard Express, Same-Day/Next-Day, Economy Saver) with transparent shipping rates and estimated delivery dates.
   - **Discount / Voucher Section:** Voucher code input, available platform discount vouchers, and free shipping vouchers.
   - **Payment Method Selector:** Support PromptPay QR, Credit/Debit Card, TrueMoney Wallet, Mobile Banking, and Cash on Delivery (COD).
   - **Payment Breakdown:** Transparent summary of Items Subtotal, Shipping Fee, Voucher Discount, and Final Amount to Pay.
2. **Order Placement & State Transition:**
   - "Place Order" button validates shipping address and payment method.
   - Upon confirming:
     - Clear the purchased items from `CartState`.
     - Generate a new order with unique Order ID and push to the active orders state.
     - Navigate to an Order Success confirmation with link to order tracking.
3. **Integration with `VelnoxApp.kt`:**
   - Connect `requestOverlay("checkout")` to the real `CheckoutScreen` with proper back stack navigation.
4. **Verification & Tests:**
   - Unit tests for order total calculation, voucher application, and cart deduction.
   - GitHub Actions CI build & test verification.

## Verification Summary
- **Commit SHA:** `0a9e0b5`
- **GitHub Actions Run:** [#44694691479](https://github.com/EnJirad/velnox-mobile/actions/runs/44694691479)
- **Status:** PASS (Build debug APK, Run unit tests, APK integrity verification, Signing verification, Upload artifact all succeeded)
- **Artifact:** `velnox-debug-apk` (ID: 14609997741)
