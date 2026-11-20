# PART 10 — Market & Catalog Search / Facet Filtering UI

## Context & Scope
`MarketScreen.kt` provides full marketplace catalog browsing, real-time search with debouncing, sorting, multi-facet filtering (categories, price range, in-stock, discounts, shop ID), and infinite scroll pagination.

## Objectives
1. **Search & Active Filters Bar:**
   - Polish market search input with instant clear action.
   - Refine active filter chips strip: dismissable chips showing active filter facets (e.g. `In Stock`, `Discount`, `฿100 - ฿500`), with an active filter count badge on the filter trigger icon.
2. **Sort Picker:**
   - Polish sorting modal / dropdown (`Relevance`, `Price: Low to High`, `Price: High to Low`, `Newest`, `Popular`).
   - Highlight currently selected sort option.
3. **Filter Bottom Sheet:**
   - Refine filter sheet layout: clear sections for Categories, Price Range inputs (Min / Max), In-stock toggle, and Discount-only toggle.
   - Polish "Reset Filters" and "Apply Filters" button row at the bottom with proper insets.
4. **Product Grid & Infinite Scroll:**
   - Polish 2-column staggered or uniform product grid with consistent spacing and card elevations.
   - Refine bottom pagination loader (subtle spinner when fetching next page).
5. **Empty & Error States:**
   - Refine empty search result view: illustrative icon, clear text, and "Clear Filters" or "Browse All" button.
   - Refine network error view with retry action button.

## Verification Checklist
- [x] Active filter chips accurately reflect selected filters and can be cleared individually.
- [x] Sorting rearranges products immediately without resetting search query.
- [x] Infinite scrolling loads additional products without stuttering or re-fetching duplicates.
- [x] Empty state appears cleanly when no matching products are found.
- [x] Remote CI passes successfully.
