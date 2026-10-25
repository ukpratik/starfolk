// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.platform

import io.github.ukpratik.starfolk.core.time.Clock
import java.time.Instant
import java.time.ZoneId

/** The device clock and its *current* zone, read each time so travel and DST are picked up (NFR-010). */
object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
