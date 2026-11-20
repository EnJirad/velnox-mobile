# PART 13 — Orders History & Tracking Status UI

## Context & Scope
`OrdersScreen.kt` displays customer orders across various fulfillment states (`All`, `Pending`, `Processing`, `Shipped`, `Delivered`, `Cancelled`) and provides tracking details.

## Objectives
1. **Status Tabs / Filter Pills:**
   - Polish horizontal scrollable filter pills for order statuses.
   - Highlight active tab with brand emerald accent (`VelnoxBrand`).
   - Show badge counter for actionable orders (e.g. `To Ship`, `To Receive`).
2. **Order Card Hierarchy:**
   - Header: Seller store name, order date, order reference ID, and status badge pill (e.g. `Delivering` in blue, `Completed` in green, `Cancelled` in neutral/gray).
   - Body: Product items thumbnail, name, variant, quantity, and unit price.
   - Footer: Item count, order total amount, and contextual action buttons (e.g. `Track Order`, `Buy Again`, `Contact Seller`).
3. **Tracking & Progress Stepper:**
   - Polish shipment tracking timeline: Ordered -> Paid -> Shipped -> Out for Delivery -> Delivered.
   - Clear delivery provider and tracking code with quick copy button.
4. **Empty Orders Presentation:**
   - Intentional empty state for each tab filter with guidance to browse marketplace products.

## Verification Checklist
- [x] Switching status tabs filters the orders list cleanly.
- [x] Order cards display correct totals, items, and status badges.
- [x] Action buttons (`Track Order`, `Buy Again`) trigger appropriate actions without navigation loops.
- [x] Remote CI passes successfully.
