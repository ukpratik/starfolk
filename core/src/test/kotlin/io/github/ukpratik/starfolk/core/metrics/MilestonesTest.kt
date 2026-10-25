// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.metrics

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.starfolk.core.time.AppDay
import java.time.LocalDate
import org.junit.Test

class MilestonesTest {
    private val install = AppDay.of(LocalDate.parse("2026-10-01"))

    @Test fun `activation counts quests in days 0 to 6 and fires once from day 7`() {
        val done = listOf(install, install + 2, install + 6, install + 7)
        assertThat(Milestones.due(install, install + 6, emptySet(), done, emptySet()).none { it is Event.Activation }).isTrue()
        val e = Milestones.due(install, install + 7, emptySet(), done, emptySet()).filterIsInstance<Event.Activation>().single()
        assertThat(e.props).containsEntry("quests_by_day6", "3+")
        assertThat(Milestones.due(install, install + 8, emptySet(), done, setOf("activation")).none { it is Event.Activation }).isTrue()
    }

    @Test fun `classic retention is about opening on exactly that day`() {
        val opens = setOf(install, install + 1, install + 8)
        val events = Milestones.due(install, install + 31, opens, emptyList(), emptySet()).filterIsInstance<Event.Retained>()
        assertThat(events.associate { it.day to it.retained }).containsExactly(1, true, 7, false, 30, false)
    }

    @Test fun `props can't carry free text`() {
        val e = Event.InstallReferrer("share; DROP TABLE <script>")
        assertThat(e.props.getValue("source")).matches("[A-Za-z0-9_]*")
        assertThat(Event.OnboardingCompleted("CONNECT", "OK").preConsent).isTrue()
        assertThat(Event.QuestCompleted("CONNECT", "SPARK", "app").preConsent).isFalse()
    }
}
