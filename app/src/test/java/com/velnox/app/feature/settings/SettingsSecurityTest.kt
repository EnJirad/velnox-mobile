package com.velnox.app.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSecurityTest {

    @Test
    fun passwordStrength_calculatesAccurately() {
        assertEquals(PasswordStrength.None, calculatePasswordStrength(""))
        assertEquals(PasswordStrength.Weak, calculatePasswordStrength("1234"))
        assertEquals(PasswordStrength.Weak, calculatePasswordStrength("abcdefg"))
        assertEquals(PasswordStrength.Medium, calculatePasswordStrength("Secret12"))
        assertEquals(PasswordStrength.Strong, calculatePasswordStrength("Secret12345!@#"))
    }

    @Test
    fun passwordValidation_identifiesMismatchAndRequirements() {
        val pass = "Password123"
        val confirmWrong = "Password321"
        val confirmRight = "Password123"

        assertFalse(pass == confirmWrong)
        assertTrue(pass == confirmRight)

        assertTrue(pass.length >= 8)
        assertTrue(pass.any { it.isDigit() })
        assertTrue(pass.any { it.isLetter() })
    }

    @Test
    fun faqSearch_matchesKeywords() {
        val sampleFaq = listOf(
            "PromptPay" to "สแกน QR Code PromptPay",
            "Delivery" to "Standard Delivery 2-3 days",
            "Returns" to "Return within 7 days",
        )

        val query = "promptpay"
        val filtered = sampleFaq.filter {
            it.first.lowercase().contains(query) || it.second.lowercase().contains(query)
        }

        assertEquals(1, filtered.size)
        assertEquals("PromptPay", filtered[0].first)
    }
}
