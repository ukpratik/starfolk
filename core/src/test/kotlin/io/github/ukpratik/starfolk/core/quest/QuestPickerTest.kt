// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.quest

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.starfolk.core.content.Level
import io.github.ukpratik.starfolk.core.content.Quest
import io.github.ukpratik.starfolk.core.content.Realm
import io.github.ukpratik.starfolk.core.time.AppDay
import java.time.LocalDate
import org.junit.Test

class QuestPickerTest {
    private val today = AppDay.of(LocalDate.parse("2026-10-25"))

    private fun q(realm: Realm, level: Level, n: Int) = Quest(
        id = "q-${realm.name.lowercase()}-%04d".format(n), realm = realm, level = level,
        minutes = when (level) { Level.SPARK -> 2; Level.FLAME -> 20; Level.WILDFIRE -> 120 },
        title = "t", why = "w", evidence = listOf("e"),
    )
    private val library = Realm.entries.flatMap { r -> Level.entries.flatMap { l -> (1..6).map { i -> q(r, l, l.ordinal * 10 + i) } } }

    private fun input(
        day: AppDay = today, energy: Energy = Energy.HIGH, mood: Int? = null, recent: List<Shown> = emptyList(),
        exclude: Set<String> = emptySet(), shelter: Boolean = false, quests: List<Quest> = library,
        preferred: Realm = Realm.CONNECT, last: Map<Realm, AppDay> = emptyMap(),
    ) = PickInput(day, seed = 42L, quests, recent, preferred, energy, mood, last, exclude, shelter)

    @Test fun `same input gives the same quest`() {
        assertThat(QuestPicker.pick(input())).isEqualTo(QuestPicker.pick(input()))
        val days = (0 until 30).map { QuestPicker.pick(input(day = today + it))!!.id }
        assertThat(days.toSet().size).isGreaterThan(10)
    }

    @Test fun `low mood or Low energy only ever gets a Spark, never Wildfire`() {
        repeat(200) { i ->
            assertThat(QuestPicker.pick(input(day = today + i, mood = 2))!!.level).isEqualTo(Level.SPARK)
            assertThat(QuestPicker.pick(input(day = today + i, energy = Energy.LOW))!!.level).isEqualTo(Level.SPARK)
            assertThat(QuestPicker.pick(input(day = today + i, energy = Energy.OK))!!.level).isNotEqualTo(Level.WILDFIRE)
        }
    }

    @Test fun `quests seen in the last 30 days are not repeated`() {
        val recent = library.filter { it.realm == Realm.CONNECT }.mapIndexed { i, q -> Shown(q.id, today - (i % 29)) }
        repeat(100) { i -> assertThat(QuestPicker.pick(input(day = today, recent = recent, exclude = setOf("x$i")))!!.realm).isNotEqualTo(Realm.CONNECT) }
    }

    @Test fun `the window relaxes to 14 days, then to least recently shown, but courage never relaxes`() {
        val sparks = library.filter { it.level == Level.SPARK }
        val within20 = sparks.mapIndexed { i, q -> Shown(q.id, today - (if (i == 3) 20 else 5)) }
        assertThat(QuestPicker.pick(input(mood = 1, recent = within20))!!.id).isEqualTo(sparks[3].id)
        val allRecent = sparks.mapIndexed { i, q -> Shown(q.id, today - (if (i == 7) 9 else 2)) }
        val picked = QuestPicker.pick(input(mood = 1, recent = allRecent))!!
        assertThat(picked.id).isEqualTo(sparks[7].id)
        assertThat(picked.level).isEqualTo(Level.SPARK)
    }

    @Test fun `swaps never repeat today's tickets and run out cleanly`() {
        val first = QuestPicker.pick(input())!!
        val second = QuestPicker.pick(input(exclude = setOf(first.id)))!!
        assertThat(second.id).isNotEqualTo(first.id)
        val tiny = library.filter { it.level == Level.SPARK }.take(2)
        assertThat(QuestPicker.pick(input(quests = tiny, exclude = tiny.map { it.id }.toSet()))).isNull()
    }

    @Test fun `the preferred realm is picked most often, and Connect gets a nudge after three quiet days`() {
        val counts = (0 until 2000).map { QuestPicker.pick(input(day = today + it, preferred = Realm.PURPOSE, last = mapOf(Realm.CONNECT to today + it, Realm.ADVENTURE to today + it)))!!.realm }.groupingBy { it }.eachCount()
        assertThat(counts.getValue(Realm.PURPOSE)).isGreaterThan(counts.getValue(Realm.CARE))
        // Weights 4/2/2/2 → purpose ≈ 40%.
        assertThat(counts.getValue(Realm.PURPOSE) / 2000.0).isWithin(0.05).of(0.4)
        val nudged = (0 until 2000).map { QuestPicker.pick(input(day = today + it, preferred = Realm.PURPOSE))!!.realm }.count { it == Realm.CONNECT }
        assertThat(nudged / 2000.0).isWithin(0.05).of(3.0 / 12.0)
    }

    @Test fun `while a Shelter track is active the ticket is a Spark, mostly Care`() {
        val picks = (0 until 1000).map { QuestPicker.pick(input(day = today + it, shelter = true))!! }
        assertThat(picks.all { it.level == Level.SPARK }).isTrue()
        assertThat(picks.count { it.realm == Realm.CARE } / 1000.0).isWithin(0.06).of(4.0 / 7.0)
    }
}
