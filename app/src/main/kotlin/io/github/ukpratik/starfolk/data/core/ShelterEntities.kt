// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data.core

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Shelter progress (LLD §2.1): which day, how many walked, slips. No words the user wrote; those live in private.db.

@Entity(tableName = "shelter_track")
data class ShelterTrackEntity(
    @PrimaryKey val track: String,
    /** ACTIVE / PAUSED / DONE. */
    val status: String,
    val currentDay: Int,
    val daysWalked: Int,
    val lastDoneDay: Long?,
    val noContactSince: Long?,
    val startedAt: Long,
)

@Entity(tableName = "shelter_day_done", primaryKeys = ["track", "day", "appDay"])
data class ShelterDayDoneEntity(val track: String, val day: Int, val appDay: Long, val doneAt: Long)

@Entity(tableName = "shelter_slip")
data class SlipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val track: String,
    val appDay: Long,
    /** RESTART / KEEP. */
    val choice: String,
    /** "slip" (I slipped) or "reached_out" (the no-contact counter). */
    val kind: String,
)

@Dao
interface ShelterDao {
    @Query("SELECT * FROM shelter_track")
    fun tracks(): Flow<List<ShelterTrackEntity>>

    @Query("SELECT * FROM shelter_track")
    suspend fun tracksNow(): List<ShelterTrackEntity>

    @Upsert suspend fun upsert(tracks: List<ShelterTrackEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDone(done: ShelterDayDoneEntity)
    @Insert suspend fun insertSlip(slip: SlipEntity)
}
