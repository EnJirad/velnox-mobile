# PART 12 — Cart & Checkout Overlay UI

## Context & Scope
`CartScreen.kt` manages user selected items, variant summaries, quantity updates, removal, and checkout initiation.

## Objectives
1. **Cart Item Card Polish:**
   - Group cart items by seller store header with store selection checkbox.
   - Refine item row: product checkbox, product thumbnail image, title, variant tag pill, unit price, and compact quantity stepper (`- 1 +`).
   - Add clear item removal confirmation (swipe-to-dismiss or delete icon with dialog).
2. **Order Summary Card:**
   - Polish pricing summary: Subtotal, Estimated Shipping, Vouchers / Discounts applied, and Grand Total.
   - High-contrast, clean typography for final price in Thai Baht (`฿`).
3. **Bottom Checkout Bar:**
   - Fixed bottom checkout bar: "Select All" checkbox, Total amount label, and prominent "Checkout" button showing item count.
   - Graceful disabled state when 0 items are selected.
4. **Empty Cart State:**
   - High-quality empty cart illustration/icon.
   - Clear message ("Your cart is empty") and prominent "Explore Marketplace" action button leading to Home or Market.

## Verification Checklist
- [x] Item quantities can be adjusted with instant price recalculation.
- [x] Toggling selection updates the total price and checkout button state immediately.
- [x] Empty cart screen looks intentional, inviting, and uncluttered.
- [x] Remote CI passes successfully.
