// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data

import io.github.ukpratik.starfolk.core.content.TrackId
import io.github.ukpratik.starfolk.core.shelter.ShelterProgress
import io.github.ukpratik.starfolk.core.shelter.SlipChoice
import io.github.ukpratik.starfolk.core.shelter.TrackState
import io.github.ukpratik.starfolk.core.shelter.TrackStatus
import io.github.ukpratik.starfolk.core.time.AppDay
import io.github.ukpratik.starfolk.core.time.AppDayClock
import io.github.ukpratik.starfolk.data.core.ShelterDao
import io.github.ukpratik.starfolk.data.core.ShelterDayDoneEntity
import io.github.ukpratik.starfolk.data.core.ShelterTrackEntity
import io.github.ukpratik.starfolk.data.core.SlipEntity
import io.github.ukpratik.starfolk.data.personal.JournalEntity
import io.github.ukpratik.starfolk.data.personal.LetterEntity
import io.github.ukpratik.starfolk.data.personal.ShelterPrivateDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The Shelter (US-011…017): progress in core.db, everything written in private.db. */
class ShelterRepository(
    private val core: ShelterDao,
    private val personal: ShelterPrivateDao,
    private val appDayClock: AppDayClock,
    private val nowMillis: () -> Long,
) {
    private val mutex = Mutex()

    val states: Flow<List<TrackState>> = core.tracks().map { list -> list.map { it.toState() } }

    suspend fun active(): TrackState? = ShelterProgress.active(core.tracksNow().map { it.toState() })

    suspend fun start(track: TrackId) = mutex.withLock {
        save(ShelterProgress.start(track, core.tracksNow().map { it.toState() }))
    }

    /** Returns true when this step finished the track (graduation, US-017). False if it was already done today. */
    suspend fun markDone(track: TrackId, length: Int): Boolean = mutex.withLock {
        val today = appDayClock.today()
        val state = core.tracksNow().map { it.toState() }.firstOrNull { it.track == track } ?: return false
        if (!ShelterProgress.canMarkDone(state, today)) return false
        val marked = ShelterProgress.markDone(state, today, length)
        core.insertDone(ShelterDayDoneEntity(track.name, state.currentDay, today.epochDay, nowMillis()))
        save(listOf(marked.state))
        marked.graduated
    }

    /** "I slipped" or "I reached out" (US-012/013). Reaching out also restarts the no-contact counter. */
    suspend fun slip(track: TrackId, choice: SlipChoice, reachedOut: Boolean) = mutex.withLock {
        val today = appDayClock.today()
        val state = core.tracksNow().map { it.toState() }.firstOrNull { it.track == track } ?: return@withLock
        var next = ShelterProgress.slip(state, choice)
        if (reachedOut && next.noContactSince != null) next = next.copy(noContactSince = today)
        core.insertSlip(SlipEntity(track = track.name, appDay = today.epochDay, choice = choice.name, kind = if (reachedOut) "reached_out" else "slip"))
        save(listOf(next))
    }

    suspend fun setNoContact(track: TrackId, on: Boolean) = mutex.withLock {
        val state = core.tracksNow().map { it.toState() }.firstOrNull { it.track == track } ?: return@withLock
        save(listOf(state.copy(noContactSince = if (on) appDayClock.today() else null)))
    }

    fun letters(track: TrackId) = personal.letters(track.name)
    suspend fun saveLetter(track: TrackId, id: Long?, body: String) {
        val now = nowMillis()
        personal.upsertLetter(LetterEntity(id = id ?: 0, track = track.name, body = body, createdAt = now, updatedAt = now))
    }
    suspend fun releaseLetter(id: Long) = personal.releaseLetter(id)

    fun journal(track: TrackId, day: Int) = personal.journal(track.name, day)
    suspend fun saveJournal(track: TrackId, day: Int, body: String) = personal.upsertJournal(JournalEntity(track.name, day, body, nowMillis()))

    private suspend fun save(states: List<TrackState>) {
        val existing = core.tracksNow().associateBy { it.track }
        core.upsert(states.map { s ->
            ShelterTrackEntity(
                track = s.track.name, status = s.status.name, currentDay = s.currentDay, daysWalked = s.daysWalked,
                lastDoneDay = s.lastDoneDay?.epochDay, noContactSince = s.noContactSince?.epochDay,
                startedAt = existing[s.track.name]?.startedAt ?: nowMillis(),
            )
        })
    }

    private fun ShelterTrackEntity.toState() = TrackState(
        TrackId.valueOf(track), TrackStatus.valueOf(status), currentDay, daysWalked, lastDoneDay?.let(::AppDay), noContactSince?.let(::AppDay),
    )
}
