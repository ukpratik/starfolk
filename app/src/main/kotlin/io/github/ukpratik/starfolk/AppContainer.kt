// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk

import android.content.Context
import io.github.ukpratik.starfolk.core.content.ContentSource
import io.github.ukpratik.starfolk.core.time.AppDayClock
import io.github.ukpratik.starfolk.core.time.Clock
import io.github.ukpratik.starfolk.data.BundledContentSource
import io.github.ukpratik.starfolk.platform.SystemClock

/** Wiring for the whole app (ADR-004). Properties are lazy so cold start only pays for what the first screen uses. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: Clock = SystemClock
    val appDayClock by lazy { AppDayClock(clock) }
    val content: ContentSource by lazy { BundledContentSource(appContext.assets) }
}
