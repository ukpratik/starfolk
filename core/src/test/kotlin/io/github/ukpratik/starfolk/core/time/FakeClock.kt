// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.time

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class FakeClock(var instant: Instant, var zoneId: ZoneId) : Clock {
    override fun now() = instant
    override fun zone() = zoneId

    companion object {
        fun at(local: String, zone: String): FakeClock {
            val z = ZoneId.of(zone)
            return FakeClock(LocalDateTime.parse(local).atZone(z).toInstant(), z)
        }
    }
}
