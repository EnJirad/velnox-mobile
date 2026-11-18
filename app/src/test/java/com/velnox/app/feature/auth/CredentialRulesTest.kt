package com.velnox.app.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The client-side credential rules.
 *
 * These must agree with the API, because the API is what actually accepts or
 * rejects a registration: `min(8)`, at least one letter and one digit, max 128;
 * a trimmed 1–120 character name; a normalized email. If these drift, the form
 * starts promising things the server refuses (or refusing things it accepts).
 */
class CredentialRulesTest {

    @Test
    fun `email must be present and well formed`() {
        assertEquals(CredentialError.EmailRequired, CredentialRules.emailError(""))
        assertEquals(CredentialError.EmailRequired, CredentialRules.emailError("   "))
        assertEquals(CredentialError.EmailInvalid, CredentialRules.emailError("ann"))
        assertEquals(CredentialError.EmailInvalid, CredentialRules.emailError("ann@b"))
        assertEquals(CredentialError.EmailInvalid, CredentialRules.emailError("ann@b.c"))
        assertNull(CredentialRules.emailError("ann@b.co"))
        assertNull(CredentialRules.emailError(" a@b.co "))
    }

    @Test
    fun `sign in only requires a password to be present`() {
        assertNull(CredentialRules.loginPasswordError("short"))
        assertEquals(CredentialError.PasswordRequired, CredentialRules.loginPasswordError(""))
    }

    @Test
    fun `registration password follows the server policy`() {
        assertEquals(CredentialError.PasswordRequired, CredentialRules.passwordError(""))
        assertEquals(CredentialError.PasswordTooShort, CredentialRules.passwordError("Pass1"))
        assertEquals(
            CredentialError.PasswordTooLong,
            CredentialRules.passwordError("a1" + "x".repeat(127)),
        )
        assertEquals(
            CredentialError.PasswordNoDigit,
            CredentialRules.passwordError("PasswordOnly"),
        )
        assertEquals(
            CredentialError.PasswordNoLetter,
            CredentialRules.passwordError("12345678"),
        )
        assertNull(CredentialRules.passwordError("Password1"))
        assertNull(CredentialRules.passwordError("12345678a"))
    }

    @Test
    fun `confirmation must match exactly`() {
        assertEquals(
            CredentialError.ConfirmRequired,
            CredentialRules.confirmError("Password1", ""),
        )
        assertEquals(
            CredentialError.ConfirmMismatch,
            CredentialRules.confirmError("Password1", "Password2"),
        )
        assertNull(CredentialRules.confirmError("Password1", "Password1"))
    }

    @Test
    fun `name must be present and within the server limit`() {
        assertEquals(CredentialError.NameRequired, CredentialRules.nameError(""))
        assertEquals(CredentialError.NameRequired, CredentialRules.nameError("   "))
        assertEquals(
            CredentialError.NameTooLong,
            CredentialRules.nameError("a".repeat(121)),
        )
        assertNull(CredentialRules.nameError("Ann"))
        assertNull(CredentialRules.nameError("a".repeat(120)))
    }

    @Test
    fun `strength grows with length and variety`() {
        // Score = 1 per satisfied property: length >= 8, >= 12, >= 16,
        // letters + digits together, a symbol, and mixed case.
        assertEquals(PasswordStrength.Empty, PasswordStrength.of(""))
        assertEquals(PasswordStrength.Weak, PasswordStrength.of("abc"))          // 0
        assertEquals(PasswordStrength.Fair, PasswordStrength.of("Password"))     // 2: length, case
        assertEquals(PasswordStrength.Good, PasswordStrength.of("Password1"))    // 3: + digit
        assertEquals(PasswordStrength.Good, PasswordStrength.of("Password1234")) // 4: + length 12
        assertEquals(PasswordStrength.Strong, PasswordStrength.of("Password1234!xyz")) // 6
    }

    @Test
    fun `strength bars never exceed the meter`() {
        PasswordStrength.entries.forEach { strength ->
            assertTrue("${strength.name} has ${strength.bars} bars", strength.bars in 0..4)
        }
    }
}
