package com.velnox.app.core.preferences

import androidx.annotation.StringRes
import com.velnox.app.R
import java.util.Locale

/**
 * The three primary languages Velnox ships. Order is the display order in
 * Settings → Language. Each carries the BCP-47 tag stored in DataStore.
 */
enum class AppLanguage(
    val tag: String,
    @StringRes val labelRes: Int,
    @StringRes val nativeLabelRes: Int,
) {
    TH("th", R.string.language_thai, R.string.language_thai_native),
    EN("en", R.string.language_english, R.string.language_english_native),
    MY("my", R.string.language_burmese, R.string.language_burmese_native);

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        /** Stored tags are authoritative; unknown or absent falls back to the device language. */
        fun fromTag(tag: String?): AppLanguage {
            entries.firstOrNull { it.tag == tag }?.let { return it }
            return fromDeviceLocale()
        }

        fun fromDeviceLocale(): AppLanguage {
            val device = Locale.getDefault().language
            return entries.firstOrNull { it.tag == device } ?: TH
        }
    }
}
