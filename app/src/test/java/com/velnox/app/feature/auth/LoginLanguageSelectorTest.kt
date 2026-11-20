package com.velnox.app.feature.auth

import com.velnox.app.core.preferences.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for login screen language selection and supported languages.
 */
class LoginLanguageSelectorTest {

    @Test
    fun `supported login languages are Thai and English`() {
        val supported = listOf(AppLanguage.TH, AppLanguage.EN)
        assertEquals(2, supported.size)
        assertEquals("th", supported[0].tag)
        assertEquals("en", supported[1].tag)
    }

    @Test
    fun `language resolution from tag is authoritative for Thai and English`() {
        assertEquals(AppLanguage.TH, AppLanguage.fromTag("th"))
        assertEquals(AppLanguage.EN, AppLanguage.fromTag("en"))
    }

    @Test
    fun `language locales map to valid Java Locales`() {
        assertEquals("th", AppLanguage.TH.locale.language)
        assertEquals("en", AppLanguage.EN.locale.language)
    }

    @Test
    fun `unknown or null tags resolve without throwing`() {
        val fallbackNull = AppLanguage.fromTag(null)
        assertNotNull(fallbackNull)
        assertTrue(fallbackNull in AppLanguage.entries)

        val fallbackUnknown = AppLanguage.fromTag("xx")
        assertNotNull(fallbackUnknown)
        assertTrue(fallbackUnknown in AppLanguage.entries)
    }
}
