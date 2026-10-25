// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.platform

import android.app.Activity
import android.content.Context
import io.github.ukpratik.starfolk.analytics.Transport

/** F-Droid flavour (D-028): sends nothing, reads no referrer, asks for no review. No Play classes, no network. */
object Flavour {
    fun transportFactory(): (() -> Transport)? = null
    @Suppress("UNUSED_PARAMETER") suspend fun installSource(context: Context): String? = null
    @Suppress("UNUSED_PARAMETER") fun askForReview(activity: Activity) = Unit
}
