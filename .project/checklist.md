# Velnox Mobile — Round 2 Master Plan & Development Checklist

## Progress Overview
- [x] **Round 1 (Parts 1–16):** Full UI/UX Overhaul & Remote Verification Completed.
- [ ] **Round 2 (Parts 01–10):** E-Commerce Engine, Flow Completeness, RAM Efficiency & Polish.
  - Completed: 7 / 10 Parts (70%)

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

### [x] Part 04: Dynamic Order Management & Live Package Tracking
- **Document:** `.project/part-04-orders-tracking-lifecycle.md`
- **Commit:** `e0b9b0b`
- **CI Run:** [Run #44697947576](https://github.com/EnJirad/velnox-mobile/actions/runs/44697947576) (PASS)
- **Target Files:**
  - `OrdersScreen.kt`
  - `OrderDetailScreen.kt`
  - `ProfileScreen.kt`
  - `OrdersStateTest.kt`
- **Key Objectives:**
  - [x] Observable `OrdersState` / `OrdersDemo` with dynamic order additions and cancellations.
  - [x] Category tabs (All, Pending, Processing, Shipping, Completed, Cancelled).
  - [x] Interactive delivery status timeline (Placed → Confirmed → Shipped → Out for Delivery → Delivered).
  - [x] Courier tracking info with tracking number and copy-to-clipboard action.
  - [x] Cancel Order action with confirmation dialog for pending/processing orders.
  - [x] Synchronized dynamic order counters with Profile screen badges.

---

### [x] Part 05: Interactive Address Book & User Profile Management
- **Document:** `.project/part-05-address-book-profile-management.md`
- **Commit:** `fc2936f`
- **CI Run:** [Run #44704517564](https://github.com/EnJirad/velnox-mobile/actions/runs/44704517564) (PASS)
- **Target Files:**
  - `SettingsDetailScreen.kt` (AddressesPage & EditProfilePage)
  - `ProfileScreen.kt`
  - `AddressModels.kt`
  - `CheckoutScreen.kt`
  - `OrdersScreen.kt`
  - `AddressBookStateTest.kt`
  - `SessionControllerTest.kt`
- **Key Objectives:**
  - [x] Implement address management with Add/Edit/Delete address modal sheet.
  - [x] Default address badge toggle.
  - [x] Form validation (recipient name, phone digits, postal code).
  - [x] Real two-way binding of user profile data (`AuthUser`) with edit form.
  - [x] Address book immediate availability inside Checkout.

---

### [x] Part 06: Settings Hub Refinement, Security & Help Center
- **Document:** `.project/part-06-settings-security-help-center.md`
- **Commit:** `bb520f8`
- **CI Run:** [Run #44706844244](https://github.com/EnJirad/velnox-mobile/actions/runs/44706844244) (PASS)
- **Target Files:**
  - `SettingsScreen.kt`
  - `SettingsDetailScreen.kt`
  - `SettingsSecurityTest.kt`
- **Key Objectives:**
  - [x] Functional Change Password dialog with strength validation and error handling.
  - [x] Two-Factor Authentication (2FA) toggle with persistent state.
  - [x] Active login sessions list with remote logout simulation.
  - [x] Expandable FAQ accordion in Help Center with search filtering.
  - [x] Clickable contact support actions (email, phone, live chat).

---

### [x] Part 07: Wishlist & Favorites System
- **Document:** `.project/part-07-wishlist-favorites-system.md`
- **Commit:** `cdf7079`
- **CI Run:** [Run #44719129209](https://github.com/EnJirad/velnox-mobile/actions/runs/44719129209) (PASS)
- **Target Files:**
  - `WishlistScreen.kt`
  - `WishlistModels.kt`
  - `ProductDetailScreen.kt`
  - `HomeScreen.kt`
  - `ProfileScreen.kt`
  - `WishlistStateTest.kt`
- **Key Objectives:**
  - [x] Replace `PlaceholderDestination("wishlist")` with dedicated `WishlistScreen`.
  - [x] Interactive heart toggle on Product Detail, Home, and Market product cards.
  - [x] Stock status, quick "Move to Cart", and item removal with undo.
  - [x] Live counter badge in Profile screen statistics tile.

---

### [x] Part 08: Notification Center & Activity Feed
- **Document:** `.project/part-08-notification-center-activity-feed.md`
- **Commit:** `3f605b8`
- **CI Run:** [Run #44730363828](https://github.com/EnJirad/velnox-mobile/actions/runs/44730363828) (PASS)
- **Target Files:**
  - `NotificationCenterScreen.kt`
  - `NotificationModels.kt`
  - `HomeScreen.kt`
  - `VelnoxApp.kt`
  - `AccessPolicy.kt`
  - `NotificationsStateTest.kt`
- **Key Objectives:**
  - [x] Replace empty notification popover with full `NotificationCenterScreen`.
  - [x] Categorized tabs: All, Orders, Promotions, System.
  - [x] Mark all as read action & unread indicator dots.
  - [x] Notification tap navigation to relevant screens (orders, vouchers, products).
  - [x] Unread notification count badge over Bell icon in Home TopBar.

---

### [x] Part 09: Advanced Search Experience & Product Filtering
- **Document:** `.project/part-09-advanced-search-filtering.md`
- **Commit:** `ca37358`
- **CI Run:** [Run #44732270305](https://github.com/EnJirad/velnox-mobile/actions/runs/44732270305) (PASS)
- **Target Files:**
  - `SearchScreen.kt`
  - `SearchModels.kt`
  - `HomeScreen.kt`
  - `VelnoxApp.kt`
  - `SearchStateTest.kt`
- **Key Objectives:**
  - [x] Replace `PlaceholderDestination("search")` with interactive `SearchScreen`.
  - [x] Recent search history chips with individual delete and clear all.
  - [x] Trending search tag cloud.
  - [x] Debounced real-time auto-suggestions as user types.
  - [x] Multi-criteria filter & sort sheet (price range, ratings, categories).

---

### [x] Part 10: End-to-End Performance, Transition Animations & Integration Polish
- **Document:** `.project/part-10-performance-transitions-polish.md`
- **Target Files:**
  - `VelnoxApp.kt`
  - `RouteIntegrityTest.kt`
  - Global navigation, UI components, Theme
- **Key Objectives:**
  - [x] Compose recomposition audit & lambda stability (`derivedStateOf`, method references).
  - [x] Smooth slide-up bottom sheets & fade/slide overlay transitions (`Crossfade` 220ms).
  - [x] Zero remaining placeholder destinations across entire app.
  - [x] Final CI build, unit test execution, and APK verification on GitHub Actions.
