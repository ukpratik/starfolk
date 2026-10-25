// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data.core

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// core.db (LLD §2.1, D-017): progress only, backed up. Nothing typed by the user lives here.
// App-days are stored as epoch days (Long); instants as epoch millis.

/** One completed quest. Unique per (quest, app-day), so a double Done (app + notification) can't double-count. */
@Entity(tableName = "completion", indices = [Index(value = ["questId", "appDay"], unique = true), Index("appDay")])
data class CompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questId: String,
    val realm: String,
    val level: String,
    val appDay: Long,
    val completedAt: Long,
    /** "app" or "notification". */
    val source: String,
    /** Adventure stamp category 0–7, copied from the quest so the sky never needs the content to draw. */
    val stamp: Int?,
)

/** Today's ticket, persisted once per app-day so it never changes mid-day (ADR-005, US-003). */
@Entity(tableName = "daily_pick")
data class DailyPickEntity(
    @PrimaryKey val appDay: Long,
    val questId: String,
    val swapsUsed: Int,
    /** Every quest shown that day, comma-separated (feeds the 30-day no-repeat window). */
    val shownIds: String,
)

/** Hold-to-tear commitment with an optional reminder time (US-005). */
@Entity(tableName = "quest_commit", primaryKeys = ["appDay", "questId"])
data class CommitEntity(
    val appDay: Long,
    val questId: String,
    /** now / morning / lunch / evening / custom. */
    val slot: String,
    val remindAt: Long?,
    val createdAt: Long,
)

/** One row per app-day the app was opened (feeds the on-device retention milestones, D-018). */
@Entity(tableName = "app_open")
data class AppOpenEntity(@PrimaryKey val appDay: Long)

/** Latest completion app-day per realm (QuestPicker's Connect/Adventure nudge). */
data class RealmLast(val realm: String, @ColumnInfo(name = "lastDay") val lastDay: Long)
