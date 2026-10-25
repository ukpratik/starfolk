// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.help

import android.content.Context
import android.telephony.TelephonyManager
import io.github.ukpratik.starfolk.core.content.HelplineCountry
import java.util.Locale

/** The crisis-line country (US-015): no location or phone permission is needed for any of these sources. */
sealed interface HelplineResult {
    val country: String?
    data class Found(val entry: HelplineCountry) : HelplineResult { override val country get() = entry.country }
    /** Not in our table: local emergency guidance + findahelpline.com (needs internet). */
    data class Fallback(override val country: String?) : HelplineResult
}

object HelplineResolver {
    /** Manual override → SIM country → network country → locale. */
    fun countryOf(context: Context, override: String?): String? {
        override?.let { return it }
        val tm = context.getSystemService(TelephonyManager::class.java)
        val candidates = listOf(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country)
        return candidates.firstOrNull { !it.isNullOrBlank() }?.uppercase(Locale.ROOT)
    }

    fun resolve(country: String?, table: List<HelplineCountry>): HelplineResult =
        table.firstOrNull { it.country == country }?.let { HelplineResult.Found(it) } ?: HelplineResult.Fallback(country)
}
