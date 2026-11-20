# PART 15 — Messages & Seller Chat UI

## Context & Scope
`MessagesScreen.kt` provides customer communication with marketplace sellers, inquiry threads, and order support.

## Objectives
1. **Thread List Screen:**
   - Conversation row: Seller store avatar, store name, verified checkmark, last message preview, relative timestamp (`5m ago`, `Yesterday`), and unread indicator dot/badge.
   - Search bar to filter conversation threads by store name.
2. **Active Chat Conversation:**
   - Chat top bar: Back arrow, seller avatar, seller name, online status indicator (`Online`, `Active 1h ago`).
   - Pinned product reference snippet (if inquiry started from a Product Detail page) with "View Product" link.
   - Message bubble styling:
     - User messages: emerald brand background (`VelnoxBrand`), aligned to right, subtle rounded squircle.
     - Seller messages: surfaceVariant background, aligned to left with small seller avatar.
     - Timestamp underneath message bubble in subtle 2xs typography.
3. **Message Input Bar:**
   - Rounded text input field, attachment / image icon button, and send button (enabled only when text is non-blank).
   - Smooth `imePadding()` handling so the input field moves above the software keyboard without clipping or jitter.
4. **Empty State:**
   - Clean illustration and guidance: "No messages yet. Chat with sellers to inquire about products and orders."

## Verification Checklist
- [x] Thread list and message bubbles adapt smoothly to light and dark themes.
- [x] Software keyboard does not obscure latest messages or input bar.
- [x] Tapping a thread opens the conversation smoothly.
- [x] Remote CI passes successfully.
