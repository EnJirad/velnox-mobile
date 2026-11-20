# Velnox Mobile — Round 2 Master Plan & Development Checklist

## Progress Overview
- [x] **Round 1 (Parts 1–16):** Full UI/UX Overhaul & Remote Verification Completed.
- [ ] **Round 2 (Parts 01–10):** E-Commerce Engine, Flow Completeness, RAM Efficiency & Polish.
  - Completed: 3 / 10 Parts (30%)

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
  - [x] **Variant Image Propagation:** Forward selected variant/option image directly to `CartItem.imageUrl`.
  - [x] **Variant Separation vs Deduplication:** Exact variant matches increment quantity; different variants create distinct line items.
  - [x] **Multi-Shop Grouping:** Items in cart grouped by seller/shop with shop header and shop select-all checkbox.
  - [x] **Unit Tests & Remote CI:** Added unit tests in `CartStateTest.kt`, remote CI verified PASS.

---

### [x] Part 02: High-Performance Image Engine & RAM Optimization
- **Document:** `.project/part-02-image-engine-ram-optimization.md`
- **Commit:** `3cc30c4`
- **CI Run:** [Run #44692167847](https://github.com/EnJirad/velnox-mobile/actions/runs/44692167847) (PASS)
- **Target Files:**
  - `VelnoxApplication.kt`
- **Key Objectives:**
  - [x] Implement `ImageLoaderFactory` in `VelnoxApplication`.
  - [x] Configure Coil MemoryCache with strict limit (15% max heap) & LRU DiskCache (50MB).
  - [x] Enable low-memory downsampling (`RGB_565` for budget devices) and bitmap pooling.
  - [x] Ensure all lazy lists have stable keys for efficient bitmap recycling.

---

### [x] Part 03: Complete Checkout Flow & Multi-Shop Order Placement
- **Document:** `.project/part-03-checkout-flow-order-placement.md`
- **Commit:** `0a9e0b5`
- **CI Run:** [Run #44694691479](https://github.com/EnJirad/velnox-mobile/actions/runs/44694691479) (PASS)
- **Target Files:**
  - `CheckoutScreen.kt`
  - `VelnoxApp.kt`
  - `CartScreen.kt`
  - `CartState.kt`
- **Key Objectives:**
  - [x] Replace `PlaceholderDestination("checkout")` with a full-fledged `CheckoutScreen`.
  - [x] Recipient address selector card with quick switch action.
  - [x] Items breakdown grouped by store with variant details and shipping options per shop.
  - [x] Discount voucher code input & platform voucher selection.
  - [x] Payment method selector (PromptPay, Credit/Debit card, TrueMoney, COD).
  - [x] "Place Order" execution: clears cart items, creates active order, and transitions to order success.

---

### [ ] Part 04: Dynamic Order Management & Live Package Tracking
- **Document:** `.project/part-04-orders-tracking-lifecycle.md`
- **Target Files:**
  - `OrdersScreen.kt`
  - `OrdersState.kt`
  - `OrderDetailScreen.kt`
- **Key Objectives:**
  - [ ] Observable `OrdersState` reflecting orders placed from checkout.
  - [ ] Category tabs (All, Pending, Processing, Shipping, Completed, Cancelled).
  - [ ] Interactive delivery status timeline (Placed → Confirmed → Shipped → Out for Delivery → Delivered).
  - [ ] Actions: Cancel Order, Buy Again (re-add to cart), Contact Seller direct chat.
  - [ ] Synchronize order status counters with Profile screen badges.

---

### [ ] Part 05: Interactive Address Book & User Profile Management
- **Document:** `.project/part-05-address-book-profile-management.md`
- **Target Files:**
  - `SettingsDetailScreen.kt` (AddressesPage & EditProfilePage)
  - `ProfileScreen.kt`
- **Key Objectives:**
  - [ ] Implement address management with Add/Edit/Delete address modal sheet.
  - [ ] Default address badge toggle.
  - [ ] Form validation (recipient name, phone digits, postal code).
  - [ ] Real two-way binding of user profile data (`AuthUser`) with edit form.
  - [ ] Address book immediate availability inside Checkout.

---

### [ ] Part 06: Settings Hub Refinement, Security & Help Center
- **Document:** `.project/part-06-settings-security-help-center.md`
- **Target Files:**
  - `SettingsScreen.kt`
  - `SettingsDetailScreen.kt`
- **Key Objectives:**
  - [ ] Functional Change Password dialog with strength validation and error handling.
  - [ ] Two-Factor Authentication (2FA) toggle with persistent state.
  - [ ] Active login sessions list with remote logout simulation.
  - [ ] Expandable FAQ accordion in Help Center with search filtering.
  - [ ] Clickable contact support actions (email, phone, live chat).

---

### [ ] Part 07: Wishlist & Favorites System
- **Document:** `.project/part-07-wishlist-favorites-system.md`
- **Target Files:**
  - `WishlistScreen.kt`
  - `WishlistState.kt`
  - `ProductDetailScreen.kt`
  - `HomeScreen.kt`
  - `ProfileScreen.kt`
- **Key Objectives:**
  - [ ] Replace `PlaceholderDestination("wishlist")` with dedicated `WishlistScreen`.
  - [ ] Interactive heart toggle on Product Detail, Home, and Market product cards.
  - [ ] Stock status, quick "Move to Cart", and item removal with undo.
  - [ ] Live counter badge in Profile screen statistics tile.

---

### [ ] Part 08: Notification Center & Activity Feed
- **Document:** `.project/part-08-notification-center-activity-feed.md`
- **Target Files:**
  - `NotificationCenterScreen.kt`
  - `HomeScreen.kt`
- **Key Objectives:**
  - [ ] Replace empty notification popover with full `NotificationCenterScreen`.
  - [ ] Categorized tabs: All, Orders, Promotions, System.
  - [ ] Mark all as read action & unread indicator dots.
  - [ ] Notification tap navigation to relevant screens (orders, vouchers, products).
  - [ ] Unread notification count badge over Bell icon in Home TopBar.

---

### [ ] Part 09: Advanced Search Experience & Product Filtering
- **Document:** `.project/part-09-advanced-search-filtering.md`
- **Target Files:**
  - `SearchScreen.kt`
  - `MarketScreen.kt`
  - `VelnoxApp.kt`
- **Key Objectives:**
  - [ ] Replace `PlaceholderDestination("search")` with interactive `SearchScreen`.
  - [ ] Recent search history chips with individual delete and clear all.
  - [ ] Trending search tag cloud.
  - [ ] Debounced real-time auto-suggestions as user types.
  - [ ] Multi-criteria filter & sort sheet (price range, ratings, categories).

---

### [ ] Part 10: End-to-End Performance, Transition Animations & Integration Polish
- **Document:** `.project/part-10-performance-transitions-polish.md`
- **Target Files:**
  - Global navigation, UI components, Theme
- **Key Objectives:**
  - [ ] Compose recomposition audit & lambda stability (`derivedStateOf`, method references).
  - [ ] Smooth slide-up bottom sheets & fade/slide overlay transitions.
  - [ ] Zero remaining placeholder destinations across entire app.
  - [ ] Final CI build, unit test execution, and APK verification on GitHub Actions.
