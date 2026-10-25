// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.analytics

import io.github.ukpratik.starfolk.core.metrics.Event
import io.github.ukpratik.starfolk.core.metrics.Milestones
import io.github.ukpratik.starfolk.core.time.AppDay
import io.github.ukpratik.starfolk.core.time.AppDayClock
import io.github.ukpratik.starfolk.data.SettingsRepository
import io.github.ukpratik.starfolk.data.core.AnalyticsDao
import io.github.ukpratik.starfolk.data.core.EventQueueEntity
import io.github.ukpratik.starfolk.data.core.MilestoneSentEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Sends a batch; returns true on success. Only analytics/ may touch the network (LLD §1). */
fun interface Transport {
    suspend fun send(batch: List<EventQueueEntity>): Boolean
}

/**
 * Opt-in anonymous analytics (US-020, D-008, ADR-006). Before consent only [Event.preConsent] events are queued; on "No"
 * the queue is deleted and nothing is ever sent. The transport is created only when there is consent and a key, and
 * only when flushing, so with consent off the app makes zero network calls (NFR-006).
 */
class Analytics(
    private val dao: AnalyticsDao,
    private val settings: SettingsRepository,
    private val appDayClock: AppDayClock,
    private val nowMillis: () -> Long,
    /** null when this build can't send (fdroid, or no key): then nothing is ever queued past consent. */
    private val transportFactory: (() -> Transport)?,
    private val scheduleFlush: () -> Unit,
) {
    val available: Boolean get() = transportFactory != null

    suspend fun track(event: Event) {
        if (!available) return
        when (settings.current().analyticsConsent) {
            false -> return
            null -> if (!event.preConsent) return
            true -> Unit
        }
        val props = JsonObject(event.props.mapValues { JsonPrimitive(it.value) })
        dao.enqueue(EventQueueEntity(name = event.name, propsJson = props.toString(), hourMillis = nowMillis() / HOUR * HOUR, preConsent = event.preConsent))
        if (settings.current().analyticsConsent == true) scheduleFlush()
    }

    suspend fun setConsent(on: Boolean) {
        settings.setAnalyticsConsent(on)
        if (on) scheduleFlush() else dao.clear()
    }

    /** Daily on-device milestones (D-018): activation and classic D1/D7/D30 retention. */
    suspend fun checkMilestones() {
        if (!available || settings.current().analyticsConsent != true) return
        val install = settings.current().installAppDay ?: return
        val due = Milestones.due(
            install, appDayClock.today(), dao.opens().map(::AppDay).toSet(), dao.completionDays().map(::AppDay), dao.milestonesSent().toSet(),
        )
        for (e in due) {
            track(e)
            Milestones.key(e)?.let { dao.markSent(MilestoneSentEntity(it, nowMillis())) }
        }
    }

    /** Called by [FlushWorker]. Batches of 25; 3 attempts, then dropped. */
    suspend fun flush(): Boolean {
        if (settings.current().analyticsConsent != true) { dao.clear(); return true }
        val transport = transportFactory?.invoke() ?: return true
        while (true) {
            val batch = dao.batch(BATCH)
            if (batch.isEmpty()) return true
            if (transport.send(batch)) dao.delete(batch.map { it.id }) else {
                dao.bumpAttempts(batch.map { it.id }); dao.dropExhausted(MAX_ATTEMPTS); return false
            }
        }
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val BATCH = 25
        const val MAX_ATTEMPTS = 3
    }
}
