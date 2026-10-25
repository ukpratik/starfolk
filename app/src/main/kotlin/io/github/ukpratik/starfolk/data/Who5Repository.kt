// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data

import io.github.ukpratik.starfolk.core.safety.Who5
import io.github.ukpratik.starfolk.core.time.AppDayClock
import io.github.ukpratik.starfolk.data.personal.ShelterPrivateDao
import io.github.ukpratik.starfolk.data.personal.Who5Entity

/** WHO-5 answers (US-019): private.db only. Nothing leaves the phone here (the opt-in bucket is M-D). */
class Who5Repository(
    private val dao: ShelterPrivateDao,
    private val appDayClock: AppDayClock,
    private val nowMillis: () -> Long,
    private val shareDelta: suspend () -> Boolean = { false },
    private val onDelta: suspend (String) -> Unit = {},
) {
    val all = dao.who5()

    suspend fun save(answers: List<Int>): Who5Entity {
        val score = Who5.score(answers)
        val baseline = dao.who5Now().firstOrNull { it.kind == BASELINE }
        val kind = if (baseline != null) RECHECK else BASELINE
        return Who5Entity(nowMillis(), appDayClock.today().epochDay, kind, answers.joinToString(","), score.percent).also {
            dao.insertWho5(it)
            // US-019: only with the separate explicit consent, and only as a bucket.
            if (baseline != null && shareDelta()) onDelta(Who5.deltaBucket(baseline.percent, it.percent))
        }
    }

    companion object {
        const val BASELINE = "baseline"
        const val RECHECK = "recheck"
    }
}
