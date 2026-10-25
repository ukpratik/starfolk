// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.sky

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.starfolk.core.content.Realm
import io.github.ukpratik.starfolk.core.time.AppDay
import java.time.LocalDate
import org.junit.Test

class SkyLayoutTest {
    private val today = AppDay.of(LocalDate.parse("2026-10-25"))
    private fun completions(n: Int, realm: Realm = Realm.CONNECT, startId: Long = 1) =
        (0 until n).map { CompletionLite(startId + it, realm, today - (n - it)) }

    private fun layout(c: List<CompletionLite>, seed: Long = 7L) = SkyLayout.compute(seed, c, today, 390f, 440f)

    @Test fun `same inputs give the same sky`() {
        val c = completions(14) + completions(5, Realm.ADVENTURE, 100)
        assertThat(layout(c)).isEqualTo(layout(c.shuffled()))
    }

    @Test fun `a different seed gives a different sky`() {
        assertThat(layout(completions(5), 1L).stars).isNotEqualTo(layout(completions(5), 2L).stars)
    }

    @Test fun `day zero has three starter stickers and nothing else of the user's`() {
        val m = layout(emptyList())
        assertThat(m.starters).hasSize(3)
        assertThat(m.stars).isEmpty()
        assertThat(m.flameStage).isEqualTo(0)
    }

    @Test fun `adding a star never moves the existing ones`() {
        val before = layout(completions(10)).stars
        val after = layout(completions(11)).stars
        assertThat(after.take(10)).isEqualTo(before)
    }

    @Test fun `beyond 40 stars the oldest become labelled constellations and draw ops stay capped`() {
        val m = layout(completions(300))
        assertThat(m.stars).hasSize(SkyLayout.MAX_STARS)
        assertThat(m.clusters.sumOf { it.count }).isAtMost(260)
        assertThat(m.clusters.size).isAtMost(SkyLayout.MAX_CLUSTERS)
        assertThat(m.clusters.first().label).contains("friends")
        assertThat(m.drawOps).isAtMost(SkyLayout.MAX_DRAW_OPS)
        assertThat(m.totalStars).isEqualTo(300)
    }

    @Test fun `stars keep their distance and stay inside the canvas`() {
        val m = layout(completions(40))
        for (s in m.stars) {
            assertThat(s.x).isIn(com.google.common.collect.Range.closed(0f, 390f))
            assertThat(s.y).isIn(com.google.common.collect.Range.closed(0f, 440f))
            assertThat(s.rotation).isIn(com.google.common.collect.Range.closed(-15f, 15f))
        }
    }

    @Test fun `keep-clear areas are respected even when the sky is crowded`() {
        val clear = Box(0f, 0f, 390f, 150f)
        for (n in listOf(12, 40, 300)) {
            val m = SkyLayout.compute(7L, completions(n), today, 390f, 368f, listOf(clear))
            assertThat(m.stars.none { clear.contains(it.x, it.y) }).isTrue()
            assertThat(m.clusters.none { clear.contains(it.x, it.y) }).isTrue()
        }
    }

    @Test fun `the Today strip shows only the newest stickers`() {
        val c = completions(60) + completions(20, Realm.ADVENTURE, 100)
        val m = SkyLayout.compute(7L, c, today, 390f, 368f, maxStars = 8, maxStamps = 4, showClusters = false)
        assertThat(m.stars.map { it.completionId }).isEqualTo((53L..60L).toList())
        assertThat(m.stamps).hasSize(4)
        assertThat(m.clusters).isEmpty()
        assertThat(m.totalStars).isEqualTo(60)
    }

    @Test fun `clusters are balanced, never a lonely one-star constellation`() {
        val m = layout(completions(40 + 13))
        assertThat(m.clusters.map { it.count }.sorted()).isEqualTo(listOf(6, 7))
    }

    @Test fun `no constellation has fewer than three stars`() {
        for (n in listOf(43, 75, 140, 300)) assertThat(layout(completions(n)).clusters.minOf { it.count }).isAtLeast(3)
        assertThat(layout(completions(42)).clusters).isEmpty()
        assertThat(layout(completions(42)).stars).hasSize(42)
    }

    @Test fun `stars stay above the stamp rows`() {
        val c = completions(40) + completions(12, Realm.ADVENTURE, 100)
        val m = SkyLayout.compute(7L, c, today, 390f, 760f)
        val topStamp = m.stamps.minOf { it.y }
        assertThat(m.stars.maxOf { it.y }).isLessThan(topStamp - 20f)
    }

    @Test fun `stamps don't overlap each other`() {
        val m = layout(completions(12, Realm.ADVENTURE))
        for (a in m.stamps) for (b in m.stamps) if (a !== b) {
            val dx = a.x - b.x; val dy = a.y - b.y
            assertThat(dx * dx + dy * dy).isAtLeast(50f * 50f)
        }
    }

    @Test fun `flame grows in three stages and stamps cap at twelve`() {
        assertThat(listOf(0, 1, 4, 5, 14, 15, 99).map(SkyLayout::flameStage)).containsExactly(0, 1, 1, 2, 2, 3, 3).inOrder()
        assertThat(layout(completions(30, Realm.ADVENTURE)).stamps).hasSize(SkyLayout.MAX_STAMPS)
    }

    @Test fun `care in the last three app-days warms the paper`() {
        val care = listOf(CompletionLite(1, Realm.CARE, today), CompletionLite(2, Realm.CARE, today - 5))
        assertThat(layout(care).warmth).isWithin(0.001f).of(1f / 3f)
    }

    @Test fun `TalkBack description`() {
        val m = layout(completions(14) + completions(5, Realm.ADVENTURE, 100) + completions(6, Realm.PURPOSE, 200))
        assertThat(SkyLayout.describe(m, "Maya")).isEqualTo("Your sky: 14 stars, 5 stamps, flame stage 2. Last added: a star for Maya.")
        assertThat(SkyLayout.describe(layout(completions(1)), null)).isEqualTo("Your sky: 1 star, 0 stamps.")
    }
}
