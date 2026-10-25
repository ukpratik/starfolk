// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data.core

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CoreDao {
    @Query("SELECT * FROM completion ORDER BY id")
    fun completions(): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM completion WHERE appDay = :appDay AND questId = :questId")
    suspend fun completion(appDay: Long, questId: String): CompletionEntity?

    /** Returns -1 when the quest is already done today (idempotent Done, US-006). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(c: CompletionEntity): Long

    @Query("DELETE FROM completion WHERE id = :id")
    suspend fun deleteCompletion(id: Long)

    @Query("SELECT realm, MAX(appDay) AS lastDay FROM completion GROUP BY realm")
    suspend fun lastPerRealm(): List<RealmLast>

    @Query("SELECT questId, appDay FROM completion WHERE appDay >= :since")
    suspend fun completedSince(since: Long): List<QuestDay>

    @Query("SELECT * FROM daily_pick WHERE appDay = :appDay")
    suspend fun dailyPick(appDay: Long): DailyPickEntity?

    @Query("SELECT * FROM daily_pick WHERE appDay = :appDay")
    fun dailyPickFlow(appDay: Long): Flow<DailyPickEntity?>

    @Query("SELECT * FROM daily_pick WHERE appDay >= :since")
    suspend fun picksSince(since: Long): List<DailyPickEntity>

    @Upsert
    suspend fun upsertPick(p: DailyPickEntity)

    @Upsert
    suspend fun upsertCommit(c: CommitEntity)

    @Query("SELECT * FROM quest_commit WHERE appDay = :appDay AND questId = :questId")
    fun commitFlow(appDay: Long, questId: String): Flow<CommitEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordOpen(open: AppOpenEntity)
}

data class QuestDay(val questId: String, val appDay: Long)
