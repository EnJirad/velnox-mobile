# PART 8 — Auth Screens UI Consistency & Simplification

## Context & Scope
In Part 6.1, `LoginScreen.kt` was successfully simplified:
- Redundant brand descriptions, eyebrows, repetitive slogans, and decorative footers were eliminated.
- The "VELNOX ID" badge was removed.
- The non-functional LINE placeholder button and its temporary banner were removed.
- Google Sign-In was expanded to a full-width, accessible button.
- The compact 3-language selector (`LoginLanguageSelector`) was introduced in the top-right header with immediate switching and native language labels (`ไทย`, `English`, `မြန်မာ`).

`RegisterScreen.kt` and `ForgotPasswordScreen.kt` currently still have the old design elements:
- Redundant eyebrows ("สร้างบัญชีใหม่", "ลืมรหัสผ่าน").
- Subtitle descriptions.
- Non-functional LINE placeholder button on Register.
- Missing top-right language selector, so changing language from register or password reset is not directly available without going back to login.
- Redundant encrypted connection footer.

## Objectives
1. **Header Consistency:**
   - Add `trailing = { LoginLanguageSelector(currentLanguage = currentLanguage, onLanguageSelected = onLanguageSelected) }` to `AuthBrandHeader` on `RegisterScreen` and `ForgotPasswordScreen`.
2. **UI Simplification:**
   - Remove redundant eyebrow texts (`register_eyebrow`, `forgot_eyebrow`).
   - Remove repetitive subtitles (`register_subtitle`, `forgot_subtitle`).
   - Keep clear, bold screen titles (`register_title`, `forgot_title`).
   - Remove non-functional LINE placeholder button and `showComingSoon` state on `RegisterScreen`.
   - Expand Google Sign-In button on Register to full-width, matching Login.
   - Remove redundant `AuthFooter` from the screen layout.
3. **Form Card Polish:**
   - Refine spacing, input field padding, error message display, and password strength guidance.
   - Ensure IME actions (`ImeAction.Next`, `ImeAction.Done`) seamlessly move focus between Name -> Email -> Password -> Confirm Password.
4. **Localization & Safety:**
   - Ensure all strings are fully localized across Thai, English, and Burmese.
   - Retain all credential validation rules (`CredentialRules`), password strength meters, and registration API endpoints.

## Verification Checklist
- [x] Language selector appears in top-right of Register and Forgot Password screens.
- [x] Selecting language updates strings immediately without losing typed input.
- [x] No "VELNOX ID" badge visible.
- [x] No non-functional placeholder buttons.
- [x] Clean layout on narrow screens (< 360dp) and normal screens.
- [x] Pushed to GitHub and verified 100% PASS on GitHub Actions CI.
