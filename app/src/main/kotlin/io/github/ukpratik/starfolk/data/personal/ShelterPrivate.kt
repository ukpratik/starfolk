// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data.personal

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// The most private things in the app (D-017): unsent letters, Shelter journal entries, WHO-5 answers.

@Entity(tableName = "letter")
data class LetterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val track: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "journal", primaryKeys = ["track", "day"])
data class JournalEntity(val track: String, val day: Int, val body: String, val updatedAt: Long)

@Entity(tableName = "who5")
data class Who5Entity(
    @PrimaryKey val takenAt: Long,
    val appDay: Long,
    /** baseline / recheck. */
    val kind: String,
    /** Five answers 0–5, comma-separated. */
    val answers: String,
    val percent: Int,
)

@Dao
interface ShelterPrivateDao {
    @Query("SELECT * FROM letter WHERE track = :track ORDER BY createdAt DESC")
    fun letters(track: String): Flow<List<LetterEntity>>

    @Insert suspend fun insertLetter(letter: LetterEntity): Long

    @Query("UPDATE letter SET body = :body, updatedAt = :now WHERE id = :id")
    suspend fun updateLetter(id: Long, body: String, now: Long)

    /** "Release" a letter: it's gone for good (US-013). */
    @Query("DELETE FROM letter WHERE id = :id")
    suspend fun releaseLetter(id: Long)

    @Query("SELECT * FROM journal WHERE track = :track AND day = :day")
    fun journal(track: String, day: Int): Flow<JournalEntity?>

    @Upsert suspend fun upsertJournal(entry: JournalEntity)

    @Query("SELECT * FROM who5 ORDER BY takenAt")
    fun who5(): Flow<List<Who5Entity>>

    @Query("SELECT * FROM who5 ORDER BY takenAt")
    suspend fun who5Now(): List<Who5Entity>

    @Insert suspend fun insertWho5(entry: Who5Entity)
}
