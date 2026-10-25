// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.time

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class AppDayClockTest {

    private fun appDay(local: String, zone: String) = AppDayClock(FakeClock.at(local, zone)).today()

    @Test fun `the day rolls over at 04-00 local, not midnight`() {
        assertThat(appDay("2026-10-25T03:59", "Europe/London").date).isEqualTo(LocalDate.parse("2026-10-24"))
        assertThat(appDay("2026-10-25T04:00", "Europe/London").date).isEqualTo(LocalDate.parse("2026-10-25"))
        assertThat(appDay("2026-10-25T23:59", "Europe/London").date).isEqualTo(LocalDate.parse("2026-10-25"))
    }

    @Test fun `DST end in London keeps one app-day across the repeated hour`() {
        // 2026-10-25 01:00 BST → 01:00 GMT. Walk 26 hours in 10-minute steps: the app-day only ever moves forward,
        // and it changes exactly once.
        val clock = FakeClock.at("2026-10-24T12:00", "Europe/London")
        val days = mutableListOf<AppDay>()
        repeat(26 * 6) { days += AppDayClock(clock).today(); clock.instant = clock.instant.plus(Duration.ofMinutes(10)) }
        assertThat(days).isInOrder()
        assertThat(days.distinct()).hasSize(2)
    }

    @Test fun `DST start in New York skips an hour without skipping a day`() {
        val clock = FakeClock.at("2026-03-07T12:00", "America/New_York")
        val days = mutableListOf<AppDay>()
        repeat(48 * 6) { days += AppDayClock(clock).today(); clock.instant = clock.instant.plus(Duration.ofMinutes(10)) }
        assertThat(days.distinct().map { it.date.toString() }).containsExactly("2026-03-07", "2026-03-08", "2026-03-09").inOrder()
    }

    @Test fun `a zone change follows the new local time`() {
        val clock = FakeClock.at("2026-10-25T05:00", "Asia/Kolkata") // 23:30 UTC on the 24th
        assertThat(AppDayClock(clock).today().date).isEqualTo(LocalDate.parse("2026-10-25"))
        clock.zoneId = ZoneId.of("America/Los_Angeles") // same instant is 16:30 on the 24th there
        assertThat(AppDayClock(clock).today().date).isEqualTo(LocalDate.parse("2026-10-24"))
    }

    @Test fun `app-day arithmetic`() {
        val d = AppDay.of(LocalDate.parse("2026-10-25"))
        assertThat((d + 14).daysSince(d)).isEqualTo(14)
        assertThat(d - 1).isLessThan(d)
    }
}
