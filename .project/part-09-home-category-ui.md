# PART 9 — Home & Category Hub UI Alignment

## Context & Scope
The Home screen is the primary entry point for all users. It displays marketplace banners, quick category shortcuts, featured/trending/best-seller rails, and product discovery grids. `CategoryScreen.kt` serves as the category hub.

## Objectives
1. **Top Header & Search:**
   - Polish `HomeHeader`: Brand logo (40dp), rounded search bar with clear button, notification bell with unread badge counter, and Messages button at the far-right.
   - Maintain seamless `statusBarsPadding()` and elevation divider.
2. **Hero Banners & Carousel:**
   - Polish promotional banner card aspect ratio, border radius (`SquircleLg`), indicator dots, and auto-scroll/swipe transitions.
3. **Quick Categories & Category Hub:**
   - Polish `QuickCategoriesSection`: round category icons, clear category labels with natural wrapping, and smooth horizontal scrolling.
   - Refine `CategoryScreen.kt`: grid layout, category banner images, subcategory chips, and product counts.
4. **Product Discovery Rails:**
   - Standardize horizontal rail headers (`SectionHeader`) with consistent "View All" touch targets.
   - Polish `ProductCard`: clean image aspect ratio, price formatting (`฿xxx`), discount badges (`-xx%`), and sold count pills.
5. **Layout & Performance:**
   - Guard against text overflow and ensure responsive column counts (2 columns on phones, 3+ on tablets/landscape).
   - Verify `LazyColumn` item recycling keys and content types for 60fps scrolling.

## Verification Checklist
- [x] Top header elements fit cleanly on narrow devices (< 360dp) without overlap.
- [x] Category cards navigate to filtered Market listing or Category hub.
- [x] Product cards navigate to correct Product Detail page.
- [x] Empty rails are safely hidden without awkward blank spacing.
- [x] Remote CI passes successfully.
