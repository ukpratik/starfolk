// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.quest

import io.github.ukpratik.starfolk.core.content.Level
import io.github.ukpratik.starfolk.core.content.Quest
import io.github.ukpratik.starfolk.core.content.Realm
import io.github.ukpratik.starfolk.core.time.AppDay
import io.github.ukpratik.starfolk.core.time.SplitMix64

enum class Energy { LOW, OK, HIGH }

/** A quest that was shown or completed on an app-day. */
data class Shown(val questId: String, val appDay: AppDay)

data class PickInput(
    val appDay: AppDay,
    val seed: Long,
    val quests: List<Quest>,
    /** Every (quest, app-day) shown or completed recently; at least the last 30 app-days. */
    val recent: List<Shown>,
    val preferredRealm: Realm,
    val energy: Energy,
    /** The latest non-null check-in mood (1–5), if any. */
    val lastMood: Int?,
    /** The latest completion app-day per realm. */
    val lastCompletion: Map<Realm, AppDay>,
    /** Quests already shown today (swaps never deal the same ticket twice). */
    val excludeToday: Set<String> = emptySet(),
    /** D-031: while a Shelter track is active, the optional daily ticket is Spark only, Care-weighted. */
    val shelterActive: Boolean = false,
    /** US-001: the very first ticket after onboarding is a Spark from [preferredRealm]. */
    val firstQuest: Boolean = false,
)

/**
 * Picks today's quest (US-003/004, LLD §4, D-031). Pure and deterministic: the same input always gives the same
 * quest. The caller persists the result as the day's DailyPick, so it never changes during the app-day.
 */
object QuestPicker {
    const val NO_REPEAT_DAYS = 30
    const val RELAXED_NO_REPEAT_DAYS = 14
    private const val NUDGE_WINDOW_DAYS = 3

    fun allowedLevels(energy: Energy, lastMood: Int?, shelterActive: Boolean): Set<Level> = when {
        shelterActive -> setOf(Level.SPARK)
        energy == Energy.LOW || (lastMood != null && lastMood <= 2) -> setOf(Level.SPARK)
        energy == Energy.OK -> setOf(Level.SPARK, Level.FLAME)
        else -> Level.entries.toSet()
    }

    /** Returns null only when every allowed quest is already in [PickInput.excludeToday]. */
    fun pick(input: PickInput): Quest? {
        val rng = SplitMix64(input.seed xor input.appDay.epochDay xor (input.excludeToday.size.toLong() shl 32))
        val levels = allowedLevels(input.energy, input.lastMood, input.shelterActive)
        // The courage rule is never relaxed (D-031); only the repetition window is.
        val base = input.quests.filter { it.level in levels && it.id !in input.excludeToday }.sortedBy { it.id }
        if (base.isEmpty()) return null
        if (input.firstQuest) {
            val first = base.filter { it.level == Level.SPARK && it.realm == input.preferredRealm }
            if (first.isNotEmpty()) return first[rng.nextInt(first.size)]
        }

        val lastSeen = input.recent.groupBy { it.questId }.mapValues { (_, v) -> v.maxOf { it.appDay } }
        fun fresh(window: Int) = base.filter { q -> lastSeen[q.id]?.let { input.appDay.daysSince(it) >= window } ?: true }
        val candidates = fresh(NO_REPEAT_DAYS).ifEmpty { fresh(RELAXED_NO_REPEAT_DAYS) }.ifEmpty {
            val oldest = base.minOf { lastSeen[it.id]?.epochDay ?: Long.MIN_VALUE }
            base.filter { (lastSeen[it.id]?.epochDay ?: Long.MIN_VALUE) == oldest }
        }

        val byRealm = candidates.groupBy { it.realm }
        val realms = Realm.entries.filter { it in byRealm }
        val weights = realms.map { weight(it, input) }
        var roll = rng.nextInt(weights.sum())
        val realm = realms.first { r -> roll -= weights[realms.indexOf(r)]; roll < 0 }
        val pool = byRealm.getValue(realm)
        return pool[rng.nextInt(pool.size)]
    }

    private fun weight(realm: Realm, input: PickInput): Int {
        if (input.shelterActive) return if (realm == Realm.CARE) 4 else 1
        var w = if (realm == input.preferredRealm) 4 else 2
        if (realm == Realm.CONNECT || realm == Realm.ADVENTURE) {
            val last = input.lastCompletion[realm]
            if (last == null || input.appDay.daysSince(last) >= NUDGE_WINDOW_DAYS) w += 1
        }
        return w
    }
}
