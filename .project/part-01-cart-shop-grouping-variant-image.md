# PART 01 — Cart Multi-Shop Grouping & Variant Image Sync

## Context & Scope
Resolves critical cart experience issues:
1. Product images in the cart previously always showed the product's primary first image instead of the selected option/variant image (e.g. specific color or model).
2. Different variants of the same product were not properly distinguished if variantId was null or option-based.
3. Cart items were displayed as a flat list rather than grouped by Seller / Shop.

## Objectives
1. **Accurate Variant Image Propagation:**
   - Update `ProductOptionsBottomSheet.kt` to forward the active option/variant preview image URL in `onAddToCart` and `onBuyNow`.
   - Update `ProductDetailScreen.kt` to accept and pass the resolved variant image to navigation callbacks.
   - Update `VelnoxApp.kt` to store the variant image in `CartItem.imageUrl`.
2. **Item Identity & Variant Separation:**
   - Same product + same variant: increment quantity (up to `maxStock`).
   - Same product + different variant: separate line item in cart.
3. **Multi-Shop Grouped Cart UI:**
   - Group cart items by shop name / store name.
   - Display each shop in a card container with shop header, shop select-all checkbox, and item rows.
4. **Verification & Tests:**
   - Update `CartStateTest.kt` to verify variant image retention, deduplication, and shop-level selection.
   - Confirm remote GitHub Actions build succeeds.

## Verification Summary
- **Commit SHA:** `4342d6b`
- **GitHub Actions Run:** [#44684196825](https://github.com/EnJirad/velnox-mobile/actions/runs/44684196825)
- **Status:** PASS (Build debug APK, Run unit tests, APK integrity verification, Signing verification, Upload artifact all succeeded)
- **Artifact:** `velnox-debug-apk` (ID: 14605787054)
