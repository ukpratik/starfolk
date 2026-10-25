// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.safety

import io.github.ukpratik.starfolk.core.time.AppDay

/**
 * Deterministic safety rules (US-016/019, D-031, QA review). Free text is never analysed: these rules only look at
 * mood taps and WHO-5 scores, and they only ever *offer* support, never switch modes.
 */
object SafetyRules {
    const val LOW_MOOD = 2
    const val WHO5_LOW_PCT = 28
    const val SNOOZE_DAYS = 7
    const val WHO5_RECHECK_DAYS = 14
    const val WHO5_MAX_DISMISSALS = 3
    const val WHO5_EVENING_HOUR = 17

    /**
     * The "heavy stretch" card on Today: the last 3 non-null moods are all ≤ 2, or the latest WHO-5 is ≤ 28%; not while
     * a Shelter track is active, not while snoozed ("Not now" = 7 app-days), at most once per app-day.
     */
    fun lowMoodOffer(
        recentMoodsNewestFirst: List<Int>, who5Pct: Int?, shelterActive: Boolean,
        snoozedUntil: AppDay?, lastShown: AppDay?, today: AppDay,
    ): Boolean {
        val low = (recentMoodsNewestFirst.size >= 3 && recentMoodsNewestFirst.take(3).all { it <= LOW_MOOD }) ||
            (who5Pct != null && who5Pct <= WHO5_LOW_PCT)
        return low && !shelterActive && (snoozedUntil == null || today >= snoozedUntil) && lastShown != today
    }

    /** Offer the baseline WHO-5 after a check-in, in the evening or from app-day 1 on; give up after 3 dismissals. */
    fun who5BaselineOffer(hasBaseline: Boolean, localHour: Int, appDayIndex: Int, offeredToday: Boolean, dismissals: Int): Boolean =
        !hasBaseline && !offeredToday && dismissals < WHO5_MAX_DISMISSALS && (localHour >= WHO5_EVENING_HOUR || appDayIndex >= 1)

    /** "How far you've come": 14+ app-days after the baseline, once. */
    fun who5RecheckDue(baselineDay: AppDay?, hasRecheck: Boolean, today: AppDay): Boolean =
        baselineDay != null && !hasRecheck && today.daysSince(baselineDay) >= WHO5_RECHECK_DAYS
}

/** The WHO-5 Well-Being Index (© World Health Organization, 1998), used unmodified with attribution (US-019). */
object Who5 {
    const val PROMPT = "Over the last two weeks…"
    val ITEMS = listOf(
        "I have felt cheerful and in good spirits",
        "I have felt calm and relaxed",
        "I have felt active and vigorous",
        "I woke up feeling fresh and rested",
        "My daily life has been filled with things that interest me",
    )
    /** Index = points (0–5). */
    val SCALE = listOf("At no time", "Some of the time", "Less than half of the time", "More than half of the time", "Most of the time", "All of the time")
    const val ATTRIBUTION = "WHO-5 Well-Being Index © World Health Organization, 1998"

    data class Score(val raw: Int, val percent: Int) {
        /** ≤ 28% (raw ≤ 7): show "it may help to talk to a professional" and the helplines (US-016/019). */
        val supportCopy: Boolean get() = percent <= SafetyRules.WHO5_LOW_PCT
    }

    fun score(answers: List<Int>): Score {
        require(answers.size == ITEMS.size && answers.all { it in 0..5 }) { "WHO-5 needs five answers of 0–5" }
        val raw = answers.sum()
        return Score(raw, raw * 4)
    }

    /** The opt-in anonymous delta is only ever sent as a bucket (US-019, D-008). */
    fun deltaBucket(baselinePct: Int, recheckPct: Int): String = when (val d = recheckPct - baselinePct) {
        in Int.MIN_VALUE..-10 -> "<=-10"
        in -9..-1 -> "-9..-1"
        in 0..9 -> "0..9"
        in 10..19 -> "10..19"
        else -> if (d >= 20) ">=20" else "0..9"
    }
}
