// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data.core

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** Anonymous events waiting to be sent (US-020). Pre-consent rows are deleted on "No"; nothing here is personal. */
@Entity(tableName = "event_queue")
data class EventQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** The typed allowlisted props, as JSON. */
    val propsJson: String,
    /** Rounded to the hour (LLD §4: no timestamps finer than the hour). */
    val hourMillis: Long,
    val preConsent: Boolean,
    val attempts: Int = 0,
)

/** Each milestone (activation, retained_1/7/30) is computed and sent once (D-018). */
@Entity(tableName = "milestone_sent")
data class MilestoneSentEntity(@PrimaryKey val name: String, val sentAt: Long)

@Dao
interface AnalyticsDao {
    @Insert suspend fun enqueue(e: EventQueueEntity)
    @Query("SELECT * FROM event_queue ORDER BY id LIMIT :limit") suspend fun batch(limit: Int): List<EventQueueEntity>
    @Query("DELETE FROM event_queue WHERE id IN (:ids)") suspend fun delete(ids: List<Long>)
    @Query("UPDATE event_queue SET attempts = attempts + 1 WHERE id IN (:ids)") suspend fun bumpAttempts(ids: List<Long>)
    @Query("DELETE FROM event_queue WHERE attempts >= :max") suspend fun dropExhausted(max: Int)
    @Query("DELETE FROM event_queue") suspend fun clear()
    @Query("SELECT COUNT(*) FROM event_queue") suspend fun size(): Int
    @Query("SELECT name FROM milestone_sent") suspend fun milestonesSent(): List<String>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun markSent(m: MilestoneSentEntity)
    @Query("SELECT appDay FROM app_open") suspend fun opens(): List<Long>
    @Query("SELECT appDay FROM completion") suspend fun completionDays(): List<Long>
}
