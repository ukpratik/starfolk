// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.shelter

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.starfolk.core.content.TrackId
import io.github.ukpratik.starfolk.core.time.AppDay
import java.time.LocalDate
import org.junit.Test

class ShelterProgressTest {
    private val d0 = AppDay.of(LocalDate.parse("2026-10-25"))
    private fun startBreakup() = ShelterProgress.active(ShelterProgress.start(TrackId.BREAKUP, emptyList()))!!

    @Test fun `one step per app-day, no racing ahead`() {
        var s = startBreakup()
        s = ShelterProgress.markDone(s, d0, 21).state
        assertThat(ShelterProgress.canMarkDone(s, d0)).isFalse()
        assertThat(ShelterProgress.dayToShow(s, d0)).isEqualTo(1)
        assertThat(ShelterProgress.canMarkDone(s, d0 + 1)).isTrue()
        assertThat(ShelterProgress.dayToShow(s, d0 + 1)).isEqualTo(2)
    }

    @Test fun `missed days cost nothing`() {
        var s = ShelterProgress.markDone(startBreakup(), d0, 21).state
        s = ShelterProgress.markDone(s, d0 + 9, 21).state
        assertThat(s.currentDay).isEqualTo(3)
        assertThat(s.daysWalked).isEqualTo(2)
    }

    @Test fun `a slip never takes away days walked`() {
        var s = startBreakup()
        repeat(5) { s = ShelterProgress.markDone(s, d0 + it, 21).state }
        val restarted = ShelterProgress.slip(s, SlipChoice.RESTART)
        assertThat(restarted.currentDay).isEqualTo(1)
        assertThat(restarted.daysWalked).isEqualTo(5)
        assertThat(ShelterProgress.slip(s, SlipChoice.KEEP)).isEqualTo(s)
    }

    @Test fun `restarting after today's step keeps today done (BUG-001)`() {
        var s = ShelterProgress.markDone(startBreakup(), d0, 21).state
        s = ShelterProgress.slip(s, SlipChoice.RESTART)
        assertThat(s.currentDay).isEqualTo(1)
        assertThat(ShelterProgress.canMarkDone(s, d0)).isFalse()
        assertThat(ShelterProgress.dayToShow(s, d0)).isEqualTo(1)
        assertThat(ShelterProgress.canMarkDone(s, d0 + 1)).isTrue()
        assertThat(ShelterProgress.markDone(s, d0 + 1, 21).state.currentDay).isEqualTo(2)
    }

    @Test fun `the last day graduates`() {
        var s = startBreakup()
        var graduated = false
        repeat(14) { val m = ShelterProgress.markDone(s, d0 + it, 14); s = m.state; graduated = m.graduated }
        assertThat(graduated).isTrue()
        assertThat(s.status).isEqualTo(TrackStatus.DONE)
        assertThat(ShelterProgress.active(listOf(s))).isNull()
    }

    @Test fun `only one active track, and switching pauses and keeps progress`() {
        var states = ShelterProgress.start(TrackId.BREAKUP, emptyList())
        states = states.map { ShelterProgress.markDone(it, d0, 21).state }
        states = ShelterProgress.start(TrackId.WORK, states)
        assertThat(states.count { it.status == TrackStatus.ACTIVE }).isEqualTo(1)
        val breakup = states.first { it.track == TrackId.BREAKUP }
        assertThat(breakup.status).isEqualTo(TrackStatus.PAUSED)
        assertThat(breakup.currentDay).isEqualTo(2)
        states = ShelterProgress.start(TrackId.BREAKUP, states)
        assertThat(ShelterProgress.active(states)!!.currentDay).isEqualTo(2)
    }

    @Test fun `no-contact days count from the start day`() {
        val s = startBreakup().copy(noContactSince = d0)
        assertThat(ShelterProgress.noContactDays(s, d0 + 6)).isEqualTo(6)
        assertThat(ShelterProgress.noContactDays(startBreakup(), d0)).isNull()
    }
}
