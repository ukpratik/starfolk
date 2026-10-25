// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data

import io.github.ukpratik.starfolk.core.content.ContentSource
import io.github.ukpratik.starfolk.core.content.Quest
import io.github.ukpratik.starfolk.core.content.Realm
import io.github.ukpratik.starfolk.core.quest.PickInput
import io.github.ukpratik.starfolk.core.quest.QuestPicker
import io.github.ukpratik.starfolk.core.quest.Shown
import io.github.ukpratik.starfolk.core.time.AppDay
import io.github.ukpratik.starfolk.core.time.AppDayClock
import io.github.ukpratik.starfolk.data.core.AppOpenEntity
import io.github.ukpratik.starfolk.data.core.CommitEntity
import io.github.ukpratik.starfolk.data.core.CompletionEntity
import io.github.ukpratik.starfolk.data.core.CoreDao
import io.github.ukpratik.starfolk.data.core.DailyPickEntity
import io.github.ukpratik.starfolk.data.personal.CheckinEntity
import io.github.ukpratik.starfolk.data.personal.PrivateDao
import io.github.ukpratik.starfolk.data.personal.StarNameEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The daily quest loop (US-003…009): one persisted pick per app-day, at most 2 swaps, commit, idempotent Done,
 * reflection (private.db) and undo. Every method takes "today" from [appDayClock], so tests drive time with a fake clock.
 */
class QuestRepository(
    private val core: CoreDao,
    private val personal: PrivateDao,
    private val content: ContentSource,
    private val settings: SettingsRepository,
    private val appDayClock: AppDayClock,
    /** D-031: while a Shelter track is active, the daily ticket is a Care-leaning Spark. */
    private val shelterActive: suspend () -> Boolean = { false },
    private val nowMillis: () -> Long,
) {
    private val mutex = Mutex()

    fun completions(): Flow<List<CompletionEntity>> = core.completions()
    fun names() = personal.names()
    fun checkins() = personal.checkins()
    fun pickFlow(day: AppDay): Flow<DailyPickEntity?> = core.dailyPickFlow(day.epochDay)
    fun commitFlow(day: AppDay, questId: String): Flow<CommitEntity?> = core.commitFlow(day.epochDay, questId)

    /** The last three non-null moods, newest first (US-016). */
    suspend fun recentMoods(): List<Int> = personal.recentMoods()

    suspend fun recordOpen() = core.recordOpen(AppOpenEntity(appDayClock.today().epochDay))

    /** Today's pick, created on the first call of the app-day and never changed after (ADR-005). */
    suspend fun ensureToday(): DailyPickEntity = mutex.withLock {
        val today = appDayClock.today()
        core.dailyPick(today.epochDay) ?: run {
            val quest = pick(today, exclude = emptySet()) ?: error("no quests in the content bundle")
            DailyPickEntity(today.epochDay, quest.id, swapsUsed = 0, shownIds = quest.id).also { core.upsertPick(it) }
        }
    }

    /** US-004: at most [MAX_SWAPS] per app-day, never re-dealing a ticket already shown today. Returns false when used up. */
    suspend fun swap(): Boolean = mutex.withLock {
        val today = appDayClock.today()
        val current = core.dailyPick(today.epochDay) ?: return false
        if (current.swapsUsed >= MAX_SWAPS) return false
        val shown = current.shownIds.split(',').filter { it.isNotEmpty() }.toSet()
        val next = pick(today, exclude = shown) ?: return false
        core.upsertPick(current.copy(questId = next.id, swapsUsed = current.swapsUsed + 1, shownIds = (shown + next.id).joinToString(",")))
        true
    }

    suspend fun commit(questId: String, slot: String, remindAt: Long?) =
        core.upsertCommit(CommitEntity(appDayClock.today().epochDay, questId, slot, remindAt, nowMillis()))

    /** US-006: idempotent per (quest, app-day). Returns the completion id (the existing one on a repeat Done). */
    suspend fun complete(quest: Quest, source: String): Long {
        val today = appDayClock.today().epochDay
        val inserted = core.insertCompletion(
            CompletionEntity(questId = quest.id, realm = quest.realm.name, level = quest.level.name, appDay = today,
                completedAt = nowMillis(), source = source, stamp = quest.stamp),
        )
        return if (inserted != -1L) inserted else core.completion(today, quest.id)!!.id
    }

    /** Mood, note and star name go to private.db only (D-017). Blank fields are stored as null / no name. */
    suspend fun reflect(completionId: Long, mood: Int?, note: String?, name: String?) {
        personal.upsertCheckin(CheckinEntity(completionId, mood?.coerceIn(1, 5), note?.trim()?.take(MAX_NOTE)?.ifEmpty { null }))
        val clean = name?.trim()?.take(MAX_NAME)
        if (clean.isNullOrEmpty()) personal.deleteName(completionId) else personal.upsertName(StarNameEntity(completionId, clean))
    }

    /** The 8-second undo (US-006): removes the completion and anything private written for it. */
    suspend fun undo(completionId: Long) {
        core.deleteCompletion(completionId)
        personal.deleteCheckin(completionId)
        personal.deleteName(completionId)
    }

    private suspend fun pick(today: AppDay, exclude: Set<String>): Quest? {
        val s = settings.current()
        val since = (today - QuestPicker.NO_REPEAT_DAYS).epochDay
        val shown = core.picksSince(since).flatMap { p -> p.shownIds.split(',').filter { it.isNotEmpty() }.map { Shown(it, AppDay(p.appDay)) } } +
            core.completedSince(since).map { Shown(it.questId, AppDay(it.appDay)) }
        val last = core.lastPerRealm().mapNotNull { r -> runCatching { Realm.valueOf(r.realm) }.getOrNull()?.let { it to AppDay(r.lastDay) } }.toMap()
        return QuestPicker.pick(
            PickInput(
                appDay = today, seed = s.skySeed, quests = content.bundle().quests, recent = shown,
                preferredRealm = s.preferredRealm, energy = s.energy, lastMood = personal.recentMoods().firstOrNull(),
                lastCompletion = last, excludeToday = exclude, shelterActive = shelterActive(),
            ),
        )
    }

    companion object {
        const val MAX_SWAPS = 2
        const val MAX_NOTE = 280
        const val MAX_NAME = 24
    }
}
