// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.safety

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.starfolk.core.time.AppDay
import java.time.LocalDate
import org.junit.Test

class SafetyRulesTest {
    private val today = AppDay.of(LocalDate.parse("2026-10-25"))
    private fun offer(moods: List<Int>, who5: Int? = null, shelter: Boolean = false, snoozed: AppDay? = null, shown: AppDay? = null) =
        SafetyRules.lowMoodOffer(moods, who5, shelter, snoozed, shown, today)

    @Test fun `three low moods in a row offer the Shelter`() {
        assertThat(offer(listOf(2, 1, 2))).isTrue()
        assertThat(offer(listOf(2, 3, 1))).isFalse()
        assertThat(offer(listOf(1, 1))).isFalse()
        assertThat(offer(listOf(1, 1, 1, 5))).isTrue()
    }

    @Test fun `a low WHO-5 offers it too, boundary at 28 percent`() {
        assertThat(offer(emptyList(), who5 = 28)).isTrue()
        assertThat(offer(emptyList(), who5 = 32)).isFalse()
    }

    @Test fun `never while a track is active, while snoozed, or twice in a day`() {
        val low = listOf(1, 1, 1)
        assertThat(offer(low, shelter = true)).isFalse()
        assertThat(offer(low, snoozed = today + 1)).isFalse()
        assertThat(offer(low, snoozed = today)).isTrue()
        assertThat(offer(low, shown = today)).isFalse()
    }

    @Test fun `WHO-5 scoring and the support threshold`() {
        assertThat(Who5.score(listOf(1, 1, 2, 1, 2))).isEqualTo(Who5.Score(7, 28))
        assertThat(Who5.score(listOf(1, 1, 2, 1, 2)).supportCopy).isTrue()
        assertThat(Who5.score(listOf(2, 2, 2, 1, 1)).supportCopy).isFalse()
        assertThat(Who5.score(listOf(5, 5, 5, 5, 5)).percent).isEqualTo(100)
    }

    @Test fun `deltas are only ever buckets`() {
        assertThat(listOf(-40, -10, -9, -1, 0, 9, 10, 19, 20, 60).map { Who5.deltaBucket(40, 40 + it) })
            .containsExactly("<=-10", "<=-10", "-9..-1", "-9..-1", "0..9", "0..9", "10..19", "10..19", ">=20", ">=20").inOrder()
    }

    @Test fun `baseline offer and the 14-day re-check`() {
        assertThat(SafetyRules.who5BaselineOffer(false, localHour = 18, appDayIndex = 0, offeredToday = false, dismissals = 0)).isTrue()
        assertThat(SafetyRules.who5BaselineOffer(false, localHour = 10, appDayIndex = 0, offeredToday = false, dismissals = 0)).isFalse()
        assertThat(SafetyRules.who5BaselineOffer(false, localHour = 10, appDayIndex = 1, offeredToday = false, dismissals = 3)).isFalse()
        assertThat(SafetyRules.who5RecheckDue(today - 14, hasRecheck = false, today)).isTrue()
        assertThat(SafetyRules.who5RecheckDue(today - 13, hasRecheck = false, today)).isFalse()
    }
}
