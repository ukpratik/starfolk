// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.feature.sky

import io.github.ukpratik.starfolk.core.content.Realm
import io.github.ukpratik.starfolk.core.sky.Box
import io.github.ukpratik.starfolk.core.sky.CompletionLite
import io.github.ukpratik.starfolk.core.sky.SkyLayout
import io.github.ukpratik.starfolk.core.sky.SkyModel
import io.github.ukpratik.starfolk.core.time.AppDay

/** M-A: the install seed is fixed until onboarding stores one in DataStore (M-B, US-001). */
const val DEMO_SEED = 0x5EED_5EEDL

/** Demo completions for the sky spike (D-012): a realistic realm mix, one per app-day going back from [today]. */
fun demoCompletions(count: Int, today: AppDay): List<CompletionLite> = (1..count).map { i ->
    val realm = when (i % 10) { 0, 3, 6, 8 -> Realm.CONNECT; 1, 5, 9 -> Realm.ADVENTURE; 2, 7 -> Realm.PURPOSE; else -> Realm.CARE }
    CompletionLite(i.toLong(), realm, today - (count - i), stampCategory = i % 8)
}

fun skyModel(seed: Long, completions: List<CompletionLite>, today: AppDay, widthDp: Float, heightDp: Float, keepClear: List<Box>, strip: Boolean = false): SkyModel =
    if (strip) SkyLayout.compute(seed, completions, today, widthDp, heightDp, keepClear, maxStars = 8, maxStamps = 4, showClusters = false)
    else SkyLayout.compute(seed, completions, today, widthDp, heightDp, keepClear)
