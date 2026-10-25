// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data.core

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** core.db: backed up (D-017). TRUNCATE journal, so Auto Backup copies one consistent file with no -wal side file (D-032). */
@Database(
    entities = [CompletionEntity::class, DailyPickEntity::class, CommitEntity::class, AppOpenEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CoreDatabase : RoomDatabase() {
    abstract fun dao(): CoreDao

    companion object {
        const val NAME = "core.db"

        fun open(context: Context): CoreDatabase =
            Room.databaseBuilder(context, CoreDatabase::class.java, NAME)
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
    }
}
