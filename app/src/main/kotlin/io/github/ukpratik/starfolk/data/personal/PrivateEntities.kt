// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data.personal

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// private.db (LLD §2.2, D-017): everything the user writes or feels. Never backed up, never sent, never logged.
// Rows point at core.db completions by id; there are no foreign keys across the two databases.

@Entity(tableName = "checkin")
data class CheckinEntity(
    @PrimaryKey val completionId: Long,
    /** 1–5, or null when skipped or done from a notification. */
    val mood: Int?,
    /** ≤ 280 chars. */
    val note: String?,
)

@Entity(tableName = "star_name")
data class StarNameEntity(@PrimaryKey val completionId: Long, /** ≤ 24 chars. */ val name: String)

@Dao
interface PrivateDao {
    @Upsert suspend fun upsertCheckin(c: CheckinEntity)
    @Upsert suspend fun upsertName(n: StarNameEntity)

    @Query("DELETE FROM star_name WHERE completionId = :completionId")
    suspend fun deleteName(completionId: Long)

    @Query("SELECT * FROM checkin")
    fun checkins(): Flow<List<CheckinEntity>>

    @Query("SELECT * FROM star_name")
    fun names(): Flow<List<StarNameEntity>>

    /** The last three non-null moods, newest first (US-016 uses them in M-C; QuestPicker uses the first). */
    @Query("SELECT mood FROM checkin WHERE mood IS NOT NULL ORDER BY completionId DESC LIMIT 3")
    suspend fun recentMoods(): List<Int>

    @Query("DELETE FROM checkin WHERE completionId = :completionId")
    suspend fun deleteCheckin(completionId: Long)
}
