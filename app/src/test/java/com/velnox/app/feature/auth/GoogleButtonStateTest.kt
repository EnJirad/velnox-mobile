package com.velnox.app.feature.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Google button's action, and only its action.
 *
 * Pins the three independent reasons it must stay inert, because two of them are
 * the exact mistakes this part set out to remove: a button that opens a flow the
 * build cannot finish, and a button that stays tappable while a sign-in is in
 * flight. LINE's own "coming soon" notice is a separate state and deliberately not
 * part of this predicate.
 */
class GoogleButtonStateTest {

    @Test
    fun `the button is enabled when configured and idle`() {
        assertTrue(
            googleButtonEnabled(
                isSubmitting = false,
                googleBusy = false,
                googleAvailable = true,
            ),
        )
    }

    @Test
    fun `an unconfigured deployment leaves the button inert`() {
        assertFalse(
            googleButtonEnabled(
                isSubmitting = false,
                googleBusy = false,
                googleAvailable = false,
            ),
        )
    }

    @Test
    fun `a password sign-in in flight blocks a second flow`() {
        assertFalse(
            googleButtonEnabled(
                isSubmitting = true,
                googleBusy = false,
                googleAvailable = true,
            ),
        )
    }

    @Test
    fun `the sheet being open or its token being in transit blocks a second tap`() {
        assertFalse(
            googleButtonEnabled(
                isSubmitting = false,
                googleBusy = true,
                googleAvailable = true,
            ),
        )
    }
}
