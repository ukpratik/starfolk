// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data

import android.content.Context
import io.github.ukpratik.starfolk.data.core.CoreDatabase
import io.github.ukpratik.starfolk.data.personal.PrivateDatabase
import io.github.ukpratik.starfolk.notify.Reminders
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Export and delete-everything (US-024). With no account, export is the user's own backup of private.db. */
class UserData(
    private val context: Context,
    private val core: () -> CoreDatabase,
    private val personal: () -> PrivateDatabase,
    private val settings: SettingsRepository,
) {
    /** Every table of both databases plus settings, as one JSON document. */
    suspend fun export(): String = withContext(Dispatchers.IO) {
        fun dump(db: androidx.room.RoomDatabase, tables: List<String>) = JsonObject(tables.associateWith { table ->
            db.query("SELECT * FROM $table", null).use { c ->
                JsonArray(buildList {
                    while (c.moveToNext()) add(JsonObject((0 until c.columnCount).associate { i ->
                        c.getColumnName(i) to when (c.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(c.getLong(i))
                            android.database.Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(c.getDouble(i))
                            android.database.Cursor.FIELD_TYPE_NULL -> kotlinx.serialization.json.JsonNull
                            else -> JsonPrimitive(c.getString(i))
                        }
                    }))
                })
            }
        })
        val s = settings.settings.first()
        buildJsonObject {
            put("app", "Starfolk"); put("exportedAt", java.time.Instant.now().toString())
            put("settings", buildJsonObject {
                put("palette", s.palette); put("preferredRealm", s.preferredRealm.name); put("reminderTime", s.reminderTime)
                put("installAppDay", s.installAppDay?.date?.toString())
            })
            put("progress", dump(core(), listOf("completion", "daily_pick", "quest_commit", "shelter_track", "shelter_day_done", "shelter_slip")))
            put("private", dump(personal(), listOf("checkin", "star_name", "letter", "journal", "who5")))
        }.toString()
    }

    /** Deletes both databases, settings, reminders and cached share images. The caller restarts the app. */
    suspend fun wipe() = withContext(Dispatchers.IO) {
        Reminders.cancelAll(context)
        runCatching { core().close() }
        runCatching { personal().close() }
        context.deleteDatabase(CoreDatabase.NAME)
        context.deleteDatabase(PrivateDatabase.NAME)
        settings.wipe()
        File(context.cacheDir, "share").deleteRecursively()
    }
}
