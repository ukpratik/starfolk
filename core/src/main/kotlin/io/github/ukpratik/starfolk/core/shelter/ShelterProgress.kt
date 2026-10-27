// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.shelter

import io.github.ukpratik.starfolk.core.content.TrackId
import io.github.ukpratik.starfolk.core.time.AppDay

enum class TrackStatus { ACTIVE, PAUSED, DONE }

/** What "I slipped" chose (US-012). Both keep the days walked: progress is never taken away. */
enum class SlipChoice { RESTART, KEEP }

/**
 * One Shelter track's progress (US-011/012/013/017). [currentDay] is the day being worked on; after it's done today,
 * the screen shows "done for today" and [currentDay] opens at the next app-day (04:00).
 */
data class TrackState(
    val track: TrackId,
    val status: TrackStatus,
    val currentDay: Int,
    /** Total days a step was done, across restarts. Never goes down. */
    val daysWalked: Int,
    val lastDoneDay: AppDay?,
    /** Breakup: the app-day the no-contact counter started; null = off. */
    val noContactSince: AppDay? = null,
)

/** Pure Shelter rules (LLD §4). The repository persists the states it returns. */
object ShelterProgress {

    /** Start or resume [track]; any other active track is paused with its progress kept (US-011). */
    fun start(track: TrackId, states: List<TrackState>): List<TrackState> {
        val others = states.filter { it.track != track }.map { if (it.status == TrackStatus.ACTIVE) it.copy(status = TrackStatus.PAUSED) else it }
        val existing = states.firstOrNull { it.track == track }
        val started = when {
            existing == null -> TrackState(track, TrackStatus.ACTIVE, currentDay = 1, daysWalked = 0, lastDoneDay = null)
            existing.status == TrackStatus.DONE -> existing.copy(status = TrackStatus.ACTIVE, currentDay = 1) // walk it again
            else -> existing.copy(status = TrackStatus.ACTIVE)
        }
        return others + started
    }

    fun active(states: List<TrackState>): TrackState? = states.firstOrNull { it.status == TrackStatus.ACTIVE }

    fun doneToday(state: TrackState, today: AppDay) = state.lastDoneDay == today

    /** One step per app-day; you can't race ahead (US-012). */
    fun canMarkDone(state: TrackState, today: AppDay) =
        state.status == TrackStatus.ACTIVE && (state.lastDoneDay == null || state.lastDoneDay < today)

    data class Marked(val state: TrackState, val graduated: Boolean)

    fun markDone(state: TrackState, today: AppDay, length: Int): Marked {
        require(canMarkDone(state, today)) { "already done today, or not active" }
        val walked = state.daysWalked + 1
        return if (state.currentDay >= length) {
            Marked(state.copy(status = TrackStatus.DONE, daysWalked = walked, lastDoneDay = today), graduated = true)
        } else {
            Marked(state.copy(currentDay = state.currentDay + 1, daysWalked = walked, lastDoneDay = today), graduated = false)
        }
    }

    /**
     * "Day 1 again is still progress." RESTART goes back to day 1; KEEP changes nothing. Days walked stay either way.
     * Today's step stays done, so a restart after today's step opens day 1 tomorrow (one step per app-day).
     */
    fun slip(state: TrackState, choice: SlipChoice): TrackState = when (choice) {
        SlipChoice.RESTART -> state.copy(currentDay = 1)
        SlipChoice.KEEP -> state
    }

    /** The day whose content to show: the one just done when it's done today (day 1 after a same-day restart), else the current one. */
    fun dayToShow(state: TrackState, today: AppDay): Int =
        if (doneToday(state, today) && state.status != TrackStatus.DONE) (state.currentDay - 1).coerceAtLeast(1) else state.currentDay

    fun noContactDays(state: TrackState, today: AppDay): Int? = state.noContactSince?.let { today.daysSince(it).coerceAtLeast(0) }
}
