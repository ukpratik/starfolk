// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.time

import java.time.Instant
import java.time.ZoneId

/** The only source of time in :core (LLD §1). The app supplies the system clock; tests supply a fake. */
interface Clock {
    fun now(): Instant
    fun zone(): ZoneId
}
