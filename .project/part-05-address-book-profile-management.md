# PART 05 — Interactive Address Book & User Profile Management

## Context & Objectives
Transform the static address and profile pages into an interactive, functional user management system where users can manage multiple shipping addresses and update their profile details.

## Key Deliverables
1. **Interactive Address Book:**
   - Implement `AddressBookState` or repository supporting multiple shipping addresses.
   - Address list with "Default" badge, Recipient Name, Phone Number, Full Address, and Postal Code.
   - Bottom sheet / Modal for adding and editing addresses:
     - Form validation for Recipient Name, Phone Number, Address Line, Province, District, Sub-district, Postal Code.
     - "Set as Default Address" toggle switch.
   - Delete address with confirmation dialog.
2. **User Profile Edit & State Sync:**
   - Connect `EditProfilePage` to live session user state (`AuthUser`).
   - Profile avatar selection / photo placeholder picker.
   - Form fields: Display Name, Username/Handle, Email, Phone Number, Gender, Date of Birth.
   - Input validation (valid email format, phone digits).
   - "Save Changes" updates the active in-memory user session and displays confirmation banner.
3. **Seamless Checkout Integration:**
   - Addresses created or edited here are immediately available for selection during Checkout.
4. **Verification & Tests:**
   - Unit tests for address addition, default address switching, and profile field validation.
   - GitHub Actions CI verification.

## Implementation Summary
- **Address State & Data Model:** Implemented `UserAddress` and `AddressBookState` (`mutableStateListOf`) with methods for `add`, `update`, `delete`, and `setDefault`.
- **Addresses Page & Form Dialog:** Created `AddressFormDialog` in `SettingsDetailScreen.kt` supporting full address fields (recipient name, phone, address line, sub-district, district, province, postal code, default toggle) and validation.
- **Profile Edit Sync:** Connected `EditProfilePage` to `LocalSession.current` with real-time `session.updateProfile(name, email)` syncing with `ProfileScreen`.
- **Checkout Integration:** Wired `CheckoutScreen` to `AddressBookState` with an address selection dialog and propagation to order delivery address.
- **Unit Tests:** Added `AddressBookStateTest` and updated `SessionControllerTest`.
