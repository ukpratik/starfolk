// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.help

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.starfolk.data.BundledContentSource
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-015: the right lines for the country, a manual override that wins, and a safe fallback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HelplineResolverTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `known countries resolve to their lines, unknown ones fall back`() = runTest {
        val table = BundledContentSource(app.assets).bundle().helplines
        val gb = HelplineResolver.resolve("GB", table) as HelplineResult.Found
        assertThat(gb.entry.lines.map { it.phone ?: it.sms }).containsAtLeast("116 123", "85258")
        assertThat(gb.entry.emergency).isEqualTo("999")
        assertThat(HelplineResolver.resolve("FR", table)).isEqualTo(HelplineResult.Fallback("FR"))
        assertThat(HelplineResolver.resolve(null, table)).isEqualTo(HelplineResult.Fallback(null))
    }

    @Test fun `a manual override always wins`() {
        assertThat(HelplineResolver.countryOf(app, "IN")).isEqualTo("IN")
        assertThat(HelplineResolver.countryOf(app, null)).isNotEmpty()
    }
}
