package com.velnox.app.feature.auth

import androidx.annotation.StringRes
import com.velnox.app.R

/**
 * Client-side credential rules.
 *
 * These mirror the server; they do not replace it. The API validates every field
 * (`min(8)`, a letter and a digit, max 128 for the password; a trimmed 1–120
 * character name; a normalized email) and answers `VALIDATION_ERROR` for anything
 * it rejects. Checking here saves a round trip and puts the message next to the
 * field that caused it.
 */
object CredentialRules {

    /** Same shape the API uses: no whitespace, one `@`, a dotted domain. */
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 128
    const val NAME_MAX = 120

    fun emailError(email: String): CredentialError? = when {
        email.isBlank() -> CredentialError.EmailRequired
        !EMAIL.matches(email.trim()) -> CredentialError.EmailInvalid
        else -> null
    }

    /** Sign-in: only presence. Strength is a creation rule, not an entry rule. */
    fun loginPasswordError(password: String): CredentialError? =
        if (password.isEmpty()) CredentialError.PasswordRequired else null

    /**
     * Registration: the documented API policy (8–128 characters, at least one
     * letter and one digit). Returns the first violation so the message is
     * actionable instead of a list.
     */
    fun passwordError(password: String): CredentialError? = when {
        password.isEmpty() -> CredentialError.PasswordRequired
        password.length < PASSWORD_MIN -> CredentialError.PasswordTooShort
        password.length > PASSWORD_MAX -> CredentialError.PasswordTooLong
        !password.any { it.isDigit() } -> CredentialError.PasswordNoDigit
        !password.any { it.isLetter() } -> CredentialError.PasswordNoLetter
        else -> null
    }

    fun confirmError(password: String, confirm: String): CredentialError? = when {
        confirm.isEmpty() -> CredentialError.ConfirmRequired
        confirm != password -> CredentialError.ConfirmMismatch
        else -> null
    }

    fun nameError(name: String): CredentialError? = when {
        name.isBlank() -> CredentialError.NameRequired
        name.trim().length > NAME_MAX -> CredentialError.NameTooLong
        else -> null
    }
}

/** Everything the credential rules can reject, with a localized message. */
enum class CredentialError(@StringRes val messageRes: Int) {
    EmailRequired(R.string.login_error_email_required),
    EmailInvalid(R.string.login_error_email_invalid),
    PasswordRequired(R.string.login_error_password_required),
    PasswordTooShort(R.string.register_error_password_short),
    PasswordTooLong(R.string.register_error_password_long),
    PasswordNoDigit(R.string.register_error_password_needs_digit),
    PasswordNoLetter(R.string.register_error_password_needs_letter),
    ConfirmRequired(R.string.register_error_confirm_required),
    ConfirmMismatch(R.string.register_error_confirm_mismatch),
    NameRequired(R.string.register_error_name_required),
    NameTooLong(R.string.register_error_name_long),
    ;
}

/**
 * A rough, honest strength estimate for the guidance meter.
 *
 * Guidance only: the server's `min(8)` + letter + digit rule decides whether an
 * account can be created. Length carries the most weight because it is the
 * property that actually resists guessing.
 */
enum class PasswordStrength(val bars: Int, @StringRes val labelRes: Int) {
    Empty(0, R.string.register_strength_empty),
    Weak(1, R.string.register_strength_weak),
    Fair(2, R.string.register_strength_fair),
    Good(3, R.string.register_strength_good),
    Strong(4, R.string.register_strength_strong),
    ;

    companion object {
        fun of(password: String): PasswordStrength {
            if (password.isEmpty()) return Empty

            var score = 0
            if (password.length >= CredentialRules.PASSWORD_MIN) score++
            if (password.length >= 12) score++
            if (password.length >= 16) score++
            if (password.any { it.isDigit() } && password.any { it.isLetter() }) score++
            if (password.any { !it.isLetterOrDigit() }) score++
            if (password.lowercase() != password && password.uppercase() != password) score++

            return when {
                score <= 1 -> Weak
                score == 2 -> Fair
                score <= 4 -> Good
                else -> Strong
            }
        }
    }
}
