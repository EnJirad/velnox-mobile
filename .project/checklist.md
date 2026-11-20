# Velnox Mobile — Project Master Checklist

## Project Information
- **Repository:** `https://github.com/EnJirad/velnox-mobile.git`
- **Current Branch:** `main`
- **Build Policy:** Strict Remote Build Policy (GitHub Actions only, zero local builds).
- **Current Objective:** **Round 1 (รอบที่ 1)** — Full Application UI/UX Comprehensive Audit & Polish across all screens.

---

## Completed Phases (Parts 1 – 7)

- [x] **Part 1:** Repository Setup, Tooling & Remote CI Workflow
- [x] **Part 2:** Authentication & Session Architecture (DataStore, JWT, SessionController)
- [x] **Part 3:** Catalog Data Source, Remote API & Domain Models
- [x] **Part 4:** Home & Marketplace Browsing Architecture
- [x] **Part 5:** Product Detail Layout & Interactive Options Sheet
- [x] **Part 5.1:** Redesign Product Options Bottom Sheet (Simplified chips, context-aware actions)
- [x] **Part 5.2:** Refine Product Gallery (Right-side vertical thumbnails, nested scroll trap, seller card)
- [x] **Part 6:** Login Screen Language Switch & DataStore Persistence
- [x] **Part 6.1:** Login UI Simplification, Velnox ID Removal & 3-Language (TH, EN, MY) Localization
- [x] **Part 7:** Marketplace Final QA & Regression Audit (100% CI Green verified)

---

## Round 1: Full Application UI Overhaul & Polish (รอบที่ 1: ระบบ UI ทุกหน้าของแอปพลิเคชัน)

### [x] Part 8: Auth Screens UI Consistency & Simplification
- **Document:** `.project/part-08-auth-screens-ui.md`
- **Target Screens:** `RegisterScreen.kt`, `ForgotPasswordScreen.kt`
- **Key Objectives:**
  - [x] Integrate compact 3-language selector (`LoginLanguageSelector`) in the top-right header for both Register and Forgot Password.
  - [x] Remove redundant slogans, eyebrows, repetitive subtitles, and decorative footers.
  - [x] Remove non-functional LINE placeholder button and unused temporary notices.
  - [x] Expand Google button to full-width in registration flow.
  - [x] Refine form card layout, touch target sizes, error banners, and keyboard actions.
  - [x] Verify Burmese stacked glyph rendering and immediate language switching across auth screens.

### [x] Part 9: Home & Category Hub UI Alignment
- **Document:** `.project/part-09-home-category-ui.md`
- **Target Screens:** `HomeScreen.kt`, `CategoryScreen.kt`
- **Key Objectives:**
  - [x] Polish Home top header (Brand, Search input, Notification bell, Messages button).
  - [x] Refine hero banners, category icons, section rails, and product card typography.
  - [x] Audit narrow screen (< 360dp) layouts and eliminate potential text clipping or overflow.
  - [x] Ensure smooth scrolling performance and key stability across list items.

### [x] Part 10: Market & Catalog Search / Facet Filtering UI
- **Document:** `.project/part-10-market-catalog-ui.md`
- **Target Screens:** `MarketScreen.kt`
- **Key Objectives:**
  - [x] Polish search bar, active filter chips bar, and filter summary count.
  - [x] Refine filter bottom sheet layout (price range sliders/inputs, category selection, stock/discount toggles).
  - [x] Enhance empty search results state with quick action to clear filters.
  - [x] Audit grid columns spacing, product card tags, and pagination loading indicators.

### [x] Part 11: Product Detail & Shop Storefront UI
- **Document:** `.project/part-11-product-detail-shop-ui.md`
- **Target Screens:** `ProductDetailScreen.kt`, `ShopScreen.kt`, `ProductOptionsBottomSheet.kt`
- **Key Objectives:**
  - [x] Refine product title, verified seller badge, price typography, and guarantee badges.
  - [x] Polish bottom fixed purchase bar (Chat, Cart badge, Add to Cart, Buy Now buttons).
  - [x] Polish seller storefront header, rating badges, product count, and seller tabs.
  - [x] Verify image zoom dialog and adaptive ~80% options sheet on various screen heights.

### [x] Part 12: Cart & Checkout Overlay UI
- **Document:** `.project/part-12-cart-checkout-ui.md`
- **Target Screens:** `CartScreen.kt`, Checkout overlay
- **Key Objectives:**
  - [x] Refine cart item cards, seller grouping, variant labels, and quantity stepper.
  - [x] Polish bottom checkout summary bar (total price calculation, select-all toggle, checkout button).
  - [x] Clean up empty cart presentation with high-quality graphic/icon and "Start Shopping" button.
  - [x] Ensure swipe-to-delete or remove item confirmation dialog is intuitive.

### [x] Part 13: Orders History & Tracking Status UI
- **Document:** `.project/part-13-orders-tracking-ui.md`
- **Target Screens:** `OrdersScreen.kt`
- **Key Objectives:**
  - [x] Polish order status tab pills (`All`, `Pending`, `Processing`, `Shipped`, `Delivered`, `Cancelled`).
  - [x] Refine order card visual hierarchy: Order #, date, seller name, product items, and total amount.
  - [x] Polish order status badges and delivery progress indicator.
  - [x] Enhance empty orders view with contextual guidance to market/home.

### [x] Part 14: Profile, Account & Settings UI
- **Document:** `.project/part-14-profile-settings-ui.md`
- **Target Screens:** `ProfileScreen.kt`, `SettingsScreen.kt`, `SettingsDetailScreen.kt`
- **Key Objectives:**
  - [x] Polish user profile header: avatar, name, email, member tier badge, quick stats row.
  - [x] Refine quick action shortcuts (My Orders, Wishlist, Shipping Addresses, Payment Methods).
  - [x] Polish Settings list items, icons, switches, and chevron indicators.
  - [x] Polish Settings Detail screens (Language selection with 3 native languages, Theme mode selector, Notifications).

### [x] Part 15: Messages & Seller Chat UI
- **Document:** `.project/part-15-messages-chat-ui.md`
- **Target Screens:** `MessagesScreen.kt`
- **Key Objectives:**
  - [x] Polish conversation thread list: seller avatar, store name, last message snippet, timestamp, and unread badge.
  - [x] Polish active conversation screen: chat bubbles (incoming vs outgoing), timestamp, product reference preview card.
  - [x] Polish message input field, send button, and keyboard safe area handling (`imePadding`).
  - [x] Clean up empty messages state.

### [x] Part 16: Global Navigation, Theme System & Mobile Insets Consistency
- **Document:** `.project/part-16-global-navigation-theme-ui.md`
- **Target Screens:** `VelnoxBottomBar.kt`, `VelnoxApp.kt`, Dialogs & Overlays
- **Key Objectives:**
  - [x] Audit bottom navigation bar: active squircle indicator, badge indicators (cart/unread), navigation bar insets.
  - [x] Audit light/dark theme contrast across all screens (elevations, border strokes, outline colors).
  - [x] Verify touch targets (minimum 44dp/48dp) and keyboard dismissal interactions.
  - [x] Conduct final end-to-end visual QA and trigger remote verification via GitHub Actions.
