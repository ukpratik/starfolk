// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.time

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A Starfolk day: it starts at 04:00 local time, so a 1 a.m. check-in still counts for "today" (ADR-005, NFR-010).
 * Stored as the epoch day of its calendar date.
 */
@JvmInline
value class AppDay(val epochDay: Long) : Comparable<AppDay> {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)

    operator fun plus(days: Int) = AppDay(epochDay + days)
    operator fun minus(days: Int) = AppDay(epochDay - days)

    /** Whole app-days from [other] to this one. */
    fun daysSince(other: AppDay): Int = (epochDay - other.epochDay).toInt()

    override fun compareTo(other: AppDay) = epochDay.compareTo(other.epochDay)
    override fun toString() = date.toString()

    companion object {
        const val ROLLOVER_HOUR = 4L
        fun of(date: LocalDate) = AppDay(date.toEpochDay())
    }
}

/** appDay = the local date of (now − 4 h), in the device's current zone. DST-safe because it works on local time. */
class AppDayClock(private val clock: Clock) {
    fun today(): AppDay {
        val local = LocalDateTime.ofInstant(clock.now(), clock.zone())
        return AppDay.of(local.minusHours(AppDay.ROLLOVER_HOUR).toLocalDate())
    }

    /** The local hour right now (0–23), for rules such as "offer WHO-5 after 17:00" (US-019). */
    fun localHour(): Int = LocalDateTime.ofInstant(clock.now(), clock.zone()).hour
}
