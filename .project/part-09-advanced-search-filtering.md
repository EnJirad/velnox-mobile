# PART 09 — Advanced Search Experience & Product Filtering

## Context & Objectives
Provide a responsive search experience replacing `PlaceholderDestination("search")` with real-time suggestions, search history chips, and multi-criteria product filtering.

## Key Deliverables
1. **Dedicated Search Screen (`SearchScreen.kt`):**
   - Clean top search bar with immediate autofocus and clear button.
   - **Recent Searches:** Clickable chips of user's past queries with individual delete and "Clear All" action.
   - **Trending & Popular Searches:** Tag cloud of trending e-commerce queries (e.g. "Wireless Headphones", "Mechanical Keyboard", "Coffee Tumbler").
   - **Live Auto-Suggestions:** Real-time suggestion list as the user types, debounced to save network/CPU overhead.
2. **Product Filter & Sort Sheet:**
   - Filter by Price Range (Min - Max slider/input).
   - Filter by Category & Shop Verified Status.
   - Sort options: Top Sales, Price: Low to High, Price: High to Low, Rating: High to Low.
   - Result count preview on the Apply Filter button.
3. **Verification & Tests:**
   - Test search query filtering and history persistence.
   - GitHub Actions CI verification.
