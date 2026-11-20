# Velnox Mobile — Round 2 Development Checklist

## Progress Overview
- [x] **Round 1 (Parts 1–16):** Full UI/UX Overhaul & Remote Verification Completed.
- [x] **Round 2:** Part 01 completed and verified (Commit `4342d6b`, Run #44684196825 — PASS).

---

### [x] Part 01: Cart Multi-Shop Grouping & Variant Image Sync
- **Document:** `.project/part-01-cart-shop-grouping-variant-image.md`
- **Commit:** `4342d6b`
- **CI Run:** [Run #44684196825](https://github.com/EnJirad/velnox-mobile/actions/runs/44684196825) (PASS)
- **Target Files:**
  - `ProductOptionsBottomSheet.kt`
  - `ProductDetailScreen.kt`
  - `VelnoxApp.kt`
  - `CartState.kt`
  - `CartScreen.kt`
  - `CartStateTest.kt`
- **Key Objectives:**
  - [x] **Variant Image Propagation:** Pass the exact selected variant/option image from `ProductOptionsBottomSheet` and `ProductDetailScreen` to `CartState.add(...)` instead of hardcoding `images.firstOrNull()`.
  - [x] **Exact Match vs Variant Separation:**
    - Adding the exact same product with the exact same variant increments quantity (capped at `maxStock`).
    - Adding the same product with a different variant (e.g. different color, size, or model) creates a distinct line item in the cart.
  - [x] **Shop / Seller Grouping in Cart:**
    - Group cart items by seller/shop in `CartScreen.kt`.
    - Render distinct Shop Cards with Shop icon, name, verified badge, and individual shop select-all checkbox.
  - [x] **Unit Tests & Remote CI Verification:**
    - Add comprehensive unit tests in `CartStateTest.kt`.
    - Push to GitHub and verify Remote CI pass.
