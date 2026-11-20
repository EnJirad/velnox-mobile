# PART 07 — Wishlist & Favorites System

## Context & Objectives
Replace the current placeholder destination for wishlist with a dedicated, full-featured Wishlist screen and interactive favorite heart toggles across catalog screens.

## Key Deliverables
1. **Global Wishlist State (`WishlistState.kt`):**
   - Observable collection of favorite items: `id`, `name`, `price`, `imageUrl`, `shopName`, `inStock`.
   - Methods: `add(product)`, `remove(productId)`, `isFavorite(productId)`, `toggle(product)`.
2. **Interactive Heart Toggle Across the App:**
   - Add favorite heart button to `ProductDetailScreen` top bar and purchase section.
   - Add quick heart icon on product tiles in `HomeScreen` and `MarketScreen`.
3. **Dedicated Wishlist Screen (`WishlistScreen.kt`):**
   - Staggered grid / list of saved products with high-quality thumbnails.
   - Stock status indicators (In Stock, Low Stock, Out of Stock).
   - "Move to Cart" one-tap action (with variant selection if needed).
   - Quick removal button with undo snackbar.
   - Honest empty state when no items are bookmarked, linking to Home discovery.
4. **Integration with Profile Tab:**
   - Profile stat counter for "Wishlist" dynamically reflects `wishlistState.items.size`.
   - Tapping the stat tile opens the real `WishlistScreen`.
5. **Verification & Tests:**
   - Unit tests for adding, removing, and toggling wishlist items.
   - GitHub Actions CI verification.

## Implementation Summary
- **Commit:** `cdf7079`
- **GitHub Actions Run:** [Run #44719129209](https://github.com/EnJirad/velnox-mobile/actions/runs/44719129209) (PASS)
- **APK Artifact:** `velnox-debug-apk` (ID: 14621720586, 12.5 MB)
- **Wishlist State:** Implemented `WishlistItem` and `WishlistState` (`mutableStateListOf`) with `add`, `remove`, `toggle`, `isFavorite`, and `reset`.
- **Dedicated Screen:** Created `WishlistScreen.kt` with product thumbnails, stock status, direct "Move to Cart" action, and removal with undo banner.
- **Interactive Heart Toggles:** Added heart button to `ProductDetailScreen` top bar actions and corner heart button on `HomeScreen` `ProductCard` tiles.
- **Profile Integration:** Linked Profile tab wishlist stat counter directly to `WishlistState.items.size`.
- **Unit Tests:** Created `WishlistStateTest.kt` covering all core operations.
