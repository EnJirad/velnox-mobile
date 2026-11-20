# PART 11 — Product Detail & Shop Storefront UI

## Context & Scope
`ProductDetailScreen.kt`, `ProductOptionsBottomSheet.kt`, and `ShopScreen.kt` handle product discovery, image previewing, variant selection, and seller storefront presentation.

## Objectives
1. **Product Gallery & Preview:**
   - Polish `ProductGallery`: main image on left, vertical thumbnail strip on right.
   - Refine active thumbnail highlight border (`VelnoxBrand`), counter badge (`1/n`), and fullscreen zoom toggle icon.
   - Ensure fullscreen pinch-to-zoom dialog has high-contrast close button and smooth dismissal.
2. **Product Info & Trust Badges:**
   - Polish product title typography, price display with original price strikethrough, and discount percentage badge.
   - Refine seller card: store avatar, store name, verified badge, rating summary, and "Visit Store" button.
   - Polish trust guarantee signals (Authentic 100%, Fast Delivery, 7-Day Return).
3. **Product Options Bottom Sheet:**
   - Maintain the ~80% adaptive height bottom sheet.
   - Refine upper product preview with selected variant image and dynamic stock.
   - Polish option chips (Option group labels, active selection border, disabled state for out-of-stock options).
   - Polish quantity stepper with active stock boundaries.
   - Polish context-aware action buttons (`ADD_TO_CART`, `BUY_NOW`, `SELECT_OPTIONS`).
4. **Bottom Purchase Bar:**
   - Polish fixed bottom navigation bar: Chat with Seller button, Cart shortcut with item badge, secondary "Add to Cart" button, primary "Buy Now" button.
   - Ensure bottom safe area (`navigationBarsPadding`) is properly applied.
5. **Shop Storefront (`ShopScreen.kt`):**
   - Polish shop header banner, store statistics (followers, positive rating, response rate).
   - Refine shop product tabs and catalog grid.

## Verification Checklist
- [x] Gallery thumbnails scroll without bubbling vertical scroll to parent page.
- [x] Variant selection dynamically updates price and stock count.
- [x] Bottom purchase bar does not obscure product content (appropriate bottom padding).
- [x] Storefront layout renders cleanly with real seller information.
- [x] Remote CI passes successfully.
